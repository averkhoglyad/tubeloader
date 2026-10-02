#!/usr/bin/env bash
# Weeek API client: ticket state, comment dedup, publishing notifications
# about a push and about a PR merge.
#
# The domain knows no CI provider: the base web host comes in via --web-base, commit and
# author profile links — ready-made in the TSV, runtime data — stdin and env, global
# settings — from weeek.conf next to the script (path overridden by the WEEEK_CONF env var),
# provider path templates branchPath/prPath — from the calling adapter config; the domain
# has no defaults.
#
# Variables: TRACKER_API_TOKEN (required), DRY_RUN=1, WEEEK_CONF.

set -euo pipefail

DRY_RUN="${DRY_RUN:-0}"
TOKEN="${TRACKER_API_TOKEN:-}"
script_dir="$(dirname -- "${BASH_SOURCE[0]}")"
conf_file="${WEEEK_CONF:-${script_dir}/weeek.conf}"

fail() { printf '%s\n' "$*" >&2; exit 1; }
# Diagnostics go to stderr: stdout is reserved for the contract data (markdown, ticket state).
log() { printf '%s\n' "$*" >&2; }

usage() {
  cat <<'EOF'
usage: weeek.sh <command> [arguments]

  task-status   <id>                                state: ok|deleted|missing|error:<code>
  comment-has   <id> <marker>                       exit 0 — marker present, 1 — absent, 2 — request error
  comment-post  <id> --body-file <path>             append a comment
  notify-push   --web-base <url> [--branch <name>]  push commits: TSV
                                                    id<TAB>sha<TAB>subject<TAB>author<TAB>commit-url<TAB>author-url on stdin
  notify-pr     --web-base <url> --ticket-ids <ids>  PR merge: ticket ids come from the argument,
                                                    PR_NUMBER, PR_TITLE, PR_AUTHOR, PR_HEAD_REF,
                                                    PR_BASE_REF from env; dedup marker — pr:<PR number>

Config: WEEEK_CONF env var, otherwise weeek.conf next to the script.
Provider link templates branchPath and prPath are set by adapters only
(WEEEK_CONF points at the provider file).
Variables: TRACKER_API_TOKEN (required), DRY_RUN=1 — print without writing.
EOF
}
usage_error() { usage >&2; exit 2; }

case "${1:-}" in
  '') usage >&2; exit 1 ;;
  -h | --help | help) usage; exit 0 ;;
esac

[ -n "$TOKEN" ] || fail 'TRACKER_API_TOKEN is not set'
[ -f "$conf_file" ] || fail "config ${conf_file} not found (path is set by WEEEK_CONF)"
# shellcheck source=./weeek.conf
source "$conf_file"
for key in apiBase commentsPage apiMaxTime; do
  [ -n "${!key:-}" ] || fail "key ${key} is not set in ${conf_file}"
done

# branchPath and prPath are provider-side: the domain does not substitute them and has no default.
require_provider_key() { # key
  local value="${!1:-}"
  [ -n "$value" ] || fail "${1} is not set: the provider config (${conf_file}) or env sets the link path template"
  # without the placeholder the link is silently glued together broken, so check before API requests
  case "$value" in
    *__VALUE__*) ;;
    *) fail "${1}='${value}': no __VALUE__ placeholder, nowhere to substitute the value (${conf_file})" ;;
  esac
}

body_file="$(mktemp)"
trap 'rm -f "$body_file"' EXIT

request() { # method path [json-body] -> HTTP code, body in $body_file
  local method="$1" path="$2" data="${3:-}"
  local args=(-sS -o "$body_file" -w '%{http_code}' -X "$method"
    -H "Authorization: Bearer $TOKEN" -H 'Accept: application/json'
    --max-time "$apiMaxTime")
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' --data-binary "$data")
  curl "${args[@]}" "$apiBase$path"
}

