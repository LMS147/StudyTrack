package com.studytrack.app.notifications

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.studytrack.app.MainActivity
import com.studytrack.app.R
import com.studytrack.app.ServiceLocator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One next-alarm slot; every delivery re-reads current account data before posting. */
object ReminderEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private const val CHANNEL = "study_reminders"
    private lateinit var appContext: Context
    private var started = false
    private var observedUid: String? = null
    private lateinit var preferences: android.content.SharedPreferences
    private val settingsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refresh()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        appContext = app
        preferences = app.getSharedPreferences("studytrack_settings", Context.MODE_PRIVATE)
        preferences.registerOnSharedPreferenceChangeListener(settingsListener)
        scope.launch {
            ServiceLocator.currentAccount.activeUid.collectLatest { uid ->
                if (observedUid != uid) {
                    NotificationManagerCompat.from(app).cancelAll()
                    observedUid = uid
                }
                reconcile(app, false)
                if (uid != null) combine(
                    ServiceLocator.database.taskDao().observeForOwner(uid),
                    ServiceLocator.database.timetableDao().observe(uid)
                ) { _, _ -> Unit }.collect { reconcile(app, false) }
            }
        }
    }

    fun refresh() { scope.launch { reconcile(appContext, false) } }

    suspend fun reconcile(context: Context, deliver: Boolean) = mutex.withLock {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ReminderReceiver::class.java).setAction("com.studytrack.REMINDER")
        val alarm = PendingIntent.getBroadcast(context, 481, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.cancel(alarm)
        val uid = ServiceLocator.currentAccount.uidOrNull()
        val authUid = FirebaseAuth.getInstance().currentUser?.uid
        val settings = ServiceLocator.settingsRepository
        if (uid == null || uid != authUid || !settings.pushNotificationsEnabled || !settings.taskRemindersEnabled) {
            NotificationManagerCompat.from(context).cancelAll()
            return@withLock
        }
        val now = System.currentTimeMillis()
        val candidates = ReminderPlan.build(ServiceLocator.database.taskDao().getAllForOwner(uid),
            ServiceLocator.database.timetableDao().all(uid), now)
        val sent = context.getSharedPreferences("delivered_reminders", Context.MODE_PRIVATE)
        val delivered = sent.getStringSet("keys", emptySet()).orEmpty().toMutableSet()
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Task and class reminders", NotificationManager.IMPORTANCE_HIGH))
        val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (deliver && allowed && manager.areNotificationsEnabled()) {
            for (item in candidates.filter { it.at <= now && "$uid:${it.key}" !in delivered }) {
                // Sign-out/switch may have happened during the suspending database reads.
                if (ServiceLocator.currentAccount.uidOrNull() != uid || FirebaseAuth.getInstance().currentUser?.uid != uid) return@withLock
                val open = PendingIntent.getActivity(context, 0,
                    Intent(context, MainActivity::class.java).setData(Uri.parse("studytrack://reminder"))
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                manager.notify("study_reminders", item.key.hashCode(), NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notifications).setContentTitle(item.title)
                    .setContentText(item.message).setContentIntent(open).setAutoCancel(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPriority(NotificationCompat.PRIORITY_HIGH).build())
                delivered += "$uid:${item.key}"
            }
            // Retain only keys still present in the current rolling plan.
            val valid = candidates.map { "$uid:${it.key}" }.toSet()
            sent.edit().putStringSet("keys", delivered.intersect(valid)).apply()
        }
        if (!allowed || !manager.areNotificationsEnabled()) return@withLock
        if (ServiceLocator.currentAccount.uidOrNull() != uid || FirebaseAuth.getInstance().currentUser?.uid != uid) return@withLock
        val next = candidates.firstOrNull { "$uid:${it.key}" !in delivered } ?: return@withLock
        val at = maxOf(next.at, now + 1000)
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            try { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm) }
            catch (_: SecurityException) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm) }
        } else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm)
    }
}
