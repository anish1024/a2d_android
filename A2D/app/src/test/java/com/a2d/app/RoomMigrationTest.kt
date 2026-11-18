package com.a2d.app

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.a2d.app.data.local.A2DDatabase
import com.a2d.app.data.local.RoomMigration
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Robolectric test verifying the Room v1 -> v2 migration for FEAT-0002:
 *  - new columns sync_id / updated_at / deleted / dirty are added
 *  - existing rows survive
 *  - sync_id and updated_at are backfilled, dirty is set to 1
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomMigrationTest {

    private val app: android.content.Context
        get() = RuntimeEnvironment.getApplication()

    private fun createV1Database(file: File) {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(app)
            .name(file.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE tasks (id INTEGER PRIMARY KEY NOT NULL, " +
                            "taskName TEXT NOT NULL, description TEXT NOT NULL, " +
                            "priority INTEGER NOT NULL, context TEXT NOT NULL, " +
                            "achieved INTEGER NOT NULL, creationTime TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE action_items (id INTEGER PRIMARY KEY NOT NULL, " +
                            "taskId INTEGER NOT NULL, description TEXT NOT NULL, " +
                            "achieved INTEGER NOT NULL, " +
                            "FOREIGN KEY(taskId) REFERENCES tasks(id) ON DELETE CASCADE)"
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int
                ) { /* no-op */ }
            })
            .build()
        val helper = factory.create(config)
        val db = helper.writableDatabase
        try {
            db.execSQL(
                "INSERT INTO tasks (id, taskName, description, priority, context, achieved, creationTime) " +
                    "VALUES (1, 'Buy milk', 'dairy', 1, 'Home', 0, '2026-01-01T08:00:00Z')"
            )
            db.execSQL(
                "INSERT INTO action_items (id, taskId, description, achieved) " +
                    "VALUES (10, 1, 'Get milk', 0)"
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate1To2_preservesDataAndBackfillsSyncColumns() {
        val dbFile = File(app.cacheDir, "migration_${UUID.randomUUID()}.db")
        dbFile.delete()
        createV1Database(dbFile)

        val database = Room.databaseBuilder(app, A2DDatabase::class.java, dbFile.absolutePath)
            .addMigrations(RoomMigration.MIGRATION_1_2)
            .build()

        try {
            // 1. Data preserved: task visible through DAO
            runBlocking {
                val dao = database.taskDao()
                val snapshot = dao.getTaskSnapshot()
                assertEquals(1, snapshot.size)
                assertEquals("Buy milk", snapshot[0].task.taskName)
                assertEquals("Get milk", snapshot[0].actionItems[0].description)
            }

            // 2. New columns exist and were backfilled
            val taskCursor = database.query(
                "SELECT sync_id, updated_at, deleted, dirty FROM tasks WHERE id = 1",
                emptyArray<String>()
            )
            assertTrue(taskCursor.moveToNext())
            assertNotEquals("", taskCursor.getString(0))   // sync_id backfilled
            assertNotEquals(0L, taskCursor.getLong(1))     // updated_at backfilled
            assertEquals(0, taskCursor.getInt(2))          // deleted defaults 0
            assertEquals(1, taskCursor.getInt(3))          // dirty backfilled to 1
            taskCursor.close()

            val itemCursor = database.query(
                "SELECT sync_id, updated_at, deleted, dirty FROM action_items WHERE id = 10",
                emptyArray<String>()
            )
            assertTrue(itemCursor.moveToNext())
            assertNotEquals("", itemCursor.getString(0))
            assertNotEquals(0L, itemCursor.getLong(1))
            assertEquals(0, itemCursor.getInt(2))
            assertEquals(1, itemCursor.getInt(3))
            itemCursor.close()
        } finally {
            database.close()
            dbFile.delete()
        }
    }

    @Test
    fun migrationObject_coversV1ToV2() {
        assertEquals(1, RoomMigration.MIGRATION_1_2.startVersion)
        assertEquals(2, RoomMigration.MIGRATION_1_2.endVersion)
    }
}
