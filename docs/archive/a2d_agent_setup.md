# Agent Instructions: Android Development Environment Setup & Hello-World Verification

> **Status: Archived (2026-11-06).** Superseded/historical material, kept for history only. Do not use
> it as current guidance; see [README.md](../../README.md) and [docs/INDEX.md](../INDEX.md).

You are an expert Android engineer. Your task is to set up a clean, modern native Android project that matches the technology stack defined in the A2D (Adult ADHD Assistant) Android port proposal, then verify the environment by generating and building a minimal Jetpack Compose "Hello World" application.

## 1. Target Technology Stack (must follow exactly)

| Layer              | Technology                          | Notes |
|--------------------|-------------------------------------|-------|
| Language           | Kotlin 2.x                          | Prefer data classes, immutability, coroutines |
| UI Framework       | Jetpack Compose + Material 3        | Declarative UI only – no XML layouts |
| Architecture       | Offline-first, reactive             | Room + Kotlin Flow / StateFlow |
| Dependency Injection | Dagger Hilt                       | `@Singleton`, `@HiltViewModel`, etc. |
| Persistence        | Room (SQLite)                       | Entities + DAOs with Flow |
| Concurrency        | Kotlin Coroutines + Flow            | No blocking calls on main thread |
| Min SDK            | 26 (Android 8.0)                    | Target / Compile SDK 34 or 35 |
| Build System       | Gradle (Kotlin DSL – `.kts`)        | Version Catalogs preferred |

## 2. Project Bootstrap Steps

1. Create a new Android project named `A2D` (or `a2d-android`) using the **Empty Activity** template with Compose enabled.
2. Configure `build.gradle.kts` (project + app) with the following core dependencies (use the latest stable versions compatible with Kotlin 2.x / AGP 8.x):

   ```kotlin
   // app/build.gradle.kts essentials
   plugins {
       id("com.android.application")
       id("org.jetbrains.kotlin.android")
       id("com.google.dagger.hilt.android")
       id("com.google.devtools.ksp")
       kotlin("kapt") // only if needed; prefer KSP
   }

   android {
       namespace = "com.a2d.app"
       compileSdk = 34

       defaultConfig {
           applicationId = "com.a2d.app"
           minSdk = 26
           targetSdk = 34
           versionCode = 1
           versionName = "0.1.0"
       }

       compileOptions {
           sourceCompatibility = JavaVersion.VERSION_17
           targetCompatibility = JavaVersion.VERSION_17
       }
       kotlinOptions {
           jvmTarget = "17"
       }
       buildFeatures {
           compose = true
       }
       composeOptions {
           kotlinCompilerExtensionVersion = "1.5.14" // or latest matching Kotlin
       }
   }

   dependencies {
       // Compose BOM
       val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
       implementation(composeBom)
       androidTestImplementation(composeBom)

       implementation("androidx.compose.ui:ui")
       implementation("androidx.compose.ui:ui-graphics")
       implementation("androidx.compose.ui:ui-tooling-preview")
       implementation("androidx.compose.material3:material3")
       implementation("androidx.activity:activity-compose:1.9.0")
       implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
       implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")

       // Hilt
       implementation("com.google.dagger:hilt-android:2.51.1")
       ksp("com.google.dagger:hilt-compiler:2.51.1")
       implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

       // Room
       implementation("androidx.room:room-runtime:2.6.1")
       implementation("androidx.room:room-ktx:2.6.1")
       ksp("androidx.room:room-compiler:2.6.1")

       // Coroutines
       implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

       // Testing
       testImplementation("junit:junit:4.13.2")
       androidTestImplementation("androidx.test.ext:junit:1.2.1")
       androidTestImplementation("androidx.compose.ui:ui-test-junit4")
       debugImplementation("androidx.compose.ui:ui-tooling")
   }
   ```

3. Enable Hilt in the `Application` class:

   ```kotlin
   @HiltAndroidApp
   class A2DApplication : Application()
   ```

4. Register the Application class in `AndroidManifest.xml`.

## 3. Verification: Generate Equivalent "Hello World"

After the project skeleton is created and dependencies resolve, implement a minimal Compose screen that proves the environment works:

- Single `MainActivity` annotated with `@AndroidEntryPoint`.
- A simple `@Composable` function that displays:
  - App title: **"A2D – Adult ADHD Assistant"**
  - Subtitle: **"Hello World – Environment Ready"**
  - A Material 3 Button labeled **"Pulse HIGH"** that, when clicked, shows a Toast or updates a `mutableStateOf` counter (simulating a single HIGH pulse).
- Use Material 3 theme (`MaterialTheme` with dynamic color if available).
- No Room / Hilt business logic yet – keep it pure UI verification.

Expected visual result:
```
┌──────────────────────────────┐
│  A2D – Adult ADHD Assistant  │
│                              │
│  Hello World – Environment   │
│  Ready                       │
│                              │
│     [ Pulse HIGH ]           │
│                              │
│  Pulses: 0                   │
└──────────────────────────────┘
```

## 4. Success Criteria

- Project builds successfully with `./gradlew assembleDebug` (or Android Studio Build → Make Project).
- App launches on emulator or physical device without crashes.
- Compose preview works in Android Studio.
- Button interaction updates state reactively.
- No XML layout files are present (Compose-only).

## 5. Next Steps (after Hello World succeeds)

Once the environment is verified, you may proceed to implement the domain models (`Task`, `ActionItem`, `PulseResult`), the `SerialAndGateProcessor`, Room entities, and the two-tier hierarchical logic-gate pipeline described in the original proposal. Do **not** implement those features until the Hello-World verification is complete and confirmed.

## 6. Constraints

- Prefer pure Kotlin – avoid Java.
- Keep the UI distraction-free and ADHD-friendly (generous spacing, clear hierarchy, Material 3 defaults).
- All new code must be placed under the package `com.a2d.app`.
- Use version catalogs (`libs.versions.toml`) if the project template supports them.

Begin by creating the project structure and the Hello-World Compose screen. Report the build status and any configuration issues you encounter.