json_string() { # stdin -> JSON string contents without quotes
  # -z: the whole input is escaped as a single stream; line-wise rules with the ':a' label after
  # s/// skipped lines appended by N, and a quote in them produced invalid JSON
  sed -z -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/\t/\\t/g' -e 's/\r/\\r/g' -e 's/\n/\\n/g'
}

check_task() { # id -> ok|deleted|missing|error:<code>
  local code
  code="$(request GET "/tm/tasks/$1")" || return 1
  case "$code" in
    200)
      if grep -q '"isDeleted": *true' "$body_file"; then printf 'deleted'; else printf 'ok'; fi
      ;;
    400) printf 'missing' ;;
    *) printf 'error:%s' "$code" ;;
  esac
}

# A transport failure under set -e would take down the whole batch, not just this ticket.
task_state() { # id -> state; 1 — the state request failed
  local state
  state="$(check_task "$1")" || return 1
  printf '%s' "$state"
}

has_marker() { # task_id marker -> 0 found, 1 not found, 2 request error
  local task_id="$1" marker="$2" offset=0 code
  while :; do
    code="$(request GET "/tm/tasks/$task_id/comments?limit=$commentsPage&offset=$offset")" || return 2
    if [ "$code" != 200 ]; then
      log "!! GET comments ${task_id} -> ${code}"
      return 2
    fi
    # slashes in the response body are escaped as \/ — otherwise a marker with a slash is not found
    if sed 's|\\/|/|g' "$body_file" | grep -qF -- "$marker"; then return 0; fi
    grep -q '"hasMore": *true' "$body_file" || return 1
    offset=$((offset + commentsPage))
  done
}

post_comment() { # task_id markdown
  local task_id="$1" markdown="$2" payload code
  if [ "$DRY_RUN" = 1 ]; then
    log "[dry] POST /tm/tasks/$task_id/comments"
    printf '%s\n' "$markdown" | sed 's/^/    | /'
    return 0
  fi
  payload="$(printf '{"markdown":"%s"}' "$(printf '%s' "$markdown" | json_string)")"
  code="$(request POST "/tm/tasks/$task_id/comments" "$payload")" || return 1
  if [ "$code" != 200 ]; then
    log "!! POST comment ${task_id} -> ${code} $(head -c 300 "$body_file")"
    return 1
  fi
  log "   comment added to ticket ${task_id}"
}

arg_web_base=''
arg_branch=''
arg_task_ids=''

parse_args() { # --web-base URL [--branch NAME] [--ticket-ids IDS]
  arg_web_base=''
  arg_branch=''
  arg_task_ids=''
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --web-base)
        [ "$#" -ge 2 ] || fail '--web-base has no value'
        arg_web_base="$2"
        shift 2
        ;;
      --branch)
        [ "$#" -ge 2 ] || fail '--branch has no value'
        arg_branch="$2"
        shift 2
        ;;
      --ticket-ids)
        [ "$#" -ge 2 ] || fail '--ticket-ids has no value'
        arg_task_ids="$2"
        shift 2
        ;;
      *) fail "unknown argument: $1" ;;
    esac
  done
  [ -n "$arg_web_base" ] || fail '--web-base is not set'
}

# Markdown for the "Commits" block of a single ticket. stdout is markdown only,
# diagnostics go to stderr; exit 1 — no new commits.
render_commits() { # task_id web_base ; stdin: "sha<TAB>subject<TAB>author<TAB>commit_url<TAB>author_url" ; 1 — none new, 2 — request error
  local task_id="$1" web_base="$2"
  local sha subject author commit_url author_url sha_short line markdown='' rc
  while IFS=$'\t' read -r sha subject author commit_url author_url; do
    [ -n "${sha:-}" ] || continue
    # dedup marker — the full sha: it lands in the link text, no other full sha is present in the comment
    rc=0
    has_marker "$task_id" "$sha" || rc=$?
    if [ "$rc" = 0 ]; then
      log "   ${sha:0:7} already marked in ticket ${task_id} — skipped"
      continue
    fi
    # 2 — the dedup request failed: publishing is not allowed, otherwise every failure produces a duplicate
    if [ "$rc" = 2 ]; then
      return 2
    fi
    sha_short="${sha:0:7}"
    # backticks must not go inside the link: Weeek moves them outside and the link breaks
    if [ -n "${commit_url:-}" ]; then
      line="- [${sha_short}](${commit_url}) ${subject:-}"
    else
      line="- ${sha_short} ${subject:-}"
    fi
    if [ -n "${author:-}" ]; then
      if [ -n "${author_url:-}" ]; then
        line+=" · author [${author}](${author_url})"
      else
        line+=" · author ${author}"
      fi
    fi
    markdown+="${line}"$'\n'
  done
  [ -n "$markdown" ] || return 1
  printf '%s' "$markdown"
}

