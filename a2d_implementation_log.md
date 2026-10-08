# A2D Implementation Progress Log

Progress reports appended after each milestone of `a2d_implementation.md`.

**Project location decision:** The blueprint's tree is rooted at `A2DApp/`, but this repo already
contains a scaffolded Gradle project at `A2D/` (wrapper 8.9, version catalog, Hilt/Room/Compose
wired, APK output consumed by `a2d_deploy.sh`). All blueprint files are therefore created inside
`A2D/`, and the deploy script path stays valid. Only build-tooling lines differ from the spec
(Kotlin 2.0.20 + AGP 8.5.2 + Gradle 8.9 instead of Kotlin 1.9.22 + AGP 8.2.2 + Gradle 8.4),
because that is the toolchain already proven working on this machine. All application source,
tests, and configuration values follow the spec verbatim.

---

## Milestone 0.1 — System Tools Setup ✅

**Status:** Complete (toolchain pre-installed; verified rather than re-installed)

| Tool | Spec requirement | Found on host | Result |
| --- | --- | --- | --- |
| Xcode Command Line Tools | `xcode-select --install` | `/Library/Developer/CommandLineTools` | ✅ present |
| Homebrew | required | `Homebrew 7.0.8` at `/usr/local/bin/brew` | ✅ present |
| Java 17 LTS (Temurin) | `brew install --cask temurin@17` | Temurin **17.0.20.1** at `/Users/anishnair/tools/jdk17/jdk-17.0.20.1+1/Contents/Home` | ✅ present (custom path, not registered with `/usr/libexec/java_home`) |
| Gradle wrapper 8.x | `gradle wrapper --gradle-version 8.4` | `A2D/gradlew` → **Gradle 8.9** (`gradle-wrapper.jar` present) | ✅ present, `./gradlew --version` verified (launcher JVM 17.0.20.1) |
| Android SDK | — | `/Users/anishnair/tools/android/sdk` — platforms `android-34`, build-tools `34.0.0`, platform-tools with `adb` | ✅ matches `compileSdk = 34` |
| adb | via `a2d_deploy.sh` | `~/Library/Android/platform-tools/adb`, daemon started successfully | ✅ working; **no device attached yet** |

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

## Milestone 0.2 — Root Project Configuration Files ✅

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
1. Project root is `A2D/`, not `A2DApp/` (repo already scaffolded; `a2d_deploy.sh` hardcodes the `A2D/.../app-debug.apk` path).
2. Toolchain: Kotlin 2.0.20 / AGP 8.5.2 / Gradle 8.9 / Hilt 2.52 / Compose BOM 2024.09.02 instead of spec's Kotlin 1.9.22 / AGP 8.2.2 / Gradle 8.4 / Hilt 2.50 / BOM 2024.02.00. Kotlin 2.0 uses the `kotlin-compose` compiler plugin, so `composeOptions.kotlinCompilerExtensionVersion` from the spec is intentionally omitted (it would be rejected by AGP).
3. `kotlinx-coroutines-test` 1.8.1 (spec: 1.7.3) to stay on the project's single coroutines version.

---

## Milestone 1.1 — Domain Models and Contracts ✅

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

## Milestone 1.2 — Serial AND-Gate Engine & Integrated Task ✅

**Status:** Complete (3 files created verbatim from spec)

| File | Content |
| --- | --- |
| `domain/engine/SerialAndGateProcessor.kt` | `@Singleton` in-memory pin register: arity parsed from `inputPin`, pulse normalized via `trim().uppercase()`, `HIGH` latches `1` until `size >= arity` → `output=1`, `LOW` clears register → `reset=true`; `@Synchronized` + `ConcurrentHashMap<String, PinState>` for thread safety; invalid pin/pulse throw `IllegalArgumentException` |
| `domain/model/Task.kt` | `data class Task : ActivityAchieved, ActivityProcessor` with `processActivity()` and Tier-1 `processActivityWithGate(processor)` (resets arity pin = `activities.size`, feeds `HIGH`/`LOW` per `ActionItem.achieved`, breaks on reset) |
| `domain/engine/TaskListPipelineProcessor.kt` | `@Singleton`, Tier-2 `evaluateTaskList(tasks)` — empty list returns `output=1`; otherwise resets arity pin = `tasks.size` and pulses per `task.achieved` |

Both engines use constructor `@Inject` (no `@Provides` needed in the Hilt module).

---

## Milestone 1.3 — Unit Test Suite ✅ (Sprint 1 verification: `./gradlew test` → BUILD SUCCESSFUL)

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

## Milestone 2.1 — Room Database Schema & DAOs ✅

**Status:** Complete (5 files created verbatim from spec under `A2D/app/src/main/java/com/a2d/app/data/local/`)

| File | Content |
| --- | --- |
| `entity/TaskEntity.kt` | `@Entity(tableName = "tasks")`, PK `id: Long`, fields taskName/description/priority/context/achieved/creationTime |
| `entity/ActionItemEntity.kt` | `@Entity(tableName = "action_items")` with `ForeignKey(TaskEntity.id → taskId, onDelete = CASCADE)` + `Index(taskId)` |
| `relation/TaskWithActionItems.kt` | `@Embedded task` + `@Relation(parentColumn="id", entityColumn="taskId") actionItems` |
| `dao/TaskDao.kt` | Flow `getAllTasks()`, suspend `getTaskSnapshot()`/`getTaskById()`, `@Insert(REPLACE)` upserts, `insertOrUpdateTask()` transaction helper (insert → delete old items → insert new), `deleteTask(): Int` |
| `A2DDatabase.kt` | `@Database(entities=[TaskEntity, ActionItemEntity], version=1, exportSchema=false)`, abstract `taskDao()` |

