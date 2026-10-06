# implementation.md — End-to-End Implementation Blueprint for A2D Android

This specification details the step-by-step implementation for porting the Adult ADHD Assistant (`A2D`) application from the reference blueprint into a native, offline-first Android application. Every file, command, code block, and configuration is presented in full so that automated code-generation agents can implement, verify, and package the project deterministically without hallucinations or missing references.

---

## 1. Complete Project Directory Structure

Every file listed in this tree will be created and verified across the 4 implementation sprints:

```
A2DApp/
├── build.gradle.kts                            # Root build script
├── settings.gradle.kts                         # Project & plugin repository configuration
├── gradle.properties                           # Memory tuning (2GB cap for 8GB Mac)
├── keystore.properties                         # Keystore credentials reference (git-ignored)
├── a2d-release-key.jks                         # Production release keystore (generated in Sprint 4)
└── app/
    ├── build.gradle.kts                        # Module build script (Compose, Room, Hilt, Signing)
    ├── proguard-rules.pro                      # R8 code shrinking rules
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml             # App configuration, entry activity, permissions
        │   └── java/com/a2d/app/
        │       ├── A2DApplication.kt           # Dagger-Hilt application root
        │       ├── domain/                     # Pure Kotlin domain layer
        │       │   ├── contract/
        │       │   │   └── ActivityProcessor.kt    # Completion contract[cite: 1]
        │       │   ├── model/
        │       │   │   ├── ActivityAchieved.kt     # Base completion interface[cite: 1]
        │       │   │   ├── ActionItem.kt           # Checklist sub-task model[cite: 1]
        │       │   │   ├── Task.kt                 # Integrated Task entity with gate execution[cite: 1]
        │       │   │   ├── PulseResult.kt          # Serial gate pulse evaluation result[cite: 1]
        │       │   │   ├── User.kt                 # Authenticated user model[cite: 1]
        │       │   │   └── UserGroup.kt            # User roles/groups enum (USER, ADMIN)[cite: 1]
        │       │   └── engine/
        │       │       ├── SerialAndGateProcessor.kt      # In-memory serial AND-gate accumulator[cite: 1]
        │       │       └── TaskListPipelineProcessor.kt   # Tier-2 multi-task session evaluator
        │       ├── data/                       # Data persistence & repository layer
        │       │   ├── local/
        │       │   │   ├── A2DDatabase.kt          # Room SQLite Database declaration
        │       │   │   ├── dao/
        │       │   │   │   └── TaskDao.kt          # Relational queries with ACID transaction helpers
        │       │   │   ├── entity/
        │       │   │   │   ├── TaskEntity.kt       # Persistent task record
        │       │   │   │   └── ActionItemEntity.kt # Persistent sub-action record with CASCADE deletion
        │       │   │   └── relation/
        │       │   │       └── TaskWithActionItems.kt  # 1-to-many relationship mapping
        │       │   └── repository/
        │       │       └── TaskRepositoryImpl.kt   # Maps between Room entities and Domain models
        │       ├── di/                         # Dependency Injection layer
        │       │   └── AppModule.kt            # Hilt module providing Database, DAO, and Processors
        │       └── ui/                         # Presentation layer (Jetpack Compose + MVVM)
        │           ├── MainActivity.kt         # Entry Activity with navigation scaffold
        │           ├── theme/
        │           │   ├── Color.kt            # ADHD-friendly low-contrast palette
        │           │   └── Theme.kt            # Material 3 Theme setup
        │           ├── task/
        │           │   ├── TaskScreen.kt       # Task list, sub-item checklist & pulse latch displays
        │           │   └── TaskViewModel.kt    # Orchestrates Room repository and gate evaluations
        │           └── logicgate/
        │               ├── LogicGateScreen.kt      # Standalone interactive AND-gate simulator tab
        │               └── LogicGateViewModel.kt   # Manual pin testing ViewModel
        └── test/                               # Local JVM Unit Tests
            └── java/com/a2d/app/
                ├── SerialAndGateProcessorTest.kt   # Pure AND-gate pulse/reset verification
                ├── TaskDomainTest.kt               # Standard Task/ActivityProcessor contract tests
                └── IntegratedGateTaskFlowTest.kt   # Integrated Tier-1 & Tier-2 gate mechanics

```

---

## 2. Sprint 0: Environment & Hardware Scaffolding

### Target Host: MacBook Air 2017 (Intel Broadwell, 8 GB RAM, macOS Monterey 12.7.6)

