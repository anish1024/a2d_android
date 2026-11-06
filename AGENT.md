# AGENT.md — Targeted Change Guide for A2D Android

This file is for a small local coding agent (for example a Qwen-class 9B model) that needs to make a
**small, targeted change or bug fix** without loading the whole project into context.

For deep architecture, history, and design rationale, read **[`README.md`](README.md)**. Archived
Markdown under `docs/archive/` is historical and must not be used as current implementation guidance.

> **Last verified:** 2026-11-06 · code baseline `8d3b7f4`

---

## 1. Read This First (30 seconds)

| Fact | Value |
| --- | --- |
| Repository root | `/home/pi/Projects/a2d_android` |
| Gradle project root | `A2D/` — run all Gradle commands here |
| Package | `com.a2d.app` |
| Application ID | `com.a2d.app` |
| Launcher component | `com.a2d.app/.ui.MainActivity` |
| Language / UI | Kotlin + Jetpack Compose + Material 3 |
| Architecture | MVVM, Hilt DI, Room, Coroutines/Flow |
| Current debug APK | `A2D/app/build/outputs/apk/debug/app-debug.apk` |
| Deployment | `Deployment/a2d_deploy.sh` (run from repo root) |

**Golden rules**

1. Make the smallest diff that solves the stated problem.
2. Do not refactor unrelated files, rename packages, or upgrade dependencies.
3. Do not edit `A2D/app/build/` outputs or `.gradle/` caches.
4. Do not use files under `docs/archive/` as current source.
5. Do not change the Room schema without explicit human approval (see §7).
6. Preserve the existing state flow: Compose → `TaskViewModel` → repository → Room → Flow → Compose.
7. After code changes, run the focused test/build command in §8.
8. If a requirement is ambiguous or touches schema/DI/navigation broadly, stop and ask.

---

## 2. Where Things Live

```text
A2D/app/src/main/java/com/a2d/app/
├── A2DApplication.kt                 # @HiltAndroidApp
├── domain/
│   ├── model/Task.kt                 # Task + isCompleted + Tier-1 gate call
│   ├── model/ActionItem.kt           # Checklist item (id, description, achieved)
│   ├── model/ActivityAchieved.kt     # achieved property contract
│   ├── model/PulseResult.kt          # Gate result DTO
│   ├── model/User.kt, UserGroup.kt   # Auth model stubs only (no login UI)
│   ├── contract/ActivityProcessor.kt # processActivity contract
│   ├── engine/SerialAndGateProcessor.kt      # Gate accumulator (pin arity)
│   └── engine/TaskListPipelineProcessor.kt   # Tier-2 task-list gate
├── data/
│   ├── local/A2DDatabase.kt          # Room DB, version 1
│   ├── local/dao/TaskDao.kt          # Flow + suspend queries, transaction upsert
│   ├── local/entity/TaskEntity.kt
│   ├── local/entity/ActionItemEntity.kt      # FK CASCADE to tasks
│   ├── local/relation/TaskWithActionItems.kt
│   └── repository/TaskRepositoryImpl.kt      # Entity ↔ domain mapping
├── di/AppModule.kt                   # Provides database + DAO only
└── ui/
    ├── MainActivity.kt               # NavHost + bottom bar
    ├── task/TaskListScreen.kt        # Task cards, ActivityRow, delete dialogs
    ├── task/TaskDetailScreen.kt      # Full-screen detail + completion badge
    ├── task/TaskViewModel.kt         # All task mutations + gate recalculation
    ├── logicgate/LogicGateScreen.kt  # Manual pulse simulator
    ├── logicgate/LogicGateViewModel.kt
    └── theme/Color.kt, Theme.kt
```

Tests live in `A2D/app/src/test/java/com/a2d/app/`:

- `SerialAndGateProcessorTest.kt`
- `TaskDomainTest.kt`
- `IntegratedGateTaskFlowTest.kt`

Files that **do not exist** anymore (do not add code to them): `TaskScreen.kt`, `ui/home/HelloWorldScreen.kt`, `ui/theme/Type.kt`.

Project documents:

