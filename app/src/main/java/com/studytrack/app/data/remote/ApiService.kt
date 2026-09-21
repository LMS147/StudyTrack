package com.studytrack.app.data.remote

import com.studytrack.app.data.model.CompleteTaskPayload
import com.studytrack.app.data.model.Progress
import com.studytrack.app.data.model.StudySessionPayload
import com.studytrack.app.data.model.Subject
import com.studytrack.app.data.model.SubjectPayload
import com.studytrack.app.data.model.Task
import com.studytrack.app.data.model.TaskAssistanceRequest
import com.studytrack.app.data.model.TaskAssistanceResponse
import com.studytrack.app.data.model.TaskPayload
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Retrofit interface for the StudyTrack FastAPI backend.
 * Base URL: https://api.studytrack.example.com/ (placeholder — see RetrofitClient).
 */
interface ApiService {

    // ----------------------------------------------------------------- Subjects

    @POST("api/subjects")
    suspend fun createSubject(@Body payload: SubjectPayload): Subject

    @GET("api/subjects")
    suspend fun getSubjects(): List<Subject>

    @GET("api/subjects/{id}")
    suspend fun getSubject(@Path("id") id: String): Subject

    @PUT("api/subjects/{id}")
    suspend fun updateSubject(@Path("id") id: String, @Body payload: SubjectPayload): Subject

    @DELETE("api/subjects/{id}")
    suspend fun deleteSubject(@Path("id") id: String): Response<ResponseBody>

    // -------------------------------------------------------------------- Tasks

    @POST("api/tasks")
    suspend fun createTask(@Body payload: TaskPayload): Task

    @GET("api/tasks")
    suspend fun getTasks(): List<Task>

    @GET("api/tasks/{id}")
    suspend fun getTask(@Path("id") id: String): Task

    @PUT("api/tasks/{id}")
    suspend fun updateTask(@Path("id") id: String, @Body payload: TaskPayload): Task

    @DELETE("api/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String): Response<ResponseBody>

    @PATCH("api/tasks/{id}/complete")
    suspend fun setTaskCompleted(
        @Path("id") id: String,
        @Body payload: CompleteTaskPayload = CompleteTaskPayload()
    ): Task

    // -------------------------------------------------------- Calendar & progress

    /**
     * Returns all dated tasks; the client groups them by dueDate for the
     * month view. No separate "calendar entry" type — a correctly dated task
     * automatically appears on the right day.
     */
    @GET("api/calendar")
    suspend fun getCalendarTasks(): List<Task>

    @GET("api/progress")
    suspend fun getProgress(): Progress

    // ------------------------------------------------------------ Study sessions

    @POST("api/study-sessions")
    suspend fun logStudySession(@Body payload: StudySessionPayload): Response<ResponseBody>

    // ---------------------------------------------------------------------- AI

    @POST("api/ai/task-assistance")
    suspend fun taskAssistance(@Body request: TaskAssistanceRequest): TaskAssistanceResponse
}
