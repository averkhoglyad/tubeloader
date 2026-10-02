#!/usr/bin/env bash
# Provider-independent collection of push commits: TSV
# id<TAB>sha<TAB>subject<TAB>author<TAB>commit-url<TAB>author-url on stdout. Links are built by
# the CI layer from templates passed in by the adapter: it knows nothing about hosts.
# The fifth column is the ready commit link (webBase + commitPath), the sixth is the author
# profile link returned by the command in --author-url-command; empty when the key is absent.
# The ticket number is recognised here, in the commit message: ticketPattern lives in the CI
# layer config (ci.conf next to the script, path overridden by the CI_CONF env var). The Weeek
# domain receives ids ready-made.

set -euo pipefail

ZERO=0000000000000000000000000000000000000000
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
conf_file="${CI_CONF:-${script_dir}/ci.conf}"
# Paths in configs are given relative to the repo root and resolved from it, not from cwd:
# the chain is invoked from various places and must not depend on the current directory.
repo_root="$(cd -- "${script_dir}/../.." && pwd)"

# Diagnostics go to stderr: stdout is reserved for the contract data.
log() { printf '%s\n' "$*" >&2; }
fail() { log "$*"; exit 1; }

usage() {
    cat >&2 <<'USAGE'
Usage: commits.sh --before <sha> --after <sha> [--branch <name>] [--default-ref <ref>]
                  [--web-base <url> --commit-path <tpl>] [--author-url-command <path>]
       commits.sh --ids-from-text

Collects push commits and prints them as TSV:
id<TAB>sha<TAB>subject<TAB>author<TAB>commit-url<TAB>author-url, one line per (ticket, commit) pair.
Commits without a ticket id are not printed.

  --before        sha of the ref before the push; 40 zeros if the ref is new
  --after         sha of the ref after the push; if empty — branch tip, then HEAD; 40 zeros
                  on branch deletion — the event has no commits, nothing to publish
  --branch        branch name, used to determine --after
  --default-ref   reference ref for a new branch (default refs/remotes/origin/main)
  --web-base      base repo URL; together with --commit-path yields the commit link
  --commit-path   commit path template, placeholder __VALUE__ — the full sha
  --author-url-command  author profile link resolver command: one argument — the author
                  name, one answer — the profile url or an empty string; path relative to
                  the repo root. Not given — the column is printed empty, no requests
  --ids-from-text reads text on stdin, prints ticket ids one per line;
                  incompatible with all other flags

Config: CI_CONF env var, otherwise ci.conf next to the script (key ticketPattern).
Exit code: 0 on success, 2 on argument error.
USAGE
}

before=''
after=''
branch=''
web_base=''
commit_path=''
author_url_command=''
default_ref='refs/remotes/origin/main'
default_ref_given=0
ids_from_text=0

take_value() {
    if [ "$#" -lt 2 ]; then
        echo "commits.sh: $1 requires a value" >&2
        usage
        exit 2
    fi
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --before)      take_value "$@"; before="$2"; shift 2 ;;
        --after)       take_value "$@"; after="$2"; shift 2 ;;
        --branch)      take_value "$@"; branch="$2"; shift 2 ;;
        --default-ref) take_value "$@"; default_ref="$2"; default_ref_given=1; shift 2 ;;
        --web-base)    take_value "$@"; web_base="$2"; shift 2 ;;
        --commit-path) take_value "$@"; commit_path="$2"; shift 2 ;;
        --author-url-command) take_value "$@"; author_url_command="$2"; shift 2 ;;
        --ids-from-text) ids_from_text=1; shift ;;
        -h|--help)     usage; exit 0 ;;
        *)             echo "commits.sh: unknown argument: $1" >&2; usage; exit 2 ;;
    esac
done

[ -f "$conf_file" ] || fail "no settings file ${conf_file} (path is set by CI_CONF)"
# shellcheck source=./ci.conf
source "$conf_file"
[ -n "${ticketPattern:-}" ] || fail "key ticketPattern is not set in ${conf_file}"
# grep returns code 2 for an uncompilable pattern and 1 for "no match". Without this check
# a broken ticketPattern silently disables notifications: grep is swallowed by || true,
# zero tickets, exit code 0.
pattern_rc=0
{ grep -qE "$ticketPattern" /dev/null; } || pattern_rc=$?
[ "$pattern_rc" -le 1 ] || fail "uncompilable ticketPattern in ${conf_file}: ${ticketPattern}"

