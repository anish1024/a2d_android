#!/usr/bin/env bash
set -e

BASE_URL="http://127.0.0.1:8000"

echo "=== 1. Login ==="
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/v1/auth/login" \
  -d "email=test@example.com&password=test123")

echo "$LOGIN_RESPONSE" | jq
TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.access_token')

if [ -z "$TOKEN" ] || [ "$TOKEN" == "null" ]; then
  echo "Failed to retrieve access token."
  exit 1
fi

PAYLOAD='{"tables":{"tasks":[{"syncId":"abc-1","taskName":"Task A","description":"do A","priority":1,"context":"ctx","achieved":false,"creationTime":"2026-11-15T08:00:00Z","updatedAt":1000,"deleted":false}]}}'

echo -e "\n=== 2. Backup a Task ==="
curl -s -X POST "$BASE_URL/v1/sync/backup" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD" | jq

echo -e "\n=== 3. Download Rows ==="
curl -s "$BASE_URL/v1/sync/updates?lastDownloadAt=&limit=500" \
  -H "Authorization: Bearer $TOKEN" | jq

echo -e "\n=== 4. Refresh Token ==="
curl -s -X GET "$BASE_URL/v1/auth/refresh" \
  -H "Authorization: Bearer $TOKEN" | jq

echo -e "\n=== 5. Idempotency (Attempt 1) ==="
curl -s -X POST "$BASE_URL/v1/sync/backup" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: my-key-001" \
  -d "$PAYLOAD" | jq

echo -e "\n=== 6. Idempotency (Attempt 2) ==="
curl -s -X POST "$BASE_URL/v1/sync/backup" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: my-key-001" \
  -d "$PAYLOAD" | jq
