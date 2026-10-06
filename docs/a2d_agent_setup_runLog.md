# A2D Android Environment Setup — Verified Runbook

Steps in the exact order that produced a successful `./gradlew assembleDebug`
on this machine (macOS x86_64, Homebrew at `/usr/local`, no pre-existing Java
or Android tooling). Verified: **BUILD SUCCESSFUL**, APK at
`A2D/app/build/outputs/apk/debug/app-debug.apk`.

## 1. Verify starting state

```bash
java -version                          # expected: missing
ls ~/Library/Android/sdk 2>/dev/null   # expected: missing
uname -m                               # x86_64 on this machine
```

Result: no Java, no Android SDK. Install both.

## 2. Install JDK 17

> **Deviation D1** — The `brew install openjdk@17` attempt timed out (>10 min,
> nothing installed). Instead, download the Temurin 17 tarball directly from
> the Adoptium API into `~/tools` (verified working, ~172 MB):

```bash
mkdir -p ~/tools/jdk17
curl -L -o /tmp/jdk17.tar.gz \
  "https://api.adoptium.net/v3/binary/latest/17/ga/mac/x64/jdk/hotspot/normal/eclipse"
tar -xzf /tmp/jdk17.tar.gz -C ~/tools/jdk17
~/tools/jdk17/jdk-17*/Contents/Home/bin/java -version
# => OpenJDK Runtime Environment Temurin-17.0.20.1
```

- **JDK home**: `~/tools/jdk17/jdk-17.0.20.1+1/Contents/Home`

## 3. Install Android command-line tools

> **Deviation D2** — No Android Studio on this machine, so the
> "Empty Activity" template from the original instructions could not be used;
> the whole project was scaffolded by hand (see step 6).
>
> **Deviation D3** — SDK lives at `~/tools/android/sdk` instead of the default
> `~/Library/Android/sdk` (purely for organization). `sdkmanager` **requires**
> the tools to sit at `<sdk>/cmdline-tools/latest` — do not skip this layout.

```bash
curl -sL -o /tmp/cmdtools.zip \
  "https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"
unzip -q /tmp/cmdtools.zip -d ~/tools/android
mkdir -p ~/tools/android/sdk/cmdline-tools
mv ~/tools/android/cmdline-tools ~/tools/android/sdk/cmdline-tools/latest
```

## 4. Accept SDK licenses

```bash
export JAVA_HOME=~/tools/jdk17/jdk-17.0.20.1+1/Contents/Home
export ANDROID_HOME=~/tools/android/sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
yes | sdkmanager --licenses
```

## 5. Install SDK packages

```bash
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

## 6. Scaffold the project

> **Deviation D4** — All of the following was written by hand instead of using
> the Android Studio "Empty Activity" template (no Android Studio installed).

Create `A2D/` containing:

```
A2D/
├── settings.gradle.kts            # rootProject.name = "A2D", include(":app")
├── build.gradle.kts               # plugins { alias(...) apply false }
├── gradle.properties              # jvmargs -Xmx2048m, useAndroidX, config cache
├── .gitignore
├── gradle/
│   ├── libs.versions.toml         # version catalog (see below)
│   └── wrapper/gradle-wrapper.properties
├── local.properties               # sdk.dir (see step 7)
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml    # registers .A2DApplication + MainActivity
        ├── java/com/a2d/app/
        │   ├── A2DApplication.kt  # @HiltAndroidApp
        │   ├── MainActivity.kt    # @AndroidEntryPoint, setContent
        │   └── ui/
        │       ├── home/HelloWorldScreen.kt   # title, subtitle, Pulse HIGH button
        │       └── theme/{Color,Type,Theme}.kt
        └── res/
            ├── values/strings.xml
            ├── values/themes.xml
            └── drawable/ic_launcher.xml
