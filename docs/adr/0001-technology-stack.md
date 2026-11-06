# ADR 0001 — Technology Stack Selection

- Status: Accepted
- Date: 2026-11-06
- Owner: project maintainer
- Supersedes: none
- Superseded by: none

## Context

This decision records the rationale for porting the A2D (Adult ADHD Assistant) system from the Python
FastAPI reference blueprint (`a2d_graph.yaml`) into an offline-first, native Android application. The
current stack is summarized in [README §3](../../README.md#3-technology-stack).

> **Implementation status note:** this record preserves the original technology rationale. The
> toolchain versions have been updated to the versions actually in use. The security/authentication
> section (BiometricPrompt + KeyStore) describes an intended direction, not current functionality:
> the codebase currently contains only the `User` / `UserGroup` domain models and has no login or
> biometric UI.

## Decision

### 1. Executive Matrix

| Layer / Concern | Chosen Technology | Primary Alternatives Considered | Key Justification for A2D |
| --- | --- | --- | --- |
| **Primary Language** | Kotlin 2.x | Java, Dart (Flutter) | Native null-safety, coroutine primitives, and idiomatic data classes mapping to the blueprint models.|
| **UI Framework** | Jetpack Compose + Material 3 | XML Views with DataBinding | Low-cognitive-load declarative UI, instant state-to-pixel re-composition for ADHD visual feedback. |
| **Persistence Engine** | Room (SQLite) | DataStore, Flat JSON Files | Relational foreign keys (`CASCADE` deletes between `Task` and `ActionItem`), replacing flat `task.json` with ACID transactions.|
| **Logic & Concurrency** | Kotlin Coroutines & `Flow` / `StateFlow` | RxJava 3, LiveData | First-class structured concurrency, reactive database streaming, non-blocking hardware-logic signal propagation.|
| **Dependency Injection** | Dagger Hilt | Koin, Manual Injection | Compile-time dependency verification, native lifecycle scoping, thread-safe `@Singleton` state preservation.|
| **Security & Auth** | BiometricPrompt + Android KeyStore | Plaintext JSON (`users.json`) | Eliminates password cognitive overhead for ADHD users while replacing unencrypted flat-file credential storage.|
| **Target Build & Runtime** | Min SDK 26 / Target SDK 34 / JDK 17 | Min SDK 21, Java 8 | Enables modern `java.time` APIs without desugaring overhead, optimized for AGP 8.x and low-memory Intel Macs. |

---

### 2. Detailed Component Breakdown

#### 2.1 Language: Kotlin 2.x

* **1:1 Mirroring of Blueprint Domain Shapes:**
* `a2d_graph.yaml` strictly specifies data models with optional defaults (e.g., `Task.creation_time`, `context` defaulting to `"No Context"`, `achieved` defaulting to `false`).


* Kotlin data classes provide native default parameter assignments and immutability, matching the contract without boilerplate builders.




* **Abstract Contract Fulfillment:**
* The `Task` entity extends `ActivityAchieved` while implementing `ActivityProcessor`.


* Kotlin’s interface property delegation and clean SAM (Single Abstract Method) closures allow `Task.processActivity` to accept flexible lambda callbacks seamlessly.




* **Elimination of NullPointerExceptions (NPE):**
* ADHD focus applications require zero friction and extreme reliability.
* Kotlin enforces strict null-safety at compile time, rejecting malformed states before runtime execution.



---

#### 2.2 UI Toolkit: Jetpack Compose with Material 3

* **ADHD Behavioral Scaffolding & Low Cognitive Load:**
* Adults with ADHD experience executive dysfunction and easily suffer from visual overwhelm.
* Jetpack Compose allows conditional rendering of only the most critical visual cues (e.g., hiding completed sub-tasks, expanding active checklists, or zooming in on a single action item).




* **Declarative Unidirectional Data Flow (UDF):**
* Traditional Android XML Views rely on imperative state manipulation (`findViewById`, `notifyDataSetChanged`), leading to UI state desynchronization bugs.
* In Compose, the UI is an immutable projection of state: `UI = f(StateFlow<List<Task>>)`.
* When a user toggles an `ActionItem` checkbox, state transitions re-compose only the modified nodes with immediate visual and haptic feedback.




* **Reactive Logic Gate Visualization:**
* Displaying active pin states, latched buffers, and threshold indicators for `SerialAndGateProcessor` is accomplished using dynamic Compose cards and state-driven progress bars, avoiding complex custom XML drawables.





---

#### 2.3 Persistence Engine: Room (SQLite Abstraction)

* **Replacing Vulnerable Flat Files:**
* In the reference implementation, `task_service` serialized the entire dictionary to `server/task.json` on every create, update, or delete operation.


* On mobile operating systems, unexpected app termination or device battery death during flat-file writes causes persistent data corruption.
* Room provides ACID-compliant transaction safety on SQLite.


* **Enforcing Relational Integrity with `CASCADE`:**
* `a2d_graph.yaml` specifies a 1-to-many relationship: a `Task` owns an ordered list of `ActionItem` objects.


* Room models this via `@ForeignKey(onDelete = CASCADE)`. Deleting a task automatically cleans up orphaned action items, preventing database bloat.


* **Auto-Updating Reactive Streams:**
* Room DAOs return `Flow<List<TaskWithActionItems>>`.
* Any background insert or mutation triggers an automatic emission to the active ViewModel, eliminating manual refresh calls or polling loops.



---

#### 2.4 Asynchronous Engine: Kotlin Coroutines & Flow

* **Structured Concurrency:**
* Managing asynchronous database I/O and sequential pulse processing across the logic gate is scoped to `viewModelScope`.


* If a user navigates away or closes a screen, background coroutines cancel automatically, preventing memory leaks and orphaned execution threads.


* **Non-Blocking Serial Signal Propagation:**
* The two-tier logic gate evaluates pulses sequentially (`HIGH`/`LOW`) across task and action item lists.


* Coroutines allow these loops to execute off the Main thread without blocking UI touch events or frame rendering.



---

#### 2.5 Dependency Injection: Dagger Hilt

* **Preserving Stateful In-Memory Singletons:**
* In `a2d_graph.yaml`, the `mapper` module retains an in-memory instance of `SerialAndGateProcessor` so that latched input states persist across multiple operations.


* Hilt injects `SerialAndGateProcessor` as a `@Singleton`, guaranteeing that latched gate state survives across screen navigation and ViewModel destructions without resorting to global mutable singletons.




* **Modular Clean Architecture:**
* Decouples the presentation layer from the data layer (`TaskRepositoryImpl`) and domain layer (`SerialAndGateProcessor`).


* Allows swapping Room data sources with mock implementations during unit and instrumented testing without altering domain contracts.





---

#### 2.6 Local Security: Android BiometricPrompt & KeyStore

* **Resolving Insecure Blueprint Storage:**
* The original blueprint notes that user credentials in `server/users.json` were *“stored and compared in plaintext”*.


* Storing unencrypted credentials directly violates Android security standards.


* **Reducing Friction for ADHD Users:**
* Requiring complex alphanumeric password input introduces cognitive friction, often causing users to abandon task tracking.
* Modern Android `BiometricPrompt` leverages hardware-backed authentication (Fingerprint / Face Unlock), providing hardware-level protection for sensitive personal tasks with instant, frictionless access.



---

#### 2.7 Hardware & Tooling Alignment (macOS 12.7.6 / 8 GB RAM)

* **Gradle Memory Conservation:**
* The toolchain configuration (Java 17, AGP 8.5.2, Kotlin 2.0.20, Compose Gradle plugin) allows building directly using Gradle CLI (`./gradlew test`, `./gradlew installDebug`).
* Avoids memory-heavy runtime compilation layers (like cross-platform Flutter/React-Native bridges), keeping memory consumption within the 8 GB ceiling of a 2017 MacBook Air.


* **Direct JVM Unit Testing:**
* Pure Kotlin domain modules (`Task`, `ActionItem`, `SerialAndGateProcessor`) execute directly on the local host JVM via JUnit 4, avoiding the thermal and memory penalties of running local Android Studio emulators.

## Consequences

- The app is offline-first: Room is the source of truth and emits `Flow` updates to the UI.
- Hilt provides compile-time dependency verification and keeps gate state in `@Singleton` scope.
- Compose keeps the UI reactive and lowers cognitive load for ADHD users.
- The biometric/KeyStore security direction remains unimplemented; see the status note above.
- The toolchain is optimized for the 8 GB development machine, not for cross-platform reuse.

## Alternatives considered

The executive matrix lists the primary alternatives per layer. Flutter/React Native were rejected
for memory overhead; XML views for imperative state-synchronization bugs; flat JSON for corruption
risk; Koin/manual DI for weaker compile-time guarantees; and plaintext credentials for security.

## References

- [README §3 — Technology Stack](../../README.md#3-technology-stack)
- [README §8 — Deployment](../../README.md#8-deployment)
- [Implementation log](../history/implementation-log.md)
