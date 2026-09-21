package com.studytrack.app.data.repository

import com.studytrack.app.data.model.Priority
import com.studytrack.app.data.model.Task
import com.studytrack.app.data.model.TaskPayload
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.DateTimeUtils
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime
import java.util.UUID

/**
 * [TaskRepository] backed by [LocalStore] — no backend required. This is the
 * write path the AI chat uses in local mode, so accepted suggestions persist
 * on-device exactly like backend tasks.
 */
class LocalTaskRepository(
    private val store: LocalStore,
) : TaskRepository {

    override val tasks: StateFlow<List<Task>> = store.tasks

    override suspend fun refresh(): ApiResult<List<Task>> =
        ApiResult.Success(store.tasks.value)

    override suspend fun getTask(taskId: String): ApiResult<Task> =
        store.tasks.value.firstOrNull { it.taskId == taskId }
            ?.let { ApiResult.Success(it) }
            ?: ApiResult.Error("Task not found")

    override suspend fun create(payload: TaskPayload): ApiResult<Task> {
        val task = Task(
            taskId = UUID.randomUUID().toString(),
            title = payload.title,
            subjectId = payload.subjectId,
            description = payload.description,
            taskType = payload.taskType,
            priority = payload.priority,
            dueDate = payload.dueDate,
            reminderDate = payload.reminderDate,
        )
        store.mutateTasks { it.add(task) }
        return ApiResult.Success(task)
    }

    override suspend fun update(taskId: String, payload: TaskPayload): ApiResult<Task> {
        var updated: Task? = null
        store.mutateTasks { list ->
            val index = list.indexOfFirst { it.taskId == taskId }
            if (index >= 0) {
                updated = list[index].copy(
                    title = payload.title,
                    subjectId = payload.subjectId,
                    description = payload.description,
                    taskType = payload.taskType,
                    priority = payload.priority,
                    dueDate = payload.dueDate,
                    reminderDate = payload.reminderDate,
                    // completion state is owned by setCompleted()
                )
                list[index] = updated!!
            }
        }
        return updated?.let { ApiResult.Success(it) }
            ?: ApiResult.Error("Task not found")
    }

    override suspend fun delete(taskId: String): ApiResult<Unit> {
        store.mutateTasks { list ->
            list.removeAll { it.taskId == taskId }
        }
        return ApiResult.Success(Unit)
    }

    /**
     * Toggles completion locally. Completing a task stamps completedAt and
     * awards points by priority (the backend's points rule is replicated
     * deterministically: High 20 / Medium 10 / Low 5).
     */
    override suspend fun setCompleted(taskId: String, completed: Boolean): ApiResult<Task> {
        var updated: Task? = null
        store.mutateTasks { list ->
            val index = list.indexOfFirst { it.taskId == taskId }
            if (index >= 0) {
                val task = list[index]
                updated = task.copy(
                    completed = completed,
                    completedAt = if (completed) {
                        DateTimeUtils.formatIso(LocalDateTime.now())
                    } else {
                        null
                    },
                    points = when {
                        !completed -> 0
                        task.points > 0 -> task.points
                        else -> pointsFor(task.priority)
                    },
                )
                list[index] = updated!!
            }
        }
        return updated?.let { ApiResult.Success(it) }
            ?: ApiResult.Error("Task not found")
    }

    private fun pointsFor(priority: Priority): Int = when (priority) {
        Priority.LOW -> 5
        Priority.MEDIUM -> 10
        Priority.HIGH -> 20
    }
}
