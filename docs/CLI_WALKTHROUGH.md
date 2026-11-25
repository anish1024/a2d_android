# A2D Android - CLI Walkthrough for Login + Cloud Sync Flow

- Status: Current
- Owner: project maintainer
- Last updated: 2026-11-24

## Prerequisites
- Device must have USB debugging enabled and be connected via USB
- `adb` must be available on your host machine (works despite BOX64 warnings)
- Android app must be installed and running on the device

## Step 1: Verify Device Connection
```bash
adb devices
# Should show: RZCX627F0EV	device
```

## Step 2: Clear Logcat Buffer (optional but recommended)
```bash
adb logcat -c
```

## Step 2: Run Login Flow (matches LoginViewModel.onContinueClicked)
```bash
adb shell "curl -s -X POST 'https://devapp8000.anisandbox.org/v1/auth/login' \
  -d 'email=tester@example.com&password=secret123' | jq -r '.user_id'"
# Should return: Za64levcU1nje73vI7xwTQ
```

## Step 3: Get Access Token (for subsequent API calls)
```bash
ACCESS_TOKEN=$(adb shell "curl -s -X POST 'https://devapp8000.anisandbox.org/v1/auth/login' \
  -d 'email=tester@example.com&password=secret123' | jq -r '.access_token'")
echo "Access Token: $ACCESS_TOKEN"
```

## Step 3b: Test Sync Backup (matches SyncEngine.uploadDirtyRows)
```bash
# Empty tables test (no dirty rows)
IDEMPOTENCY_KEY=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || echo "test-key")
curl -v "https://devapp8000.anisandbox.org/v1/sync/backup" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{"tables":{"tasks":[],"action_items":[]},"lastBackupAt":0}' | jq
```

## Step 3c: Test Sync Backup with Sample Data
```bash
TASK_SYNC_ID="task-$(date +%s)"
curl -v "https://devapp8000.anisandbox.org/v1/sync/backup" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(cat /proc/sys/kernel/random/uuid)" \
  -d '{
    "tables": {
      "tasks": [
        {
          "syncId": "'"$TASK_SYNC_ID"'",
          "taskName": "Test Task",
          "description": "Created from CLI",
          "priority": 1,
          "context": "CLI Test",
          "achieved": false,
          "creationTime": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'",
          "updatedAt": $(date +%s)000,
          "deleted": false
        }
      ],
      "action_items": []
    },
    "lastBackupAt": 0
  }' | jq
```

## Step 4: Test Sync Updates (matches SyncEngine.downloadUpdates)
```bash
# First page
curl -s "https://devapp8000.anisandbox.org/v1/sync/updates?lastDownloadAt=&limit=500" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq

# Subsequent pages (if any)
# Use the nextDownloadToken from previous response:
NEXT_TOKEN=$(echo "$RESPONSE" | jq -r '.nextDownloadToken')
if [ "$NEXT_TOKEN" != "null" ] && [ "$NEXT_TOKEN" != "null" ]; then
  curl -s "https://devapp8000.anisandbox.org/v1/sync/updates?lastDownloadAt=$NEXT_TOKEN&limit=500" | jq
fi
```

## Step 5: Refresh Token (matches AuthViewModel)
```bash
curl -s "https://devapp8000.anisandbox.org/v1/auth/refresh" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq
```

## Step 6: Logout (matches LoginViewModel logout)
```bash
curl -s "https://devapp8000.anisandbox.org/v1/auth/logout" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq
```

## Key Notes
- The Android app uses **Bearer tokens** in the `Authorization` header for all API calls
- All API calls must include `Content-Type: application/json` for POST requests
- The `Idempotency-Key` header is required for sync operations to prevent duplicates
- The `user_id` returned from login is a UUID string (not Long) - this was fixed in the codebase
- All API responses are JSON with standard fields: `accepted`, `rejected`, `nextDownloadToken`, `nextDownloadAt`, `nextBatchCount`
- The backend runs on Cloudflare tunnel (devapp8000.anisandbox.org) - device must be able to reach this via public internet