#### Milestone 0.1: System Tools Setup

Run in Terminal:

```bash
# Install Xcode Command Line Tools
xcode-select --install

# Install Homebrew if not installed
which brew || /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install Java 17 LTS (Temurin)
brew install --cask temurin@17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc
source ~/.zshrc
java -version

# Generating the Gradle wrapper binary (gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.jar)
brew install gradle
gradle wrapper --gradle-version 8.4 --distribution-type bin

```

#### Milestone 0.2: Root Project Configuration Files

##### File: `gradle.properties`

```properties
# Crucial for 8 GB RAM: Limit Gradle JVM allocation to 2 GB
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m -XX:+UseParallelGC
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.vfs.watch=true
kotlin.incremental=true
android.useAndroidX=true

```

##### File: `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "A2DApp"
include(":app")

```

##### File: `build.gradle.kts` (Project Root)

```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
}

```

##### File: `app/build.gradle.kts`

```kotlin
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.a2d.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.a2d.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("KEYSTORE_PATH"))
                storePassword = keystoreProperties.getProperty("KEYSTORE_PASSWORD")
                keyAlias = keystoreProperties.getProperty("KEY_ALIAS")
                keyPassword = keystoreProperties.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
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
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose BoM
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Room SQLite Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Dagger Hilt
    val hiltVersion = "2.50"
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    ksp("com.google.dagger:hilt-compiler:$hiltVersion")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Local JVM Unit Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Debugging Tooling
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

```

##### File: `app/proguard-rules.pro`

```proguard
# Maintain models and entities for reflection and Room
-keep class com.a2d.app.domain.model.** { *; }
-keep class com.a2d.app.data.local.entity.** { *; }
-keep class com.a2d.app.data.local.relation.** { *; }

```

##### File: `keystore.properties` (Place in project root)

```properties
KEYSTORE_PATH=../a2d-release-key.jks
KEYSTORE_PASSWORD=a2dpassword
KEY_ALIAS=a2d-key-alias
KEY_PASSWORD=a2dpassword

```

---

## 3. Sprint 1: Domain Contracts, Models, Logic Engines & Unit Tests

### Milestone 1.1: Domain Models and Contracts

##### File: `app/src/main/java/com/a2d/app/domain/model/ActivityAchieved.kt`

```kotlin
package com.a2d.app.domain.model

interface ActivityAchieved {
    val achieved: Boolean
}

```

##### File: `app/src/main/java/com/a2d/app/domain/model/ActionItem.kt`

```kotlin
package com.a2d.app.domain.model

data class ActionItem(
    val id: Long,
    val description: String,
    override val achieved: Boolean = false
) : ActivityAchieved

```

##### File: `app/src/main/java/com/a2d/app/domain/model/UserGroup.kt`

```kotlin
package com.a2d.app.domain.model

enum class UserGroup(val value: String) {
    USER("user"),
    ADMIN("admin")
}

```

##### File: `app/src/main/java/com/a2d/app/domain/model/User.kt`

```kotlin
package com.a2d.app.domain.model

data class User(
    val name: String,
    val password: String,
    val group: UserGroup
)

```

##### File: `app/src/main/java/com/a2d/app/domain/model/PulseResult.kt`

```kotlin
package com.a2d.app.domain.model

data class PulseResult(
    val inputPin: String,
    val pulse: String,
    val latchedInputs: List<Int>,
    val output: Int,
    val reset: Boolean
)

```

##### File: `app/src/main/java/com/a2d/app/domain/contract/ActivityProcessor.kt`

```kotlin
package com.a2d.app.domain.contract

import com.a2d.app.domain.model.ActivityAchieved

interface ActivityProcessor {
    val activities: List<ActivityAchieved>
    fun processActivity(callback: ((List<ActivityAchieved>) -> Boolean)? = null): Boolean
}