```

Key version choices in `gradle/libs.versions.toml`:

| Component | Version |
|---|---|
| AGP | 8.5.2 |
| Kotlin | 2.0.20 |
| KSP | 2.0.20-1.0.25 |
| Compose BOM | 2024.09.02 |
| Hilt | 2.52 |
| Room | 2.6.1 |
| Coroutines | 1.8.1 |
| activity-compose | 1.9.0 |
| lifecycle | 2.8.6 |
| Gradle (wrapper) | 8.9 |

> **Deviation D5** — The original instructions pin
> `kotlinCompilerExtensionVersion = "1.5.14"` in `composeOptions`, which is
> incompatible with the required Kotlin 2.x. Replaced with the Compose Compiler
> Gradle plugin: `alias(libs.plugins.kotlin.compose)` in `app/build.gradle.kts`
> (no `composeOptions` block at all).
>
> **Deviation D6** — `kotlin("kapt")` from the instructions was omitted
> entirely; Hilt and Room are compiled with KSP only.
>
> **Deviation D7** — Slightly newer stable versions than the instructions'
> literals (Hilt 2.52 vs 2.51.1, BOM 2024.09.02 vs 2024.06.00, lifecycle
> 2.8.6 vs 2.8.3), per the instruction "use the latest stable versions
> compatible with Kotlin 2.x / AGP 8.x".

## 7. Point the project at the SDK

```
# A2D/local.properties
sdk.dir=/Users/anishnair/tools/android/sdk
```

## 8. Generate the Gradle wrapper

> **Deviation D8** — `raw.githubusercontent.com` (the usual source for
> `gradlew` + `gradle-wrapper.jar`) was too slow from this network. Instead,
> download the Gradle distribution itself from `services.gradle.org`, then use
> that binary to generate a genuine wrapper.
>
> **Deviation D9** — Gradle 8.9 refuses to run in a directory without a
> settings file, so `gradle wrapper` cannot be run directly in the new
> project. Generate it in a scratch directory and copy the artifacts over.

```bash
mkdir -p ~/tools/gradle
curl -sL -o /tmp/gradle-8.9-bin.zip \
  "https://services.gradle.org/distributions/gradle-8.9-bin.zip"
unzip -q /tmp/gradle-8.9-bin.zip -d ~/tools/gradle

mkdir -p /tmp/gradlew-gen && cd /tmp/gradlew-gen
touch settings.gradle.kts
~/tools/gradle/gradle-8.9/bin/gradle wrapper --gradle-version 8.9
cp /tmp/gradlew-gen/gradlew /tmp/gradlew-gen/gradlew.bat A2D/
cp /tmp/gradlew-gen/gradle/wrapper/gradle-wrapper.jar A2D/gradle/wrapper/
chmod +x A2D/gradlew
```

`gradle/wrapper/gradle-wrapper.properties` (written in step 6, unchanged):

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
```

## 9. Build

```bash
export JAVA_HOME=~/tools/jdk17/jdk-17.0.20.1+1/Contents/Home
export ANDROID_HOME=~/tools/android/sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
cd A2D
./gradlew assembleDebug
```

Result: `BUILD SUCCESSFUL` (41 tasks), Hilt/KSP generation clean,
APK at `app/build/outputs/apk/debug/app-debug.apk`.

## Persistent environment (optional, add to `~/.zshrc`)

```bash
export JAVA_HOME=~/tools/jdk17/jdk-17.0.20.1+1/Contents/Home
export ANDROID_HOME=~/tools/android/sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

## Deviations summary

| # | Original instruction | What was done instead | Why |
|---|---|---|---|
| D1 | (implicit) assume Java present / install via brew | Temurin 17 tarball from Adoptium API | `brew install openjdk@17` timed out and installed nothing |
| D2 | Bootstrap via Android Studio "Empty Activity" template | Project scaffolded entirely by hand | No Android Studio on this machine |
| D3 | (implicit) SDK at `~/Library/Android/sdk` | SDK at `~/tools/android/sdk` | Organization; `sdkmanager` layout requirement still honored (`cmdline-tools/latest`) |
| D4 | Template-generated project files | Hand-written Gradle Kotlin DSL + version catalog | Same as D2; required by "Version Catalogs preferred" |
| D5 | `composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }` | `kotlin("plugin.compose")` Compose Compiler plugin | 1.5.x Compose compiler is incompatible with Kotlin 2.x (stack table mandates Kotlin 2.x) |
| D6 | `kotlin("kapt")` (if needed) | Omitted; KSP only | KSP handles both Hilt and Room; spec says "prefer KSP" |
| D7 | Pinned literal dep versions (Hilt 2.51.1, BOM 2024.06.00, lifecycle 2.8.3) | Newer stable releases (Hilt 2.52, BOM 2024.09.02, lifecycle 2.8.6, AGP 8.5.2, Kotlin 2.0.20) | Instruction to "use the latest stable versions compatible with Kotlin 2.x / AGP 8.x" |
| D8 | (implicit) wrapper from template / GitHub | Wrapper generated from `services.gradle.org` distribution | `raw.githubusercontent.com` too slow on this network |
| D9 | (implicit) `gradle wrapper` in the project | Wrapper generated in scratch dir, files copied in | Gradle 8.9 won't run without a settings file in the working dir |

## Verified success criteria

- ✅ `./gradlew assembleDebug` builds successfully
- ✅ No XML layout files (Compose-only; only `strings.xml`, `themes.xml`,
  launcher vector, manifest)
- ✅ Hilt enabled end-to-end (`Hilt_A2DApplication` generated by KSP)
- ⬜ Launch on emulator/device — no emulator installed yet; run
  `sdkmanager "emulator" "system-images;android-34;google_apis;x86_64"` to
  verify runtime behavior
- ⬜ Compose preview in Android Studio — not verifiable without Android Studio
