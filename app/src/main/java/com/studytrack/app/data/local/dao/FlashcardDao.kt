package com.studytrack.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.studytrack.app.data.local.entity.FlashcardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards WHERE ownerUid = :uid ORDER BY dueDate, question, cardId")
    fun observe(uid: String): Flow<List<FlashcardEntity>>
    @Query("SELECT * FROM flashcards WHERE ownerUid = :uid AND cardId = :id")
    suspend fun get(uid: String, id: String): FlashcardEntity?
    @Upsert suspend fun upsert(card: FlashcardEntity)
    @Query("DELETE FROM flashcards WHERE ownerUid = :uid AND cardId = :id")
    suspend fun delete(uid: String, id: String): Int
}
