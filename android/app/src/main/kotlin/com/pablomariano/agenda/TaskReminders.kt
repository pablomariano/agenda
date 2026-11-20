package com.pablomariano.agenda

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.json.JSONObject

private val reminderZone = ZoneId.of("America/Santiago")

internal fun reminderInstant(date: String, time: String): Long? {
    val local = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
    val offsets = reminderZone.rules.getValidOffsets(local)
    return if (offsets.size == 1) local.toInstant(offsets[0]).toEpochMilli() else null
}

internal class TaskReminders(private val context: Context) {
    private val prefs = context.getSharedPreferences("task_reminders", Context.MODE_PRIVATE)
    private val tokenPrefs = context.getSharedPreferences("reminder_intents", Context.MODE_PRIVATE)
    private val token: String = tokenPrefs.getString("token", null) ?: UUID.randomUUID().toString().also {
        tokenPrefs.edit().putString("token", it).commit()
    }
    fun accepts(intent: Intent): Boolean = intent.getStringExtra("reminder_token") == token
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    fun notificationsAllowed() = notifications.areNotificationsEnabled() &&
        notifications.getNotificationChannel("tasks")?.importance != NotificationManager.IMPORTANCE_NONE &&
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    fun exactAllowed() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    private fun alarmIntent(id: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, TaskReminderReceiver::class.java).setData(Uri.parse("agenda://reminder/$id")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun schedule(id: String, record: JSONObject) {
        val whenMs = record.optLong("due")
        alarms.cancel(alarmIntent(id))
        if (!record.optBoolean("enabled", true) || record.optBoolean("fired") || whenMs <= System.currentTimeMillis()) return
        try {
            if (exactAllowed()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, alarmIntent(id))
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, alarmIntent(id))
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, alarmIntent(id))
        }
    }

    fun reconcile(snapshot: AgendaSnapshot) {
        val tasks = snapshot.tasks + snapshot.futureTasks
        val ids = tasks.map { it.id }.toSet()
        prefs.all.keys.filter { it !in ids }.forEach(::remove)
        tasks.forEach(::update)
    }

    fun update(task: AgendaTask) {
        val time = task.plannedTime
        if (task.completed || time == null) { remove(task.id); return }
        val plan = "${task.plannedOn}T$time"
        val old = read(task.id)
        val record = if (old != null && old.optString("plan") == plan) old else JSONObject()
            .put("plan", plan).put("due", reminderInstant(task.plannedOn, time) ?: 0L)
            .put("enabled", old?.optBoolean("enabled", true) ?: true).put("fired", false)
        record.put("title", task.title)
        save(task.id, record)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val record = read(id) ?: return
        record.put("enabled", enabled)
        save(id, record)
        if (!enabled) notifications.cancel(id, 0)
    }

    fun enabled(id: String): Boolean = read(id)?.optBoolean("enabled", true) ?: true

    fun snooze(id: String, due: Long): Boolean {
        val record = read(id) ?: return false
        if (due <= System.currentTimeMillis()) return false
        record.put("due", due).put("fired", false).put("enabled", true)
        save(id, record)
        notifications.cancel(id, 0)
        return true
    }

    fun remove(id: String) {
        alarms.cancel(alarmIntent(id))
        notifications.cancel(id, 0)
        prefs.edit().remove(id).apply()
    }

    fun clear() { prefs.all.keys.forEach(::remove) }
    fun restore() { prefs.all.keys.forEach { id -> read(id)?.let { schedule(id, it) } } }
    private fun read(id: String): JSONObject? = prefs.getString(id, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    private fun save(id: String, record: JSONObject) { prefs.edit().putString(id, record.toString()).commit(); schedule(id, record) }

    fun fire(id: String) {
        val record = read(id) ?: return
        if (!record.optBoolean("enabled", true) || record.optBoolean("fired") || record.optLong("due") > System.currentTimeMillis() + 1_000) return
        record.put("fired", true)
        prefs.edit().putString(id, record.toString()).commit()
        if (!notificationsAllowed()) return
        notifications.createNotificationChannel(NotificationChannel("tasks", "Recordatorios de tareas", NotificationManager.IMPORTANCE_DEFAULT))
        fun activity(action: String) = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).setAction(action).setData(Uri.parse("agenda://task/$id"))
                .putExtra("reminder_token", token)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, "tasks")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Recordatorio de tarea")
            .setContentText(record.optString("title"))
            .setContentIntent(activity("open"))
            .addAction(Notification.Action.Builder(null, "Posponer", activity("snooze")).build())
            .setAutoCancel(true).build()
        notifications.notify(id, 0, notification)
    }
}

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminders = TaskReminders(context)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> reminders.restore()
            else -> intent.data?.lastPathSegment?.let(reminders::fire)
        }
    }
}
