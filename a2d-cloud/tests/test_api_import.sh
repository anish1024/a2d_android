#!/usr/bin/env bash
set -euo pipefail

# Re-test the exact web-dashboard login + import flow.
#
# Usage:
#   BASE=https://devapp8000.anisandbox.org \
#   EMAIL=importtest@example.com PASSWORD=importpass123 \
#   ./a2d-cloud/tests/test_api_import.sh
#
# Prerequisite: user exists (server-side):
#   sudo -u a2dcloud bash -c \
#     'cd /home/pi/Applications/a2d_cloud && .venv/bin/python3 -m app.cli create-user --email importtest@example.com --password importpass123'

BASE="${BASE:-https://devapp8000.anisandbox.org}"
EMAIL="${EMAIL:-importtest@example.com}"
PASSWORD="${PASSWORD:-importpass123}"

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT
SAMPLE_FILE="$TMP_DIR/sample_tasks_exact.json"

echo "=== 0. Health check ==="
curl -s "$BASE/healthz"
echo

echo "=== 1. Login (dashboard login form: POST /v1/auth/login, form-encoded) ==="
LOGIN_RESPONSE=$(curl -s -X POST "$BASE/v1/auth/login" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "email=$EMAIL" \
  --data-urlencode "password=$PASSWORD")
echo "$LOGIN_RESPONSE"

TOKEN=$(printf '%s' "$LOGIN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
echo "TOKEN length: ${#TOKEN}"

echo "=== 2. /me (dashboard fetchUserInfo -> Welcome <email>!!) ==="
curl -s "$BASE/v1/auth/me" -H "Authorization: Bearer $TOKEN"
echo

echo "=== 3. /tasks (dashboard loadTasks) ==="
curl -s "$BASE/v1/sync/tasks" -H "Authorization: Bearer $TOKEN"
echo

echo "=== 4. Generate the exact JSON sample the dashboard 'JSON sample' button downloads ==="
python3 - "$SAMPLE_FILE" <<'PY'
import json
import sys
import time

now = int(time.time() * 1000)
sample = {
    "format": "a2d.tasks",
    "formatVersion": 1,
    "tasks": [
        {
            "syncId": "1",
            "taskName": "Sample Task 1",
            "description": "This is a sample task description.",
            "priority": 1,
            "context": "Sample context",
            "achieved": False,
            "creationTime": "2023-01-01T00:00:00Z",
            "updatedAt": now,
            "deleted": False,
            "activities": [
                {
                    "syncId": "101",
                    "taskId": "1",
                    "description": "Sample activity",
                    "achieved": False,
                    "updatedAt": now,
                    "deleted": False,
                }
            ],
        },
        {
            "syncId": "2",
            "taskName": "Sample Task 2",
            "description": "Another sample task.",
            "priority": 2,
            "context": "Sample context 2",
            "achieved": True,
            "creationTime": "2023-01-02T00:00:00Z",
            "updatedAt": now,
            "deleted": False,
            "activities": [],
        },
    ],
}
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(sample, fh, indent=2)
PY
echo "Wrote $SAMPLE_FILE"

echo "=== 5. Import the sample (dashboard Import JSON: POST /v1/sync/import?dry_run=false&on_conflict=skip) ==="
curl -s -o "$TMP_DIR/import_response.json" -w "HTTP:%{http_code}\n" \
  -X POST "$BASE/v1/sync/import?dry_run=false&on_conflict=skip" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$SAMPLE_FILE"
cat "$TMP_DIR/import_response.json"
echo

echo "=== 6. Re-import the same sample (existing rows should be skipped, still HTTP 200) ==="
curl -s -o "$TMP_DIR/reimport_response.json" -w "HTTP:%{http_code}\n" \
  -X POST "$BASE/v1/sync/import?dry_run=false&on_conflict=skip" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$SAMPLE_FILE"
cat "$TMP_DIR/reimport_response.json"
echo

echo "=== 7. Logout (dashboard Logout button: POST /v1/auth/logout) ==="
curl -s -X POST "$BASE/v1/auth/logout" -H "Authorization: Bearer $TOKEN"
echo

echo "Done."