# Implementation Log — Milestones 0–2 (2026-10)

- Date range: 2026-10 (historical; exact dates were not recorded)
- Topics: environment setup, project configuration, domain models, gate engines, unit tests, Room,
  repository, dependency injection
- Feature IDs: none (milestones 0.1–2.3)
- Entries: 9 milestones
- Approx size: ~2.6k tokens
- Index: [Implementation log](../implementation-log.md)
- Next shard: [Milestones 3–4](2026-10-milestones-3-4.md)

> Historical snapshot migrated from the former `README.md` Appendix A. Superseded files and older
> toolchain versions are kept for history; use [`README.md`](../../README.md) for current truth.
> Commit IDs were not recorded for these milestone entries.

## Context

Progress reports appended after each milestone of the original implementation blueprint (archived at
`docs/archive/a2d_implementation.md`).

**Project location decision:** The blueprint's tree is rooted at `A2DApp/`, but this repo already
contains a scaffolded Gradle project at `A2D/` (wrapper 8.9, version catalog, Hilt/Room/Compose
wired, APK output consumed by `Deployment/a2d_deploy.sh`). All blueprint files are therefore created
inside `A2D/`, and the deploy script path stays valid. Only build-tooling lines differ from the spec
(Kotlin 2.0.20 + AGP 8.5.2 + Gradle 8.9 instead of Kotlin 1.9.22 + AGP 8.2.2 + Gradle 8.4), because
that is the toolchain already proven working on this machine. All application source, tests, and
configuration values follow the spec verbatim.

## Entries

### Milestone 0.1 — System Tools Setup ✅

**Status:** Complete (toolchain pre-installed; verified rather than re-installed)

| Tool | Spec requirement | Found on host | Result |
| --- | --- | --- | --- |
| Xcode Command Line Tools | `xcode-select --install` | `/Library/Developer/CommandLineTools` | ✅ present |
| Homebrew | required | `Homebrew 7.0.8` at `/usr/local/bin/brew` | ✅ present |
| Java 17 LTS (Temurin) | `brew install --cask temurin@17` | Temurin **17.0.20.1** at `/Users/anishnair/tools/jdk17/jdk-17.0.20.1+1/Contents/Home` | ✅ present (custom path, not registered with `/usr/libexec/java_home`) |
| Gradle wrapper 8.x | `gradle wrapper --gradle-version 8.4` | `A2D/gradlew` → **Gradle 8.9** (`gradle-wrapper.jar` present) | ✅ present, `./gradlew --version` verified (launcher JVM 17.0.20.1) |
| Android SDK | — | `/Users/anishnair/tools/android/sdk` — platforms `android-34`, build-tools `34.0.0`, platform-tools with `adb` | ✅ matches `compileSdk = 34` |
| adb | via `Deployment/a2d_deploy.sh` | `~/Library/Android/platform-tools/adb`, daemon started successfully | ✅ working; **no device attached yet** |

**Actions taken:**
- Verified `./gradlew --version` runs with `JAVA_HOME` pointing at the Temurin 17 JDK.
- Appended `export JAVA_HOME=/Users/anishnair/tools/jdk17/jdk-17.0.20.1+1/Contents/Home`
  to `~/.zshrc` (spec requires a persistent `JAVA_HOME`; `/usr/libexec/java_home -v 17` returns
  nothing because the JDK lives outside the standard JVM directory).
- Skipped `brew install temurin@17` / `brew install gradle` — already satisfied (avoids a large
  redundant download on the 8 GB machine).

**Notes:** No device is connected at this time (`adb devices` empty) — Sprint 4.1 deployment will
be attempted again after the build milestones.

---

### Milestone 0.2 — Root Project Configuration Files ✅

**Status:** Complete — verified with `./gradlew help` (exit 0)

