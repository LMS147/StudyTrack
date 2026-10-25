package com.studytrack.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.studytrack.app.data.local.entity.TimetableEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetable_entries WHERE ownerUid = :uid ORDER BY dayOfWeek, startMinute, title")
    fun observe(uid: String): Flow<List<TimetableEntry>>

    @Query("SELECT * FROM timetable_entries WHERE ownerUid = :uid")
    suspend fun all(uid: String): List<TimetableEntry>

    @Upsert suspend fun upsert(entry: TimetableEntry)

    @Query("DELETE FROM timetable_entries WHERE ownerUid = :uid AND entryId = :id")
    suspend fun delete(uid: String, id: String)
}
