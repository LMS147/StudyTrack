package com.studytrack.app.data.repository

import androidx.room.withTransaction
import com.studytrack.app.auth.CurrentAccount
import com.studytrack.app.data.local.StudyTrackDatabase
import com.studytrack.app.data.local.entity.TimetableEntry
import com.studytrack.app.timetable.TimetableRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

/** Local-only weekly schedule. Every read/write is scoped to the active Firebase account. */
@OptIn(ExperimentalCoroutinesApi::class)
class TimetableRepository(private val db: StudyTrackDatabase, private val account: CurrentAccount) {
    val entries: Flow<List<TimetableEntry>> = account.activeUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else flow {
            emit(emptyList())
            emitAll(db.timetableDao().observe(uid))
        }
    }

    suspend fun save(entry: TimetableEntry) {
        require(account.uidOrNull() == entry.ownerUid) { "Your account changed. Reopen the class editor." }
        TimetableRules.validate(entry)
        db.withTransaction {
            require(account.uidOrNull() == entry.ownerUid) { "Your account changed. Reopen the class editor." }
            val conflict = db.timetableDao().all(entry.ownerUid).firstOrNull {
                it.entryId != entry.entryId && TimetableRules.overlaps(it, entry)
            }
            require(conflict == null) { "This time overlaps with ${conflict?.title}. Choose another time." }
            db.timetableDao().upsert(entry.copy(title = entry.title.trim(), venue = entry.venue.trim(), lecturer = entry.lecturer.trim()))
        }
    }

    suspend fun delete(entry: TimetableEntry) {
        require(account.uidOrNull() == entry.ownerUid) { "Your account changed. Reopen the timetable." }
        db.timetableDao().delete(entry.ownerUid, entry.entryId)
    }
}
