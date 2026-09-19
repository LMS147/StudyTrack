package com.studytrack.app.data.repository

import android.content.Context
import com.studytrack.app.data.model.Subject
import com.studytrack.app.data.model.Task
import com.studytrack.app.data.remote.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.KSerializer

/**
 * On-device data store backing the local-mode repositories: subjects and
 * tasks persisted as JSON in SharedPreferences ("studytrack_local_store").
 *
 * Chosen over Room deliberately: zero new dependencies, trivially inspectable,
 * and perfectly adequate for a single-user study planner's data volume. The
 * repository interfaces are the seam where a Room-backed implementation
 * could slot in later if the data model grows.
 */
class LocalStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("studytrack_local_store", Context.MODE_PRIVATE)
    private val json = RetrofitClient.json

    private val _subjects = MutableStateFlow(loadList(KEY_SUBJECTS, Subject.serializer()))
    val subjects: StateFlow<List<Subject>> = _subjects.asStateFlow()

    private val _tasks = MutableStateFlow(loadList(KEY_TASKS, Task.serializer()))
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    /** Atomically mutates the subject list: emit to observers + persist. */
    fun mutateSubjects(transform: (MutableList<Subject>) -> Unit) {
        val next = _subjects.value.toMutableList().also(transform)
        _subjects.value = next
        save(KEY_SUBJECTS, next, Subject.serializer())
    }

    /** Atomically mutates the task list: emit to observers + persist. */
    fun mutateTasks(transform: (MutableList<Task>) -> Unit) {
        val next = _tasks.value.toMutableList().also(transform)
        _tasks.value = next
        save(KEY_TASKS, next, Task.serializer())
    }

    // ------------------------------------------------------------- internals

    private fun <T> loadList(key: String, serializer: KSerializer<T>): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            json.decodeFromString(ListSerializer(serializer), raw)
        } catch (e: Exception) {
            // Corrupted or hand-edited payload — start clean rather than crash.
            emptyList()
        }
    }

    private fun <T> save(key: String, value: List<T>, serializer: KSerializer<T>) {
        val raw = try {
            json.encodeToString(ListSerializer(serializer), value)
        } catch (e: Exception) {
            return
        }
        prefs.edit().putString(key, raw).apply()
    }

    private companion object {
        const val KEY_SUBJECTS = "subjects_json"
        const val KEY_TASKS = "tasks_json"
    }
}
