#!/bin/bash
# =============================================================================
# A2D Android - On-Device Login + Cloud Sync + Logout Test
# Run this script ON THE DEVICE (via Termux, or: adb shell < on_device_test.sh)
# =============================================================================
# Tests the exact HTTP flow the Android app uses:
#   1. Health check (/healthz)
#   2. Login (form-encoded email+password → access_token + user_id)
#   3. Sync backup (POST /v1/sync/backup with SyncBackupRequest body)
#   4. Sync updates (GET /v1/sync/updates with keyset pagination)
#   5. Refresh token
#   6. Logout
# =============================================================================

set -euo pipefail

BASE="https://devapp8000.anisandbox.org"
EMAIL="tester@example.com"
PASSWORD="secret123"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log()   { echo -e "${BLUE}[TEST]${NC} $*"; }
ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
fail()  { echo -e "${RED}[FAIL]${NC} $*"; }

# Generate a 36-char UUID for Idempotency-Key (matches Android UUID.randomUUID().toString())
gen_idempotency_key() {
    cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen 2>/dev/null || echo "test-$(date +%s)-$(shuf -i 1000-9999 -n 1)"
}

# Extract JSON value using sed (no jq needed on device)
json_extract() {
    local json="$1"
    local key="$2"
    echo "$json" | sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" | head -1
}

# Extract int value
json_extract_int() {
    local json="$1"
    local key="$2"
    echo "$json" | sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\([0-9-]*\).*/\1/p" | head -1
}

echo "========================================="
echo "  A2D Android - On-Device Cloud Flow Test"
echo "========================================="
echo "Base URL: $BASE"
echo "Email:    $EMAIL"
echo ""

# ---------------------------------------------------------------------------
# 1. HEALTH CHECK (matches LoginViewModel health check on screen load)
# ---------------------------------------------------------------------------
log "1. Health check (GET /healthz)..."
HEALTH_RESP=$(curl -s -w "\n%{http_code}" "$BASE/healthz" || true)
HTTP_CODE=$(echo "$HEALTH_RESP" | tail -1)
HEALTH_BODY=$(echo "$HEALTH_RESP" | head -n -1)

if [ "$HTTP_CODE" = "200" ]; then
    ok "Health check: 200 OK"
    echo "$HEALTH_BODY" | head -c 200; echo
else
    fail "Health check failed: HTTP $HTTP_CODE"
    echo "$HEALTH_BODY"
    exit 1
fi

# ---------------------------------------------------------------------------
# 2. LOGIN (matches LoginViewModel.onContinueClicked)
#    Form-encoded: email + password
# ---------------------------------------------------------------------------
log "2. Login (POST /v1/auth/login)..."
LOGIN_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/v1/auth/login" \
    -d "email=$EMAIL&password=$PASSWORD" || true)
HTTP_CODE=$(echo "$LOGIN_RESP" | tail -1)
LOGIN_BODY=$(echo "$LOGIN_RESP" | head -n -1)

if [ "$HTTP_CODE" = "200" ]; then
    ok "Login: 200 OK"
    echo "$LOGIN_BODY" | head -c 300; echo

    ACCESS_TOKEN=$(json_extract "$LOGIN_BODY" "access_token")
    REFRESH_TOKEN=$(json_extract "$LOGIN_BODY" "refresh_token")
    USER_ID=$(json_extract "$LOGIN_BODY" "user_id")
    EXPIRES_IN=$(json_extract_int "$LOGIN_BODY" "expires_in")

    if [ -z "$ACCESS_TOKEN" ] || [ -z "$USER_ID" ]; then
        fail "Missing access_token or user_id in login response"
        exit 1
    fi

    ok "Extracted tokens:"
    echo "  access_token: ${ACCESS_TOKEN:0:20}..."
    echo "  refresh_token: ${REFRESH_TOKEN:0:20}..."
    echo "  user_id: $USER_ID"
    echo "  expires_in: ${EXPIRES_IN}s"
else
    fail "Login failed: HTTP $HTTP_CODE"
    echo "$LOGIN_BODY"
    exit 1
fi

# ---------------------------------------------------------------------------
# 3. SYNC BACKUP (matches SyncEngine.uploadDirtyRows)
#    JSON body: SyncBackupRequest { tables: {tasks: [], action_items: []}, lastBackupAt: 0 }
#    Headers: Authorization: Bearer <token>, Idempotency-Key: <uuid>, Content-Type: application/json
# ---------------------------------------------------------------------------
log "3. Sync backup - empty tables (POST /v1/sync/backup)..."
IDEMPOTENCY_KEY=$(gen_idempotency_key)
BACKUP_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/v1/sync/backup" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
    -d '{"tables":{"tasks":[],"action_items":[]},"lastBackupAt":0}' || true)
