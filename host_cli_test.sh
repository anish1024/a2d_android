#!/bin/bash
# =============================================================================
# A2D Cloud - Host-side CLI walkthrough matching the Android app flow
# Run from: the build host (pi), NOT the device
# =============================================================================

BASE="https://devapp8000.anisandbox.org"
EMAIL="tester@example.com"
PASSWORD="secret123"

echo "=== 1. Health check (matches LoginScreen server check) ==="
curl -s "$BASE/healthz" | jq

echo -e "\n=== 2. Login (matches LoginViewModel.onContinueClicked) ==="
LOGIN_RESPONSE=$(curl -s -X POST "$BASE/v1/auth/login" \
  -d "email=$EMAIL&password=$PASSWORD")
echo "$LOGIN_RESPONSE" | jq

TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.access_token')
REFRESH_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.refresh_token')
USER_ID=$(echo "$LOGIN_RESPONSE" | jq -r '.user_id')
echo "🔐 userId=$USER_ID"

echo -e "\n=== 3. Sync backup - empty tables (matches SyncEngine.uploadDirtyRows) ==="
curl -s -X POST "$BASE/v1/sync/backup" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: sync-$(date +%s)" \
  -d '{"tables":{"tasks":[],"action_items":[]},"lastBackupAt":0}' | jq

echo -e "\n=== 3b. Sync backup - with sample task ==="
curl -s -X POST "$BASE/v1/sync/backup" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: sync-$(date +%s)" \
  -d '{"tables":{"tasks":[{"syncId":"task-2","taskName":"Idempotency test","description":"","priority":2,"context":"ctx","achieved":false,"creationTime":"2026-11-23T08:00:00Z","updatedAt":1732340000000,"deleted":false}],"action_items":[]},"lastBackupAt":0}' | jq

echo -e "\n=== 4. Sync updates - first page (matches SyncEngine.downloadUpdates) ==="
curl -s "$BASE/v1/sync/updates?lastDownloadAt=&limit=500" \
  -H "Authorization: Bearer $TOKEN" | jq

echo -e "\n=== 5. Refresh token (matches AuthViewModel refresh) ==="
curl -s "$BASE/v1/auth/refresh" \
  -H "Authorization: Bearer $TOKEN" | jq

echo -e "\n=== 6. Logout (matches LoginViewModel logout) ==="
curl -s -X POST "$BASE/v1/auth/logout" \
  -H "Authorization: Bearer $TOKEN" | jq