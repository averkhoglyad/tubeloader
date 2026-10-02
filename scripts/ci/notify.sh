#!/usr/bin/env bash
# CI notification orchestrator: sources the provider config, resolves the token and delegates
# to the domain script named in that same config. The adapter (.github/scripts/tracker-notify.sh
# and its counterpart) knows only the event and the path to the provider config — there is no
# domain knowledge in it.
#
# Provider config (TRACKER_CONF env var, KEY=value sourced):
#   webBase            base repo URL on the provider host
#   branchPath         branch path template, placeholder __VALUE__
#   prPath             PR path template, placeholder __VALUE__
#   commitPath         commit path template, placeholder __VALUE__
#   authorUrlCommand   author profile link resolver command: argument — the author name,
#                      answer — url or an empty string; path relative to the repo root
#   tokenEnv           name of the env variable holding the secret (value comes from a repo secret)
#   dispatcher         path to the domain script relative to the repo root, accepting the
#                      notify-push (--web-base --branch) and notify-pr (--web-base --ticket-ids)
#                      commands
#
#   notify.sh push  env: BEFORE AFTER [BRANCH]
#   notify.sh pr    env: PR_NUMBER PR_TITLE PR_AUTHOR PR_HEAD_REF PR_BASE_REF

set -euo pipefail

fail() { printf '%s\n' "$*" >&2; exit 1; }
log() { printf '%s\n' "$*" >&2; }

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
conf_file="${CI_CONF:-${script_dir}/ci.conf}"
# Paths in configs are given relative to the repo root and resolved from it, not from cwd.
repo_root="$(cd -- "${script_dir}/../.." && pwd)"

usage() {
    cat >&2 <<'USAGE'
Usage: notify.sh push | pr

push: notify about commits in a push event (env BEFORE AFTER [BRANCH])
pr:   notify about a PR merge (env PR_NUMBER PR_TITLE PR_AUTHOR PR_HEAD_REF PR_BASE_REF)

The provider config is passed via the TRACKER_CONF env var (path to a KEY=value file).
USAGE
}

case "${1:-}" in
  push|pr) cmd="$1"; shift ;;
  '') usage >&2; exit 1 ;;
  *) usage >&2; exit 2 ;;
esac

[ -f "$conf_file" ] || fail "no settings file ${conf_file} (path is set by CI_CONF)"
# shellcheck source=./ci.conf
source "$conf_file"
[ -n "${dispatcher:-}" ] || fail "dispatcher is not set in ${conf_file} (path to the domain script)"

conf_file="${TRACKER_CONF:-}"
[ -n "$conf_file" ] || fail 'TRACKER_CONF is not set: the adapter must pass the provider config path'
[ -f "$conf_file" ] || fail "no provider config file ${conf_file}"

# Provider keys are exported into the domain process env: webBase/branchPath/prPath/tokenEnv.
set -a
# shellcheck source=tracker.conf-from-env
source "$conf_file"
set +a

[ -n "${webBase:-}" ]  || fail "webBase is not set in ${conf_file}"
[ -n "${tokenEnv:-}" ] || fail "tokenEnv is not set in ${conf_file}"

# Token resolution: the provider names the secret (tokenEnv), the value comes from a repo
# secret. The domain reads only TRACKER_API_TOKEN — the canonical channel, independent of
# which domain is wired in.
[ -n "${!tokenEnv:-}" ] || fail "token ${tokenEnv} is not set (repo secret)"
TRACKER_API_TOKEN="${!tokenEnv}"
export TRACKER_API_TOKEN

notify_push() {
    local branch="${BRANCH:-}" tsv
    local -a commits_args=(--before "${BEFORE:-}" --after "${AFTER:-}")
    if [ -n "$branch" ]; then
        commits_args+=(--branch "$branch")
    fi
    # Commit and author profile links are built by the CI layer from the provider templates:
    # hosts stay in the provider config, the domain receives ready URLs.
    if [ -n "${commitPath:-}" ]; then
        commits_args+=(--web-base "$webBase" --commit-path "$commitPath")
    fi
    if [ -n "${authorUrlCommand:-}" ]; then
        commits_args+=(--author-url-command "$authorUrlCommand")
    fi
tsv="$(mktemp)"
    local rm_tsv="rm -f \"$tsv\""
    trap "$rm_tsv" EXIT
    bash "${script_dir}/commits.sh" "${commits_args[@]}" > "$tsv"
    log "TSV lines in range: $(wc -l < "$tsv" | tr -d ' ')"
    local -a notify_args=(--web-base "$webBase")
    if [ -n "$branch" ]; then
        notify_args+=(--branch "$branch")
    fi
    bash "${repo_root}/${dispatcher}" notify-push "${notify_args[@]}" < "$tsv"
    trap - EXIT
    rm -f "$tsv"
}

notify_pr() {
    local ids
    ids="$(printf '%s' "${PR_TITLE:-}" | bash "${script_dir}/commits.sh" --ids-from-text)"
    if [ -z "$ids" ]; then
        log 'the PR title mentions no tickets — nothing to publish'
        return 0
    fi
    ids="$(printf '%s' "$ids" | tr '\n' ' ')"
    bash "${repo_root}/${dispatcher}" notify-pr --web-base "$webBase" --ticket-ids "$ids"
}

case "$cmd" in
  push) notify_push ;;
  pr)   notify_pr ;;
esac
