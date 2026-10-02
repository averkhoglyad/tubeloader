#!/usr/bin/env bash
# Провайдер-независимый сбор коммитов пуша: TSV id<TAB>sha<TAB>subject<TAB>author в stdout.
# Номер задачи в сообщении коммита распознаётся здесь: ticketPattern живёт в конфиге CI-слоя
# (ci.conf рядом со скриптом, путь переопределяется env CI_CONF). Домен Weeek получает id готовыми.

set -euo pipefail

ZERO=0000000000000000000000000000000000000000
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
conf_file="${CI_CONF:-${script_dir}/ci.conf}"

# Диагностика идёт в stderr: stdout отдан данным контракта.
log() { printf '%s\n' "$*" >&2; }
fail() { log "$*"; exit 1; }

usage() {
    cat >&2 <<'USAGE'
Usage: commits.sh --before <sha> --after <sha> [--branch <name>] [--default-ref <ref>]
       commits.sh --ids-from-text

Собирает коммиты пуша и печатает их как TSV: id<TAB>sha<TAB>subject<TAB>author,
по строке на пару (задача, коммит). Коммиты без id задачи не печатаются.

  --before        sha ref до пуша; 40 нулей, если ref новый
  --after         sha ref после пуша; если пуст — tip ветки, затем HEAD
  --branch        имя ветки, используется для определения --after
  --default-ref   опорный ref для новой ветки (по умолчанию refs/remotes/origin/main)
  --ids-from-text читает текст на stdin, печатает id задач по одному в строке

Конфиг: env CI_CONF, иначе ci.conf рядом со скриптом (ключ ticketPattern).
Код возврата: 0 при успехе, 2 при ошибке аргументов.
USAGE
}

before=''
after=''
branch=''
default_ref='refs/remotes/origin/main'
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
        --default-ref) take_value "$@"; default_ref="$2"; shift 2 ;;
        --ids-from-text) ids_from_text=1; shift ;;
        -h|--help)     usage; exit 0 ;;
        *)             echo "commits.sh: unknown argument: $1" >&2; usage; exit 2 ;;
    esac
done

[ -f "$conf_file" ] || fail "нет файла настроек ${conf_file} (путь задаётся CI_CONF)"
# shellcheck source=./ci.conf
source "$conf_file"
[ -n "${ticketPattern:-}" ] || fail "не задан ключ ticketPattern в ${conf_file}"

# id задачи по строке; пустой вход даёт пустой вывод, не ошибку
ticket_ids() { # stdin: текст
    { grep -oE "$ticketPattern" || true; } | tr -d '[]' | sort -un
}

if [ "$ids_from_text" = 1 ]; then
    ticket_ids
    exit 0
fi

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

log "range=${range[*]}"

# Плоский TSV: строка на пару (id, коммит), порядок коммитов — как у git log.
commits="$(git log --format='%H%x09%s%x09%an' "${range[@]}")"
total=0
skipped=0
declare -A tasks=()
while IFS=$'\t' read -r sha subject author; do
    [ -n "${sha:-}" ] || continue
    total=$((total + 1))
    ids="$(printf '%s' "${subject:-}" | ticket_ids)"
    if [ -z "$ids" ]; then
        skipped=$((skipped + 1))
        continue
    fi
    for id in $ids; do
        tasks[$id]=1
        printf '%s\t%s\t%s\t%s\n' "$id" "$sha" "${subject:-}" "${author:-}"
    done
done <<<"$commits"
log "коммитов: ${total}, из них без id задачи: ${skipped}; задач: ${#tasks[@]}"
