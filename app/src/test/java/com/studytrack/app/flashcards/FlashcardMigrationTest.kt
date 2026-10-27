package com.studytrack.app.flashcards

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.studytrack.app.data.local.StudyTrackDatabase
import com.studytrack.app.data.local.entity.FlashcardEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FlashcardMigrationTest {
    @Test fun `version 2 upgrades without losing tasks or timetable entries`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "flashcards-migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val relative = "schemas/com.studytrack.app.data.local.StudyTrackDatabase/2.json"
        val schemaFile = listOf(File(relative), File("app/$relative")).first { it.exists() }
        val schema = Json.parseToJsonElement(schemaFile.readText()).jsonObject.getValue("database").jsonObject
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            schema.getValue("entities").jsonArray.forEach { value ->
                val entity = value.jsonObject
                val table = entity.getValue("tableName").jsonPrimitive.content
                old.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity.getValue("indices").jsonArray.forEach { index ->
                    old.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { old.execSQL(it.jsonPrimitive.content) }
            old.execSQL("""INSERT INTO tasks (ownerUid, taskId, title, taskType, priority, completed, points, syncStatus, localUpdatedAt, remoteUpdatedAt)
                VALUES ('alice', 'old-task', 'Preserved task', 'STUDY', 'MEDIUM', 0, 0, 'SYNCED', 1, 1)""")
            old.execSQL("""INSERT INTO timetable_entries (ownerUid, entryId, title, subjectId, dayOfWeek, startMinute, endMinute, venue, lecturer, startsOn, endsOn)
                VALUES ('alice', 'old-class', 'Preserved class', NULL, 1, 540, 600, '', '', '2026-10-01', NULL)""")
            old.version = 2
        }
        val db = Room.databaseBuilder(context, StudyTrackDatabase::class.java, name)
            .addMigrations(*StudyTrackDatabase.MIGRATIONS).allowMainThreadQueries().build()
        try {
            assertEquals(3, db.openHelper.writableDatabase.version)
            assertEquals("Preserved task", db.taskDao().getById("alice", "old-task")!!.title)
            assertEquals("Preserved class", db.timetableDao().all("alice").single().title)
            db.flashcardDao().upsert(FlashcardEntity("alice", "new-card", "subject", "Q", "A", "2026-10-27", 0, 0, null))
            assertEquals("Q", db.flashcardDao().get("alice", "new-card")!!.question)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
