#!/usr/bin/env bash
set -e

#Prerequisite: an account must exist on that backend
# The Android app cannot self-register (A2D_ALLOW_REGISTRATION=false by default). Someone with access to the a2d-cloud backend must run:
# python -m app.cli create-user --email tester@example.com --password secret123
# against the same devapp8000.anisandbox.org backend's control DB.
# CLI walkthrough (copy-paste ready)
BASE="https://devapp8000.anisandbox.org"

echo "=== 1. Health check ==="
curl -s "$BASE/healthz" | jq

echo -e "\n=== 2. Login (matches LoginViewModel.onContinueClicked) ==="
LOGIN_RESPONSE=$(curl -s -X POST "$BASE/v1/auth/login" \
  -d "email=tester@example.com&password=secret123")
echo "$LOGIN_RESPONSE" | jq

TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.access_token')
REFRESH_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.refresh_token')
USER_ID=$(echo "$LOGIN_RESPONSE" | jq -r '.user_id')
echo "🔐 userId=$USER_ID"

echo -e "\n=== 3. Sync backup (matches SyncEngine.uploadDirtyRows) ==="
PAYLOAD='{"tables":{"tasks":[{"syncId":"task-1","taskName":"Buy groceries","description":"Milk, eggs","priority":1,"category":"Grocery","achieved":false,"creationTime":"2026-11-23T08:00:00Z","updatedAt":1732340000000,"deleted":false}]},"lastBackupAt":0}'

BACKUP_RESPONSE=$(curl -s -X POST "$BASE/v1/sync/backup" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: sync-$(date +%s)" \
  -d "$PAYLOAD") 
echo "$BACKUP_RESPONSE"

echo -e "\n=== 4. Sync updates / download (matches SyncEngine.downloadUpdates) ==="
curl -s "$BASE/v1/sync/updates?lastDownloadAt=&limit=500" \
  -H "Authorization: Bearer $TOKEN" | jq

echo -e "\n=== 5. Refresh tokens (endpoint exists; not auto-called on 401 in current Android code) ==="
curl -s -X GET "$BASE/v1/auth/refresh" \
  -H "Authorization: Bearer $TOKEN" | jq

echo -e "\n=== 6. Logout (matches AuthViewModel.logout) ==="
curl -s -X POST "$BASE/v1/auth/logout" \
  -H "Authorization: Bearer $TOKEN" | jq