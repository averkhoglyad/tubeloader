#!/usr/bin/env bash
# Тонкий адаптер GitHub-раннера: собирает runtime-данные события в аргументы/env и вызывает
# провайдер-независимый CI-слой (сбор коммитов) и домен Weeek.
# Доменной логики Weeek и вычисления диапазона коммитов здесь нет.
# Провайдерские ключи (webBase, tokenEnv, branchPath, prPath) задаются в tracker.env рядом
# со скриптом и уходят домену через аргументы и env.
#
#   tracker-notify.sh push   env: BEFORE AFTER BRANCH
#   tracker-notify.sh pr     env: PR_NUMBER PR_TITLE PR_AUTHOR PR_HEAD_REF PR_BASE_REF
#
# id задач в сообщениях коммитов и в заголовке PR резолвит CI-слой (scripts/ci);
# адаптер только передаёт готовые id домену Weeek.
#
# Переменные: TRACKER_API_TOKEN (обязательна), DRY_RUN=1 — печать без записи.

set -euo pipefail

fail() { printf '%s\n' "$*" >&2; exit 1; }

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
root="$(dirname -- "$(dirname -- "$script_dir")")"
provider_conf="${script_dir}/tracker.env"

[ -f "$provider_conf" ] || fail "не найден провайдерский конфиг ${provider_conf}"

# Провайдерские ключи уходят в env процесса weeek.sh: branchPath/prPath домен берёт из окружения.
set -a
# shellcheck source=./tracker.env
source "$provider_conf"
set +a

[ -n "${webBase:-}" ] || fail 'не задан webBase в tracker.env'
token_var="${tokenEnv:-WEEEK_API_TOKEN}"
[ -n "${!token_var:-}" ] || fail "не задан токен ${token_var} (секрет репозитория)"
# домен читает только TRACKER_API_TOKEN: имя секрета задаёт провайдер, поэтому переименованную
# переменную приводим к тому имени, которое домен знает
TRACKER_API_TOKEN="${!token_var}"
export TRACKER_API_TOKEN

case "${1:-}" in
  push)
    tsv="$(mktemp)"
    trap 'rm -f "$tsv"' EXIT
    commits_args=(--before "${BEFORE:-}" --after "${AFTER:-}")
    notify_args=(--web-base "$webBase")
    if [ -n "${BRANCH:-}" ]; then
      commits_args+=(--branch "$BRANCH")
      notify_args+=(--branch "$BRANCH")
    fi
    bash "${root}/scripts/ci/commits.sh" "${commits_args[@]}" > "$tsv"
    printf 'строк TSV в диапазоне: %s\n' "$(wc -l < "$tsv" | tr -d ' ')" >&2
    bash "${root}/scripts/weeek/weeek.sh" notify-push "${notify_args[@]}" < "$tsv"
    ;;
  pr)
    # id задач — знание CI-слоя: резолвим из заголовка PR тем же шаблоном, что и для коммитов
    ids="$(printf '%s' "${PR_TITLE:-}" | bash "${root}/scripts/ci/commits.sh" --ids-from-text)"
    if [ -z "$ids" ]; then
      printf 'в заголовке PR нет упоминания задач — публиковать нечего\n' >&2
      exit 0
    fi
    ids="$(printf '%s' "$ids" | tr '\n' ' ')"
    bash "${root}/scripts/weeek/weeek.sh" notify-pr --web-base "$webBase" --ticket-ids "$ids"
    ;;
  *)
    fail 'usage: tracker-notify.sh push|pr'
    ;;
esac
