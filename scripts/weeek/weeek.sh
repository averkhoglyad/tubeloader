#!/usr/bin/env bash
# Клиент Weeek API: состояние задачи, дедуп комментариев, публикация уведомлений
# о пуше и о слиянии PR.
#
# Провайдера CI домен не знает: веб-хост ссылок приходит аргументом --web-base,
# runtime-данные — stdin и env, глобальные настройки — из weeek.conf рядом
# со скриптом (путь переопределяется env WEEEK_CONF), провайдерские шаблоны путей
# ссылок branchPath/prPath — из конфига вызывающего адаптера, у домена дефолта нет.
#
# Переменные: WEEEK_API_TOKEN (обязательна), DRY_RUN=1, WEEEK_CONF.

set -euo pipefail

DRY_RUN="${DRY_RUN:-0}"
TOKEN="${WEEEK_API_TOKEN:-}"
script_dir="$(dirname -- "${BASH_SOURCE[0]}")"
conf_file="${WEEEK_CONF:-${script_dir}/weeek.conf}"

fail() { printf '%s\n' "$*" >&2; exit 1; }
# Диагностика идёт в stderr: stdout отдан данным контракта (markdown, состояние задачи).
log() { printf '%s\n' "$*" >&2; }

usage() {
  cat <<'EOF'
usage: weeek.sh <команда> [аргументы]

  task-status   <id>                                состояние: ok|deleted|missing|error:<код>
  comment-has   <id> <marker>                       код 0 — маркер есть, 1 — нет, 2 — ошибка
  comment-post  <id> --body-file <path>             добавить комментарий
  notify-push   --web-base <url> [--branch <name>]  коммиты пуша: TSV id<TAB>sha<TAB>subject<TAB>author на stdin
  notify-pr     --web-base <url> --ticket-ids <ids>  слияние PR: id задач берутся из аргумента,
                                                    PR_NUMBER, PR_TITLE, PR_AUTHOR, PR_HEAD_REF,
                                                    PR_BASE_REF в env; маркер дедупа — pr:<номер PR>

Конфиг: env WEEEK_CONF, иначе weeek.conf рядом со скриптом.
Провайдерские шаблоны ссылок branchPath и prPath задают только адаптеры
(WEЕEK_CONF указывает на провайдерский файл).
Переменные: WEEEK_API_TOKEN (обязательна), DRY_RUN=1 — печать без записи.
EOF
}
usage_error() { usage >&2; exit 2; }

case "${1:-}" in
  '') usage >&2; exit 1 ;;
  -h | --help | help) usage; exit 0 ;;
esac

[ -n "$TOKEN" ] || fail 'WEEEK_API_TOKEN не задан'
[ -f "$conf_file" ] || fail "не найден конфиг ${conf_file} (путь задаётся WEEEK_CONF)"
# shellcheck source=./weeek.conf
source "$conf_file"
for key in apiBase commentsPage apiMaxTime; do
  [ -n "${!key:-}" ] || fail "не задан ключ ${key} в ${conf_file}"
done

# branchPath и prPath — провайдерские: домен их не подставляет и дефолта не имеет.
require_provider_key() { # key
  [ -n "${!1:-}" ] || fail "не задан ${1}: шаблон пути ссылки задаёт провайдерский конфиг (${conf_file}) или env"
}

body_file="$(mktemp)"
trap 'rm -f "$body_file"' EXIT

request() { # method path [json-body] -> HTTP-код, тело в $body_file
  local method="$1" path="$2" data="${3:-}"
  local args=(-sS -o "$body_file" -w '%{http_code}' -X "$method"
    -H "Authorization: Bearer $TOKEN" -H 'Accept: application/json'
    --max-time "$apiMaxTime")
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' --data-binary "$data")
  curl "${args[@]}" "$apiBase$path"
}

json_string() { # stdin -> содержимое JSON-строки без кавычек
  # -z: весь вход экранируется одним потоком; построчные правила с меткой ':a' после s///
  # пропускали строки, добавленные N, и кавычка в них давала невалидный JSON
  sed -z -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/\t/\\t/g' -e 's/\r/\\r/g' -e 's/\n/\\n/g'
}

