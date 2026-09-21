package com.studytrack.app.data.local

import androidx.room.TypeConverter
import com.studytrack.app.data.model.Priority
import com.studytrack.app.data.model.TaskType

/**
 * Room type adapters for the columns that are enums on the wire models.
 *
 * Everything is stored as the enum **name** (not the display label) so that a
 * label change in the UI can never orphan rows written by an older build.
 * Reads go through the lenient `fromRaw` helpers, so a value this build does
 * not recognize degrades to a sensible default instead of throwing inside a
 * query and blanking the screen.
 */
class Converters {

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus?): String = (value ?: SyncStatus.SYNCED).name

    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus = SyncStatus.fromRaw(value)

    @TypeConverter
    fun fromTaskType(value: TaskType?): String = (value ?: TaskType.STUDY).name

    @TypeConverter
    fun toTaskType(value: String?): TaskType = TaskType.fromRaw(value)

    @TypeConverter
    fun fromPriority(value: Priority?): String = (value ?: Priority.MEDIUM).name

    @TypeConverter
    fun toPriority(value: String?): Priority = Priority.fromRaw(value)
}