# Ticket id per line; empty input yields empty output, not an error
ticket_ids() { # stdin: text
    { grep -oE "$ticketPattern" || true; } | tr -d '[]' | sort -un
}

# The text mode does not look at the range: a silently accepted --before/--after/--branch
# would give the impression that the range filter was applied.
if [ "$ids_from_text" = 1 ]; then
    if [ -n "$before" ] || [ -n "$after" ] || [ -n "$branch" ] || [ "$default_ref_given" = 1 ] ||
       [ -n "$web_base" ] || [ -n "$commit_path" ] || [ -n "$author_url_command" ]; then
        echo 'commits.sh: --ids-from-text is incompatible with --before/--after/--branch/--default-ref/--web-base/--commit-path/--author-url-command' >&2
        exit 2
    fi
    ticket_ids
    exit 0
fi

# The resolver command is set by the provider config; its absence is a config error: silently
# getting empty links is worse than a red job.
if [ -n "$author_url_command" ] && [ ! -f "${repo_root}/${author_url_command}" ]; then
    fail "author resolver command file not found: ${author_url_command}"
fi

if [ -z "$after" ] && [ -n "$branch" ]; then
    after=$(git rev-parse --verify --quiet "refs/remotes/origin/${branch}^{commit}" || true)
fi
if [ -z "$after" ]; then
    after=$(git rev-parse --verify --quiet 'HEAD^{commit}' || true)
fi

# Zero sha — a normal branch deletion, not a failure: no commits, the job stays green.
if [ "$after" = "$ZERO" ]; then
    log 'no commits in the event — nothing to publish'
    exit 0
fi

main_ref=$(git rev-parse --verify --quiet "$default_ref" || true)
range=(-1)

if [ -n "${before:-}" ] && [ "$before" != "$ZERO" ] && [ -n "$after" ] &&
   git rev-parse --verify --quiet "${before}^{commit}" > /dev/null; then
    range=("$before..$after")
elif [ -n "$after" ] && [ -n "$main_ref" ] && [ "$after" = "$main_ref" ]; then
    # first push to the default branch: after equals the remote ref, so "--not" yields nothing
    # and -1 would see a single commit. The whole history is the only correct range here.
    range=("$after")
    log 'first push to the default branch — the whole history is in range'
elif [ -n "$after" ] && [ -n "$main_ref" ] && [ "$after" != "$main_ref" ]; then
    # before is zero (new branch) or unreachable (amend): count from the reference ref,
    # otherwise -1 yields a single commit and the branch history is lost
    range=("$after" --not "$default_ref")
fi

log "range=${range[*]}"

# Flat TSV: one line per (id, commit) pair, commit order as in git log.
commits="$(git log --format='%H%x09%s%x09%an' "${range[@]}")"
total=0
skipped=0
declare -A tasks=()
# Profile resolution hits the network: cache per name, otherwise a batch of N commits by one
# author becomes N requests.
declare -A author_urls=()
while IFS=$'\t' read -r sha subject author; do
    [ -n "${sha:-}" ] || continue
    total=$((total + 1))
    ids="$(printf '%s' "${subject:-}" | ticket_ids)"
    if [ -z "$ids" ]; then
        skipped=$((skipped + 1))
        continue
    fi
    commit_url=''
    if [ -n "$web_base" ] && [ -n "$commit_path" ]; then
        commit_url="${web_base}${commit_path//__VALUE__/$sha}"
    fi
    author_url=''
    if [ -n "$author_url_command" ] && [ -n "${author:-}" ]; then
        if [ -n "${author_urls["$author"]+set}" ]; then
            author_url="${author_urls["$author"]}"
        else
            # A failed resolution means "no link", not a batch error: the command exit code
            # is not checked, an empty answer means the same.
            author_url="$(bash "${repo_root}/${author_url_command}" "$author" 2>/dev/null || true)"
            author_urls["$author"]="$author_url"
        fi
    fi
    for id in $ids; do
        tasks[$id]=1
        printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$id" "$sha" "${subject:-}" "${author:-}" "$commit_url" "$author_url"
    done
done <<<"$commits"
log "commits: ${total}, of them without a ticket id: ${skipped}; tickets: ${#tasks[@]}"
