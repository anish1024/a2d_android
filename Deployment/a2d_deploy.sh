#!/usr/bin/env bash
# =============================================================================
# A2D deployment helper
#
# Builds/installs are done by Gradle; this script only:
#   1. finds adb,
#   2. verifies a device is connected and authorized,
#   3. installs the debug APK, and
#   4. launches the app's launcher activity.
#
# The APK path is resolved relative to this script, so the script works from
# any checkout location and from any current working directory.
# =============================================================================

set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

APK_PATH="$REPO_ROOT/A2D/app/build/outputs/apk/debug/app-debug.apk"
APP_ID="com.a2d.app"
LAUNCH_COMPONENT="${APP_ID}/.ui.MainActivity"

echo "=============================================="
echo "  A2D Deployment: adb check -> install -> launch"
echo "=============================================="
echo "Repository: $REPO_ROOT"
echo "APK:        $APK_PATH"
echo ""

# ========== 1. Locate adb ==========
find_adb() {
    if command -v adb >/dev/null 2>&1; then
        command -v adb
        return 0
    fi

    local candidate
    for candidate in \
        "${ANDROID_HOME:-}/platform-tools/adb" \
        "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
        "$HOME/Library/Android/platform-tools/adb" \
        "$HOME/tools/android/sdk/platform-tools/adb"; do
        if [ -n "$candidate" ] && [ -x "$candidate" ]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done

    return 1
}

ADB="$(find_adb || true)"

if [ -z "$ADB" ]; then
    echo "ERROR: adb was not found."
    echo ""
    echo "Install Android platform-tools, or set ANDROID_HOME / ANDROID_SDK_ROOT:"
    echo "  macOS (Homebrew):   brew install --cask android-platform-tools"
    echo "  Debian/Ubuntu:      sudo apt install adb"
    echo "  SDK layout:         \$ANDROID_HOME/platform-tools/adb"
    exit 1
fi

echo "adb:        $ADB"
"$ADB" start-server >/dev/null 2>&1 || true
echo ""

# ========== 2. Check connected devices ==========
echo "Connected devices:"
"$ADB" devices
echo ""

DEVICE_COUNT="$("$ADB" devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "ERROR: no authorized device found."
    echo ""
    echo "Fix checklist:"
    echo "  - Enable Developer options and USB debugging on the phone."
    echo "  - Reconnect the USB cable and accept the 'Allow USB debugging?' prompt."
    echo "  - If the phone shows 'unauthorized', revoke/re-authorize USB debugging."
    echo "  - If it shows 'offline', unplug/replug or run: $ADB kill-server"
    echo ""
    echo "See Deployment/a2d_build.md for the full device setup guide."
    exit 1
fi

# ========== 3. Install the APK ==========
if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: debug APK not found at:"
    echo "  $APK_PATH"
    echo ""
    echo "Build it first:"
    echo "  cd \"$REPO_ROOT/A2D\" && ./gradlew assembleDebug"
    exit 1
fi

echo "Installing APK..."
if ! "$ADB" install -r "$APK_PATH"; then
    echo ""
    echo "ERROR: APK installation failed."
    echo "Check the device screen for an install confirmation prompt, then retry."
    exit 1
fi
echo ""

# ========== 4. Launch the app ==========
echo "Launching $LAUNCH_COMPONENT ..."
"$ADB" shell am start -n "$LAUNCH_COMPONENT"
echo ""

echo "=============================================="
echo "  Done!"
echo "=============================================="
