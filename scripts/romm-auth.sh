#!/usr/bin/env bash
# RomM API helper.
#
# Authentication priority:
#   1. CLIENT_TOKEN_FILE (long-lived bearer token from POST /api/client-tokens)
#      - Default location: ~/.romm-client-token
#      - Base URL from $ROMM_BASE_URL or a .env file (see below); no default.
#   2. OAUTH_TOKEN_FILE (legacy short-lived access/refresh pair)
#      - Default location: ~/.romm-tokens.json
#
# Config: exports from the environment, else .claude/.env, else .env at the
# project root. Required: ROMM_BASE_URL. See .env.example. Note: the
# env-file fallback gates on ROMM_BASE_URL only; strict per-file precedence
# is not guaranteed for other vars.
#
# Usage: romm-auth.sh <command> [args...]
#   call <method> <endpoint> [data]    - Make authenticated API call
#   status                             - Show current auth state

for _envf in "${CLAUDE_PROJECT_DIR:-.}/.claude/.env" "${CLAUDE_PROJECT_DIR:-.}/.env"; do
    if [[ -z "${ROMM_BASE_URL:-}" && -f "$_envf" ]]; then
        set -a; source "$_envf"; set +a
    fi
done
CLIENT_TOKEN_FILE="${ROMM_CLIENT_TOKEN_FILE:-$HOME/.romm-client-token}"
CLIENT_TOKEN_FILE="${CLIENT_TOKEN_FILE/#\~/$HOME}"
TOKEN_FILE="${ROMM_OAUTH_TOKEN_FILE:-$HOME/.romm-tokens.json}"
if [[ -z "${ROMM_BASE_URL:-}" ]]; then
    echo "romm-auth: ROMM_BASE_URL is not set (export it or define it in .claude/.env; see .env.example)" >&2
    exit 1
fi
DEFAULT_BASE_URL="$ROMM_BASE_URL"

resolve_auth() {
    # Echoes "<base_url>\t<bearer_token>\t<source>" or returns 1.
    if [[ -s "$CLIENT_TOKEN_FILE" ]]; then
        local token
        token=$(tr -d '\n\r' < "$CLIENT_TOKEN_FILE")
        if [[ -n "$token" ]]; then
            printf '%s\t%s\tclient\n' "$DEFAULT_BASE_URL" "$token"
            return 0
        fi
    fi
    if [[ -f "$TOKEN_FILE" ]]; then
        local base access
        base=$(jq -r '.base_url' "$TOKEN_FILE")
        access=$(jq -r '.access_token' "$TOKEN_FILE")
        if [[ -n "$access" && "$access" != "null" ]]; then
            printf '%s\t%s\toauth\n' "$base" "$access"
            return 0
        fi
    fi
    return 1
}

refresh_token() {
    if [[ ! -f "$TOKEN_FILE" ]]; then
        echo "Not logged in via OAuth. Use a client token instead (see header of this script)."
        return 1
    fi

    local base_url=$(jq -r '.base_url' "$TOKEN_FILE")
    local refresh=$(jq -r '.refresh_token' "$TOKEN_FILE")

    local response=$(curl -s -X POST "$base_url/api/token/refresh" \
        -H "Authorization: Bearer $refresh")

    local access_token=$(echo "$response" | jq -r '.access_token // empty')

    if [[ -z "$access_token" ]]; then
        echo "Refresh failed: $response"
        return 1
    fi

    local new_refresh=$(echo "$response" | jq -r '.refresh_token // empty')
    [[ -z "$new_refresh" ]] && new_refresh="$refresh"

    jq --arg access "$access_token" \
       --arg refresh "$new_refresh" \
       --arg time "$(date +%s)" \
       '.access_token = $access | .refresh_token = $refresh | .obtained_at = $time' \
       "$TOKEN_FILE" > "$TOKEN_FILE.tmp" && mv "$TOKEN_FILE.tmp" "$TOKEN_FILE"

    echo "Token refreshed"
}

call_api() {
    local method=$(echo "$1" | tr '[:lower:]' '[:upper:]')
    local endpoint="$2"
    local data="$3"

    local auth
    auth=$(resolve_auth) || { echo "No RomM credentials available. See header of $(basename "$0")."; return 1; }
    local base_url=$(printf '%s' "$auth" | cut -f1)
    local bearer=$(printf '%s' "$auth" | cut -f2)
    local source=$(printf '%s' "$auth" | cut -f3)

    local url="$base_url$endpoint"
    local curl_opts=(-s -X "$method" -H "Authorization: Bearer $bearer")

    if [[ -n "$data" ]]; then
        if [[ "$method" == "GET" || "$method" == "DELETE" ]]; then
            if [[ "$url" == *"?"* ]]; then
                url="$url&$data"
            else
                url="$url?$data"
            fi
        else
            curl_opts+=(-H "Content-Type: application/json" -d "$data")
        fi
    fi

    local response=$(curl "${curl_opts[@]}" "$url")

    if [[ "$source" == "oauth" ]] && echo "$response" | grep -q -i '"detail".*\(expired\|unauthorized\|forbidden\)'; then
        echo "OAuth token expired, refreshing..." >&2
        refresh_token >&2
        bearer=$(jq -r '.access_token' "$TOKEN_FILE")
        curl_opts=(-s -X "$method" -H "Authorization: Bearer $bearer")
        if [[ -n "$data" && "$method" != "GET" && "$method" != "DELETE" ]]; then
            curl_opts+=(-H "Content-Type: application/json" -d "$data")
        fi
        response=$(curl "${curl_opts[@]}" "$url")
    fi

    echo "$response" | jq . 2>/dev/null || echo "$response"
}

status() {
    local found=0
    if [[ -s "$CLIENT_TOKEN_FILE" ]]; then
        echo "Client token: $CLIENT_TOKEN_FILE ($(wc -c < "$CLIENT_TOKEN_FILE" | tr -d ' ') bytes)"
        echo "Base URL: $DEFAULT_BASE_URL"
        found=1
    fi
    if [[ -f "$TOKEN_FILE" ]]; then
        echo "OAuth token file: $TOKEN_FILE"
        jq '{base_url, obtained_at: (.obtained_at | tonumber | strftime("%Y-%m-%d %H:%M:%S")), token_preview: (.access_token | .[0:20] + "...")}' "$TOKEN_FILE"
        found=1
    fi
    if [[ $found -eq 0 ]]; then echo "No RomM credentials found"; fi
}

case "$1" in
    call)
        call_api "$2" "$3" "$4"
        ;;
    refresh)
        refresh_token
        ;;
    status)
        status
        ;;
    *)
        echo "RomM API Helper"
        echo "Auth priority: client token (\$ROMM_CLIENT_TOKEN_FILE, default ~/.romm-client-token) -> OAuth tokens"
        echo
        echo "Commands:"
        echo "  call <method> <endpoint> [data]    - API call"
        echo "  status                             - Show auth state"
        echo "  refresh                            - Refresh legacy OAuth token file"
        ;;
esac
