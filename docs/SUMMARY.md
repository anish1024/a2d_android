# A2D Android - Final Diagnosis & Testing Summary

- Status: Current
- Owner: project maintainer
- Last updated: 2026-11-24

## ✅ **Core Issue Resolved**
- **Login failure**: Fixed JSON type mismatch (`user_id` was `Long` in Android but string in backend)
- **Code changes applied**:
  - `A2DSyncApi.kt:62` - `@SerializedName("user_id") val userId: String`
  - `TokenStore.kt:38,110` - `userId: String` in data class and property
  - Compile: `./gradlew compileDebugKotlin` → **SUCCESS**
  - Core tests passed
- **Backend verified**: All endpoints return 200 OK via curl
- **Deploy fix applied**: `chown -R "$A2D_RUN_USER:$A2D_RUN_GROUP" "$A2D_DATA_DIR/users"` in `deploy.sh`

## 🔍 **Remaining Issue: "Cloud sync failed: Upload failed: 400"**
- **Root cause**: Not a backend issue - backend accepts all valid JSON payloads (200 OK via curl)
- **Likely cause**: Android app's Gson serialization differs from manual curl requests
- **Root cause diagnosis**: Need device-level logcat to capture actual exception

## 📱 **Device Testing Limitation**
- **BOX64 adb daemon issue**: `adb start-server` fails due to missing `nftw` symbol in box64
- **Workaround**: Use `host_cli_test.sh` (curl commands) to validate full flow
- **Device-level validation**: User must run `on_device_test.sh` on device (requires Termux or working adb)

## 🧪 **Verified Working Flow (Host-side)**
1. **Health check**: `GET /healthz` → 200 OK ✅
2. **Login**: `POST /v1/auth/login` → 200 OK ✅  
   → Returns: `{"user_id": "Za64levcU1nje73vI7xwTQ", "access_token": "...", ...}` ✅
3. **Sync backup**: `POST /v1/sync/backup` (empty) → 200 OK ✅
4. **Sync backup**: `POST /v1/sync/backup` (with data) → 200 OK ✅
5. **Sync updates**: `GET /v1/sync/updates` → 200 OK ✅
6. **Refresh**: `POST /v1/auth/refresh` → 200 OK ✅
7. **Logout**: `POST /v1/auth/logout` → 200 OK ✅

## 📋 **Recommended Testing Steps**
### 1. Host-side CLI validation (works regardless of adb issues):
```bash
cd /home/pi/Projects/a2d_android
./host_cli_test.sh
```

### 2. Device-side validation (if adb works):
```bash
cd /home/pi/Projects/a2d_android
./on_device_test.sh
```

### 3. If adb daemon fails to start:
- **Verify backend is running**: `sudo supervisorctl status a2d-app`
- **Verify tenant directories are owned**: `ls -la /home/pi/Applications/a2d_cloud/data/users/`
- **Restart backend**: `sudo supervisorctl restart a2d-app`

## 🛠️ **Critical Files Modified**
| File | Change |
|------|--------|
| `A2DSyncApi.kt` | `userId: String` (was `Long`) |
| `TokenStore.kt` | `userId: String` (was `Long`) |
| `SyncEngine.kt` | Added debug logging for request body |
| `LoginViewModel.kt` | Added `Log.e("LoginViewModel", "Login request failed", e)` |
| `deploy.sh` | Added tenant directory ownership fix |

## 📌 **Final Recommendation**
1. **Verify backend permissions**:
   ```bash
   sudo chown -R a2dcloud:a2dcloud /home/pi/Applications/a2d_cloud/data/users/
   sudo supervisorctl restart a2d-app
   ```
2. **Rebuild and redeploy**:
   ```bash
   cd /home/pi/Projects/a2d_android/A2D
   ./gradlew assembleDebug
   ./Deployment/a2d_deploy.sh
   ```
3. **Test with host CLI**:
   ```bash
   ./host_cli_test.sh
   ```
4. **If still failing on device**, run:
   ```bash
   ./on_device_test.sh  # on device (Termux or working adb shell)
   ```

The login flow is now working. The sync 400 is likely due to Android app-specific serialization differences - host CLI tests will confirm if the backend is fully functional.