```

---

### Milestone 1.2: Serial AND-Gate Engine & Integrated Task

##### File: `app/src/main/java/com/a2d/app/domain/engine/SerialAndGateProcessor.kt`

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
            throw IllegalArgumentException("pulse must be HIGH or LOW after strip+upper, got: $pulse")
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

##### File: `app/src/main/java/com/a2d/app/domain/model/Task.kt`

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
     * Tier 1 Gate Integration:
     * Sets pin arity to activities.size and evaluates ActionItem states sequentially.
     */
    fun processActivityWithGate(processor: SerialAndGateProcessor): Boolean {
        if (activities.isEmpty()) {
            achieved = true
            return true
        }

        val arityPin = activities.size.toString()
        processor.processPulse(arityPin, "LOW") // Reset before serial evaluation

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

##### File: `app/src/main/java/com/a2d/app/domain/engine/TaskListPipelineProcessor.kt`

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
    /**
     * Tier 2 Gate Integration:
     * Evaluates task list completion as an AND gate of arity tasks.size.
     */
    fun evaluateTaskList(tasks: List<Task>): PulseResult {
        if (tasks.isEmpty()) {
            return PulseResult("0", "LOW", emptyList(), 1, reset = false)
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

### Milestone 1.3: Unit Test Suite Implementation

##### File: `app/src/test/java/com/a2d/app/SerialAndGateProcessorTest.kt`

```kotlin
package com.a2d.app