# Host path from a template: the __VALUE__ placeholder is replaced by the value.
host_path() { # template value
  printf '%s' "${1//__VALUE__/$2}"
}

# Markdown for the "PR merge" block. The author profile link is not built here:
# its shape depends on the host, while the author name is common to all hosts.
render_merge() { # web_base marker -> markdown on stdout
  local web_base="$1" marker="$2" number="${PR_NUMBER:-}" title="${PR_TITLE:-}"
  local head_ref="${PR_HEAD_REF:-}" base_ref="${PR_BASE_REF:-main}" origin='' text
  local branch_url
  branch_url="$(host_path "$branchPath" '')"

  text="**PR merge**"$'\n\n'
  text+="[#${number}](${web_base}$(host_path "$prPath" "$number")) ${title:-}"
  [ -n "$head_ref" ] && origin="[${head_ref}](${web_base}$(host_path "$branchPath" "$head_ref")) -> "
  origin+="[${base_ref}](${web_base}${branch_url}${base_ref})"
  text+=$'\n'"${origin}"
  [ -n "${PR_AUTHOR:-}" ] && text+=" · author ${PR_AUTHOR}"
  # the dedup marker must be in the body: comments are append-only, has_marker searches the body only
  text+=$'\n'"${marker}"
  printf '%s\n' "$text"
}

notify_push() { # --web-base URL [--branch NAME] ; stdin: TSV id<TAB>sha<TAB>subject<TAB>author<TAB>commit-url<TAB>author-url
  local web_base branch id sha subject author commit_url author_url state markdown text rc
  parse_args "$@"
  web_base="$arg_web_base"
  branch="$arg_branch"
  # the range command does not read ids from an argument: a silently accepted flag would give
  # the impression that the filter was applied
  [ -z "$arg_task_ids" ] || fail 'notify-push does not accept --ticket-ids'
  # the branch path template is checked before the first API request
  [ -z "$branch" ] || require_provider_key branchPath

  local -A by_task=()   # id -> lines "sha<TAB>subject<TAB>author<TAB>commit-url<TAB>author-url"
  local -A seen=()      # id/sha -> 1, dedup within the push
  local total=0
  while IFS=$'\t' read -r id sha subject author commit_url author_url; do
    [ -n "${id:-}" ] || continue
    total=$((total + 1))
    [ "${seen["$id/$sha"]:-}" = 1 ] && continue
    seen["$id/$sha"]=1
    # links are built by the CI layer and passed in ready-made: the domain knows nothing about hosts
    by_task[$id]+="$(printf '%s\t%s\t%s\t%s\t%s' "$sha" "${subject:-}" "${author:-}" "${commit_url:-}" "${author_url:-}")"$'\n'
  done

  log "input lines: ${total}"
  if [ "$total" = 0 ] || [ "${#by_task[@]}" = 0 ]; then
    log 'no commits mentioning tickets — nothing to publish'
    return 0
  fi

  local -a task_ids
  mapfile -t task_ids < <(printf '%s\n' "${!by_task[@]}" | sort -n)

  for id in "${task_ids[@]}"; do
    rc=0
    state="$(task_state "$id")" || rc=$?
    if [ "$rc" != 0 ]; then
      log "-- ticket ${id}: state not obtained, skipped"
      continue
    fi
    case "$state" in
      ok) ;;
      deleted) log "?? ticket ${id} is deleted, the comment is written anyway" ;;
      missing) log "-- ticket ${id} not found, skipped"; continue ;;
      *) log "!! ticket ${id} -> ${state}"; continue ;;
    esac

    rc=0
    markdown="$(render_commits "$id" "$web_base" <<<"${by_task[$id]%$'\n'}")" || rc=$?
    if [ "$rc" = 2 ]; then
      log "   ticket ${id}: marker check failed — skipped"
      continue
    fi
    if [ "$rc" != 0 ]; then
      log "   ticket ${id} has no new commits"
      continue
    fi

    text="**Commits**"
    if [ -n "$branch" ]; then
      # the branch path differs between hosts: the __VALUE__ template comes in branchPath from the adapter
      text+=" to branch [${branch}](${web_base}$(host_path "$branchPath" "$branch"))"
    fi
    text+=$'\n\n'"${markdown%$'\n'}"
    # one ticket failing does not break the batch: the remaining tickets must get the notification
    post_comment "$id" "$text" || log "!! ticket ${id} not sent"
  done
}

