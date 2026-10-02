#!/usr/bin/env bash
# Thin GitHub runner adapter: knows only the event and the path to the provider config.
# Commit collection, ticket id resolution and notification delivery live in the CI
# orchestrator scripts/ci/notify.sh; no domain knowledge here.
#
#   tracker-notify.sh push  env: BEFORE AFTER BRANCH
#   tracker-notify.sh pr    env: PR_NUMBER PR_TITLE PR_AUTHOR PR_HEAD_REF PR_BASE_REF

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
root="$(dirname -- "$(dirname -- "$script_dir")")"

export TRACKER_CONF="${script_dir}/tracker.env"

case "${1:-}" in
  push|pr) exec bash "${root}/scripts/ci/notify.sh" "$@" ;;
  *) printf 'usage: tracker-notify.sh push|pr\n' >&2; exit 2 ;;
esac
