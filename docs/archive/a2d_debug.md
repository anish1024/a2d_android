# Running the A2D App Locally — Step-by-Step

> **Status: Archived (2026-11-06).** Superseded/historical material, kept for history only. Do not use
> it as current guidance; see [README.md](../../README.md) and [docs/INDEX.md](../INDEX.md).

Verified environment from `a2d_setup.md`: JDK 17 at
`~/tools/jdk17/jdk-17.0.20.1+1/Contents/Home`, Android SDK at
`~/tools/android/sdk`, project at `A2D/` (builds clean with
`./gradlew assembleDebug`).

## Additional dependencies to install first

Everything else is already in place. Two extra SDK packages are needed for a
local emulator run (Intel Mac — use the `x86_64` system image):

```bash
export JAVA_HOME=~/tools/jdk17/jdk-17.0.20.1+1/Contents/Home
export ANDROID_HOME=~/tools/android/sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

sdkmanager "emulator" "system-images;android-34;google_apis;x86_64"
```

- `emulator` — the AVD emulator binary (on this Intel Mac it uses Apple's
  Hypervisor.framework; no HAXM kernel extension needed/possible on modern
  macOS)
- `system-images;android-34;google_apis;x86_64` — the Android 14 image the
  AVD boots (~1.5 GB download)

> To make the environment persistent, append the three `export` lines above to
> `~/.zshrc`.

## Step 1 — Create the AVD (virtual device)

```bash
avdmanager create avd \
  --name a2d_pixel \
  --package "system-images;android-34;google_apis;x86_64" \
  --device pixel_7 \
  --force
```

If `pixel_7` isn't a valid device id on this SDK, list options with
`avdmanager list device` and re-run with a valid one (e.g. `pixel`,
`pixel_6`). Confirm with:

```bash
avdmanager list avd    # should show "a2d_pixel"
```

AVDs are stored under `~/.android/avd/`.

## Step 2 — Start the emulator

Run in its own terminal window (or backgrounded) so the emulator UI is visible:

```bash
~/tools/android/sdk/emulator/emulator -avd a2d_pixel &
```

First boot takes ~1–3 min. Wait for full boot:

```bash
adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed 2>/dev/null)" != "1" ]; do sleep 2; done
echo "BOOTED"
```

(`adb` is on PATH via `$ANDROID_HOME/platform-tools` from the exports above.)

## Step 3 — Build and install the app

From the project root:

```bash
cd A2D
./gradlew installDebug
```

This compiles and pushes `app-debug.apk` to the running emulator.

## Step 4 — Launch the app

```bash
adb shell am start -n com.a2d.app/.MainActivity
```

Expected screen: **A2D – Adult ADHD Assistant** / **Hello World – Environment
Ready** / **[ Pulse HIGH ]** button / `Pulses: 0`. Tapping the button should
increment the counter.

## Step 5 — Verify / debug

```bash
# app logs only (look for crashes, Hilt init, Compose)
adb logcat -s A2D:* ActivityTaskManager:V 2>/dev/null | head -50

# take a screenshot to a file
adb exec-out screencap -p > a2d_screenshot.png

# restart the app after a code change
./gradlew installDebug && adb shell am start -n com.a2d.app/.MainActivity
```

## Stopping the emulator

```bash
adb emu kill      # or: adb kill-server (stops adb entirely)
```

## Alternative: run on a physical device instead

If you'd rather skip the emulator:

1. On the phone: Settings → About phone → tap **Build number** 7 times to
   enable Developer Options; enable **USB debugging**.
2. Connect via USB, accept the debugging prompt.
3. `adb devices` should list the device.
4. `./gradlew installDebug && adb shell am start -n com.a2d.app/.MainActivity`

No extra SDK packages needed for this path.
