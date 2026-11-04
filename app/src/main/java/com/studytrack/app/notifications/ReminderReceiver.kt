package com.studytrack.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.*

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReminderEngine.reconcile(context.applicationContext, intent.action == "com.studytrack.REMINDER")
            } catch (e: Exception) {
                Log.e("StudyTrackReminders", "Unable to restore reminders", e)
            } finally { pending.finish() }
        }
    }
}
