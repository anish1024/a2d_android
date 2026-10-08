# Porting A2D (Adult ADHD Assistant) to Native Android with Hierarchical Serial AND-Gate Logic Integration

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

The presentation layer connects the two-tier logic gate to Jetpack Compose:

```
TaskScreen User Interaction (Check Item)
                 │
                 ▼
        TaskViewModel.toggleActionItem()
                 │
        ┌────────┴───────────────────────────┐
        ▼                                    ▼
Tier 1: Task.processActivityWithGate()   TaskRepository.saveTask()
        │                                    │
        ▼                                    ▼
Tier 2: PipelineProcessor.evaluateTaskList() Room Database Update
        │                                    │
        ▼                                    ▼
StateFlow<milestoneOutput>            Flow<List<Task>>
        │                                    │
        └─────────────────┬──────────────────┘
                          ▼
            Jetpack Compose Re-render

```

* **Visual Gate Latching:** Each task card displays an interactive pulse latch indicator (e.g., `[ 1 / 3 Latched ]`).


* **Milestone Output Badge:** The top app bar indicates whether the session threshold has been cleared (`Milestone Gate: ACTIVE / 1` vs `LATCHING / 0`).



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

## 6. Implementation Roadmap

```
Sprint 1: Engine & Domain Layer
 ├── Implement models (Task, ActionItem, PulseResult)
 ├── Implement SerialAndGateProcessor & TaskListPipelineProcessor
 └── Execute JUnit 4 test suite for gate accumulator mechanics

Sprint 2: Storage & Persistence (Room)
 ├── Configure Room schemas (TaskEntity, ActionItemEntity with CASCADE)
 ├── Build TaskDao & TaskRepositoryImpl with Flow observers
 └── Wire Hilt modules for database and singleton processor injection

Sprint 3: Integrated Presentation Layer
 ├── Implement TaskViewModel orchestrating Tier-1 and Tier-2 gate evaluation
 ├── Compose TaskScreen with real-time latch indicators and pulse counters
 └── Compose standalone Logic Gate playground tab for manual signal testing

Sprint 4: Hardware Deployment & Testing
 ├── Deploy to physical device via ADB on macOS Monterey (12.7.6)
 └── Profile memory and coroutine dispatchers to ensure zero main-thread hitching

```
---

## 7. redesign01 — Task Card Redesign (Completed)

Summary of tasks completed under the `redesign01.md` specification (Task Card Redesign v1.0).
Full per-task progress reports are in `a2d_implementation_log.md` → *Redesign 01* section.

| # | Task | Status | What was delivered |
| --- | --- | --- | --- |
| T1 | Domain completion rule & ID ordering | ✅ | `Task.isCompleted` = `activities.isNotEmpty() && activities.all { it.achieved }`; `TaskRepositoryImpl` maps activities with `.sortedBy { it.id }` (all 3 query methods) so items always render in ID order; 3 new unit tests in `TaskDomainTest` |
| T2 | ViewModel operations | ✅ | `TaskViewModel` now exposes `tasks`, `currentTask: StateFlow<Task?>` (derived via `combine(tasks, selectedTaskId)`), `selectTask(taskId)`, and the spec operations `toggleActivity(taskId, activityId, achieved)`, `addActivity(taskId)`, `deleteActivity(taskId, activityId)`, `deleteTask(taskId)` — all Room-backed with Tier-1 gate re-evaluation and Tier-2 pipeline recalculation |
| T3 | Jetpack Navigation | ✅ | Added `androidx.navigation:navigation-compose:2.8.2` (+ `material-icons-core`); `MainActivity` now hosts a `NavHost` with routes `taskList`, `taskDetail/{taskId}` (Long arg) and `logicGate`; bottom tabs retained, Tasks tab stays active on the detail route |
| T4 | TaskListScreen | ✅ | Compact `ElevatedCard` per task: × task-delete in header, optional description, checkbox activity list (strikethrough + 60 % fade when done), per-activity 🗑 delete, `+ Add activity` button; activity list capped at 200 dp for internal scrolling; card body taps open the detail screen; both deletes use confirmation `AlertDialog`s. Replaces the old `TaskScreen.kt` (removed) |
| T5 | TaskDetailScreen | ✅ | Full-screen view: back arrow (`navigateUp()`), task header with green "Completed" / neutral "In progress" badge pill, full activity list with shared `ActivityRow`, task-delete button in the top bar (confirm → delete + navigate back), `+ Add activity` below the list, loading indicator while the task flow resolves |
| T6 | Verification | ✅ | `./gradlew test` → **11/11 tests pass**; `./gradlew assembleDebug` → BUILD SUCCESSFUL (`app-debug.apk`, 10.3 MB). No Room schema changes required |
| T7 | Documentation | ✅ | This README section + per-task entries in `a2d_implementation_log.md` |