check_task() { # id -> ok|deleted|missing|error:<код>
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

has_marker() { # task_id marker -> 0 найдено, 1 нет, 2 ошибка запроса
  local task_id="$1" marker="$2" offset=0 code
  while :; do
    code="$(request GET "/tm/tasks/$task_id/comments?limit=$commentsPage&offset=$offset")" || return 2
    if [ "$code" != 200 ]; then
      log "!! GET comments ${task_id} -> ${code}"
      return 2
    fi
    # в теле ответа слэши экранированы как \/ — иначе маркер со слэшем не найдётся
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
  log "   комментарий добавлен в задачу ${task_id}"
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
        [ "$#" -ge 2 ] || fail 'нет значения у --web-base'
        arg_web_base="$2"
        shift 2
        ;;
      --branch)
        [ "$#" -ge 2 ] || fail 'нет значения у --branch'
        arg_branch="$2"
        shift 2
        ;;
      --ticket-ids)
        [ "$#" -ge 2 ] || fail 'нет значения у --ticket-ids'
        arg_task_ids="$2"
        shift 2
        ;;
      *) fail "неизвестный аргумент: $1" ;;
    esac
  done
  [ -n "$arg_web_base" ] || fail 'не задан --web-base'
}

# Markdown блока «Коммиты» для одной задачи. stdout — только markdown,
# диагностика уходит в stderr; код 1 — новых коммитов нет.
render_commits() { # task_id web_base ; stdin: строки "sha<TAB>subject<TAB>author" ; 1 — новых нет, 2 — ошибка запроса
  local task_id="$1" web_base="$2"
  local sha subject author sha_short line markdown='' rc
  while IFS=$'\t' read -r sha subject author; do
    [ -n "${sha:-}" ] || continue
    # маркер дедупа — полный sha: он попадает в текст ссылки, других полных sha в комментарии нет
    rc=0
    has_marker "$task_id" "$sha" || rc=$?
    if [ "$rc" = 0 ]; then
      log "   ${sha:0:7} уже отмечен в задаче ${task_id} — пропуск"
      continue
    fi
    # 2 — запрос дедупа не удался: публиковать нельзя, иначе на каждый сбой будет дубль
    if [ "$rc" = 2 ]; then
      return 2
    fi
    sha_short="${sha:0:7}"
    # бэктики внутрь ссылки нельзя: Weeek выносит их наружу и ссылка ломается
    line="- [${sha_short}](${web_base}/commit/${sha}) ${subject:-}"
    [ -n "${author:-}" ] && line+=" · автор ${author}"
    markdown+="${line}"$'\n'
  done
  [ -n "$markdown" ] || return 1
  printf '%s' "$markdown"
}

# Путь хоста из шаблона: плейсхолдер __VALUE__ меняется на значение.
host_path() { # template value
  printf '%s' "${1//__VALUE__/$2}"
}

# Markdown блока «Слияние PR». Ссылка на профиль автора не строится:
# её форма зависит от хоста, а имя автора — общее для всех хостов.
render_merge() { # web_base marker -> markdown в stdout
  local web_base="$1" marker="$2" number="${PR_NUMBER:-}" title="${PR_TITLE:-}"
  local head_ref="${PR_HEAD_REF:-}" base_ref="${PR_BASE_REF:-main}" origin='' text
  local branch_url
  branch_url="$(host_path "$branchPath" '')"

  text="**Слияние PR**"$'\n\n'
  text+="[#${number}](${web_base}$(host_path "$prPath" "$number")) ${title:-}"
  [ -n "$head_ref" ] && origin="[${head_ref}](${web_base}$(host_path "$branchPath" "$head_ref")) -> "
  origin+="[${base_ref}](${web_base}${branch_url}${base_ref})"
  text+=$'\n'"${origin}"
  [ -n "${PR_AUTHOR:-}" ] && text+=" · автор ${PR_AUTHOR}"
  # маркер дедупа обязан быть в теле: комментарии append-only, has_marker ищет только по телу
  text+=$'\n'"${marker}"
  printf '%s\n' "$text"
}

