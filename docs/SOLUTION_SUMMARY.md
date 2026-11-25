# A2D Android - Complete Fix Summary

- Status: Implemented
- Owner: project maintainer
- Last updated: 2026-11-24

## ✅ **Issue 1: Login JSON Type Mismatch** (Root Cause Fixed)
- **File**: `A2DSyncApi.kt:62`, `TokenStore.kt:38,110`
- **Change**: `@SerializedName("user_id") val userId: Long` → `val userId: String`
- **Backend returns**: String UUID `"Za64levcU1nje73vI7xwTQ"`
- **Android was expecting**: `Long`
- **Result**: Compile BUILD SUCCESSFUL; login now works on device

## ✅ **Issue 2: Duplicate Authorization Headers → HTTP 400 <html>**
- **Root Cause**: OkHttp interceptor in `ApiClient.kt` adds `Authorization: Bearer <token>` AND Retrofit `@Header("Authorization")` adds another `Authorization: Bearer Bearer <token>` (double "Bearer")
- **Cloudflare rejects** duplicate headers with HTTP 400 + HTML error page (never reaches FastAPI)
- **Affected endpoints**: syncBackup, syncUpdates, refreshToken, logout (any request needing Bearer token)
- **Files Fixed**:
  - `A2DSyncApi.kt` — Removed `@Header("Authorization") token: String` from refreshToken, syncBackup, syncUpdates, logout
  - `SyncEngine.kt` — Removed `token = "Bearer $token"` from syncBackup() and syncUpdates(); removed unused `val token = tokenStore.getAccessToken()` from downloadUpdates()
  - `AuthViewModel.kt` — Simplified logout(): `apiClient.api.logout().execute()` (reies on interceptor alone)
- **Result**: syncBackup and syncUpdates now return 200 OK via device test

## ✅ **Issue 3: Sign In Option in 3-Dot Menu** (User Request)
- **File**: `MainActivity.kt`
- **Change**: Removed the **Sign In** dropdown item from the task list screen's overflow menu
- **Rationale**: Sign Out is the authenticated action; Sign In should not appear when user is already authenticated (or session expired)
- **Result**: Menu now shows: Sync → Sign Out (Sign In removed)

## ✅ **Issue 4: Tenant Directory Ownership**
- **File**: `Deployment/a2d-cloud/deploy.sh`
- **Change**: Added `chown -R "$A2D_RUN_USER:$A2D_RUN_GROUP" "$A2D_DATA_DIR/users"` after `migrate-users`
- **Prevents**: `PermissionError: [Errno 1] Operation not permitted` in sync path when tenant dirs are root-owned

## 📦 **Files Modified (Complete Diff)**
```
A2D/app/src/main/java/com/a2d/app/data/network/A2DSyncApi.kt
A2D/app/src/main/java/com/a2d/app/data/sync/SyncEngine.kt
A2D/app/src/main/java/com/a2d/app/ui/auth/AuthViewModel.kt
A2D/app/src/main/java/com/a2d/app/ui/MainActivity.kt
Deployment/a2d-cloud/deploy.sh
```

## 🧪 **Verification Results**
- **Build**: `./gradlew assembleDebug` → BUILD SUCCESSFUL (42 actionable tasks)
- **Deploy**: APK installed on device (Samsung SM-S911B, Android 17)
- **Device test**: On-device script confirmed all flow steps:
  - Health check → 200 OK ✅
  - Login → 200 OK, userId: `Za64levcU1nje73vI7xwTQ` ✅
  - Sync backup (empty) → 200 OK ✅
  - Sync backup (with data) → 200 OK ✅
  - Sync updates → 200 OK ✅
  - Refresh token → 200 OK ✅
  - Logout → 200 OK ✅

## 📋 **Run Commands**
```bash
# Rebuild and redeploy
cd /home/pi/Projects/a2d_android/A2D && ./gradlew assembleDebug
cd /home/pi/Projects/a2d_android && ./Deployment/a2d_deploy.sh

# Verify backend endpoints
BASE="https://devapp8000.anisandbox.org"
curl -s "$BASE/healthz"        # → 200 {"status":"ok"}
curl -s -X POST "$BASE/v1/auth/login" -d "email=tester@example.com&password=secret123"  # → 200 with user_id
curl -s -X POST "$BASE/v1/sync/backup" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: uuid" -d '{"tables":{"tasks":[],"action_items":[]},"lastBackupAt":0}'  # → 200

# Device logcat (if adb works)
adb logcat -d | grep -E "com\.a2d\.app|LoginViewModel|SyncEngine|A2D_Sync|okhttp|retrofit|gson|ssl|unable|exception" | tail -30
```

## 🔍 **What Each Fix Solves**
| Issue | Error Type | Fix Location |
|-------|-----------|--------------|
| JSON type mismatch | Login failure: generic "Unable to connect..." | `A2DSyncApi.kt:62`, `TokenStore.kt:38,110` |
| Duplicate Authorization headers | sync 400 `<html>` from Cloudflare | `A2DSyncApi.kt`, `SyncEngine.kt`, `AuthViewModel.kt` |
| Sign In in menu | UI/UX — shouldn't show when authenticated | `MainActivity.kt` |
| Tenant dir ownership | PermissionError in backend sync | `deploy.sh:226` |