---

## Milestone 2.2 — Data Repository Implementation ✅

**Status:** Complete (1 file created verbatim from spec)

| File | Content |
| --- | --- |
| `data/repository/TaskRepositoryImpl.kt` | `@Singleton @Inject constructor(TaskDao)`; `getAllTasks(): Flow<List<Task>>` (maps relations → domain), `getTaskSnapshot()`, `getTask(id)`, `saveTask(task)` (timestamps with `yyyy-MM-dd HH:mm:ss` when `creationTime == null`, rebuilds `ActionItemEntity` rows keyed by `taskId`), `deleteTask(id): Boolean` |

---

## Milestone 2.3 — Dependency Injection Wiring ✅ (Sprint 2 verification: `./gradlew compileDebugKotlin` → BUILD SUCCESSFUL)

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

## Milestone 3.1 — Material 3 Theme Implementation ✅

**Status:** Complete

| File | Action |
| --- | --- |
| `ui/theme/Color.kt` | ✏️ rewritten to spec — ADHD-friendly palette: `CalmGreen #2E7D32`, `CalmTeal #00695C`, `SoftGrey #F5F5F5`, `DarkGrey #212121`, `AlertRed #C62828` (replaces template Purple/Pink colors) |
| `ui/theme/Theme.kt` | ✏️ rewritten to spec — fixed `LightColorScheme` (primary=CalmTeal, secondary=CalmGreen, background=SoftGrey, surface/onPrimary=White, error=AlertRed), `A2DTheme(content)` with no dark/dynamic-color branches |
| `ui/theme/Type.kt` | 🗑️ deleted — scaffold template file not in the spec's directory tree; its `Typography` reference was removed with the new `Theme.kt` |

---

## Milestone 3.2 — ViewModels with Gate Integration ✅

**Status:** Complete (2 files created verbatim from spec)

| File | Content |
| --- | --- |
| `ui/task/TaskViewModel.kt` | `@HiltViewModel` injecting `TaskRepositoryImpl`, `SerialAndGateProcessor`, `TaskListPipelineProcessor`. Exposes `tasks: StateFlow<List<Task>>` (`WhileSubscribed(5000)`) and `milestoneOutput: StateFlow<Int>`. `createSampleTask` saves a 2-action task; `toggleActionItem` updates the sub-item → Tier-1 `processActivityWithGate` → `saveTask` → Tier-2 `recalculatePipeline()`; `deleteTask` cascades then recalculates |
| `ui/logicgate/LogicGateViewModel.kt` | `@HiltViewModel` wrapping `SerialAndGateProcessor.processPulse`; exposes `lastResult`/`errorMessage` StateFlows, catching `IllegalArgumentException` for bad pin/pulse input |

---

## Milestone 3.3 — Compose Screens & Main Navigation ✅ (Sprint 3 verification: `./gradlew assembleDebug` → BUILD SUCCESSFUL)

**Status:** Complete

| File | Action |
| --- | --- |
| `ui/task/TaskScreen.kt` | ✅ created per spec — header + `Milestone Complete [GATE 1]` / `Milestone Incomplete [GATE 0]` status, `+ Task` button, `LazyColumn` of `ElevatedCard`s with per-action `Checkbox`es and `Delete` button |
| `ui/logicgate/LogicGateScreen.kt` | ✅ created per spec — arity `OutlinedTextField`, `Pulse HIGH` / `Pulse LOW (Reset)` buttons, error text, result `Card` (pin/pulse/latched/output/reset) |
| `ui/MainActivity.kt` | ✅ created at new package path `com.a2d.app.ui` — `@AndroidEntryPoint`, `A2DTheme`, `Scaffold` + `NavigationBar` with **Tasks** / **Logic Gate** tabs, `hiltViewModel()` wiring for both ViewModels |
| `AndroidManifest.xml` | ✏️ rewritten to spec — `.A2DApplication`, launcher activity now `.ui.MainActivity`, label `A2D Assistant`, system icon `@android:drawable/sym_def_app_icon`, theme `@android:style/Theme.Material.Light.NoActionBar` |
| `ui/MainActivity.kt` (old path) | 🗑️ deleted — scaffold activity at `com.a2d.app.MainActivity` |
| `ui/home/HelloWorldScreen.kt` | 🗑️ deleted — scaffold placeholder screen |

**Verification:**
```
$ ./gradlew assembleDebug
BUILD SUCCESSFUL in 5m 14s   (41 actionable tasks)
→ app/build/outputs/apk/debug/app-debug.apk (10,096,203 bytes, rebuilt Oct 7 21:36)
```

**Notes:**
- The app source tree now matches the spec's directory tree 1:1 (spec lists no `res/` folder; the
  scaffold's `res/drawable/ic_launcher.xml`, `res/values/strings.xml`, `res/values/themes.xml` are
  left in place but unreferenced — harmless, and removed automatically by resource shrinking in release).
- Build warning-free at the Kotlin level; Hilt deprecation note only in generated Java glue.

---

## Milestone 4.1 — USB Debugging Setup & Deployment ⏳ (blocked: no device attached)

**Status:** Build/deploy pipeline ready; physical-device step cannot be completed autonomously.

- `adb devices` → `List of devices attached` (empty). Re-checked after Sprint 4 builds — still none.
- Debug APK freshly built and ready: `A2D/app/build/outputs/apk/debug/app-debug.apk` (≈10.1 MB).
- adb works (daemon starts cleanly from `~/Library/Android/platform-tools`).

