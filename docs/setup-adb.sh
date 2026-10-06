#!/bin/bash

echo "=== Setting up Android Platform-Tools ==="

mkdir -p ~/Library/Android

if [ -f ~/Downloads/platform-tools-latest-darwin.zip ]; then
    unzip -o ~/Downloads/platform-tools-latest-darwin.zip -d ~/Library/Android
else
    echo "ERROR: platform-tools-latest-darwin.zip not found in Downloads"
    exit 1
fi

~/Library/Android/platform-tools/adb --version

if ! grep -q 'Library/Android/platform-tools' ~/.zshrc; then
    echo 'export PATH="$HOME/Library/Android/platform-tools:$PATH"' >> ~/.zshrc
fi

export PATH="$HOME/Library/Android/platform-tools:$PATH"

echo ""
echo "=== Final Check ==="
which adb
adb --version
echo ""
echo "✅ Setup complete!"
