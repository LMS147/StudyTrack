package com.studytrack.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.studytrack.app.data.local.dao.AccountDao
import com.studytrack.app.data.local.dao.ProgressDao
import com.studytrack.app.data.local.dao.StudySessionDao
import com.studytrack.app.data.local.dao.SubjectDao
import com.studytrack.app.data.local.dao.TaskDao
import com.studytrack.app.data.local.entity.AccountRecordEntity
import com.studytrack.app.data.local.entity.ProgressEntity
import com.studytrack.app.data.local.entity.StudySessionEntity
import com.studytrack.app.data.local.entity.SubjectEntity
import com.studytrack.app.data.local.entity.TaskEntity

/**
 * The offline cache and sync queue.
 *
 * ## Migration strategy
 *
 * Schemas are exported to `app/schemas/` (see the `room.schemaLocation` KSP arg
 * in `app/build.gradle.kts`) and committed, so every schema change is a
 * reviewable JSON diff. Migration classes are written for each bump and
 * registered here.
 *
 * **`fallbackToDestructiveMigration()` is deliberately never called.** With no
 * destructive fallback, an unhandled version bump fails loudly at database-open
 * time instead of silently dropping every account's cached tasks. For an
 * offline-first app whose whole value is "your queued work survives", losing
 * the queue quietly is the worst possible failure mode — a crash is recoverable,
 * a wiped offline queue is not.
 *
 * [MIGRATIONS] is derived from [ALL_MIGRATIONS] filtered by the current
 * [VERSION], so a migration written ahead of time stays dormant until the
 * version it targets actually ships, and activates itself the moment
 * [VERSION] is bumped.
 */
@Database(
    entities = [
        TaskEntity::class,
        SubjectEntity::class,
        ProgressEntity::class,
        StudySessionEntity::class,
        AccountRecordEntity::class,
        com.studytrack.app.data.local.entity.TimetableEntry::class,
        com.studytrack.app.data.local.entity.FlashcardEntity::class,
    ],
    version = StudyTrackDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class StudyTrackDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun subjectDao(): SubjectDao
    abstract fun progressDao(): ProgressDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun accountDao(): AccountDao
    abstract fun timetableDao(): com.studytrack.app.data.local.dao.TimetableDao
    abstract fun flashcardDao(): com.studytrack.app.data.local.dao.FlashcardDao

    companion object {

        const val VERSION = 3
        private const val DATABASE_NAME = "studytrack.db"

        @Volatile
        private var instance: StudyTrackDatabase? = null

        /** Add weekly schedules without modifying or deleting existing study data. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `timetable_entries` (
                    `ownerUid` TEXT NOT NULL, `entryId` TEXT NOT NULL, `title` TEXT NOT NULL,
                    `subjectId` TEXT, `dayOfWeek` INTEGER NOT NULL, `startMinute` INTEGER NOT NULL,
                    `endMinute` INTEGER NOT NULL, `venue` TEXT NOT NULL, `lecturer` TEXT NOT NULL,
                    `startsOn` TEXT NOT NULL, `endsOn` TEXT, PRIMARY KEY(`ownerUid`, `entryId`)
                )""")
            }
        }

        /** Add flashcards and indexes without modifying existing classes or tasks. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `flashcards` (
                    `ownerUid` TEXT NOT NULL, `cardId` TEXT NOT NULL, `subjectId` TEXT NOT NULL,
                    `question` TEXT NOT NULL, `answer` TEXT NOT NULL, `dueDate` TEXT NOT NULL,
                    `intervalDays` INTEGER NOT NULL, `reviewCount` INTEGER NOT NULL,
                    `lastReviewedOn` TEXT, PRIMARY KEY(`ownerUid`, `cardId`)
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_ownerUid_subjectId` ON `flashcards` (`ownerUid`, `subjectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_ownerUid_dueDate` ON `flashcards` (`ownerUid`, `dueDate`)")
            }
        }

        /**
         * Every migration written so far, including ones targeting versions
         * above [VERSION] (those stay dormant — see the class docs).
         */
        val ALL_MIGRATIONS: List<Migration> = listOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
        )

        /** Migrations applicable to the schema this build actually ships. */
        val MIGRATIONS: Array<Migration> = ALL_MIGRATIONS
            .filter { it.endVersion <= VERSION }
            .toTypedArray()

        /**
         * Single shared instance. Room's build is expensive and the DAOs are
         * stateless, so the whole app — including WorkManager workers, which
         * get their own process-independent entry point — shares one handle.
         */
        fun getInstance(context: Context): StudyTrackDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        /**
         * Exposed for tests, which need their own in-memory database rather
         * than the process-wide singleton.
         */
        fun build(context: Context): StudyTrackDatabase =
            Room.databaseBuilder(context, StudyTrackDatabase::class.java, DATABASE_NAME)
                .addMigrations(*MIGRATIONS)
                // No fallbackToDestructiveMigration() — see the class docs.
                .build()

        /** Test hook: drops the singleton so the next call builds fresh. */
        fun resetInstanceForTesting() {
            synchronized(this) { instance = null }
        }
    }
}
