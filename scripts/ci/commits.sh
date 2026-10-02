#!/usr/bin/env bash
# Провайдер-независимый сбор коммитов пуша: TSV sha<TAB>subject<TAB>author в stdout.

set -euo pipefail

ZERO=0000000000000000000000000000000000000000

usage() {
    cat >&2 <<'USAGE'
Usage: commits.sh --before <sha> --after <sha> [--branch <name>] [--default-ref <ref>]

Собирает коммиты пуша и печатает их как TSV: sha<TAB>subject<TAB>author.

  --before       sha ref до пуша; 40 нулей, если ref новый
  --after        sha ref после пуша; если пуст — tip ветки, затем HEAD
  --branch       имя ветки, используется для определения --after
  --default-ref  опорный ref для новой ветки (по умолчанию refs/remotes/origin/main)

Код возврата: 0 при успехе, 2 при ошибке аргументов.
USAGE
}

before=''
after=''
branch=''
default_ref='refs/remotes/origin/main'

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
        --default-ref) take_value "$@"; default_ref="$2"; shift 2 ;;
        -h|--help)     usage; exit 0 ;;
        *)             echo "commits.sh: unknown argument: $1" >&2; usage; exit 2 ;;
    esac
done

if [ -z "$after" ] && [ -n "$branch" ]; then
    after=$(git rev-parse --verify --quiet "refs/remotes/origin/${branch}^{commit}" || true)
fi
if [ -z "$after" ]; then
    after=$(git rev-parse --verify --quiet 'HEAD^{commit}' || true)
fi

main_ref=$(git rev-parse --verify --quiet "$default_ref" || true)
range=(-1)

if [ -n "${before:-}" ] && [ "$before" != "$ZERO" ] && [ -n "$after" ] &&
   git rev-parse --verify --quiet "${before}^{commit}" > /dev/null; then
    range=("$before..$after")
elif [ -n "$after" ] && [ -n "$main_ref" ] && [ "$after" != "$main_ref" ]; then
    # before нулевой (новая ветка) или недостижимый (amend): считаем от опорного ref,
    # иначе -1 отдаёт только один коммит и история ветки теряется
    range=("$after" --not "$default_ref")
fi

echo "range=${range[*]}" >&2
git log --format='%H%x09%s%x09%an' "${range[@]}"