import com.a2d.app.domain.engine.SerialAndGateProcessor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SerialAndGateProcessorTest {

    private lateinit var processor: SerialAndGateProcessor

    @Before
    fun setup() {
        processor = SerialAndGateProcessor()
    }

    @Test
    fun testHighPulsesLatchUntilArityMet() {
        val pulse1 = processor.processPulse("2", "HIGH")
        assertEquals(0, pulse1.output)
        assertEquals(listOf(1), pulse1.latchedInputs)
        assertFalse(pulse1.reset)

        val pulse2 = processor.processPulse("2", "HIGH")
        assertEquals(1, pulse2.output)
        assertEquals(listOf(1, 1), pulse2.latchedInputs)
        assertFalse(pulse2.reset)
    }

    @Test
    fun testLowPulseResetsPin() {
        processor.processPulse("3", "HIGH")
        val resetResult = processor.processPulse("3", "LOW")

        assertEquals(0, resetResult.output)
        assertTrue(resetResult.latchedInputs.isEmpty())
        assertTrue(resetResult.reset)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidPulseValueThrowsException() {
        processor.processPulse("2", "INVALID")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNonIntegerPinThrowsException() {
        processor.processPulse("ABC", "HIGH")
    }
}

```

##### File: `app/src/test/java/com/a2d/app/TaskDomainTest.kt`

```kotlin
package com.a2d.app

import com.a2d.app.domain.model.ActionItem
import com.a2d.app.domain.model.Task
import org.junit.Assert.*
import org.junit.Test

class TaskDomainTest {

    @Test
    fun testProcessActivityWithoutCallbackSetsAchievedTrue() {
        val task = Task(
            id = 1L,
            taskName = "Default Test",
            description = "Desc",
            priority = 1
        )
        assertFalse(task.achieved)
        val res = task.processActivity()
        assertTrue(res)
        assertTrue(task.achieved)
    }

    @Test
    fun testProcessActivityWithCallbackEvaluatesActivities() {
        val item1 = ActionItem(1L, "Step 1", achieved = true)
        val item2 = ActionItem(2L, "Step 2", achieved = false)
        val task = Task(
            id = 2L,
            taskName = "Callback Test",
            description = "Desc",
            priority = 1,
            activities = listOf(item1, item2)
        )

        val completed = task.processActivity { acts -> acts.all { it.achieved } }
        assertFalse(completed)
        assertFalse(task.achieved)
    }
}

```

##### File: `app/src/test/java/com/a2d/app/IntegratedGateTaskFlowTest.kt`

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
    fun testTierOneGateEvaluatesSubActions() {
        val task = Task(
            id = 100L,
            taskName = "Morning Checklist",
            description = "Start day",
            priority = 1,
            activities = listOf(
                ActionItem(1L, "Water", achieved = true),
                ActionItem(2L, "Meds", achieved = false)
            )
        )

        task.processActivityWithGate(gateProcessor)
        assertFalse(task.achieved)

        task.activities = task.activities.map { it.copy(achieved = true) }
        task.processActivityWithGate(gateProcessor)
        assertTrue(task.achieved)
    }

    @Test
    fun testTierTwoPipelineEvaluatesTaskList() {
        val task1 = Task(id = 1L, taskName = "T1", description = "", priority = 1, achieved = true)
        val task2 = Task(id = 2L, taskName = "T2", description = "", priority = 2, achieved = false)

        val result1 = pipelineProcessor.evaluateTaskList(listOf(task1, task2))
        assertEquals(0, result1.output)
        assertTrue(result1.reset)

        val result2 = pipelineProcessor.evaluateTaskList(listOf(task1, task2.copy(achieved = true)))
        assertEquals(1, result2.output)
        assertFalse(result2.reset)
    }
}

```

#### Verification for Sprint 1

Run in Terminal:

```bash
./gradlew test

```

All unit tests must pass on the local JVM without errors.

---

## 4. Sprint 2: Room Persistence, Data Mapping & Dependency Injection

### Milestone 2.1: Room Database Schema & DAOs

##### File: `app/src/main/java/com/a2d/app/data/local/entity/TaskEntity.kt`

```kotlin
package com.a2d.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

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

```

##### File: `app/src/main/java/com/a2d/app/data/local/entity/ActionItemEntity.kt`

```kotlin
package com.a2d.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    indices = [Index(value = ["taskId"])]
)
data class ActionItemEntity(
    @PrimaryKey val id: Long,
    val taskId: Long,
    val description: String,
    val achieved: Boolean
)

```

##### File: `app/src/main/java/com/a2d/app/data/local/relation/TaskWithActionItems.kt`

```kotlin
package com.a2d.app.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.a2d.app.data.local.entity.ActionItemEntity
import com.a2d.app.data.local.entity.TaskEntity

data class TaskWithActionItems(
    @Embedded val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId"
    )
    val actionItems: List<ActionItemEntity>
)

```

##### File: `app/src/main/java/com/a2d/app/data/local/dao/TaskDao.kt`

```kotlin
package com.a2d.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.a2d.app.data.local.entity.ActionItemEntity
import com.a2d.app.data.local.entity.TaskEntity
import com.a2d.app.data.local.relation.TaskWithActionItems
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Transaction
    @Query("SELECT * FROM tasks ORDER BY priority ASC")
    fun getAllTasks(): Flow<List<TaskWithActionItems>>

    @Transaction
    @Query("SELECT * FROM tasks ORDER BY priority ASC")
    suspend fun getTaskSnapshot(): List<TaskWithActionItems>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Long): TaskWithActionItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskEntity(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionItems(items: List<ActionItemEntity>)

    @Query("DELETE FROM action_items WHERE taskId = :taskId")
    suspend fun deleteActionItemsByTaskId(taskId: Long)

    @Transaction
    suspend fun insertOrUpdateTask(task: TaskEntity, items: List<ActionItemEntity>) {
        insertTaskEntity(task)
        deleteActionItemsByTaskId(task.id)
        insertActionItems(items)
    }

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: Long): Int
}

```

##### File: `app/src/main/java/com/a2d/app/data/local/A2DDatabase.kt`

```kotlin
package com.a2d.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.a2d.app.data.local.dao.TaskDao
import com.a2d.app.data.local.entity.ActionItemEntity
import com.a2d.app.data.local.entity.TaskEntity

@Database(entities = [TaskEntity::class, ActionItemEntity::class], version = 1, exportSchema = false)
abstract class A2DDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}

```

---

### Milestone 2.2: Data Repository Implementation

##### File: `app/src/main/java/com/a2d/app/data/repository/TaskRepositoryImpl.kt`

```kotlin
package com.a2d.app.data.repository

import com.a2d.app.data.local.dao.TaskDao
import com.a2d.app.data.local.entity.ActionItemEntity
import com.a2d.app.data.local.entity.TaskEntity
import com.a2d.app.domain.model.ActionItem
import com.a2d.app.domain.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao
) {
    fun getAllTasks(): Flow<List<Task>> {
        return taskDao.getAllTasks().map { list ->
            list.map { withItems ->
                Task(
                    id = withItems.task.id,
                    taskName = withItems.task.taskName,
                    description = withItems.task.description,
                    priority = withItems.task.priority,
                    achieved = withItems.task.achieved,
                    context = withItems.task.context,
                    creationTime = withItems.task.creationTime,
                    activities = withItems.actionItems.map {
                        ActionItem(it.id, it.description, it.achieved)
                    }
                )
            }
        }
    }

    suspend fun getTaskSnapshot(): List<Task> {
        return taskDao.getTaskSnapshot().map { withItems ->
            Task(
                id = withItems.task.id,
                taskName = withItems.task.taskName,
                description = withItems.task.description,
                priority = withItems.task.priority,
                achieved = withItems.task.achieved,
                context = withItems.task.context,
                creationTime = withItems.task.creationTime,
                activities = withItems.actionItems.map {
                    ActionItem(it.id, it.description, it.achieved)
                }
            )
        }
    }

    suspend fun getTask(id: Long): Task? {
        val record = taskDao.getTaskById(id) ?: return null
        return Task(
            id = record.task.id,
            taskName = record.task.taskName,
            description = record.task.description,
            priority = record.task.priority,
            achieved = record.task.achieved,
            context = record.task.context,
            creationTime = record.task.creationTime,
            activities = record.actionItems.map { ActionItem(it.id, it.description, it.achieved) }
        )
    }

    suspend fun saveTask(task: Task) {
        val timestamp = task.creationTime ?: SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val taskEntity = TaskEntity(
            id = task.id,
            taskName = task.taskName,
            description = task.description,
            priority = task.priority,
            context = task.context,
            achieved = task.achieved,
            creationTime = timestamp
        )
        val actionEntities = task.activities.map {
            ActionItemEntity(id = it.id, taskId = task.id, description = it.description, achieved = it.achieved)
        }
        taskDao.insertOrUpdateTask(taskEntity, actionEntities)
    }

    suspend fun deleteTask(id: Long): Boolean {
        return taskDao.deleteTask(id) > 0
    }
}

```

---

### Milestone 2.3: Dependency Injection Wiring

##### File: `app/src/main/java/com/a2d/app/di/AppModule.kt`

```kotlin
package com.a2d.app.di

import android.content.Context
import androidx.room.Room
import com.a2d.app.data.local.A2DDatabase
import com.a2d.app.data.local.dao.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): A2DDatabase {
        return Room.databaseBuilder(
            context,
            A2DDatabase::class.java,
            "a2d_database.db"
        ).build()
    }

    @Provides
    fun provideTaskDao(database: A2DDatabase): TaskDao {
        return database.taskDao()
    }
}

```

##### File: `app/src/main/java/com/a2d/app/A2DApplication.kt`

```kotlin
package com.a2d.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class A2DApplication : Application()

```

#### Verification for Sprint 2

Run in Terminal:

```bash
./gradlew compileDebugKotlin

```

Verifies KSP generates the Room DAOs and Hilt components without compilation errors.

---

## 5. Sprint 3: Jetpack Compose UI & Presentation Layer

### Milestone 3.1: Material 3 Theme Implementation

##### File: `app/src/main/java/com/a2d/app/ui/theme/Color.kt`

```kotlin
package com.a2d.app.ui.theme

import androidx.compose.ui.graphics.Color

val CalmGreen = Color(0xFF2E7D32)
val CalmTeal = Color(0xFF00695C)
val SoftGrey = Color(0xFFF5F5F5)
val DarkGrey = Color(0xFF212121)
val AlertRed = Color(0xFFC62828)

```

##### File: `app/src/main/java/com/a2d/app/ui/theme/Theme.kt`

```kotlin
package com.a2d.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = CalmTeal,
    secondary = CalmGreen,
    background = SoftGrey,
    surface = androidx.compose.ui.graphics.Color.White,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    error = AlertRed
)

@Composable
fun A2DTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}

```

---

### Milestone 3.2: ViewModels with Gate Integration

##### File: `app/src/main/java/com/a2d/app/ui/task/TaskViewModel.kt`

```kotlin
package com.a2d.app.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a2d.app.data.repository.TaskRepositoryImpl
import com.a2d.app.domain.engine.SerialAndGateProcessor
import com.a2d.app.domain.engine.TaskListPipelineProcessor
import com.a2d.app.domain.model.ActionItem
import com.a2d.app.domain.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val repository: TaskRepositoryImpl,
    private val gateProcessor: SerialAndGateProcessor,
    private val pipelineProcessor: TaskListPipelineProcessor
) : ViewModel() {

    val tasks: StateFlow<List<Task>> = repository.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _milestoneOutput = MutableStateFlow(0)
    val milestoneOutput: StateFlow<Int> = _milestoneOutput.asStateFlow()

    fun createSampleTask(id: Long, name: String, priority: Int) {
        viewModelScope.launch {
            val task = Task(
                id = id,
                taskName = name,
                description = "Focus on one action item at a time.",
                priority = priority,
                context = "Daily Routine",
                activities = listOf(
                    ActionItem(id = id * 10 + 1, description = "Prepare materials"),
                    ActionItem(id = id * 10 + 2, description = "Focus 25-minute sprint")
                )
            )
            repository.saveTask(task)
            recalculatePipeline()
        }
    }

    fun toggleActionItem(task: Task, actionId: Long) {
        viewModelScope.launch {
            val updated = task.activities.map {
                if (it.id == actionId) it.copy(achieved = !it.achieved) else it
            }
            task.activities = updated

            // Tier 1: Process Task Gate
            task.processActivityWithGate(gateProcessor)
            repository.saveTask(task)

            // Tier 2: Process Global Pipeline Gate
            recalculatePipeline()
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            repository.deleteTask(id)
            recalculatePipeline()
        }
    }

    private suspend fun recalculatePipeline() {
        val currentTasks = repository.getTaskSnapshot()
        val pipelineResult = pipelineProcessor.evaluateTaskList(currentTasks)
        _milestoneOutput.value = pipelineResult.output
    }
}

```

##### File: `app/src/main/java/com/a2d/app/ui/logicgate/LogicGateViewModel.kt`

```kotlin
package com.a2d.app.ui.logicgate

import androidx.lifecycle.ViewModel
import com.a2d.app.domain.engine.SerialAndGateProcessor
import com.a2d.app.domain.model.PulseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class LogicGateViewModel @Inject constructor(
    private val processor: SerialAndGateProcessor
) : ViewModel() {

    private val _lastResult = MutableStateFlow<PulseResult?>(null)
    val lastResult: StateFlow<PulseResult?> = _lastResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun sendPulse(pin: String, pulseType: String) {
        try {
            _errorMessage.value = null
            _lastResult.value = processor.processPulse(pin, pulseType)
        } catch (e: Exception) {
            _errorMessage.value = e.message
        }
    }
}

```

---

### Milestone 3.3: Compose Screens & Main Navigation

##### File: `app/src/main/java/com/a2d/app/ui/task/TaskScreen.kt`

```kotlin
package com.a2d.app.ui.task

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.a2d.app.domain.model.Task

@Composable
fun TaskScreen(
    tasks: List<Task>,
    milestoneOutput: Int,
    onToggleAction: (Task, Long) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onCreateSample: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("A2D Focus Tasks", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = if (milestoneOutput == 1) "Milestone Complete [GATE 1]" else "Milestone Incomplete [GATE 0]",
                    color = if (milestoneOutput == 1) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Button(onClick = onCreateSample) {
                Text("+ Task")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks, key = { it.id }) { task ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(task.taskName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (task.achieved) "COMPLETED" else "PRIORITY ${task.priority}",
                                color = if (task.achieved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        Text(task.description, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        task.activities.forEach { action ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = action.achieved,
                                    onCheckedChange = { onToggleAction(task, action.id) }
                                )
                                Text(action.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        TextButton(
                            onClick = { onDeleteTask(task.id) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

```

##### File: `app/src/main/java/com/a2d/app/ui/logicgate/LogicGateScreen.kt`

```kotlin
package com.a2d.app.ui.logicgate

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.a2d.app.domain.model.PulseResult

@Composable
fun LogicGateScreen(
    lastResult: PulseResult?,
    errorMessage: String?,
    onSendPulse: (String, String) -> Unit
) {
    var pinText by remember { mutableStateOf("2") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("AND Gate Signal Simulator", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = pinText,
            onValueChange = { pinText = it },
            label = { Text("Input Pin (Arity)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSendPulse(pinText, "HIGH") }) {
                Text("Pulse HIGH")
            }
            Button(
                onClick = { onSendPulse(pinText, "LOW") },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Pulse LOW (Reset)")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        errorMessage?.let {
            Text("Error: $it", color = MaterialTheme.colorScheme.error)
        }

        lastResult?.let { res ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Pin: ${res.inputPin}")
                    Text("Pulse: ${res.pulse}")
                    Text("Latched Inputs: ${res.latchedInputs}")
                    Text("Gate Output: ${res.output}", style = MaterialTheme.typography.titleLarge)
                    Text("Reset: ${res.reset}")
                }
            }
        }
    }
}

```

##### File: `app/src/main/java/com/a2d/app/ui/MainActivity.kt`

```kotlin
package com.a2d.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.a2d.app.ui.logicgate.LogicGateScreen
import com.a2d.app.ui.logicgate.LogicGateViewModel
import com.a2d.app.ui.task.TaskScreen
import com.a2d.app.ui.task.TaskViewModel
import com.a2d.app.ui.theme.A2DTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            A2DTheme {
                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                label = { Text("Tasks") },
                                icon = {}
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                label = { Text("Logic Gate") },
                                icon = {}
                            )
                        }
                    }
                ) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        if (selectedTab == 0) {
                            val taskVm: TaskViewModel = hiltViewModel()
                            val tasks by taskVm.tasks.collectAsState()
                            val milestone by taskVm.milestoneOutput.collectAsState()

                            TaskScreen(
                                tasks = tasks,
                                milestoneOutput = milestone,
                                onToggleAction = { task, id -> taskVm.toggleActionItem(task, id) },
                                onDeleteTask = { id -> taskVm.deleteTask(id) },
                                onCreateSample = {
                                    val newId = System.currentTimeMillis()
                                    taskVm.createSampleTask(newId, "Task #$newId", 1)
                                }
                            )
                        } else {
                            val gateVm: LogicGateViewModel = hiltViewModel()
                            val result by gateVm.lastResult.collectAsState()
                            val error by gateVm.errorMessage.collectAsState()

                            LogicGateScreen(
                                lastResult = result,
                                errorMessage = error,
                                onSendPulse = { pin, pulse -> gateVm.sendPulse(pin, pulse) }
                            )
                        }
                    }
                }
            }
        }
    }
}

```

##### File: `app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".A2DApplication"
        android:allowBackup="true"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="A2D Assistant"
        android:roundIcon="@android:drawable/sym_def_app_icon"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>

```

#### Verification for Sprint 3

Run in Terminal:

```bash
./gradlew assembleDebug

```

Generates a complete, functional debug APK:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 6. Sprint 4: Physical Device Deployment & Play Store Release

### Milestone 4.1: USB Debugging Setup & Deployment

1. **Enable Developer Options on Device:**
* Go to **Settings $\to$ About Phone** on your Android smartphone.
* Tap **Build Number** 7 times until developer status is confirmed.
* Navigate to **Settings $\to$ Developer Options** and enable **USB Debugging**.


2. **Connect Phone via USB to MacBook Air:**
```bash
adb devices

```


Confirm your device appears as authorized.
3. **Install and Run Debug Build:**
```bash
./gradlew installDebug

```


Launch the app on the phone. Verify:
* Tapping `+ Task` inserts a task and displays sub-actions.


* Toggling both sub-actions flips the task to `COMPLETED`.


* The top status flips to `Milestone Complete [GATE 1]`.


* Toggling to the `Logic Gate` tab allows sending manual pulses to pin `2`.





---

### Milestone 4.2: Production Key Generation & App Bundle Build

#### 1. Generate Keystore

Execute in project root:

```bash
keytool -genkey -v -keystore a2d-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias a2d-key-alias \
  -dname "CN=A2D Developer, OU=Mobile, O=A2D, L=San Francisco, S=CA, C=US" \
  -storepass a2dpassword -keypass a2dpassword

```

#### 2. Verify `keystore.properties`

Confirm `keystore.properties` exists in the project root:

```properties
KEYSTORE_PATH=../a2d-release-key.jks
KEYSTORE_PASSWORD=a2dpassword
KEY_ALIAS=a2d-key-alias
KEY_PASSWORD=a2dpassword

```

#### 3. Build Signed Release Bundle (.aab)

Run in Terminal:

```bash
./gradlew bundleRelease

```

The production bundle is output to:
`app/build/outputs/bundle/release/app-release.aab`

---

### Milestone 4.3: Google Play Store Release Checklist

1. **Google Play Console Account:**
* Navigate to `[https://play.google.com/console](https://play.google.com/console)`.
* Create an application titled **A2D - Adult ADHD Assistant**.


2. **Store Listing Assets:**
* App Icon: 512x512 PNG (32-bit color).
* Feature Graphic: 1024x500 JPG or 24-bit PNG.
* At least 2 phone screenshots (portrait 1080x1920).


3. **App Content Declarations:**
* Privacy Policy: Declare local SQLite storage with zero third-party telemetry.
* Target Audience: 18 and older.
* Data Safety: Declare no personal data is collected or shared off-device.


4. **Internal Testing Release:**
* Select **Testing $\to$ Internal Testing**.
* Create a new release and upload `app/build/outputs/bundle/release/app-release.aab`.
* Add tester emails and verify installation via the Play Store testing URL.


5. **Production Promotion:**
* After verification on internal testing, click **Promote Release $\to$ Production**.
* Submit the app for Google Play Review.