notify_pr() { # --web-base URL --ticket-ids IDS ; env: PR_NUMBER PR_TITLE PR_AUTHOR PR_HEAD_REF PR_BASE_REF
  local web_base number title marker ids id state text rc
  parse_args "$@"
  web_base="$arg_web_base"
  number="${PR_NUMBER:-}"
  title="${PR_TITLE:-}"
  # the merge command knows refs from env: a silently accepted --branch would give the impression
  # that the passed value was used
  [ -z "$arg_branch" ] || fail 'notify-pr does not accept --branch'

  [ -n "$number" ] || fail 'PR_NUMBER is not set'
  # marker — the PR number: the merge sha appears in the push comment URL and suppressed the merge comment
  marker="pr:${number}"

  # ticket ids are resolved by the CI layer and passed as an argument; PR_TITLE is the message text only
  ids="$arg_task_ids"
  if [ -z "$ids" ]; then
    log 'no ticket ids for the PR merge notification — nothing to publish'
    return 0
  fi

  require_provider_key branchPath
  require_provider_key prPath
  text="$(render_merge "$web_base" "$marker")"

  for id in $ids; do
    rc=0
    state="$(task_state "$id")" || rc=$?
    if [ "$rc" != 0 ]; then
      log "-- ticket ${id}: state not obtained, skipped"
      continue
    fi
    case "$state" in
      ok) ;;
      deleted) log "?? ticket ${id} is deleted, the comment is written anyway" ;;
      missing) log "-- ticket ${id} not found, skipped"; continue ;;
      *) log "!! ticket ${id} -> ${state}"; continue ;;
    esac
    rc=0
    has_marker "$id" "$marker" || rc=$?
    if [ "$rc" = 0 ]; then
      log "   PR #${number} already marked in ticket ${id} — skipped"
      continue
    fi
    # 2 — the dedup request failed: publishing is not allowed, otherwise every failure produces a duplicate
    if [ "$rc" = 2 ]; then
      log "!! ticket ${id}: marker check failed — skipped"
      continue
    fi
    post_comment "$id" "$text" || log "!! ticket ${id} not sent"
  done
}

case "${1:-}" in
  task-status)
    shift
    [ "$#" = 1 ] || usage_error
    check_task "$1"
    printf '\n'
    ;;
  comment-has)
    shift
    [ "$#" = 2 ] || usage_error
    has_marker "$1" "$2"
    ;;
  comment-post)
    shift
    [ "$#" = 3 ] || usage_error
    [ "$2" = '--body-file' ] || usage_error
    [ -f "$3" ] || fail "comment body file not found: $3"
    post_comment "$1" "$(cat -- "$3")"
    ;;
  notify-push)
    shift
    notify_push "$@"
    ;;
  notify-pr)
    shift
    notify_pr "$@"
    ;;
  *) usage_error ;;
esac