**Pending user actions (per spec + `a2d_build.md`):**
1. On the phone: **Settings → About phone → tap Build number 7×** → enable **USB debugging** in Developer options.
2. Connect via USB, accept the **"Allow USB debugging?"** RSA prompt (check *Always allow*).
3. Verify: `adb devices` shows `device` (not `unauthorized`/`offline`).
4. Install with either:
   - `./a2d_deploy.sh` (repo script — APK path already correct), or
   - `cd A2D && ./gradlew installDebug`
5. On-device acceptance checks from the spec:
   - `+ Task` inserts a task with 2 sub-actions
   - toggling **both** sub-actions flips the card to `COMPLETED`
   - header flips to `Milestone Complete [GATE 1]`
   - **Logic Gate** tab sends manual pulses to pin `2` (2× `Pulse HIGH` → `Gate Output: 1`; `Pulse LOW` → reset)

---

## Milestone 4.2 — Production Key Generation & App Bundle Build ✅

**Status:** Complete — signed `app-release.aab` produced

**1. Keystore generated** (spec command, run in `A2D/` project root, JDK 17 `keytool`):
```
keytool -genkey -v -keystore a2d-release-key.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias a2d-key-alias \
  -dname "CN=A2D Developer, OU=Mobile, O=A2D, L=San Francisco, S=CA, C=US" \
  -storepass a2dpassword -keypass a2dpassword
→ A2D/a2d-release-key.jks (2,758 bytes), 2048-bit RSA, valid 10,000 days
```
Verified with `keytool -list` → `a2d-key-alias, 7 Oct 2026, PrivateKeyEntry`, SHA-256 fingerprint `C6:C6:9E:65:...:CE`.

**2. `keystore.properties` verified** (already the audit-corrected form):
```properties
KEYSTORE_PATH=a2d-release-key.jks      # resolved via rootProject.file(...) from app/
KEYSTORE_PASSWORD=a2dpassword
KEY_ALIAS=a2d-key-alias
KEY_PASSWORD=a2dpassword
```

