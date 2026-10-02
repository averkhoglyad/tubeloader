#!/usr/bin/env bash
# Тонкий адаптер GitHub-раннера: собирает runtime-данные события в аргументы/env и вызывает
# провайдер-независимый сбор коммитов (scripts/ci) и домен Weeek (scripts/weeek).
# Доменной логики Weeek и вычисления диапазона коммитов здесь нет.
#
#   tracker-notify.sh push   env: BEFORE AFTER BRANCH
#   tracker-notify.sh pr     env: PR_NUMBER PR_TITLE PR_AUTHOR PR_HEAD_REF PR_BASE_REF
#
# id задач в сообщениях коммитов и в заголовке PR резолвит CI-слой (scripts/ci);
# адаптер только передаёт готовые id домену Weeek.
#
# Переменные: WEEEK_API_TOKEN (обязательна), DRY_RUN=1 — печать без записи.

set -euo pipefail

fail() { printf '%s\n' "$*" >&2; exit 1; }

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
root="$(dirname -- "$(dirname -- "$script_dir")")"
provider_conf="${script_dir}/weeek.env"
global_conf="${root}/scripts/weeek/weeek.conf"

[ -f "$provider_conf" ] || fail "не найден провайдерский конфиг ${provider_conf}"
[ -f "$global_conf" ] || fail "не найден конфиг Weeek ${global_conf}"

# Оба конфига попадают в env процесса weeek.sh: глобальные ключи домен читает сам из своего
# файла, а провайдерские branchPath/prPath берёт из окружения после source.
set -a
# shellcheck source=./weeek.conf
source "$global_conf"
# shellcheck source=./weeek.env
source "$provider_conf"
set +a

[ -n "${webBase:-}" ] || fail 'не задан webBase в weeek.env'
token_var="${tokenEnv:-WEEEK_API_TOKEN}"
[ -n "${!token_var:-}" ] || fail "не задан токен ${token_var} (секрет репозитория)"
# домен читает только WEEEK_API_TOKEN: имя секрета задаёт провайдер, поэтому переименованную
# переменную приводим к тому имени, которое домен знает
WEEEK_API_TOKEN="${!token_var}"
export WEEEK_API_TOKEN

zero=0000000000000000000000000000000000000000

case "${1:-}" in
  push)
    # нулевой after — событие удаления ветки: коммитов для уведомления нет,
    # и commits.sh такой диапазон не разбирает
    if [ "${AFTER:-}" = "$zero" ]; then
      printf 'ветка удалена (after = нулевой sha) — публиковать нечего\n' >&2
      exit 0
    fi
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
