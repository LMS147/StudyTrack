package com.studytrack.app.data.local

import com.studytrack.app.data.local.entity.ProgressEntity
import com.studytrack.app.data.local.entity.StudySessionEntity
import com.studytrack.app.data.local.entity.SubjectEntity
import com.studytrack.app.data.local.entity.TaskEntity
import com.studytrack.app.data.model.Priority
import com.studytrack.app.data.model.Progress
import com.studytrack.app.data.model.StudySessionPayload
import com.studytrack.app.data.model.Subject
import com.studytrack.app.data.model.SubjectPayload
import com.studytrack.app.data.model.Task
import com.studytrack.app.data.model.TaskPayload
import com.studytrack.app.data.model.TaskType

/**
 * Conversions between the wire models the UI already uses and the Room
 * entities that carry `ownerUid` plus sync bookkeeping.
 *
 * Keeping these in one file means the rest of the app never constructs an
 * entity by hand — and therefore never has the chance to forget `ownerUid`.
 * Every mapper *requires* it as a parameter.
 */

fun TaskEntity.toModel(): Task = Task(
    taskId = taskId,
    title = title,
    subjectId = subjectId,
    description = description,
    taskType = TaskType.fromRaw(taskType),
    priority = Priority.fromRaw(priority),
    dueDate = dueDate,
    reminderDate = reminderDate,
    completed = completed,
    completedAt = completedAt,
    points = points,
)

/**
 * Builds the row to persist for a task.
 *
 * @param ownerUid the account this row belongs to — always the active session's
 *   UID, never supplied by UI code.
 */
fun Task.toEntity(
    ownerUid: String,
    syncStatus: SyncStatus,
    localUpdatedAt: Long,
    remoteUpdatedAt: Long = 0L,
): TaskEntity = TaskEntity(
    ownerUid = ownerUid,
    taskId = taskId,
    title = title,
    subjectId = subjectId,
    description = description,
    taskType = taskType.name,
    priority = priority.name,
    dueDate = dueDate,
    reminderDate = reminderDate,
    completed = completed,
    completedAt = completedAt,
    points = points,
    syncStatus = syncStatus,
    localUpdatedAt = localUpdatedAt,
    remoteUpdatedAt = remoteUpdatedAt,
)

/** Applies an edit payload onto a cached row, preserving identity + ownership. */
fun TaskEntity.applyPayload(payload: TaskPayload, updatedAt: Long): TaskEntity = copy(
    title = payload.title,
    subjectId = payload.subjectId,
    description = payload.description,
    taskType = payload.taskType.name,
    priority = payload.priority.name,
    dueDate = payload.dueDate,
    reminderDate = payload.reminderDate,
    syncStatus = SyncStatus.UPDATED,
    localUpdatedAt = updatedAt,
)

/** The request body used to push this row to the remote. */
fun TaskEntity.toPayload(): TaskPayload = TaskPayload(
    title = title,
    subjectId = subjectId,
    description = description,
    taskType = TaskType.fromRaw(taskType),
    priority = Priority.fromRaw(priority),
    dueDate = dueDate,
    reminderDate = reminderDate,
)

// -------------------------------------------------------------------- Subjects

fun SubjectEntity.toModel(): Subject = Subject(
    subjectId = subjectId,
    subjectName = subjectName,
    description = description,
)

fun Subject.toEntity(
    ownerUid: String,
    syncStatus: SyncStatus,
    localUpdatedAt: Long,
    remoteUpdatedAt: Long = 0L,
): SubjectEntity = SubjectEntity(
    ownerUid = ownerUid,
    subjectId = subjectId,
    subjectName = subjectName,
    description = description,
    syncStatus = syncStatus,
    localUpdatedAt = localUpdatedAt,
    remoteUpdatedAt = remoteUpdatedAt,
)

/** The request body used to push this row to the remote. */
fun SubjectEntity.toPayload(): SubjectPayload = SubjectPayload(
    subjectName = subjectName,
    description = description,
)

// -------------------------------------------------------------------- Progress

fun ProgressEntity.toModel(): Progress = Progress(
    totalPoints = totalPoints,
    completedTasks = completedTasks,
    currentStreak = currentStreak,
)

fun Progress.toEntity(
    ownerUid: String,
    localUpdatedAt: Long,
    remoteUpdatedAt: Long = 0L,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
): ProgressEntity = ProgressEntity(
    ownerUid = ownerUid,
    totalPoints = totalPoints,
    completedTasks = completedTasks,
    currentStreak = currentStreak,
    syncStatus = syncStatus,
    localUpdatedAt = localUpdatedAt,
    remoteUpdatedAt = remoteUpdatedAt,
)

// -------------------------------------------------------------- Study sessions

fun StudySessionEntity.toPayload(): StudySessionPayload = StudySessionPayload(
    subjectId = subjectId,
    taskId = taskId,
    durationMinutes = durationMinutes,
    studiedAt = studiedAt,
    notes = notes,
)
