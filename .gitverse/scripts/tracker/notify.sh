#!/usr/bin/env bash
# Публикует упоминания задач Weeek из коммитов пуша и из смерженных PR.
#
#   git log --format='%H%x09%s%x09%an' <range> | notify.sh push
#   PR_NUMBER=6 PR_TITLE='...' notify.sh pr
#
# Переменные: WEEEK_API_TOKEN (обязательна), DRY_RUN=1, GITVERSE_REPOSITORY.

set -euo pipefail

WEEEK_API_BASE="${WEEEK_API_BASE:-https://api.weeek.net/public/v1}"
GITVERSE_REPOSITORY="${GITVERSE_REPOSITORY:-averkhogliad/tube-loader}"
TICKET_PATTERN='\[[0-9]+\]'
COMMENTS_PAGE=100
API_MAX_TIME=30

MODE="${1:-}"
DRY_RUN="${DRY_RUN:-0}"
TOKEN="${WEEEK_API_TOKEN:-}"
WEB_BASE="https://gitverse.ru/${GITVERSE_REPOSITORY}"

body_file="$(mktemp)"
trap 'rm -f "$body_file"' EXIT

fail() { printf '%s\n' "$*" >&2; exit 1; }
log() { printf '%s\n' "$*"; }

case "$MODE" in
  push | pr) ;;
  *) fail 'usage: notify.sh push|pr' ;;
esac
[ -n "$TOKEN" ] || fail 'WEEEK_API_TOKEN не задан'

request() { # method path [json-body] -> HTTP-код, тело в $body_file
  local method="$1" path="$2" data="${3:-}"
  local args=(-sS -o "$body_file" -w '%{http_code}' -X "$method"
    -H "Authorization: Bearer $TOKEN" -H 'Accept: application/json'
    --max-time "$API_MAX_TIME")
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' --data-binary "$data")
  curl "${args[@]}" "$WEEEK_API_BASE$path"
}

json_string() { # stdin -> содержимое JSON-строки без кавычек
  sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e ':a' -e 'N' -e '$!ba' -e 's/\n/\\n/g'
}

ticket_ids() { # stdin текст -> id задачи по строке; пустой вход даёт пустой вывод, не ошибку
  { grep -oE "$TICKET_PATTERN" || true; } | tr -d '[]' | sort -un
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
    code="$(request GET "/tm/tasks/$task_id/comments?limit=$COMMENTS_PAGE&offset=$offset")" || return 2
    if [ "$code" != 200 ]; then
      log "!! GET comments ${task_id} -> ${code}"
      return 2
    fi
    # в теле ответа слэши экранированы как \/ — иначе маркер со слэшем не найдётся
    if sed 's|\\/|/|g' "$body_file" | grep -qF -- "$marker"; then return 0; fi
    grep -q '"hasMore": *true' "$body_file" || return 1
    offset=$((offset + COMMENTS_PAGE))
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

run_push() {
  local sha subject author ids id state total=0
  local -A by_task=()      # id -> строки "sha<TAB>subject<TAB>author"
  local -A seen=()         # id/sha -> 1, дедуп внутри пуша

  while IFS=$'\t' read -r sha subject author; do
    [ -n "${sha:-}" ] || continue
    total=$((total + 1))
    ids="$(printf '%s' "${subject:-}" | ticket_ids)"
    for id in $ids; do
      [ "${seen["$id/$sha"]:-}" = 1 ] && continue
      seen["$id/$sha"]=1
      # перевод строки — вне подстановки: $(...) срезает завершающий \n и записи батча слипаются
      by_task[$id]+="$(printf '%s\t%s\t%s' "$sha" "${subject:-}" "${author:-}")"$'\n'
    done
  done

  log "коммитов на входе: ${total}"
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

    local markdown="" sha_short line author
    while IFS=$'\t' read -r sha subject author; do
      [ -n "$sha" ] || continue
      # маркер дедупа — полный sha: он попадает в текст ссылки, других полных sha в комментарии нет
      if has_marker "$id" "$sha"; then
        log "   ${sha:0:7} уже отмечен в задаче ${id} — пропуск"
        continue
      fi
      sha_short="${sha:0:7}"
      # бэктики внутрь ссылки нельзя: сервер выносит их наружу и ссылка ломается
      line="- [${sha_short}](${WEB_BASE}/commit/${sha}) ${subject:-}"
      [ -n "${author:-}" ] && line+=" · автор ${author}"
      markdown+="${line}"$'\n'
    done <<<"${by_task[$id]}"

    if [ -z "$markdown" ]; then
      log "   в задаче ${id} новых коммитов нет"
      continue
    fi

    local text="**Коммиты**"
    if [ -n "${BRANCH:-}" ]; then
      text+=" в ветку [${BRANCH}](${WEB_BASE}/content/${BRANCH})"
    fi
    text+=$'\n\n'"${markdown%$'\n'}"
    post_comment "$id" "$text"
  done
}

run_pr() {
  local number="${PR_NUMBER:-}" title="${PR_TITLE:-}" marker text

  [ -n "$number" ] || fail 'PR_NUMBER не задан'
  marker="${PR_MERGE_SHA:-#${number}}"

  local ids
  ids="$(printf '%s' "$title" | ticket_ids)"
  if [ -z "$ids" ]; then
    log 'в заголовке PR нет упоминания задач — публиковать нечего'
    return 0
  fi

  text="**Слияние PR**"$'\n\n'
  text+="[#${number}](${WEB_BASE}/pulls/${number}) ${title:-}"
  local origin="" head_ref="${PR_HEAD_REF:-}" base_ref="${PR_BASE_REF:-main}"
  [ -n "$head_ref" ] && origin="[${head_ref}](${WEB_BASE}/content/${head_ref}) -> "
  origin+="[${base_ref}](${WEB_BASE}/content/${base_ref})"
  text+=$'\n'"${origin}"
  [ -n "${PR_AUTHOR:-}" ] && text+=" · автор @${PR_AUTHOR} [↗️](https://gitverse.ru/${PR_AUTHOR})"

  local id state
  for id in $ids; do
    state="$(check_task "$id")"
    case "$state" in
      ok) ;;
      deleted) log "?? задача ${id} удалена, комментарий всё равно пишется" ;;
      missing) log "-- задача ${id} не найдена, пропуск"; continue ;;
      *) log "!! задача ${id} -> ${state}"; continue ;;
    esac
    if has_marker "$id" "$marker"; then
      log "   PR #${number} уже отмечен в задаче ${id} — пропуск"
      continue
    fi
    post_comment "$id" "$text"
  done
}

if [ "$MODE" = push ]; then run_push; else run_pr; fi
