package com.studytrack.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** A locally stored question with a day-based revision schedule. */
@Entity(tableName = "flashcards", primaryKeys = ["ownerUid", "cardId"],
    indices = [Index(value = ["ownerUid", "subjectId"]), Index(value = ["ownerUid", "dueDate"])])
data class FlashcardEntity(
    @ColumnInfo(name = "ownerUid") val ownerUid: String,
    val cardId: String,
    val subjectId: String,
    val question: String,
    val answer: String,
    val dueDate: String,
    val intervalDays: Int,
    val reviewCount: Int,
    val lastReviewedOn: String?,
)