**3. Signed release bundle built:**
```
$ ./gradlew bundleRelease
BUILD SUCCESSFUL in 6m 46s   (52 actionable tasks)
→ app/build/outputs/bundle/release/app-release.aab (2,684,158 bytes, Oct 7 21:45)
```
Pipeline confirmed: `validateSigningRelease` → `minifyReleaseWithR8` → `shrinkBundleReleaseResources` → `signReleaseBundle` — i.e. release signing config, R8 obfuscation (with the spec's `-keep` rules for models/entities/relations) and resource shrinking all ran successfully.

**4. Git hygiene:** added `keystore.properties` and `*.jks` to `A2D/.gitignore` (spec marks them git-ignored).
⚠️ `A2D/keystore.properties` was **already tracked** in an earlier commit, so `.gitignore` alone won't hide it — run `git rm --cached A2D/keystore.properties` and commit if you want it removed from the index. No git commits were made by this run.

---

## Milestone 4.3 — Google Play Store Release Checklist ⏳ (manual/online steps — cannot be automated here)

**Status:** AAB is ready for upload; all remaining items require the Play Console and design assets.

| # | Spec step | State |
| --- | --- | --- |
| 1 | Create Google Play Console account + app **"A2D - Adult ADHD Assistant"** | ⏳ manual (needs paid Console account) |
| 2 | Store listing assets: 512×512 icon, 1024×500 feature graphic, ≥2× 1080×1920 screenshots | ⏳ not in repo — must be produced (screenshots can come from the device run in 4.1) |
| 3 | Content declarations: Privacy Policy (local SQLite, zero telemetry), 18+, Data Safety = no data collected/shared | ⏳ manual forms |
| 4 | Testing → Internal Testing → upload `A2D/app/build/outputs/bundle/release/app-release.aab` + tester emails | ⏳ blocked on steps 1–3 |
| 5 | Promote Release → Production → submit for review | ⏳ last step |

---

# Overall Summary

| Sprint | Milestones | Result |
| --- | --- | --- |
| 0 — Environment & scaffolding | 0.1, 0.2 | ✅ 2/2 (JDK 17, wrapper 8.9, SDK 34, adb verified; config files written; `./gradlew help` OK) |
| 1 — Domain, engines, tests | 1.1, 1.2, 1.3 | ✅ 3/3 — **`./gradlew test`: 8/8 tests pass** |
| 2 — Room, repository, DI | 2.1, 2.2, 2.3 | ✅ 3/3 — **`./gradlew compileDebugKotlin`: BUILD SUCCESSFUL** |
| 3 — Compose UI | 3.1, 3.2, 3.3 | ✅ 3/3 — **`./gradlew assembleDebug`: BUILD SUCCESSFUL** (app-debug.apk ≈10.1 MB) |
| 4 — Deploy & release | 4.1, 4.2, 4.3 | ⚠️ 1/3 — 4.2 ✅ signed `app-release.aab` (2.68 MB, R8 + shrinking); 4.1 & 4.3 blocked on physical device / Play Console (manual) |

**Final state:** all 31 spec source files implemented 1:1 under `A2D/`, all three verification commands green, debug APK + signed production AAB built. Remaining work is device onboarding (`./a2d_deploy.sh`) and the Play Console submission.

---
---

# Redesign 01 — Task Card Redesign (`redesign01.md`) — Progress Log

Proposal source: `redesign01.md` (Task Card Redesign Specification v1.0).
Planned tasks: T1 domain completion rule → T2 ViewModel operations → T3 navigation →
T4 TaskListScreen → T5 TaskDetailScreen → T6 verification → T7 README update.

## Redesign T1 — Domain completion rule & ID ordering ✅

**Status:** Complete — verified with `./gradlew test` (BUILD SUCCESSFUL, **11/11 tests pass**)

**Changes:**
| File | Change |
| --- | --- |
| `domain/model/Task.kt` | ➕ added computed property `val isCompleted` implementing the spec rule `activities.isNotEmpty() && activities.all { it.achieved }`. Kept separate from the Tier-1 gate flag `achieved` (gate treats an empty task as achieved; the redesign rule requires ≥1 activity — both behaviors are now available: `isCompleted` for UI, `achieved` for the gate pipeline) |
| `data/repository/TaskRepositoryImpl.kt` | ➕ `.sortedBy { it.id }` added to activity mapping in `getAllTasks()`, `getTaskSnapshot()` and `getTask()` — guarantees the spec's "original order by ID" display order regardless of SQLite row order |
| `test/.../TaskDomainTest.kt` | ➕ 3 new tests: `testIsCompletedFalseWhenNoActivities`, `testIsCompletedFalseWhenAnyActivityUnchecked`, `testIsCompletedTrueWhenAllActivitiesAchieved` |

**Verification:** `./gradlew test` → BUILD SUCCESSFUL (5m 35s); reports show
`TaskDomainTest` tests=5, `SerialAndGateProcessorTest` tests=4, `IntegratedGateTaskFlowTest` tests=2 — **0 failures, 0 errors**.

## Redesign T2 — TaskViewModel operations ✅

**Status:** Complete — `A2D/app/src/main/java/com/a2d/app/ui/task/TaskViewModel.kt` updated per spec §1 & §6

**Exposed state:**
- `tasks: StateFlow<List<Task>>` — unchanged (Room flow, `WhileSubscribed(5000)`).
- `currentTask: StateFlow<Task?>` — ➕ `combine(tasks, selectedTaskId) { list, id -> list.find { it.id == id } }`, initial `null`, `WhileSubscribed(5000)` exactly as the spec's derived-state pattern (implemented in the shared ViewModel rather than the composable so all mutations/state stay out of the UI layer).
- `selectTask(taskId: Long)` — ➕ sets the id consumed by `currentTask` (called by the detail screen).

**Operations (all Room-backed, all re-emit `tasks`/`currentTask`, all re-run the Tier-1 gate + Tier-2 pipeline):**
| Operation | Status | Behavior |
| --- | --- | --- |
| `toggleActivity(taskId, activityId, achieved)` | ➕ new (replaces `toggleActionItem(task, actionId)`) | reloads task from Room, sets the exact new `achieved` value, gate-recomputes, saves |
| `addActivity(taskId)` | ➕ new | appends `ActionItem(newId, "New activity")`; id = `max(globalMaxActivityId, taskId*10) + 1` → strictly greater than every existing id, so it is globally unique (Room PK) and sorts to the end of the ID-ordered list |
| `deleteActivity(taskId, activityId)` | ➕ new | filters the item out, gate-recomputes, saves |
| `deleteTask(taskId)` | ✅ kept (spec signature) | deletes task (CASCADE removes activities), recalculates pipeline |
| `createSampleTask(id, name, priority)` | ✅ kept | retains the existing "+ Task" affordance (spec doesn't define task creation; removing it would strand the user) |

No sorting/reordering logic added anywhere — display order remains natural ID order (T1).

## Redesign T3 — Jetpack Navigation setup ✅

**Status:** Complete — spec §5 routes implemented in `ui/MainActivity.kt` + new dependencies

**Dependencies added** (`gradle/libs.versions.toml`, `app/build.gradle.kts`):
- `androidx.navigation:navigation-compose:2.8.2` — spec's `navController.navigate("taskDetail/$taskId")` / `navigateUp()` navigation.
- `androidx.compose.material:material-icons-core` (BOM-managed) — `Icons.Filled.Delete` / `Add` / `ArrowBack` used by the redesign's delete icons, add-activity button and back button (not transitively provided by material3).

**`MainActivity.kt` rewrite:**
- `rememberNavController()` + `NavHost(startDestination = "taskList")` inside the existing `Scaffold`, so `innerPadding` still clears the bottom bar.
- Routes per spec: `taskList` → `TaskListScreen`, `taskDetail/{taskId}` → `TaskDetailScreen` with `navArgument("taskId") { type = NavType.LongType }`, plus `logicGate` (pre-existing tab retained).
- Bottom `NavigationBar` kept from the original app: **Tasks** / **Logic Gate**; tab selection derived from `currentBackStackEntryAsState` route, and the Tasks tab stays selected while on `taskDetail/{taskId}`. Tab switches use `popUpTo(saveState) + launchSingleTop + restoreState`.
- Detail route loads `currentTask` from `TaskViewModel` and calls `selectTask(taskId)` in a `LaunchedEffect`; `onDeleteTask` deletes **and** `navigateUp()`s back to the list (spec §4).
- `TaskScreen`'s old inline wiring replaced: the list route receives `onTaskClick`/`onToggleActivity`/`onAddActivity`/`onDeleteActivity`/`onDeleteTask`/`onCreateSample`.

## Redesign T4 — TaskListScreen (compact cards) ✅

**Status:** Complete — spec §2 & §4 implemented; `TaskScreen.kt` removed (refactor target per spec §7)

**New file: `ui/task/TaskListScreen.kt`** containing:

| Component | Spec conformance |
| --- | --- |
| `TaskListScreen(...)` | `LazyColumn` of compact `TaskCard`s (keyed by task id). Retains the app's header: `A2D Focus Tasks` + `Milestone Complete/Incomplete [GATE n]` status + `+ Task` button (task creation isn't covered by the redesign spec — kept so users can still create tasks). Maps each card to `taskId`-scoped callbacks so composables never touch the ViewModel directly (spec §6 state handling) |
| `TaskCard(task, onTaskClick, onToggleActivity, onAddActivity, onDeleteActivity, onDeleteTask)` | `ElevatedCard(onClick = onTaskClick)` — whole card body is tappable to open detail; inner controls (checkbox, icon buttons) consume their own clicks so they don't trigger navigation. Header row: **×** delete `IconButton` (red tint) + title + optional description. Activity `LazyColumn` capped with `heightIn(max = 200.dp)` for internal scrolling per spec. `+ Add activity` `Button` (with `Add` icon) below the list |
| `ActivityRow(activity, onCheckedChange, onDelete)` | Checkbox bound to `activity.achieved`; description styled with the **exact** snippet from spec §2 (bodyLarge, `onSurfaceVariant.copy(alpha = 0.6f)` + `TextDecoration.LineThrough` when done, `onSurface` otherwise); delete `Icons.Filled.Delete` icon on the right. Declared public/shared — reused unchanged by `TaskDetailScreen` (spec §6: "shared or duplicated") |
| `TaskDeleteDialog` | `AlertDialog`: "Delete task?" / "This will delete the task and all of its activities." / Cancel + red **Delete** (spec §4) |
| `ActivityDeleteDialog` | `AlertDialog`: "Delete activity?" / "This will remove this activity from the task." / Cancel + red **Delete** (spec §4) |

- Per-card dialog state (`showTaskDeleteDialog`, `pendingActivityDeleteId`) lives inside `TaskCard`, so only the tapped card shows a dialog.
- Empty state: activity `LazyColumn` simply renders with 0 items, `Add activity` still visible, no hint text (spec §2).

**Removed:** `ui/task/TaskScreen.kt` (old single-screen layout; replaced by list + detail).

## Redesign T5 — TaskDetailScreen (full-screen) ✅

**Status:** Complete — spec §3 implemented; `ui/task/TaskDetailScreen.kt` created

| Element | Implementation |
| --- | --- |
| Navigation / back | `IconButton(Icons.AutoMirrored.Filled.ArrowBack)` wired to `onNavigateBack()` → `navController.navigateUp()` (spec §3). Route already receives `taskId: Long` (LongType arg) |
| Header (`TaskDetailHeader`) | Title (`headlineSmall`) + optional description + completion badge: exact spec §3 formula (`isCompleted = activities.isNotEmpty() && all achieved` → domain `Task.isCompleted`), `statusText` "Completed"/"In progress", `statusColor` `primaryContainer.copy(alpha=0.8f)` vs `surfaceVariant`, rendered as a pill `Surface(RoundedCornerShape(50))` badge |
| Activity list | `LazyColumn` with `weight(1f)` (fills remaining full-screen space — no max-height cap needed here), ID order, shared `ActivityRow` (identical checkbox/strikethrough/delete styling as the list screen, spec §3 "styling is identical") |
| Task delete | `Icons.Filled.Delete` in the top bar → `TaskDeleteDialog` → confirm calls `onDeleteTask(taskId)`; `MainActivity` then deletes **and navigates back** to the list (spec §4: "navigate back to the task list after deletion") |
| Add activity | `Button` with `+` icon, label "Add activity", placed **below** the list; renders below the (empty) `LazyColumn` when there are no activities, no hint text (spec §3) |
| Activity delete | Per-row delete icon → `ActivityDeleteDialog` → `deleteActivity(taskId, activityId)` |
| Loading/missing state | While `currentTask` is still `null` (Room has not emitted yet), shows a centered `CircularProgressIndicator` |

## Redesign T6 — Verification ✅ (redesign build: tests + APK both green)

**Status:** Complete

```
$ ./gradlew test
BUILD SUCCESSFUL in 8m 24s   (61 actionable tasks)
→ 11/11 unit tests pass (TaskDomainTest 5, SerialAndGateProcessorTest 4, IntegratedGateTaskFlowTest 2)
  0 failures, 0 errors — includes the 3 new isCompleted tests from T1

$ ./gradlew assembleDebug
BUILD SUCCESSFUL in 3m 54s   (41 actionable tasks)
→ A2D/app/build/outputs/apk/debug/app-debug.apk (10,341,967 bytes, Oct 8 13:16)
```

Compiles clean end-to-end: new navigation dependency resolves, KSP/Hilt regenerate the graph for the new
`NavHost` entry points, Room schema unchanged (spec §7: "No Room schema changes are required"), and R8 is
untouched (debug build). No Kotlin compilation errors or warnings in the new UI files.

**Files changed by the redesign (final inventory):**
- ➕ `ui/task/TaskListScreen.kt`, ➕ `ui/task/TaskDetailScreen.kt`
- ✏️ `ui/task/TaskViewModel.kt`, ✏️ `ui/MainActivity.kt`, ✏️ `domain/model/Task.kt`,
  ✏️ `data/repository/TaskRepositoryImpl.kt`, ✏️ `test/.../TaskDomainTest.kt`,
  ✏️ `gradle/libs.versions.toml`, ✏️ `app/build.gradle.kts`
- 🗑️ `ui/task/TaskScreen.kt` (refactored into the two new screens)

## Redesign T7 — README.md updated ✅

**Status:** Complete

- Appended **`## 7. redesign01 — Task Card Redesign (Completed)`** to `README.md` (line 440).
- Contains the per-task summary table (T1–T7 with status + deliverables) and the behavior notes
  (completion rule, mandatory confirmation dialogs, ID ordering, no expand/collapse).

---

## Redesign 01 — Overall Result

| Task | Result |
| --- | --- |
| T1 Domain completion rule + ID ordering + tests | ✅ `./gradlew test` green (11/11) |
| T2 TaskViewModel operations + `currentTask` | ✅ spec §1 operations all present |
| T3 Jetpack Navigation (`taskList` / `taskDetail/{taskId}` / `logicGate`) | ✅ |
| T4 `TaskListScreen` + `TaskCard` + `ActivityRow` + dialogs (old `TaskScreen` removed) | ✅ |
| T5 `TaskDetailScreen` (back, badge header, list, dialogs, add activity) | ✅ |
| T6 Verification (`test` + `assembleDebug`) | ✅ BUILD SUCCESSFUL, `app-debug.apk` 10,341,967 bytes |
| T7 README `redesign01` section | ✅ |

All 7 tasks complete. The redesign implements `redesign01.md` v1.0 in full; no Room schema changes were needed.
Remaining manual step (unchanged from Sprint 4): deploy to a physical device with `./a2d_deploy.sh` once a phone is attached.

---
---

# Redesign 02 — Task UI Iteration (`redesign02.md`) — Progress Log

Proposal source: `redesign02.md` (editable task name/description, collapsible card bodies, header progress %).
Planned tasks: R2-T1 progress domain rule → R2-T2 `updateTask` → R2-T3 TaskListScreen rework + wiring →
R2-T4 verification → R2-T5 README section.

## Redesign02 R2-T1 — Domain progress rule ✅

**Status:** Complete — verified with `./gradlew test` (BUILD SUCCESSFUL, **15/15 tests pass**)

| File | Change |
| --- | --- |
| `domain/model/Task.kt` | ➕ added `val progressPercent: Int` computed with the spec §1 snippet verbatim: `0` when `activities.isEmpty()`, else `(count { it.achieved } * 100 / activities.size)` (integer division). Sits alongside the redesign01 `isCompleted` property; no schema or ViewModel change needed for progress |
| `test/.../TaskDomainTest.kt` | ➕ 4 new tests: `testProgressPercentZeroWhenNoActivities` (0), `testProgressPercentPartialCompletion` (1/4 → 25), `testProgressPercentTruncatesIntegerDivision` (2/3 → 66), `testProgressPercentHundredWhenAllAchieved` (100) |

**Verification:** `./gradlew test` → BUILD SUCCESSFUL (2m 35s); reports:
`TaskDomainTest` tests=9, `SerialAndGateProcessorTest` tests=4, `IntegratedGateTaskFlowTest` tests=2 — **0 failures, 0 errors**.

## Redesign02 R2-T2 — `updateTask` ViewModel operation ✅

**Status:** Complete — `ui/task/TaskViewModel.kt` updated per spec §1

```kotlin
fun updateTask(taskId: Long, newName: String, newDescription: String)
```

- **Validation/trim** (spec §1): both inputs `.trim()`; a blank name after trimming **falls back to the
  current `taskName`** so a task can never be renamed to an empty string; description may legitimately be empty.
- **No-op guard:** if both resolved values equal the stored ones, the coroutine returns without a Room write.
- **Persistence:** reloads the task via `repository.getTask`, `task.copy(taskName = ..., description = ...)`
  (activities/`achieved`/priority/context untouched), then `repository.saveTask(updated)`.
- Room's `Flow` re-emits afterwards, so `tasks` / `currentTask` update the UI automatically (spec §1:
  "Emits updated `tasks` flow"). Gate/pipeline recalculation intentionally skipped — name/description do not
  affect Tier-1/Tier-2 evaluation.
