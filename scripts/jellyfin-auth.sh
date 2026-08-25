#!/usr/bin/env bash
# Jellyfin API helper.
#
# Mirrors scripts/romm-auth.sh so both servers are reachable the same way.
#
# Authentication:
#   A token file holds the access token and user id obtained from
#   POST /Users/AuthenticateByName.
#     - Default location: ~/.jellyfin-token.json
#     - Override with $JELLYFIN_TOKEN_FILE
#   The header format matches what the app sends
#   (JellyfinApiFactory.buildAuthorizationHeader), so a request made here
#   exercises the same auth path the client does.
#
# Config: exports from the environment, else .claude/.env, else .env at the
# project root. Required: JELLYFIN_BASE_URL.
#
# Usage: jellyfin-auth.sh <command> [args...]
#   login [user] [password]         - Authenticate and write the token file
#   call <method> <endpoint> [data] - Make an authenticated API call
#   me                              - Show the authenticated user
#   status                          - Show current auth state

set -uo pipefail

CLIENT_NAME="Argosy"
DEVICE_NAME="argosy-cli"
DEVICE_ID="argosy-cli-$(hostname | tr -cd '[:alnum:]-')"
CLIENT_VERSION="dev"

for _envf in "${CLAUDE_PROJECT_DIR:-.}/.claude/.env" "${CLAUDE_PROJECT_DIR:-.}/.env"; do
    if [[ -z "${JELLYFIN_BASE_URL:-}" && -f "$_envf" ]]; then
        set -a; source "$_envf"; set +a
    fi
done

TOKEN_FILE="${JELLYFIN_TOKEN_FILE:-$HOME/.jellyfin-token.json}"
TOKEN_FILE="${TOKEN_FILE/#\~/$HOME}"

if [[ -z "${JELLYFIN_BASE_URL:-}" ]]; then
    echo "jellyfin-auth: JELLYFIN_BASE_URL is not set (export it or define it in .claude/.env)" >&2
    exit 1
fi
BASE_URL="${JELLYFIN_BASE_URL%/}"

auth_header() {
    # $1 = token, may be empty for the pre-auth call.
    local parts="Client=\"$CLIENT_NAME\", Device=\"$DEVICE_NAME\""
    parts="$parts, DeviceId=\"$DEVICE_ID\", Version=\"$CLIENT_VERSION\""
    if [[ -n "${1:-}" ]]; then
        parts="$parts, Token=\"$1\""
    fi
    printf 'MediaBrowser %s' "$parts"
}

login() {
    local user="${1:-${JELLYFIN_USERNAME:-}}"
    local pass="${2:-${JELLYFIN_PASSWORD:-}}"
    if [[ -z "$user" ]]; then
        read -r -p "Jellyfin username: " user
    fi
    if [[ -z "$pass" ]]; then
        read -r -s -p "Jellyfin password: " pass
        echo
    fi

    local body
    body=$(jq -nc --arg u "$user" --arg p "$pass" '{Username: $u, Pw: $p}')

    local response
    response=$(curl -s -X POST "$BASE_URL/Users/AuthenticateByName" \
        -H "Authorization: $(auth_header '')" \
        -H "Content-Type: application/json" \
        -d "$body")

    local token user_id
    token=$(printf '%s' "$response" | jq -r '.AccessToken // empty')
    user_id=$(printf '%s' "$response" | jq -r '.User.Id // empty')

    if [[ -z "$token" ]]; then
        echo "Login failed: $response" >&2
        return 1
    fi

    local prior_umask
    prior_umask=$(umask)
    umask 077
    jq -nc --arg b "$BASE_URL" --arg t "$token" --arg u "$user_id" \
        --arg n "$user" --arg time "$(date +%s)" \
        '{base_url: $b, access_token: $t, user_id: $u, username: $n, obtained_at: $time}' \
        > "$TOKEN_FILE"
    umask "$prior_umask"

    echo "Authenticated as $user (user id $user_id); token written to $TOKEN_FILE"
}

resolve_token() {
    # Echoes "<base_url>\t<token>\t<user_id>" or returns 1.
    [[ -f "$TOKEN_FILE" ]] || return 1
    local base token user_id
    base=$(jq -r '.base_url // empty' "$TOKEN_FILE")
    token=$(jq -r '.access_token // empty' "$TOKEN_FILE")
    user_id=$(jq -r '.user_id // empty' "$TOKEN_FILE")
    [[ -n "$token" ]] || return 1
    [[ -n "$base" ]] || base="$BASE_URL"
    printf '%s\t%s\t%s\n' "$base" "$token" "$user_id"
}

call_api() {
    local method
    method=$(echo "${1:-GET}" | tr '[:lower:]' '[:upper:]')
    local endpoint="${2:-}"
    local data="${3:-}"

    local auth
    auth=$(resolve_token) || {
        echo "No Jellyfin credentials. Run: scripts/jellyfin-auth.sh login" >&2
        return 1
    }
    local base_url token user_id
    base_url=$(printf '%s' "$auth" | cut -f1)
    token=$(printf '%s' "$auth" | cut -f2)
    user_id=$(printf '%s' "$auth" | cut -f3)

    # {userId} is the common path/query placeholder in Jellyfin docs; expand it
    # so an endpoint can be pasted from the docs unchanged.
    endpoint="${endpoint//\{userId\}/$user_id}"
    endpoint="${endpoint//\{UserId\}/$user_id}"

    local url="$base_url$endpoint"
    local curl_opts=(-s -X "$method" -H "Authorization: $(auth_header "$token")")

    if [[ -n "$data" ]]; then
        if [[ "$method" == "GET" || "$method" == "DELETE" ]]; then
            if [[ "$url" == *"?"* ]]; then url="$url&$data"; else url="$url?$data"; fi
        else
            curl_opts+=(-H "Content-Type: application/json" -d "$data")
        fi
    fi

    local response
    response=$(curl "${curl_opts[@]}" "$url")
    echo "$response" | jq . 2>/dev/null || echo "$response"
}

me() {
    call_api GET "/Users/Me"
}

status() {
    if [[ ! -f "$TOKEN_FILE" ]]; then
        echo "No Jellyfin credentials found ($TOKEN_FILE absent)"
        echo "Base URL: $BASE_URL"
        return
    fi
    jq '{base_url, username, user_id,
         obtained_at: (.obtained_at | tonumber | strftime("%Y-%m-%d %H:%M:%S")),
         token_preview: (.access_token | .[0:12] + "...")}' "$TOKEN_FILE"
}

case "${1:-}" in
    login)  login "${2:-}" "${3:-}" ;;
    call)   call_api "${2:-}" "${3:-}" "${4:-}" ;;
    me)     me ;;
    status) status ;;
    *)
        echo "Jellyfin API Helper"
        echo "Token file: \$JELLYFIN_TOKEN_FILE (default ~/.jellyfin-token.json)"
        echo "Base URL:   \$JELLYFIN_BASE_URL (or .claude/.env)"
        echo
        echo "Commands:"
        echo "  login [user] [password]         - Authenticate, write token file"
        echo "  call <method> <endpoint> [data] - API call ({userId} is expanded)"
        echo "  me                              - Show the authenticated user"
        echo "  status                          - Show auth state"
        ;;
esac
