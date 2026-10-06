#!/bin/bash

echo "=============================================="
echo "  A2D Setup: Check adb → Install APK"
echo "=============================================="
echo ""

# ========== 1. Check if adb already exists ==========
if command -v adb &> /dev/null; then
    echo "✓ adb is already installed"
    echo "→ Current version:"
    adb --version
    echo ""
else
    echo "→ adb not found. Installing Platform-Tools..."
    echo ""

    mkdir -p ~/Library/Android

    if [ -f ~/Downloads/platform-tools-latest-darwin.zip ]; then
        unzip -o ~/Downloads/platform-tools-latest-darwin.zip -d ~/Library/Android
    else
        echo "❌ ERROR: platform-tools-latest-darwin.zip not found in Downloads"
        echo "   Please download it from:"
        echo "   https://developer.android.com/tools/releases/platform-tools"
        exit 1
    fi

    # Add to PATH permanently (only if not already present)
    if ! grep -q 'Library/Android/platform-tools' ~/.zshrc 2>/dev/null; then
        echo 'export PATH="$HOME/Library/Android/platform-tools:$PATH"' >> ~/.zshrc
        echo "✓ Added Platform-Tools to ~/.zshrc"
    fi

    # Load for current session
    export PATH="$HOME/Library/Android/platform-tools:$PATH"

    echo ""
    echo "→ Installed adb version:"
    adb --version
    echo ""
fi

# ========== 2. Check connected devices ==========
echo "→ Checking connected devices..."
adb devices
echo ""

# ========== 3. Install the APK ==========
# ⚠️  CHANGE THIS to your actual APK path
APK_PATH="/Users/anishnair/Projects/a2d_android/A2D/app/build/outputs/apk/debug/app-debug.apk"
echo "APK_PATH:   $APK_PATH"
if [ -f "$APK_PATH" ]; then
    echo "→ Installing APK..."
    adb install -r "$APK_PATH"
    echo ""
    echo "✅ APK installation finished!"
else
    echo "⚠️  APK not found at:"
    echo "   $APK_PATH"
    echo ""
    echo "Please update the APK_PATH in this script,"
    echo "or run manually later:"
    echo "   adb install -r /full/path/to/app-debug.apk"
fi

echo ""
echo "=============================================="
echo "  Done!"
echo "=============================================="