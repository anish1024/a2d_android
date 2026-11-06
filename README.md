# A2D (Adult ADHD Assistant) — Native Android

> **Canonical current-state documentation.** This file is the source of truth for architecture,
> current behavior, toolchain, and deployment. It does not accumulate milestone logs, completed
> specifications, or decision rationale; those live in the linked documents below.
>
> - **Last verified:** 2026-11-06 · code baseline `8d3b7f4`
> - New to the project or making a small targeted change? Start with [`AGENT.md`](AGENT.md).
> - Implementation history: [`docs/history/implementation-log.md`](docs/history/implementation-log.md)
> - Completed feature records: [`docs/features/`](docs/features/)
> - Architecture decisions: [`docs/adr/`](docs/adr/)
> - Release notes: [`CHANGELOG.md`](CHANGELOG.md)
> - Documentation index: [`docs/INDEX.md`](docs/INDEX.md)
> - Device setup and installation: [`Deployment/a2d_build.md`](Deployment/a2d_build.md) and
>   [`Deployment/a2d_deploy.sh`](Deployment/a2d_deploy.sh).
> - Historical/superseded documents: [`docs/archive/`](docs/archive/).

## Contents

**Current state**

1. [Executive Summary](#1-executive-summary)
2. [Core Architectural Concept: Hierarchical Logic Gating](#2-core-architectural-concept-hierarchical-logic-gating)
3. [Technology Stack](#3-technology-stack)
4. [Component Mapping & Domain Implementation](#4-component-mapping--domain-implementation)
5. [Verification & Unit Testing](#5-verification--unit-testing)
6. [Implementation Status](#6-implementation-status)
7. [Repository Map](#7-repository-map)
8. [Deployment](#8-deployment)
9. [Documentation & History](#9-documentation--history)

---

## 1. Executive Summary

The **A2D (Adult ADHD Assistant)** application defined in `a2d_graph.yaml` specifies a task/action-item management workflow alongside a serial AND-gate pulse-processing engine (`SerialAndGateProcessor`). In the original Python/FastAPI specification, these two features resided side-by-side within the same service without direct integration.

This revised proposal integrates `SerialAndGateProcessor` directly into the task progression pipeline. The Android implementation operates as an **offline-first, reactive mobile application** using Kotlin, Android Jetpack (Compose, Room, Coroutines/Flow), and Dagger Hilt. Sub-actions (`ActionItem`) and parent tasks (`Task`) emit binary `"HIGH"` or `"LOW"` pulses to an in-memory serial logic gate, turning task completion and daily milestone tracking into a **hierarchical hardware-logic circuit** optimized for ADHD behavioral scaffolding.

---

## 2. Core Architectural Concept: Hierarchical Logic Gating

Instead of treating `SerialAndGateProcessor` as an isolated utility, the domain logic binds tasks to a **Two-Tier Logic Gate Pipeline**:

```
[ActionItem 1] ──(HIGH/LOW)──┐
[ActionItem 2] ──(HIGH/LOW)──┼──► [Tier 1: Task Gate (Arity = N)] ──► Task.achieved (0 or 1)
[ActionItem 3] ──(HIGH/LOW)──┘                                               │
                                                                             ▼
[Task 1 (achieved)] ──(HIGH/LOW)──┐                                    (HIGH/LOW)
[Task 2 (achieved)] ──(HIGH/LOW)──┼──► [Tier 2: Pipeline Gate (Arity = M)] ──► Session Output (0 or 1)
[Task 3 (achieved)] ──(HIGH/LOW)──┘

```

* **Tier 1 (Sub-Task Accumulator):**
* The gate pin arity $N$ is set to the total count of sub-actions (`task.activities.size`).


* Each `ActionItem.achieved` status emits `"HIGH"` (if true) or `"LOW"` (if false).


* A `"LOW"` pulse flushes the latched register back to 0 (`reset = true`), reinforcing sequential sub-task execution.


* `Task.achieved` unlocks (`output = 1`) only when all $N$ consecutive items emit `"HIGH"` pulses.




* **Tier 2 (Global Milestone/Session Pipeline):**
* The global gate pin arity $M$ is set to the total number of tasks in the active context/session (`taskList.size`).


* Each `Task.achieved` emits a `"HIGH"` or `"LOW"` pulse to evaluate whether the overall milestone is achieved.





---

## 3. Technology Stack

| Layer | Technology | Domain Alignment |
| --- | --- | --- |
| **Language** | Kotlin 2.x | Immutability, data classes mirroring `models` (`Task`, `ActionItem`, `PulseResult`), and native coroutines. |
| **UI Framework** | Jetpack Compose + Material 3 | Declarative, distraction-free UI providing reactive visual feedback when gate latches flip. |
| **Logic Engine** | Pure Kotlin Engine (`SerialAndGateProcessor`) | Thread-safe, in-memory latch accumulator injected as a Hilt `@Singleton`.|
| **Persistence** | Room (SQLite) | Relational database enforcing `CASCADE` deletion from `Task` to `ActionItem`, replacing flat-file JSON serialization (`task.json`).|
| **Data Streams** | Kotlin Coroutines & `Flow` / `StateFlow` | Asynchronous propagation from Room transactions through the logic gate to UI rendering. |
| **DI** | Dagger Hilt | Manages lifecycle scoping across repositories, engines, and ViewModels. |

The rationale and alternatives for these choices are recorded in
[ADR 0001 — Technology Stack Selection](docs/adr/0001-technology-stack.md).

---

## 4. Component Mapping & Domain Implementation

### 4.1 Domain Layer: Models, Contract & Gate Engine

#### Core Contract & Models (`models/` subtree)



```kotlin
package com.a2d.app.domain.model

interface ActivityAchieved {
    val achieved: Boolean
}

data class ActionItem(
    val id: Long,
    val description: String,
    override val achieved: Boolean = false
) : ActivityAchieved

enum class UserGroup(val value: String) {
    USER("user"),
    ADMIN("admin")
}

data class PulseResult(
    val inputPin: String,
    val pulse: String,
    val latchedInputs: List<Int>,
    val output: Int,
    val reset: Boolean
)

```

#### Contract Declaration (`ActivityProcessor`)



```kotlin
package com.a2d.app.domain.contract

import com.a2d.app.domain.model.ActivityAchieved

interface ActivityProcessor {
    val activities: List<ActivityAchieved>
    fun processActivity(callback: ((List<ActivityAchieved>) -> Boolean)? = null): Boolean
}

```

#### Serial AND Gate Engine (`lib/` subtree)



```kotlin
package com.a2d.app.domain.engine

import com.a2d.app.domain.model.PulseResult
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SerialAndGateProcessor @Inject constructor() {
    private data class PinState(
        val inputs: MutableList<Int> = mutableListOf(),
        var output: Int = 0
    )

    private val pinStates = ConcurrentHashMap<String, PinState>()

    @Synchronized
    fun processPulse(inputPin: String, pulse: String): PulseResult {
        val arity = inputPin.toIntOrNull()
            ?: throw IllegalArgumentException("input_pin must parse as an integer: $inputPin")

        val normalized = pulse.trim().uppercase()
        if (normalized != "HIGH" && normalized != "LOW") {
            throw IllegalArgumentException("Pulse must be HIGH or LOW after strip+upper, got: $pulse")
        }

        val state = pinStates.getOrPut(inputPin) { PinState() }

        return if (normalized == "LOW") {
            state.inputs.clear()
            state.output = 0
            PulseResult(
                inputPin = inputPin,
                pulse = "LOW",
                latchedInputs = emptyList(),
                output = 0,
                reset = true
            )
        } else {
            state.inputs.add(1)
            state.output = if (state.inputs.size >= arity) 1 else 0
            PulseResult(
                inputPin = inputPin,
                pulse = "HIGH",
                latchedInputs = ArrayList(state.inputs),
                output = state.output,
                reset = false
            )
        }
    }
}

```

#### Task Entity with Gate Integration (`task` node)



```kotlin
package com.a2d.app.domain.model

import com.a2d.app.domain.contract.ActivityProcessor
import com.a2d.app.domain.engine.SerialAndGateProcessor

data class Task(
    val id: Long,
    val taskName: String,
    val description: String,
    val priority: Int,
    override var achieved: Boolean = false,
    val context: String = "No Context",
    val creationTime: String? = null,
    override var activities: List<ActionItem> = emptyList()
) : ActivityAchieved, ActivityProcessor {

    override fun processActivity(callback: ((List<ActivityAchieved>) -> Boolean)?): Boolean {
        achieved = callback?.invoke(activities) ?: true
        return achieved
    }

    /**
     * Domain Integration: Evaluates ActionItem completion via SerialAndGateProcessor.
     * Sets pin arity to activities.size and streams HIGH/LOW pulses.
     */
    fun processActivityWithGate(processor: SerialAndGateProcessor): Boolean {
        if (activities.isEmpty()) {
            achieved = true
            return true
        }

        val arityPin = activities.size.toString()
        processor.processPulse(arityPin, "LOW") // Flush register

        var gateOutput = 0
        for (item in activities) {
            val pulse = if (item.achieved) "HIGH" else "LOW"
            val result = processor.processPulse(arityPin, pulse)
            gateOutput = result.output
            if (result.reset) break
        }

        achieved = (gateOutput == 1)
        return achieved
    }
}

```

#### Multi-Task Pipeline Processor (Tier 2 Integration)

```kotlin
package com.a2d.app.domain.engine

import com.a2d.app.domain.model.PulseResult
import com.a2d.app.domain.model.Task
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskListPipelineProcessor @Inject constructor(
    private val gateProcessor: SerialAndGateProcessor
) {
    fun evaluateTaskList(tasks: List<Task>): PulseResult {
        if (tasks.isEmpty()) {
            return PulseResult(inputPin = "0", pulse = "LOW", latchedInputs = emptyList(), output = 1, reset = false)
        }

        val arityPin = tasks.size.toString()
        gateProcessor.processPulse(arityPin, "LOW") // Reset register

        var lastResult = PulseResult(arityPin, "LOW", emptyList(), 0, reset = true)
        for (task in tasks) {
            val pulse = if (task.achieved) "HIGH" else "LOW"
            lastResult = gateProcessor.processPulse(arityPin, pulse)
            if (lastResult.reset) break
        }
        return lastResult
    }
}

```

---

### 4.2 Data & Persistence Layer (Room)

Replaces `server/task.json` with an ACID-compliant local database:

```kotlin
// TaskEntity.kt
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: Long,
    val taskName: String,
    val description: String,
    val priority: Int,
    val context: String,
    val achieved: Boolean,
    val creationTime: String
)

// ActionItemEntity.kt
@Entity(
    tableName = "action_items",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class ActionItemEntity(
    @PrimaryKey val id: Long,
    val taskId: Long,
    val description: String,
    val achieved: Boolean
)

```

`TaskDao` exposes reactive `Flow<List<TaskWithActionItems>>`, ensuring any database mutation instantly dispatches through the UI layer.

---

### 4.3 Presentation Layer & UI Architecture

The presentation layer connects the two-tier logic gate to Jetpack Compose through the shared
`TaskViewModel`. There are two task screens (`TaskListScreen` and `TaskDetailScreen`) plus the
standalone `LogicGateScreen`:

```
TaskListScreen / TaskDetailScreen (checkbox, add, delete)
                 │
                 ▼
        TaskViewModel.toggleActivity(taskId, activityId, achieved)
                 │
        ┌────────┴───────────────────────────┐
        ▼                                    ▼
Tier 1: Task.processActivityWithGate()   TaskRepository.saveTask()
        │                                    │
        ▼                                    ▼
Tier 2: PipelineProcessor.evaluateTaskList() Room Database Update
        │                                    │
        ▼                                    ▼
StateFlow<milestoneOutput>            Flow<List<Task>> / StateFlow<Task?>
        │                                    │
        └─────────────────┬──────────────────┘
                          ▼
            Jetpack Compose Re-render

```

* **Completion rule in the UI:** `Task.isCompleted` is
  `activities.isNotEmpty() && activities.all { it.achieved }`. The detail screen shows a
  "Completed" / "In progress" badge; the list screen uses the shared `ActivityRow` checkbox and
  strikethrough styling.
* **Milestone output:** The list header shows `Milestone Complete [GATE 1]` /
  `Milestone Incomplete [GATE 0]` from the Tier-2 pipeline (`milestoneOutput`).
* **No per-card "latched inputs" widget:** the UI does not currently render a `[ 1 / 3 Latched ]`
  indicator. The gate state is observable through the milestone status and through the standalone
  Logic Gate tab, which displays the full `PulseResult` (pin, pulse, latched inputs, output, reset).
* **Navigation:** `MainActivity` hosts a `NavHost` with `taskList`, `taskDetail/{taskId}` (Long arg),
  and `logicGate` routes. The Tasks bottom tab stays selected while on the detail route.



---

## 5. Verification & Unit Testing

```kotlin
package com.a2d.app

import com.a2d.app.domain.engine.SerialAndGateProcessor
import com.a2d.app.domain.engine.TaskListPipelineProcessor
import com.a2d.app.domain.model.ActionItem
import com.a2d.app.domain.model.Task
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class IntegratedGateTaskFlowTest {

    private lateinit var gateProcessor: SerialAndGateProcessor
    private lateinit var pipelineProcessor: TaskListPipelineProcessor

    @Before
    fun setup() {
        gateProcessor = SerialAndGateProcessor()
        pipelineProcessor = TaskListPipelineProcessor(gateProcessor)
    }

    @Test
    fun testTaskGateRequiresAllSubActionsHigh() {
        val task = Task(
            id = 1L,
            taskName = "Morning Focus",
            description = "Start day",
            priority = 1,
            activities = listOf(
                ActionItem(10L, "Drink Water", achieved = true),
                ActionItem(11L, "Medication", achieved = false)
            )
        )

        // 1 of 2 is HIGH -> output = 0 -> achieved = false
        task.processActivityWithGate(gateProcessor)
        assertFalse(task.achieved)

        // Mark second item true -> 2 of 2 HIGH -> output = 1 -> achieved = true
        task.activities = task.activities.map { it.copy(achieved = true) }
        task.processActivityWithGate(gateProcessor)
        assertTrue(task.achieved)
    }

    @Test
    fun testGlobalPipelineResetsOnSingleIncompleteTask() {
        val task1 = Task(id = 1L, taskName = "T1", description = "", priority = 1, achieved = true)
        val task2 = Task(id = 2L, taskName = "T2", description = "", priority = 2, achieved = false)

        val pipelineResult = pipelineProcessor.evaluateTaskList(listOf(task1, task2))
        assertEquals(0, pipelineResult.output)
        assertTrue(pipelineResult.reset)

        val completedResult = pipelineProcessor.evaluateTaskList(
            listOf(task1, task2.copy(achieved = true))
        )
        assertEquals(1, completedResult.output)
        assertFalse(completedResult.reset)
    }
}

```

---

## 6. Implementation Status

All implementation sprints are complete. The only remaining work is manual device onboarding and
Play Console submission.

| Area | Status | Evidence |
| --- | --- | --- |
| Sprint 0 — environment & scaffolding | ✅ Complete | JDK 17, Gradle wrapper 8.9, Android SDK 34, config files written |
| Sprint 1 — domain, engines, tests | ✅ Complete | `SerialAndGateProcessor`, `TaskListPipelineProcessor`, `Task.processActivityWithGate`, 11/11 unit tests |
| Sprint 2 — Room, repository, DI | ✅ Complete | `TaskEntity` / `ActionItemEntity` CASCADE, `TaskDao`, `TaskRepositoryImpl`, Hilt `AppModule` |
| Sprint 3 — Compose UI | ✅ Complete | `TaskListScreen`, `TaskDetailScreen`, `LogicGateScreen`, `NavHost`, `TaskViewModel` |
| Sprint 4.1 — physical device deploy | ⏳ Manual | use `Deployment/a2d_build.md` + `Deployment/a2d_deploy.sh` |
| Sprint 4.2 — signed release bundle | ✅ Complete (artifact not committed) | `keystore.properties` + release signing/R8 config in `A2D/app/build.gradle.kts`; generate `A2D/a2d-release-key.jks` locally to rebuild |
| Sprint 4.3 — Play Store submission | ⏳ Manual | requires Play Console account, store assets, declarations |

**Current verification baseline:** `./gradlew test` → 11 tests (5 domain + 4 gate + 2 integrated),
`./gradlew assembleDebug` → `A2D/app/build/outputs/apk/debug/app-debug.apk`.

**Toolchain actually in use:** AGP **8.5.2**, Kotlin **2.0.20**, KSP **2.0.20-1.0.25**, Compose BOM
**2024.09.02**, Hilt **2.52**, Room **2.6.1**, Gradle wrapper **8.9**, Java **17**. The older
AGP 8.2.2 / Kotlin 1.9.22 snippets in the archive are historical and must not be copied into the
current build.

---

## 7. Repository Map

```
a2d_android/
├── README.md                         # Canonical current-state documentation (this file)
├── AGENT.md                          # Short, task-oriented guide for small local LLM agents
├── CHANGELOG.md                      # Versioned release notes (Keep a Changelog + SemVer)
├── manage-docs.md                    # Repository-agnostic documentation rules
├── .github/
│   └── pull_request_template.md      # Docs/verification/commit-ID checklist
├── Deployment/
│   ├── a2d_build.md                  # Phone/USB debugging checklist
│   └── a2d_deploy.sh                 # adb discovery → install debug APK → launch app
├── docs/
│   ├── INDEX.md                      # Index of every maintained document
│   ├── known-issues.md               # Active bugs, limitations, and workarounds
│   ├── adr/
│   │   ├── _TEMPLATE.md              # Architecture Decision Record template
│   │   └── 0001-technology-stack.md  # Accepted technology-stack decision
│   ├── features/
│   │   ├── _TEMPLATE.md              # Feature/enhancement specification template
│   │   └── FEAT-0001-task-card-redesign.md
│   ├── history/implementation-log.md # Historical + current task log, verified commit IDs
│   ├── a2d_graph.yaml                # Original reference blueprint
│   ├── setup-adb.sh                  # adb bootstrap helper
│   └── archive/                      # Superseded/historical Markdown only
└── A2D/                              # Gradle project root (run ./gradlew here)
    ├── build.gradle.kts
    ├── settings.gradle.kts           # rootProject.name = "A2DApp"
    ├── gradle.properties             # 2 GB JVM cap
    ├── gradle/libs.versions.toml     # single source of truth for versions
    ├── keystore.properties           # release signing config (tracked)
    └── app/src/
        ├── main/java/com/a2d/app/
        │   ├── A2DApplication.kt
        │   ├── domain/               # models, contracts, gate engines
        │   ├── data/                 # Room entities/DAO/relation + repository
        │   ├── di/AppModule.kt       # database + DAO providers
        │   └── ui/                   # MainActivity, task screens, logic gate, theme
        └── test/java/com/a2d/app/    # JVM unit tests
```

**Important:** the Gradle project is under `A2D/`, not the repository root. Run
`cd A2D && ./gradlew ...`. `A2D/local.properties` is git-ignored; create it with
`sdk.dir=/path/to/android-sdk` or export `ANDROID_HOME`/`ANDROID_SDK_ROOT`.

---

## 8. Deployment

Device preparation is documented in [`Deployment/a2d_build.md`](Deployment/a2d_build.md).

```bash
# 1. Build the debug APK
cd A2D
./gradlew assembleDebug

# 2. From the repository root, install and launch
cd ..
./Deployment/a2d_deploy.sh
```

`Deployment/a2d_deploy.sh`:

- locates `adb` via `PATH`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or common SDK locations;
- resolves the APK relative to the script (`A2D/app/build/outputs/apk/debug/app-debug.apk`), so it
  works from any checkout location or current directory;
- refuses to continue when no authorized device is connected, with a fix checklist;
- installs with `adb install -r`, then launches `com.a2d.app/.ui.MainActivity`.

Release bundle (requires the git-ignored `A2D/a2d-release-key.jks` to exist locally):

```bash
cd A2D
./gradlew bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

---

## 9. Documentation & History

Current-state documentation lives in this README. Everything else has a single home:

| Topic | Document |
| --- | --- |
| Milestone/task history, verification, commit IDs | [`docs/history/implementation-log.md`](docs/history/implementation-log.md) |
| Completed feature specifications | [`docs/features/FEAT-0001-task-card-redesign.md`](docs/features/FEAT-0001-task-card-redesign.md) |
| Architecture decisions and technology rationale | [`docs/adr/0001-technology-stack.md`](docs/adr/0001-technology-stack.md) |
| Versioned release notes | [`CHANGELOG.md`](CHANGELOG.md) |
| Bugs, limitations, workarounds | [`docs/known-issues.md`](docs/known-issues.md) |
| Documentation rules and conventions | [`manage-docs.md`](manage-docs.md) |
| Full document index | [`docs/INDEX.md`](docs/INDEX.md) |
| Historical/superseded docs | [`docs/archive/`](docs/archive/) |

**Rule:** README describes what is true now. It does not accumulate milestone logs, completed
specifications, or decision rationale; those belong in the linked documents.
