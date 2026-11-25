#!/bin/bash
# =============================================================================
# A2D Android - On-Device Login Exception Capture Script
# Run this ON THE DEVICE (via Termux or adb shell)
# Purpose: Capture the real exception thrown during login, instead of the
#          generic "Unable to connect. Check your internet and try again."
# =============================================================================

set -euo pipefail

# Configuration
APP_PKG="com.a2d.app"
BASE_URL="https://devapp8000.anisandbox.org"
EMAIL="tester@example.com"
PASSWORD="secret123"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()   { echo -e "${GREEN}[LOG]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
fail()  { echo -e "${RED}[FAIL]${NC} $*"; }

log "=== A2D Android Login Exception Capture ==="
log "Package: $APP_PKG"
log ""

# ---------------------------------------------------------------------------
# Step 1: Clear logcat buffer
# ---------------------------------------------------------------------------
log "Step 1: Clearing logcat buffer..."
adb logcat -c 2>&1 | tail -1
sleep 1

# ---------------------------------------------------------------------------
# Step 2: Launch the app if not already running, navigate to login screen
# ---------------------------------------------------------------------------
log "Step 2: Launching app to MainActivity..."
adb shell am start -n $APP_PKG/.ui.MainActivity 2>&1 | tail -3
sleep 2

# ---------------------------------------------------------------------------
# Step 3: Clear the login error state and tap the login button
#    (We simulate what happens when onContinueClicked fires)
# ---------------------------------------------------------------------------
log "Step 3: Capturing logcat during login attempt..."
log "  (This will capture real exceptions from the OkHttp/Retrofit stack)"

# Record logcat for ~15 seconds while we trigger login
adb logcat -v time -s "$APP_PKG:V" *:S 2>&1 &
LOGCAT_PID=$!

sleep 1

# Simulate button click via accessibility or just trigger the login URL
# The app's LoginViewModel.onContinueClicked calls:
#   apiClient.api.login(email, password).execute()
# We'll trigger via instrumentation if available, or just let the user
# tap the button manually and capture what's logged.

log "  Please now tap 'Continue with Email' on the device screen"
log "  Logcat will capture for 15 seconds..."
sleep 15

# Kill the logcat capture
kill $LOGCAT_PID 2>/dev/null || true
wait $LOGCAT_PID 2>/dev/null || true

# ---------------------------------------------------------------------------
# Step 4: Extract the relevant error lines
# ---------------------------------------------------------------------------
log "Step 4: Extracting relevant error lines from logcat..."

# Get the full logcat and filter for our app's tags
adb logcat -d -v brief 2>&1 | grep -E "$APP_PKG|okhttp|retrofit|gson|LoginViewModel|unable|exception|Error|loginError" | tail -50

log ""
log "=== If you see 'Unable to connect' with a real exception underneath, capture it ==="
log "=== The exception type (NumberFormatException, JSONSyntaxException, SSLHandshakeException, etc.) is key ==="
log "=== Also note: does the app show the generic catch-all, or a specific error? ==="