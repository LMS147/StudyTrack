package com.studytrack.app.data.repository

import androidx.room.withTransaction
import com.studytrack.app.auth.CurrentAccount
import com.studytrack.app.data.local.StudyTrackDatabase
import com.studytrack.app.data.local.entity.FlashcardEntity
import com.studytrack.app.flashcards.ReviewRating
import com.studytrack.app.flashcards.ReviewSchedule
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

/** Device-local, UID-scoped cards. A session check prevents writes from stale editors. */
@OptIn(ExperimentalCoroutinesApi::class)
class FlashcardRepository(private val db: StudyTrackDatabase, private val account: CurrentAccount) {
    val cards: Flow<List<FlashcardEntity>> = account.activeUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else flow {
            emit(emptyList())
            emitAll(db.flashcardDao().observe(uid))
        }
    }

    suspend fun save(card: FlashcardEntity, isNew: Boolean) {
        require(account.uidOrNull() == card.ownerUid) { "Your account changed. Reopen the card editor." }
        require(card.question.isNotBlank() && card.answer.isNotBlank()) { "Enter both a question and an answer." }
        require(card.question.length <= 1000 && card.answer.length <= 4000) { "Question or answer is too long." }
        require(card.subjectId.isNotBlank()) { "Choose a subject." }
        db.withTransaction {
            require(account.uidOrNull() == card.ownerUid) { "Your account changed. Reopen the card editor." }
            val current = db.flashcardDao().get(card.ownerUid, card.cardId)
            require(if (isNew) current == null else current != null) { "This card changed or no longer exists." }
            require(db.subjectDao().getById(card.ownerUid, card.subjectId) != null) { "Choose an existing subject." }
            // Existing study progress is authoritative even if an editor was opened earlier.
            val progress = current ?: card
            db.flashcardDao().upsert(card.copy(question = card.question.trim(), answer = card.answer.trim(),
                dueDate = progress.dueDate, intervalDays = progress.intervalDays,
                reviewCount = progress.reviewCount, lastReviewedOn = progress.lastReviewedOn))
        }
    }

    suspend fun rate(ownerUid: String, cardId: String, rating: ReviewRating, today: LocalDate = LocalDate.now()) {
        require(account.uidOrNull() == ownerUid) { "Your account changed. Reopen the review session." }
        db.withTransaction {
            require(account.uidOrNull() == ownerUid) { "Your account changed. Reopen the review session." }
            val card = requireNotNull(db.flashcardDao().get(ownerUid, cardId)) { "This card no longer exists." }
            require(ReviewSchedule.isDue(card.dueDate, today)) { "This card is not due for review." }
            val next = ReviewSchedule.next(today, card.intervalDays, rating)
            db.flashcardDao().upsert(card.copy(dueDate = next.dueDate.toString(), intervalDays = next.intervalDays,
                reviewCount = card.reviewCount + 1, lastReviewedOn = today.toString()))
        }
    }

    suspend fun delete(ownerUid: String, cardId: String) {
        require(account.uidOrNull() == ownerUid) { "Your account changed. Reopen the cards." }
        db.flashcardDao().delete(ownerUid, cardId)
    }
}