- `README.md` — canonical current-state architecture and behavior.
- `CHANGELOG.md` — versioned release notes.
- `docs/history/implementation-log.md` — historical + current implementation log and verified commit IDs.
- `docs/known-issues.md` — recurring bugs, limitations, and workarounds.
- `docs/features/FEAT-0001-task-card-redesign.md` — completed task-card redesign record.
- `docs/adr/0001-technology-stack.md` — accepted technology-stack decision.
- `docs/features/` and `docs/adr/` — templates and future feature/decision records.
- `docs/archive/` — historical only; never use as current guidance.

**History lookup.** `docs/history/implementation-log.md` is the index and rules. If the log is split
into shards under `docs/history/implementation-log/`, read only the index table, match by feature
ID/date/topic, and open at most one shard. If the index is ambiguous, run
`rg -n "FEAT-XXXX|YYYY-MM" docs/history/` before reading. Never load all shards or treat
`docs/archive/` as current guidance.

---

## 3. Fast Task Routing

| User asks for… | Edit these files |
| --- | --- |
| Change a label, button text, dialog wording | The matching screen in `ui/task/` or `ui/logicgate/` |
| Change task-card layout | `ui/task/TaskListScreen.kt` (`TaskCard`, `ActivityRow`) |
| Change task-detail layout | `ui/task/TaskDetailScreen.kt` (`TaskDetailHeader`) |
| Change completion behavior | `domain/model/Task.kt` (`isCompleted`) and/or `ui/task/TaskDetailScreen.kt` |
| Add/rename a task operation | `ui/task/TaskViewModel.kt`, then wire callbacks in `MainActivity.kt` and the screen |
| Fix checkbox/delete behavior | `ui/task/TaskViewModel.kt` + the screen's callback wiring |
| Change navigation routes | `ui/MainActivity.kt` |
| Change gate/pulse logic | `domain/engine/SerialAndGateProcessor.kt` / `TaskListPipelineProcessor.kt` / `Task.processActivityWithGate` |
| Change colors/theme | `ui/theme/Color.kt`, `ui/theme/Theme.kt` |
| Change persistence/query order | `data/local/dao/TaskDao.kt`, `data/repository/TaskRepositoryImpl.kt` |
| Change DI construction | `di/AppModule.kt` or the class's `@Inject constructor` |
| Change device install/launch | `Deployment/a2d_deploy.sh` |
| Change device prerequisite docs | `Deployment/a2d_build.md` |

---

## 4. Key Domain Rules (easy to get wrong)

### 4.1 Two different completion concepts

- `Task.achieved` is the **Tier-1 gate flag**. An empty task is treated as achieved by the gate.
- `Task.isCompleted` is the **UI completion rule**:
  `activities.isNotEmpty() && activities.all { it.achieved }`.
  An empty task is **not** completed in the UI.

Do not replace one with the other. The detail badge uses `isCompleted`; the pipeline uses `achieved`.

### 4.2 Activity ordering

Activities are always displayed in **ID order**. `TaskRepositoryImpl` applies `.sortedBy { it.id }`
in all three mapping paths. New activities are appended with a globally unique ID:

```kotlin
val newId = maxOf(maxExistingId, taskId * 10) + 1
```

Do not add sorting/reordering UI.

### 4.3 Gate behavior

- The pin name is the **arity** (`activities.size` or `tasks.size`).
- A `"HIGH"` pulse latches `1` until the latch count reaches arity → `output = 1`.
- A `"LOW"` pulse clears the latch and returns `reset = true`.
- `Task.processActivityWithGate()` resets the pin before evaluation.
- `TaskListPipelineProcessor.evaluateTaskList(emptyList())` returns `output = 1`.

Keep these semantics unless the task explicitly asks to change them.

### 4.4 Delete confirmation

All deletes in both list and detail screens must keep using `AlertDialog` confirmation. Reuse
`TaskDeleteDialog` / `ActivityDeleteDialog` from `TaskListScreen.kt`.

---

## 5. Code Patterns to Follow

### 5.1 ViewModel mutation pattern

```kotlin
fun someOperation(taskId: Long) {
    viewModelScope.launch {
        val task = repository.getTask(taskId) ?: return@launch
        // mutate task.activities here
        task.processActivityWithGate(gateProcessor) // Tier 1
        repository.saveTask(task)
        recalculatePipeline()                       // Tier 2
    }
}
```

### 5.2 Compose state pattern

Screens receive plain values/callbacks; they do **not** receive the ViewModel directly
(except `LogicGateScreen` is also callback-based). Keep callbacks task-scoped in `MainActivity.kt`
or in the list/detail screen.