| Spec file | Action | Detail |
| --- | --- | --- |
| `gradle.properties` | ✏️ rewritten to spec | 2 GB JVM cap (`-Xmx2048m -XX:MaxMetaspaceSize=512m -XX:+UseParallelGC`), `parallel`, `caching`, `vfs.watch`, `kotlin.incremental`, `android.useAndroidX` (was: `-Xmx2048m -Dfile.encoding=UTF-8` + config-cache) |
| `settings.gradle.kts` | ✏️ updated | `rootProject.name = "A2DApp"` per spec (directory stays `A2D/`); existing repository/content-filter block kept — functionally identical to spec |
| `build.gradle.kts` (root) | ⏭️ unchanged | Already matches spec semantics: AGP / Kotlin / KSP / Hilt plugins declared `apply false` via version catalog (spec pins AGP 8.2.2 + Kotlin 1.9.22 inline; catalog pins AGP 8.5.2 + Kotlin 2.0.20 — see deviation note) |
| `app/build.gradle.kts` | ✏️ merged to spec | Added: `signingConfigs.create("release")` anchored with `rootProject.file(...)` (audit fix #1), release `isMinifyEnabled=true` + `isShrinkResources=true` + release signingConfig, debug `isMinifyEnabled=false`, `versionName "1.0.0"`, `vectorDrawables { useSupportLibrary = true }`, `packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }`, `testImplementation(kotlinx-coroutines-test)` |
| `app/proguard-rules.pro` | ✏️ rewritten to spec | Keeps `domain.model.**`, `data.local.entity.**`, `data.local.relation.**` |
| `keystore.properties` | ⏭️ already correct | `KEYSTORE_PATH=a2d-release-key.jks` (audit-corrected form, resolved via `rootProject.file`), passwords/alias match spec |
| Gradle wrapper | ⏭️ already present | `gradlew` + `gradle/wrapper/gradle-wrapper.jar`, Gradle **8.9** (spec's audit fix #2 asks for wrapper generation — satisfied) |
| `a2d-release-key.jks` | ⏳ deferred | Generated in Milestone 4.2 per spec ordering |

**Actions taken:**
- Rewrote `gradle.properties`, `app/build.gradle.kts`, `app/proguard-rules.pro`; edited `settings.gradle.kts`.
- Added `kotlinx-coroutines-test` entry to `gradle/libs.versions.toml` (version 1.8.1 vs spec's 1.7.3 — matches the project's existing coroutines line).
- Ran `./gradlew help` with `JAVA_HOME` set → **EXIT 0** (new properties + signing config parse cleanly).

**Deviations (all logged):**
1. Project root is `A2D/`, not `A2DApp/` (repo already scaffolded; `Deployment/a2d_deploy.sh` now resolves the APK relative to its own location).
2. Toolchain: Kotlin 2.0.20 / AGP 8.5.2 / Gradle 8.9 / Hilt 2.52 / Compose BOM 2024.09.02 instead of spec's Kotlin 1.9.22 / AGP 8.2.2 / Gradle 8.4 / Hilt 2.50 / BOM 2024.02.00. Kotlin 2.0 uses the `kotlin-compose` compiler plugin, so `composeOptions.kotlinCompilerExtensionVersion` from the spec is intentionally omitted (it would be rejected by AGP).
3. `kotlinx-coroutines-test` 1.8.1 (spec: 1.7.3) to stay on the project's single coroutines version.

---

### Milestone 1.1 — Domain Models and Contracts ✅

**Status:** Complete (6 files created verbatim from spec under `A2D/app/src/main/java/com/a2d/app/`)

| File | Content |
| --- | --- |
| `domain/model/ActivityAchieved.kt` | Base completion interface with `val achieved: Boolean` |
| `domain/model/ActionItem.kt` | Data class `(id: Long, description: String, achieved = false) : ActivityAchieved` |
| `domain/model/UserGroup.kt` | Enum `USER("user")`, `ADMIN("admin")` |
| `domain/model/User.kt` | Data class `(name, password, group)` |
| `domain/model/PulseResult.kt` | `(inputPin, pulse, latchedInputs, output, reset)` |
| `domain/contract/ActivityProcessor.kt` | `activities: List<ActivityAchieved>` + `processActivity(callback?): Boolean` |

No verification command is specified for this milestone; compilation is verified at the end of Sprint 1.

---

### Milestone 1.2 — Serial AND-Gate Engine & Integrated Task ✅

**Status:** Complete (3 files created verbatim from spec)

| File | Content |
| --- | --- |
| `domain/engine/SerialAndGateProcessor.kt` | `@Singleton` in-memory pin register: arity parsed from `inputPin`, pulse normalized via `trim().uppercase()`, `HIGH` latches `1` until `size >= arity` → `output=1`, `LOW` clears register → `reset=true`; `@Synchronized` + `ConcurrentHashMap<String, PinState>` for thread safety; invalid pin/pulse throw `IllegalArgumentException` |
| `domain/model/Task.kt` | `data class Task : ActivityAchieved, ActivityProcessor` with `processActivity()` and Tier-1 `processActivityWithGate(processor)` (resets arity pin = `activities.size`, feeds `HIGH`/`LOW` per `ActionItem.achieved`, breaks on reset) |
| `domain/engine/TaskListPipelineProcessor.kt` | `@Singleton`, Tier-2 `evaluateTaskList(tasks)` — empty list returns `output=1`; otherwise resets arity pin = `tasks.size` and pulses per `task.achieved` |

Both engines use constructor `@Inject` (no `@Provides` needed in the Hilt module).

---

### Milestone 1.3 — Unit Test Suite ✅ (Sprint 1 verification: `./gradlew test` → BUILD SUCCESSFUL)

**Status:** Complete — **8/8 tests pass, 0 failures, 0 errors**

**Files created (`A2D/app/src/test/java/com/a2d/app/`):**
- `SerialAndGateProcessorTest.kt` — 4 tests: HIGH latching until arity met, LOW reset, invalid pulse throws, non-integer pin throws.
- `TaskDomainTest.kt` — 2 tests: no-callback → achieved true; callback evaluating sub-activities.
- `IntegratedGateTaskFlowTest.kt` — 2 tests: Tier-1 gate over `ActionItem`s, Tier-2 pipeline over task list.

**Verification:**
```
$ ./gradlew test
BUILD SUCCESSFUL in 3m 20s   (61 actionable tasks)
```
Test reports (`app/build/test-results/testDebugUnitTest/`):
- `IntegratedGateTaskFlowTest`: tests=2, failures=0, errors=0
- `SerialAndGateProcessorTest`: tests=4, failures=0, errors=0
- `TaskDomainTest`: tests=2, failures=0, errors=0

Note: this build also exercised the new release signing/minify config and downloaded
`kotlinx-coroutines-test:1.8.1` successfully (network available).

---

### Milestone 2.1 — Room Database Schema & DAOs ✅

**Status:** Complete (5 files created verbatim from spec under `A2D/app/src/main/java/com/a2d/app/data/local/`)

| File | Content |
| --- | --- |
| `entity/TaskEntity.kt` | `@Entity(tableName = "tasks")`, PK `id: Long`, fields taskName/description/priority/context/achieved/creationTime |
| `entity/ActionItemEntity.kt` | `@Entity(tableName = "action_items")` with `ForeignKey(TaskEntity.id → taskId, onDelete = CASCADE)` + `Index(taskId)` |
| `relation/TaskWithActionItems.kt` | `@Embedded task` + `@Relation(parentColumn="id", entityColumn="taskId") actionItems` |
| `dao/TaskDao.kt` | Flow `getAllTasks()`, suspend `getTaskSnapshot()`/`getTaskById()`, `@Insert(REPLACE)` upserts, `insertOrUpdateTask()` transaction helper (insert → delete old items → insert new), `deleteTask(): Int` |
| `A2DDatabase.kt` | `@Database(entities=[TaskEntity, ActionItemEntity], version=1, exportSchema=false)`, abstract `taskDao()` |

---

### Milestone 2.2 — Data Repository Implementation ✅

**Status:** Complete (1 file created verbatim from spec)

| File | Content |
| --- | --- |
| `data/repository/TaskRepositoryImpl.kt` | `@Singleton @Inject constructor(TaskDao)`; `getAllTasks(): Flow<List<Task>>` (maps relations → domain), `getTaskSnapshot()`, `getTask(id)`, `saveTask(task)` (timestamps with `yyyy-MM-dd HH:mm:ss` when `creationTime == null`, rebuilds `ActionItemEntity` rows keyed by `taskId`), `deleteTask(id): Boolean` |

---

### Milestone 2.3 — Dependency Injection Wiring ✅ (Sprint 2 verification: `./gradlew compileDebugKotlin` → BUILD SUCCESSFUL)

**Status:** Complete

| File | Content |
| --- | --- |
| `di/AppModule.kt` | ✅ created — `@Module @InstallIn(SingletonComponent::class)` providing `@Singleton A2DDatabase` (`Room.databaseBuilder(..., "a2d_database.db")`) and `TaskDao` |
| `A2DApplication.kt` | ⏭️ already existed and matches spec exactly (`@HiltAndroidApp class A2DApplication : Application()`) |

`SerialAndGateProcessor`, `TaskListPipelineProcessor`, and `TaskRepositoryImpl` are constructor-injected `@Singleton`s, so no additional providers are required.

**Verification:**
```
$ ./gradlew compileDebugKotlin
BUILD SUCCESSFUL in 31s
```
KSP ran successfully (`:app:kspDebugKotlin`) — Room DAO implementations and Hilt components generated without errors, confirming the whole Sprint 1 + Sprint 2 source set compiles.

---
