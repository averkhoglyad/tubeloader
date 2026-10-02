#!/usr/bin/env bash
# Hub resolver for the author profile link. The mechanism is entirely provider-specific:
# the shared CI layer calls it as an opaque command and knows nothing about the host.
#
#   resolve-author-url.sh <username>  -> profile url on stdout, or an empty string
#
# Contract: exactly one argument, exactly one answer. Profile not found, request failed,
# no token — in every case an empty answer and exit code 0: "no link" is not an error,
# otherwise a single unresolvable author would take down the whole notification batch.
set -uo pipefail

name="${1:-}"
[ -n "$name" ] || exit 0

# Percent-encoding in pure bash: spaces and non-ASCII in author names otherwise produce
# "URL rejected: Malformed input to a URL function". Checked against python3 quote on UTF-8
# and reserved characters. No runner dependencies (jq, python) required.
urlencode() {
  local s="$1" out='' c i
  local LC_ALL=C
  for (( i = 0; i < ${#s}; i++ )); do
    c="${s:i:1}"
    case "$c" in
      [A-Za-z0-9._~-]) out+="$c" ;;
      *) out+="$(printf '%%%02X' "'$c")" ;;
    esac
  done
  printf '%s' "$out"
}

api_base="${AUTHOR_API_BASE:-https://api.github.com}"
url="${api_base}/users/$(urlencode "$name")"

auth=()
[ -n "${GITHUB_TOKEN:-}" ] || printf '%s\n' "resolve-author-url: GITHUB_TOKEN not set — anonymous request, limit 60/hour" >&2
[ -n "${GITHUB_TOKEN:-}" ] && auth=(-H "Authorization: Bearer ${GITHUB_TOKEN}")

raw="$(curl -sS -m 10 "${auth[@]}" -w $'\n%{http_code}' "$url" 2>/dev/null || true)"
code="${raw##*$'\n'}"
body="${raw%$'\n'*}"

if [ "$code" != 200 ]; then
  printf '%s\n' "resolve-author-url: ${name} -> http=${code:-000}, no link" >&2
  exit 0
fi

# The profile shape is reported by the platform itself; the 404 body has no such field,
# so an empty answer is safe.
link="$(printf '%s' "$body" | grep -o '"html_url": *"[^"]*"' | head -1 | sed 's/.*"\(https[^"]*\)"/\1/' || true)"
[ -n "$link" ] || printf '%s\n' "resolve-author-url: ${name} -> profile found, but html_url is empty" >&2
printf '%s' "$link"
