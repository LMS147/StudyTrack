package com.studytrack.app.data.local

import android.content.Context
import com.studytrack.app.data.local.entity.SubjectEntity
import com.studytrack.app.data.local.entity.TaskEntity
import com.studytrack.app.data.model.Subject
import com.studytrack.app.data.model.Task
import com.studytrack.app.data.remote.RetrofitClient
import kotlinx.serialization.builtins.ListSerializer

/**
 * One-time import of the pre-Room `SharedPreferences` store into Room.
 *
 * Before this feature, local-mode subjects and tasks lived as JSON blobs in the
 * `studytrack_local_store` preferences file, with **no owner column at all** —
 * every account on the device shared one list. Moving to Room is also the point
 * where per-account scoping arrives, so the import has to attribute those rows
 * to someone: they go to the account that is signed in when the migration runs.
 *
 * That is the correct attribution for the single-account installs this app has
 * had so far. It runs at most once per device (guarded by a preference flag),
 * and the old store is left in place rather than deleted, so a rollback still
 * has the data.
 */
class LegacyStoreMigration(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("studytrack_local_store", Context.MODE_PRIVATE)
    private val json = RetrofitClient.json

    /**
     * Imports legacy rows under [ownerUid]. No-op if already migrated or if
     * there is nothing legacy to import.
     */
    suspend fun migrateOnce(
        ownerUid: String,
        taskDao: com.studytrack.app.data.local.dao.TaskDao,
        subjectDao: com.studytrack.app.data.local.dao.SubjectDao,
    ) {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return

        val now = System.currentTimeMillis()
        val subjects = readList(KEY_SUBJECTS, Subject.serializer())
        val tasks = readList(KEY_TASKS, Task.serializer())
        if (subjects.isEmpty() && tasks.isEmpty()) {
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
            return
        }

        // Rows were never pending in the old store — there was no remote to owe
        // them to — so they arrive already SYNCED.
        if (subjects.isNotEmpty()) {
            subjectDao.insertAll(
                subjects.map { it.toEntity(ownerUid, SyncStatus.SYNCED, now, now) }
            )
        }
        if (tasks.isNotEmpty()) {
            taskDao.insertAll(
                tasks.map { it.toEntity(ownerUid, SyncStatus.SYNCED, now, now) }
            )
        }

        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    private fun <T> readList(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            json.decodeFromString(ListSerializer(serializer), raw)
        } catch (e: Exception) {
            // A corrupt legacy payload must not block sign-in. Skip it; the
            // remote copy is authoritative anyway once sync runs.
            emptyList()
        }
    }

    private companion object {
        const val KEY_SUBJECTS = "subjects_json"
        const val KEY_TASKS = "tasks_json"
        const val KEY_MIGRATED = "migrated_to_room_v1"
    }
}