notify_push() { # --web-base URL [--branch NAME] ; stdin: TSV id<TAB>sha<TAB>subject<TAB>author
  local web_base branch id sha subject author state markdown text rc
  parse_args "$@"
  web_base="$arg_web_base"
  branch="$arg_branch"

  local -A by_task=()   # id -> строки "sha<TAB>subject<TAB>author"
  local -A seen=()      # id/sha -> 1, дедуп внутри пуша
  local total=0
  while IFS=$'\t' read -r id sha subject author; do
    [ -n "${id:-}" ] || continue
    total=$((total + 1))
    [ "${seen["$id/$sha"]:-}" = 1 ] && continue
    seen["$id/$sha"]=1
    # перевод строки — вне подстановки: $(...) срезает завершающий \n и записи батча слипаются
    by_task[$id]+="$(printf '%s\t%s\t%s' "$sha" "${subject:-}" "${author:-}")"$'\n'
  done

  log "строк на входе: ${total}"
  if [ "$total" = 0 ] || [ "${#by_task[@]}" = 0 ]; then
    log 'нет коммитов с упоминанием задач — публиковать нечего'
    return 0
  fi

  local -a task_ids
  mapfile -t task_ids < <(printf '%s\n' "${!by_task[@]}" | sort -n)

  for id in "${task_ids[@]}"; do
    state="$(check_task "$id")"
    case "$state" in
      ok) ;;
      deleted) log "?? задача ${id} удалена, комментарий всё равно пишется" ;;
      missing) log "-- задача ${id} не найдена, пропуск"; continue ;;
      *) log "!! задача ${id} -> ${state}"; continue ;;
    esac

    rc=0
    markdown="$(render_commits "$id" "$web_base" <<<"${by_task[$id]%$'\n'}")" || rc=$?
    if [ "$rc" = 2 ]; then
      log "   задача ${id}: проверка маркеров не удалась — пропуск"
      continue
    fi
    if [ "$rc" != 0 ]; then
      log "   в задаче ${id} новых коммитов нет"
      continue
    fi

    text="**Коммиты**"
    if [ -n "$branch" ]; then
      # путь ветки у хостов разный: шаблон __VALUE__ приходит в branchPath от адаптера
      require_provider_key branchPath
      text+=" в ветку [${branch}](${web_base}$(host_path "$branchPath" "$branch"))"
    fi
    text+=$'\n\n'"${markdown%$'\n'}"
    # сбой одной задачи не обрывает батч: остальные задачи должны получить уведомление
    post_comment "$id" "$text" || log "!! задача ${id} не отправлена"
  done
}

notify_pr() { # --web-base URL --ticket-ids IDS ; env: PR_NUMBER PR_TITLE PR_AUTHOR PR_HEAD_REF PR_BASE_REF
  local web_base number title marker ids id state text rc
  parse_args "$@"
  web_base="$arg_web_base"
  number="${PR_NUMBER:-}"
  title="${PR_TITLE:-}"

  [ -n "$number" ] || fail 'PR_NUMBER не задан'
  # маркер — номер PR: sha мержа встречается в URL push-комментария и подавлял merge-комментарий
  marker="pr:${number}"

  # id задач резолвит CI-слой и передаёт аргументом; PR_TITLE — только текст сообщения
  ids="$arg_task_ids"
  if [ -z "$ids" ]; then
    log 'нет id задач для уведомления о слиянии PR — публиковать нечего'
    return 0
  fi

  require_provider_key branchPath
  require_provider_key prPath
  text="$(render_merge "$web_base" "$marker")"

  for id in $ids; do
    state="$(check_task "$id")"
    case "$state" in
      ok) ;;
      deleted) log "?? задача ${id} удалена, комментарий всё равно пишется" ;;
      missing) log "-- задача ${id} не найдена, пропуск"; continue ;;
      *) log "!! задача ${id} -> ${state}"; continue ;;
    esac
    rc=0
    has_marker "$id" "$marker" || rc=$?
    if [ "$rc" = 0 ]; then
      log "   PR #${number} уже отмечен в задаче ${id} — пропуск"
      continue
    fi
    # 2 — запрос дедупа не удался: публиковать нельзя, иначе на каждый сбой будет дубль
    if [ "$rc" = 2 ]; then
      log "!! задача ${id}: проверка маркеров не удалась — пропуск"
      continue
    fi
    post_comment "$id" "$text" || log "!! задача ${id} не отправлена"
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
    [ -f "$3" ] || fail "не найден файл с телом комментария: $3"
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
