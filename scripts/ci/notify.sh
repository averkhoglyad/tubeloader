#!/usr/bin/env bash
# CI-оркестратор уведомления: source-ит провайдерский конфиг, резолвит токен и делегирует
# в доменный скрипт, указанный в этом же конфиге. Адаптер (.github/scripts/tracker-notify.sh
# и зеркало) знает только событие и путь к провайдерскому конфигу — знания про домен в нём нет.
#
# Провайдерский конфиг (env TRACKER_CONF, KEY=value через source):
#   webBase     базовый URL репозитория на хосте провайдера
#   branchPath  шаблон пути ветки, плейсхолдер __VALUE__
#   prPath      шаблон пути PR, плейсхолдер __VALUE__
#   tokenEnv    имя env-переменной с секретом (значение приходит из секрета репозитория)
#   dispatcher  путь к скрипту домена (относительно корня репозитория), принимающему команды
#               notify-push (аргументы --web-base, --branch) и notify-pr (--web-base, --ticket-ids)
#
#   notify.sh push  env: BEFORE AFTER [BRANCH]
#   notify.sh pr    env: PR_NUMBER PR_TITLE PR_AUTHOR PR_HEAD_REF PR_BASE_REF

set -euo pipefail

fail() { printf '%s\n' "$*" >&2; exit 1; }
log() { printf '%s\n' "$*" >&2; }

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
conf_file="${CI_CONF:-${script_dir}/ci.conf}"

usage() {
    cat >&2 <<'USAGE'
Usage: notify.sh push | pr

push: уведомление о коммитах в push-событии (env BEFORE AFTER [BRANCH])
pr:   уведомление о слиянии PR (env PR_NUMBER PR_TITLE PR_AUTHOR PR_HEAD_REF PR_BASE_REF)

Провайдерский конфиг передаётся через env TRACKER_CONF (путь к KEY=value файлу).
USAGE
}

case "${1:-}" in
  push|pr) cmd="$1"; shift ;;
  '') usage >&2; exit 1 ;;
  *) usage >&2; exit 2 ;;
esac

[ -f "$conf_file" ] || fail "нет файла настроек ${conf_file} (путь задаётся CI_CONF)"
# shellcheck source=./ci.conf
source "$conf_file"
[ -n "${dispatcher:-}" ] || fail "не задан dispatcher в ${conf_file} (путь к скрипту домена)"

conf_file="${TRACKER_CONF:-}"
[ -n "$conf_file" ] || fail 'TRACKER_CONF не задан: адаптер должен передать путь к провайдерскому конфигу'
[ -f "$conf_file" ] || fail "нет файла провайдерского конфига ${conf_file}"

# Провайдерские ключи уходят в env процесса домена: webBase/branchPath/prPath/tokenEnv.
set -a
# shellcheck source=tracker.conf-from-env
source "$conf_file"
set +a

[ -n "${webBase:-}" ]  || fail "не задан webBase в ${conf_file}"
[ -n "${tokenEnv:-}" ] || fail "не задан tokenEnv в ${conf_file}"

# Резолв токена: имя секрета задаёт провайдер (tokenEnv), значение приходит из секрета
# репозитория. Домен читает только TRACKER_API_TOKEN — канонический канал, не зависящий
# от того, какой именно домен подключён.
[ -n "${!tokenEnv:-}" ] || fail "не задан токен ${tokenEnv} (секрет репозитория)"
TRACKER_API_TOKEN="${!tokenEnv}"
export TRACKER_API_TOKEN

notify_push() {
    local branch="${BRANCH:-}" tsv
    local -a commits_args=(--before "${BEFORE:-}" --after "${AFTER:-}")
    if [ -n "$branch" ]; then
        commits_args+=(--branch "$branch")
    fi
tsv="$(mktemp)"
    local rm_tsv="rm -f \"$tsv\""
    trap "$rm_tsv" EXIT
    bash "${script_dir}/commits.sh" "${commits_args[@]}" > "$tsv"
    log "строк TSV в диапазоне: $(wc -l < "$tsv" | tr -d ' ')"
    local -a notify_args=(--web-base "$webBase")
    if [ -n "$branch" ]; then
        notify_args+=(--branch "$branch")
    fi
    bash "$dispatcher" notify-push "${notify_args[@]}" < "$tsv"
    trap - EXIT
    rm -f "$tsv"
}

notify_pr() {
    local ids
    ids="$(printf '%s' "${PR_TITLE:-}" | bash "${script_dir}/commits.sh" --ids-from-text)"
    if [ -z "$ids" ]; then
        log 'в заголовке PR нет упоминания задач — публиковать нечего'
        return 0
    fi
    ids="$(printf '%s' "$ids" | tr '\n' ' ')"
    bash "$dispatcher" notify-pr --web-base "$webBase" --ticket-ids "$ids"
}

case "$cmd" in
  push) notify_push ;;
  pr)   notify_pr ;;
esac