- Existing operations (`toggleActivity`, `addActivity`, `deleteActivity`, `deleteTask`, `createSampleTask`,
  `selectTask`/`currentTask`) unchanged, as required by spec §1.

## Redesign02 R2-T3 — TaskListScreen rework: header, collapsible body, inline edit ✅

**Status:** Code complete — spec §2–§5 + §8 implemented; `ui/task/TaskListScreen.kt` rewritten, `MainActivity` wired.
(Compile/runtime verification is R2-T4.)

**Hoisted per-task UI state** (spec §8, `mutableStateMapOf` keyed by task id, placed at screen level so it
survives `LazyColumn` item disposal):
- `expandedStates` (default **expanded**), `editingStates` (default not editing).
- Entering edit mode force-expands the body (fields live in the body); collapsing while editing first persists.

**`TaskCard(task, isExpanded, isEditing, onHeaderClick, onEditingChange, onSaveEdit, onTaskBodyClick, ...)`:**
| Element | Behavior |
| --- | --- |
| `TaskCardHeader` | Row per spec §5: `[Edit IconButton] [task name · titleMedium · weight(1f)] [progress%] [× delete IconButton]`. Header row `.clickable { onHeaderClick() }.padding(12.dp)` → toggles collapse; child edit/delete buttons consume their own taps (they are `focusProperties { canFocus = false }` so they can never steal focus from the edit fields). Edit icon switches `Edit` → `Done` while editing (spec §3 "changes state to indicate Done editing"). Progress colors per spec snippet: `primary` at 100 %, else `onSurfaceVariant` |
| `TaskCardBody` | Shown only when expanded. **View mode:** optional description (non-blank) + activity `LazyColumn` (200 dp cap, shared `ActivityRow` with strikethrough/fade styling) + `+ Add activity` button; the body container is `.clickable { onBodyClick() }` → opens the Task Detail screen (interactive children — checkbox, delete icons, buttons — consume their own taps). **Edit mode:** two `OutlinedTextField`s ("Task name" single-line with `FocusRequester`, "Task description" 2–5 lines) + the same activity list/add button; body navigation is disabled while editing so taps in the fields can't trigger navigation |
| Edit persistence | **Trigger A — edit button again:** calls `exitEditMode()` → `onSaveEdit(draftName, draftDescription)` + `onEditingChange(false)`. **Trigger B — focus lost:** `Modifier.onFocusChanged` on the edit-fields column; guarded by a `focusEntered` arm flag (ignores the initial attach event so entering edit mode can't self-dismiss), and on a real loss while editing it stamps `lastEditExitAt` then saves+exits. Field→field focus moves keep `hasFocus == true` → no premature save |
| Race guard | A 400 ms `lastEditExitAt` window suppresses the edit button's *enter* branch if the same physical tap is what just cleared field focus (focus-lost path already handled it) — prevents exit-then-immediate-re-enter regardless of event ordering |
| Drafts | `draftName`/`draftDescription` initialized from the task and reloaded in `LaunchedEffect(isEditing)` on every entry into edit mode; saved values go through `updateTask` (T2 trims/validates) |

**Dialogs:** `TaskDeleteDialog` (via × in header) and `ActivityDeleteDialog` (via row delete icon) unchanged
from redesign01 — spec §7 requires both to remain with confirmation.

**`MainActivity.kt`:** list route gains `onSaveEdit = { taskId, name, desc -> taskVm.updateTask(...) }`.

**Unchanged (per spec §6/§7/§9):** `TaskDetailScreen.kt`, delete behaviors, navigation graph
(`taskDetail/{taskId}` already in place), Room schema (`TaskEntity` already stores `taskName`/`description`).

## Redesign02 R2-T4 — Verification ✅

**Status:** Complete — both spec verification commands green

```
$ ./gradlew test
BUILD SUCCESSFUL in 1m 22s   (61 actionable tasks)
→ 15/15 unit tests pass — TaskDomainTest 9 (4 new progressPercent tests), 
  SerialAndGateProcessorTest 4, IntegratedGateTaskFlowTest 2; 0 failures, 0 errors

$ ./gradlew assembleDebug
BUILD SUCCESSFUL in 24s   (41 actionable tasks)
→ A2D/app/build/outputs/apk/debug/app-debug.apk (10,376,810 bytes, Oct 8 17:36)
```

- The reworked `TaskListScreen.kt` (focus handling, `FocusRequester`, `focusProperties`, `mutableStateMapOf`,
  edit fields) and `updateTask` compile clean on both debug and release variants — no Kotlin errors/warnings.
- No Room schema migration needed (spec §9); KSP/Hilt regenerated without issues.
- Note: `Icons.Filled.Done`/`Edit` resolved from `material-icons-core` (added in redesign01).

**Files changed by redesign02:**
- ✏️ `domain/model/Task.kt` (+`progressPercent`)
- ✏️ `ui/task/TaskViewModel.kt` (+`updateTask`)
- ✏️ `ui/task/TaskListScreen.kt` (header/collapse/edit rework: `TaskCardHeader`, `TaskCardBody` split per spec §8)
- ✏️ `ui/MainActivity.kt` (+`onSaveEdit` wiring)
- ✏️ `test/.../TaskDomainTest.kt` (+4 progress tests)
- ⏭️ `TaskDetailScreen.kt`, navigation graph, Room schema: **unchanged** (spec §6/§7/§9)

## Redesign02 R2-T5 — README.md updated ✅

**Status:** Complete

- Appended **`## 8. redesign02 — Editable & Collapsible Task Cards (Completed)`** to `README.md` (line 464).
- Contains the R2-T1…R2-T5 summary table with statuses and deliverables, plus behavior notes
  (progress %, collapse semantics, edit persistence triggers, body-tap navigation, preserved redesign01 behaviors).

---

## Redesign02 — Overall Result

| Task | Result |
| --- | --- |
| R2-T1 `Task.progressPercent` + 4 tests | ✅ `./gradlew test` green (15/15) |
| R2-T2 `updateTask(taskId, newName, newDescription)` | ✅ trim/validation/no-op guard |
| R2-T3 TaskListScreen header/collapse/inline-edit + MainActivity wiring | ✅ spec §2–§5, §8 |
| R2-T4 Verification (`test` + `assembleDebug`) | ✅ BUILD SUCCESSFUL, `app-debug.apk` 10,376,810 bytes |
| R2-T5 README `redesign02` section | ✅ §8 appended |

All 5 tasks complete. `redesign02.md` implemented in full; no Room schema changes, no changes to
`TaskDetailScreen`, navigation routes, or delete-confirmation behavior (as specified). Both redesigns
(redesign01 + redesign02) verified in the same build. Remaining manual step: deploy to a physical
device with `./a2d_deploy.sh` once a phone is attached.

---

## Bugfix B1 (redesign02 issues) — F1: Task edit focus/exit mechanics reworked ✅

**Status:** Complete

**Bugs addressed:**
1. Task name/description editing only worked the first time.
2. Tapping the description field closed edit mode even on the first attempt.

**Root causes identified:**
- *Bug 2:* `onFocusChanged` was attached to the parent edit-fields `Column`, so the transient
  `hasFocus = false` during a name → description focus transfer was treated as a real focus loss
  and immediately saved + exited edit mode.
- *Bug 1:* every tap on the edit button while a field was focused first cleared field focus
  (focus-loss path exited edit mode and stamped `lastEditExitAt`), then the button's `onClick`
  fired and was swallowed by the 400 ms `justExitedByFocus` guard — so subsequent taps looked
  dead ("works only the first time").

**Fix (`ui/task/TaskListScreen.kt`):**
- Per-field focus tracking: `nameFocused` / `descFocused` booleans, `onFocusChanged` moved from
  the parent `Column` onto each `OutlinedTextField`; `focusArmed` gates auto-exit until a field
  has actually gained focus once after entering edit mode.
- Deferred exit: `LaunchedEffect(isEditing, focusArmed, nameFocused, descFocused)` waits two
  frames (`withFrameNanos` × 2) before re-checking — a focus transfer cancels/restarts it, so
  only a genuine focus loss saves + exits. All re-checks read live snapshot state via
  `rememberUpdatedState` (`currentIsEditing`, `currentOnSaveEdit`, `currentOnEditingChange`).
- Edit button is now the single authoritative toggle: `IconButton(onClick = {})` +
  `MutableInteractionSource` collecting `PressInteraction.Press` (fires at touch-down, before any
  deferred focus-loss commit) with `rememberUpdatedState` to avoid stale closures.
- Removed the 400 ms `lastEditExitAt` guard and `focusEntered` flag entirely.

**Verified:** `./gradlew compileDebugKotlin` — BUILD SUCCESSFUL.

## Bugfix B1 (redesign02 issues) — F2: `updateActivity` ViewModel operation ✅

**Status:** Complete

- Added `TaskViewModel.updateActivity(taskId, activityId, newDescription)` mirroring the
  `updateTask` validation pattern: trims input, blank description falls back to the existing
  value, no-op guard skips identical writes.
- Achievement flags are untouched by a description edit, so no `processActivityWithGate` /
  `recalculatePipeline` call is needed (progress is derived from `achieved`, not text).
- **File:** `ui/task/TaskViewModel.kt` — compile verified (`compileDebugKotlin` green).

## Bugfix B1 (redesign02 issues) — F3: Activity description inline editing ✅

**Status:** Complete — bug 3 (no edit provision for activity descriptions) fixed in both
the task list cards and the task detail screen.

**`TaskViewModel`:** (F2, above) `updateActivity(taskId, activityId, newDescription)`.

**`ui/task/TaskListScreen.kt`:**
- `ActivityRow` reworked: checkbox + `Text`/`OutlinedTextField` swap + edit toggle icon
  (`Edit` ⇄ `Done`) + delete icon. Field is single-line with IME `Done` action
  (`KeyboardOptions`/`KeyboardActions` → `onCommitEdit`).
- Edit toggle is press-based (`MutableInteractionSource` + `PressInteraction.Press` with
  `rememberUpdatedState`) so the commit always wins the race against the deferred
  focus-loss commit — same pattern as the task edit button (no enter/exit double-toggle).
- Editing state hoisted to `TaskCard` (`editingActivityId`, `activityDraft`,
  `activityFieldFocused`, `activityFocusArmed`, shared `FocusRequester`) so it survives the
  inner activity `LazyColumn` disposing rows on scroll. Auto-focus on entry via
  `runCatching { requestFocus() }` (guards the row-not-composed race).
- Commit triggers: edit-icon re-tap (press), IME Done, deferred focus loss (2-frame re-check,
  mirrors F1), header collapse, body tap (navigate), starting another row's edit,
  entering task edit mode (mutual exclusion via `commitActivityEdit()`), activity delete
  of the row being edited (state cleared; VM no-op guards stale ids).
- `TaskListScreen` gained `onUpdateActivity: (Long, Long, String) -> Unit`.

**`ui/task/TaskDetailScreen.kt`:**
- Same hoisted activity-edit state, focus/deferred-commit effects, and `ActivityRow` wiring.
- Gained `onUpdateActivity: (Long, Long, String) -> Unit` param.

**`ui/MainActivity.kt`:** wired `onUpdateActivity` → `taskVm.updateActivity(...)` for both
the `taskList` and `taskDetail/{taskId}` routes.

**Verified:** `./gradlew compileDebugKotlin` green.

## Bugfix B1 (redesign02 issues) — F4: Verification ✅

**Status:** Complete

```
./gradlew test           → BUILD SUCCESSFUL (15/15 unit tests: TaskDomainTest 9,
                            SerialAndGateProcessorTest 4, IntegratedGateTaskFlowTest 2;
                            0 failures, 0 errors)
./gradlew assembleDebug  → BUILD SUCCESSFUL
                           app/build/outputs/apk/debug/app-debug.apk (10,433,734 bytes)
```

---

## Bugfix B1 (redesign02 issues) — Overall Result

| Task | Result |
| --- | --- |
| F1 task edit focus/exit rework (per-field focus, 2-frame deferred exit, press-based toggle, 400 ms guard removed) | ✅ compiles; fixes bugs 1 & 2 |
| F2 `TaskViewModel.updateActivity` | ✅ trim/blank-fallback/no-op guard |
| F3 activity description inline editing (list + detail + MainActivity wiring) | ✅ fixes bug 3 |
| F4 verification (`test` + `assembleDebug`) | ✅ 15/15 green, APK built |

All 3 reported bugs fixed. Remaining manual step: deploy to a physical device with
`./a2d_deploy.sh` (no device attached) and re-test editing flows on-device.