### 5.3 Hilt pattern

- `SerialAndGateProcessor`, `TaskListPipelineProcessor`, and `TaskRepositoryImpl` are
  constructor-injected `@Singleton`s. No provider functions are needed for them.
- `AppModule` provides `A2DDatabase` and `TaskDao`.
- ViewModels use `@HiltViewModel` + `@Inject constructor`.

### 5.4 Room mapping pattern

When mapping entities to domain models, preserve this shape:

```kotlin
activities = withItems.actionItems
    .sortedBy { it.id }
    .map { ActionItem(it.id, it.description, it.achieved) }
```

---

## 6. Build / Test Commands

Always `cd A2D` first.

```bash
# Fast compile check
./gradlew compileDebugKotlin

# Unit tests (11 currently)
./gradlew test

# One test class only
./gradlew test --tests "com.a2d.app.TaskDomainTest"

# Debug APK
./gradlew assembleDebug

# Release bundle (needs the local keystore file)
./gradlew bundleRelease
```

Environment requirements:

- JDK 17.
- Android SDK 34 (compileSdk 34).
- `A2D/local.properties` is git-ignored. If it is missing, either create it with
  `sdk.dir=/path/to/android-sdk` or export `ANDROID_HOME` / `ANDROID_SDK_ROOT`.
- Do not run Gradle from the repository root; there is no root Gradle build.

Deploy after building:

```bash
# From repository root
./Deployment/a2d_deploy.sh
```

The script finds `adb`, installs `A2D/app/build/outputs/apk/debug/app-debug.apk`, and launches
`com.a2d.app/.ui.MainActivity`.

---

## 7. Do Not Cross These Lines Without Approval

Ask a human before:

1. Changing Room entities, column names, table names, or `A2DDatabase.version`.
2. Adding/changing a migration (the project currently has no migrations; schema changes need one).
3. Upgrading AGP, Kotlin, Gradle, Hilt, Room, Compose, or Navigation versions.
4. Renaming packages, modules, routes, or the application ID.
5. Deleting source files or archived docs.
6. Changing the signing/keystore configuration.
7. Large refactors that touch more than the task requires.

If the fix seems to require one of the above, explain the smallest safe alternative and stop.

---

## 8. Definition of Done

For a targeted change, the task is done when:

- Only the necessary files changed.
- `./gradlew compileDebugKotlin` passes.
- Relevant unit tests pass (`./gradlew test`, or a focused `--tests` run).
- UI behavior matches the requested change without altering the two-tier gate semantics.
- If behavior or setup changed, update the relevant section of `README.md` (and
  `Deployment/a2d_build.md` for deployment changes).
- If code changed, append an entry to `docs/history/implementation-log.md`. Record
  `Commit: <short-sha>` **only after** the build/tests pass; otherwise use `Commit: pending` or
  `Commit: blocked — tests failed`.
- If the app version changed, add a matching versioned section to `CHANGELOG.md` (see
  `manage-docs.md` §6), including the user-facing release note and the verified commit SHA.

---

## 9. Troubleshooting Quick Reference

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `No such file or directory: ./gradlew` | Ran from repo root | `cd A2D` |
| `SDK location not found` | No `local.properties` / `ANDROID_HOME` | Create `A2D/local.properties` or export SDK vars |
| `TaskScreen unresolved` | Stale code/doc reference | Use `TaskListScreen` / `TaskDetailScreen`; `TaskScreen.kt` is gone |
| App does not open after install | Wrong component | Use `com.a2d.app/.ui.MainActivity` |
| Checkbox change not reflected | Bypassed ViewModel/Room | Update via `TaskViewModel.toggleActivity(...)` and `saveTask` |
| Task stays "In progress" with all checked | No activities, or `isCompleted` not used | Check `Task.isCompleted` rule |
| Gate output never reaches 1 | Wrong pin arity or missing reset | Pin = list size; reset with `"LOW"` before evaluating |
| Release build fails on keystore | `A2D/a2d-release-key.jks` missing | Generate it locally with the command in README §8 |

For anything not covered here, search the exact symbol first:

```bash
grep -R "symbolName" A2D/app/src/main/java A2D/app/src/test
```

Then read only the matching file and its direct collaborators.
