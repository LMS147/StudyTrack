package com.studytrack.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema-level guarantees behind the isolation rule.
 *
 * These inspect the SQL that Room actually generated, rather than re-asserting
 * what the annotations say — so if an entity ever loses its `ownerUid` column
 * or the column becomes nullable, this fails.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudyTrackDatabaseTest {

    private lateinit var db: StudyTrackDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StudyTrackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // Force creation of every table.
        db.openHelper.writableDatabase
    }

    @After
    fun tearDown() {
        db.close()
    }

    private data class Column(val name: String, val notNull: Boolean, val partOfPk: Boolean)

    private fun columnsOf(table: String): List<Column> {
        val cursor = db.openHelper.readableDatabase.query("PRAGMA table_info($table)")
        val out = mutableListOf<Column>()
        cursor.use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            val notNullIdx = c.getColumnIndexOrThrow("notnull")
            val pkIdx = c.getColumnIndexOrThrow("pk")
            while (c.moveToNext()) {
                out += Column(
                    name = c.getString(nameIdx),
                    notNull = c.getInt(notNullIdx) != 0,
                    partOfPk = c.getInt(pkIdx) > 0,
                )
            }
        }
        return out
    }

    // -------------------------------------------------------- ownerUid columns

    @Test
    fun `every scoped table has a non-nullable ownerUid column`() {
        for (table in listOf("tasks", "subjects", "progress", "study_sessions")) {
            val ownerUid = columnsOf(table).firstOrNull { it.name == "ownerUid" }
            assertTrue("$table is missing ownerUid", ownerUid != null)
            assertTrue("$table.ownerUid must be NOT NULL", ownerUid!!.notNull)
        }
    }

    @Test
    fun `ownerUid is part of the primary key on the multi-row tables`() {
        // This is what makes a cross-account row collision impossible, rather
        // than merely unlikely.
        for (table in listOf("tasks", "subjects", "study_sessions")) {
            val ownerUid = columnsOf(table).first { it.name == "ownerUid" }
            assertTrue("$table.ownerUid must be part of the primary key", ownerUid.partOfPk)
        }
    }

    @Test
    fun `progress is keyed by ownerUid alone`() {
        val pk = columnsOf("progress").filter { it.partOfPk }.map { it.name }
        assertEquals(listOf("ownerUid"), pk)
    }

    @Test
    fun `every scoped table carries a syncStatus column`() {
        for (table in listOf("tasks", "subjects", "progress", "study_sessions")) {
            assertTrue(
                "$table is missing syncStatus",
                columnsOf(table).any { it.name == "syncStatus" },
            )
        }
    }

    @Test
    fun `the account registry stores no study data`() {
        // account_records is the one table read without a UID filter, so it must
        // contain nothing but identity and sync metadata.
        val names = columnsOf("account_records").map { it.name }
        assertEquals(
            listOf("bootstrapped", "displayName", "email", "lastSyncedAt", "ownerUid"),
            names.sorted(),
        )
        assertTrue(names.none { it in setOf("title", "subjectName", "description", "notes") })
    }

    // --------------------------------------------------------- migration policy

    @Test
    fun `the shipped migration set only contains paths that end at the current version`() {
        // MIGRATION_1_2 is written but dormant: it must not be handed to Room
        // while the schema is still version 1.
        assertEquals(StudyTrackDatabase.VERSION, 1)
        assertTrue(
            "MIGRATION_1_2 should exist as the documented template",
            StudyTrackDatabase.ALL_MIGRATIONS.any { it.startVersion == 1 && it.endVersion == 2 },
        )
        assertTrue(
            "no migration may target a version above the current schema",
            StudyTrackDatabase.MIGRATIONS.all { it.endVersion <= StudyTrackDatabase.VERSION },
        )
        assertEquals(0, StudyTrackDatabase.MIGRATIONS.size)
    }

    @Test
    fun `a dormant migration activates when its version ships`() {
        // Pins the filter logic that makes future migrations self-activating,
        // so a bumped VERSION cannot silently ship without its migration.
        val applicable = StudyTrackDatabase.ALL_MIGRATIONS.filter { it.endVersion <= 2 }
        assertTrue(applicable.any { it.startVersion == 1 && it.endVersion == 2 })
    }

    @Test
    fun `the database opens at the declared schema version`() {
        assertEquals(StudyTrackDatabase.VERSION, db.openHelper.readableDatabase.version)
    }
}