**Behavior notes**

- Completion rule used everywhere in the UI: a task is completed only when it has **at least one** activity and **all** activities are checked (the empty task is *not* completed — distinct from the Tier-1 gate's `achieved` flag, which is kept for the milestone pipeline).
- All delete operations (task-level and per-activity, in both list and detail views) require confirmation via `AlertDialog`.
- Activities are never reordered — they always display in original ID order; new activities append at the end (globally unique monotonic IDs).
- No inline expand/collapse of activity items; tapping a card navigates to the detail screen instead.

---

## 8. redesign02 — Editable & Collapsible Task Cards (Completed)

Summary of tasks completed under the `redesign02.md` proposal (Task UI Redesign – Proposal 02).
Per-task progress reports are in `a2d_implementation_log.md` → *Redesign 02* section. Builds on redesign01 (§7).

| # | Task | Status | What was delivered |
| --- | --- | --- | --- |
| R2-T1 | Domain progress rule | ✅ | `Task.progressPercent` computed exactly per spec: `0` for empty tasks, else `(achievedCount * 100 / size)`; 4 new unit tests (0 / 25 / 66 / 100 %) — **15/15 tests pass** |
| R2-T2 | `updateTask` operation | ✅ | `TaskViewModel.updateTask(taskId, newName, newDescription)` — trims both inputs, blank name falls back to the current name, no-op guard skips unchanged writes, persists via `task.copy(...)` + repository; `tasks`/`currentTask` flows re-emit from Room. All redesign01 operations retained |
| R2-T3 | TaskListScreen rework | ✅ | Header: `[Edit] task name [x%] [×]` — edit button (`Edit`/`Done` toggle), progress % (`primary` at 100 % else `onSurfaceVariant`), delete ×. Collapsible body (per-task `mutableStateMapOf` keyed state, default expanded): header tap toggles, body tap opens Task Detail, edit mode shows name + description `OutlinedTextField`s with persistence on edit-button re-tap **or** focus loss (400 ms guard prevents exit/re-enter races; auto-expands + focuses name field on entry). `TaskCardHeader`/`TaskCardBody` decomposition per spec §8. `MainActivity` wired with `onSaveEdit` |
| R2-T4 | Verification | ✅ | `./gradlew test` → **15/15 pass**; `./gradlew assembleDebug` → BUILD SUCCESSFUL (`app-debug.apk`, 10.4 MB). No Room schema changes |
| R2-T5 | Documentation | ✅ | This README section + per-task entries in `a2d_implementation_log.md` |

**Behavior notes**

- **Progress %** appears in every task-card header, derived solely from activity checkboxes; `100%` renders green (`primary`).
- **Collapse/expand:** tapping the header toggles only the body; the header (name, edit, %, delete) is always visible. Cards start expanded; state is kept per task id for the lifetime of the list screen.
- **Editing:** only in the list view (spec §6 keeps the detail screen unchanged). Exiting edit mode — via the edit button, tapping outside the fields, or collapsing the header — always saves first through `updateTask`, so no keystrokes are lost.
- **Tapping the body** opens the Task Detail screen (checkboxes, delete icons, buttons and edit fields consume their own taps); body navigation is disabled while editing to avoid accidental exits.
- All redesign01 behaviors remain: confirmation dialogs for every delete, ID-ordered activities, strikethrough/fade done styling, detail-screen back button and completion badge.