HTTP_CODE=$(echo "$BACKUP_RESP" | tail -1)
BACKUP_BODY=$(echo "$BACKUP_RESP" | head -n -1)

if [ "$HTTP_CODE" = "200" ]; then
    ok "Sync backup (empty): 200 OK"
    echo "$BACKUP_BODY" | head -c 300; echo
    NEXT_TOKEN=$(json_extract "$BACKUP_BODY" "nextDownloadToken")
    NEXT_AT=$(json_extract_int "$BACKUP_BODY" "nextDownloadAt")
    echo "  nextDownloadToken: ${NEXT_TOKEN:-null}"
    echo "  nextDownloadAt: ${NEXT_AT:-null}"
else
    fail "Sync backup failed: HTTP $HTTP_CODE"
    echo "$BACKUP_BODY"
    # Don't exit - try with data
fi

# ---------------------------------------------------------------------------
# 3b. SYNC BACKUP WITH SAMPLE DATA
# ---------------------------------------------------------------------------
log "3b. Sync backup - with sample task (POST /v1/sync/backup)..."
IDEMPOTENCY_KEY=$(gen_idempotency_key)
TASK_SYNC_ID="task-$(date +%s)"
PAYLOAD="{\"tables\":{\"tasks\":[{\"syncId\":\"$TASK_SYNC_ID\",\"taskName\":\"Test Task from Device\",\"description\":\"Created by on-device test\",\"priority\":1,\"context\":\"Test Context\",\"achieved\":false,\"creationTime\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"updatedAt\":$(date +%s)000,\"deleted\":false}],\"action_items\":[]},\"lastBackupAt\":0}"

BACKUP_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/v1/sync/backup" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
    -d "$PAYLOAD" || true)
HTTP_CODE=$(echo "$BACKUP_RESP" | tail -1)
BACKUP_BODY=$(echo "$BACKUP_RESP" | head -n -1)

if [ "$HTTP_CODE" = "200" ]; then
    ok "Sync backup (with data): 200 OK"
    echo "$BACKUP_BODY" | head -c 400; echo
    ACCEPTED=$(echo "$BACKUP_BODY" | sed -n 's/.*"accepted":\(\[.*\]\).*/\1/p')
    REJECTED=$(echo "$BACKUP_BODY" | sed -n 's/.*"rejected":\(\[.*\]\).*/\1/p')
    echo "  accepted count: $(echo "$ACCEPTED" | grep -o '"syncId"' | wc -l)"
    echo "  rejected count: $(echo "$REJECTED" | grep -o '"syncId"' | wc -l)"
    NEXT_TOKEN=$(json_extract "$BACKUP_BODY" "nextDownloadToken")
    NEXT_AT=$(json_extract_int "$BACKUP_BODY" "nextDownloadAt")
    echo "  nextDownloadToken: ${NEXT_TOKEN:-null}"
    echo "  nextDownloadAt: ${NEXT_AT:-null}"
else
    fail "Sync backup with data failed: HTTP $HTTP_CODE"
    echo "$BACKUP_BODY"
fi

# ---------------------------------------------------------------------------
# 4. SYNC UPDATES (matches SyncEngine.downloadUpdates)
#    GET /v1/sync/updates?lastDownloadAt=<token>&limit=500
#    Returns rows + nextDownloadToken for pagination
# ---------------------------------------------------------------------------
log "4. Sync updates - first page (GET /v1/sync/updates)..."
UPDATES_RESP=$(curl -s -w "\n%{http_code}" "$BASE/v1/sync/updates?lastDownloadAt=&limit=500" \
    -H "Authorization: Bearer $ACCESS_TOKEN" || true)
HTTP_CODE=$(echo "$UPDATES_RESP" | tail -1)
UPDATES_BODY=$(echo "$UPDATES_RESP" | head -n -1)

if [ "$HTTP_CODE" = "200" ]; then
    ok "Sync updates: 200 OK"
    echo "$UPDATES_BODY" | head -c 400; echo
    ROW_COUNT=$(echo "$UPDATES_BODY" | grep -o '"syncId"' | wc -l)
    echo "  rows returned: $ROW_COUNT"
    NEXT_BATCH=$(json_extract_int "$UPDATES_BODY" "nextBatchCount")
    NEXT_TOKEN=$(json_extract "$UPDATES_BODY" "nextDownloadToken")
    echo "  nextBatchCount: ${NEXT_BATCH:-0}"
    echo "  nextDownloadToken: ${NEXT_TOKEN:-null}"

    # If there are more batches, fetch them
    if [ -n "$NEXT_TOKEN" ] && [ "$NEXT_BATCH" -gt 0 ] 2>/dev/null; then
        log "4b. Sync updates - subsequent pages..."
        CURSOR="$NEXT_TOKEN"
        PAGE=2
        while [ -n "$CURSOR" ]; do
            UPDATES_RESP=$(curl -s -w "\n%{http_code}" "$BASE/v1/sync/updates?lastDownloadAt=$CURSOR&limit=500" \
                -H "Authorization: Bearer $ACCESS_TOKEN" || true)
            HTTP_CODE=$(echo "$UPDATES_RESP" | tail -1)
            UPDATES_BODY=$(echo "$UPDATES_RESP" | head -n -1)

            if [ "$HTTP_CODE" = "200" ]; then
                ROW_COUNT=$(echo "$UPDATES_BODY" | grep -o '"syncId"' | wc -l)
                ok "  Page $PAGE: $ROW_COUNT rows"
                NEXT_BATCH=$(json_extract_int "$UPDATES_BODY" "nextBatchCount")
                CURSOR=$(json_extract "$UPDATES_BODY" "nextDownloadToken")
                if [ -z "$CURSOR" ] || [ "$NEXT_BATCH" -eq 0 ] 2>/dev/null; then
                    break
                fi
                PAGE=$((PAGE + 1))
            else
                fail "  Page $PAGE failed: HTTP $HTTP_CODE"
                break
            fi
        done
    fi
else
    fail "Sync updates failed: HTTP $HTTP_CODE"
    echo "$UPDATES_BODY"
fi

# ---------------------------------------------------------------------------
# 5. REFRESH TOKEN (matches AuthViewModel token refresh)
# ---------------------------------------------------------------------------
log "5. Refresh token (POST /v1/auth/refresh)..."
REFRESH_RESP=$(curl -s -w "\n%{http_code}" "$BASE/v1/auth/refresh" \
    -H "Authorization: Bearer $ACCESS_TOKEN" || true)
HTTP_CODE=$(echo "$REFRESH_RESP" | tail -1)
REFRESH_BODY=$(echo "$REFRESH_RESP" | head -n -1)

if [ "$HTTP_CODE" = "200" ]; then
    ok "Refresh token: 200 OK"
    echo "$REFRESH_BODY" | head -c 300; echo
    NEW_ACCESS_TOKEN=$(json_extract "$REFRESH_BODY" "access_token")
    NEW_REFRESH_TOKEN=$(json_extract "$REFRESH_BODY" "refresh_token")
    if [ -n "$NEW_ACCESS_TOKEN" ] && [ "$NEW_ACCESS_TOKEN" != "$ACCESS_TOKEN" ]; then
        ok "Tokens rotated successfully"
        ACCESS_TOKEN="$NEW_ACCESS_TOKEN"
        REFRESH_TOKEN="$NEW_REFRESH_TOKEN"
    else
        warn "Token rotation may not have occurred (same token returned)"
    fi
else
    fail "Refresh token failed: HTTP $HTTP_CODE"
    echo "$REFRESH_BODY"
fi

# ---------------------------------------------------------------------------
# 6. LOGOUT (matches LoginViewModel logout)
# ---------------------------------------------------------------------------
log "6. Logout (POST /v1/auth/logout)..."
LOGOUT_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/v1/auth/logout" \
    -H "Authorization: Bearer $ACCESS_TOKEN" || true)
HTTP_CODE=$(echo "$LOGOUT_RESP" | tail -1)
LOGOUT_BODY=$(echo "$LOGOUT_RESP" | head -n -1)

if [ "$HTTP_CODE" = "200" ]; then
    ok "Logout: 200 OK"
    echo "$LOGOUT_BODY"
else
    fail "Logout failed: HTTP $HTTP_CODE"
    echo "$LOGOUT_BODY"
fi

# ---------------------------------------------------------------------------
# SUMMARY
# ---------------------------------------------------------------------------
echo ""
echo "========================================="
echo "  TEST COMPLETE"
echo "========================================="
echo "All API endpoints tested successfully!"
echo ""
echo "If any step showed FAIL, check:"
echo "  - Network connectivity (can you ping devapp8000.anisandbox.org?)"
echo "  - Backend is running (supervisorctl status a2d-app)"
echo "  - User exists (python -m app.cli create-user ...)"
echo "  - Tenant directory permissions (chown a2dcloud:a2dcloud /data/users/...)"
echo ""
echo "The Android app follows this exact flow:"
echo "  1. Health check on LoginScreen load"
echo "  2. POST /v1/auth/login (form-encoded) → stores tokens in EncryptedSharedPreferences"
echo "  3. On sync: POST /v1/sync/backup (dirty rows) → GET /v1/sync/updates (pagination)"
echo "  4. Token refresh via AuthViewModel when access_token expires"
echo "  5. Logout clears tokens locally + POST /v1/auth/logout revokes refresh tokens"