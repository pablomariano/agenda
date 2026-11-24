package com.pablomariano.agenda

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ReminderTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val manager = TaskReminders(context)
            if (intent.hasExtra("expectedNotifications")) {
                check(manager.notificationsAllowed() == intent.getBooleanExtra("expectedNotifications", false))
                check(manager.exactAllowed() == intent.getBooleanExtra("expectedExact", false))
                Log.i("AgendaReminderTest", "PERMISSIONS PASS")
                return
            }
            manager.clear()
            val now = LocalDateTime.now(ZoneId.of("America/Santiago"))
            val future = now.plusHours(2)
            val date = future.toLocalDate().toString()
            val time = future.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            val task = AgendaTask("e5d05b55-ad37-4b4d-a638-72ef9e27ebda", "Prueba", "project", "Proyecto", AgendaLevel.MEDIUM, date, false, time)
            val tasks = AgendaSnapshot(emptyList(), listOf(task), emptyList(), emptyList())
            check(reminderInstant("2026-09-06", "00:30") == null)
            check(reminderInstant("2026-04-04", "23:30") == null)
            val nearMidnight = reminderInstant("2026-11-23", "23:30")!!
            val nextDay = java.time.Instant.ofEpochMilli(nearMidnight).plusSeconds(3_600).atZone(ZoneId.of("America/Santiago"))
            check(nextDay.toLocalDate().toString() == "2026-11-24" && nextDay.hour == 0 && nextDay.minute == 30)
            check(reminderInstant(date, time) != null)
            val overdue = task.copy(plannedOn = now.minusDays(4).toLocalDate().toString(), carriedOver = true)
            manager.update(overdue)
            val oldRecord = org.json.JSONObject(context.getSharedPreferences("task_reminders", Context.MODE_PRIVATE).getString(task.id, null)!!)
            check(oldRecord.getLong("due") < System.currentTimeMillis())
            manager.reconcile(tasks)
            val prefs = context.getSharedPreferences("task_reminders", Context.MODE_PRIVATE)
            fun record() = org.json.JSONObject(prefs.getString(task.id, null)!!)
            check(record().getLong("due") > System.currentTimeMillis())
            manager.setEnabled(task.id, false)
            check(!manager.enabled(task.id))
            manager.restore()
            check(!manager.enabled(task.id))
            manager.setEnabled(task.id, true)
            val postponed = System.currentTimeMillis() + 3_600_000
            check(manager.snooze(task.id, postponed))
            manager.reconcile(tasks)
            check(record().getLong("due") == postponed)
            check(!manager.snooze(task.id, System.currentTimeMillis() - 1000))
            val notifications = context.getSystemService(android.app.NotificationManager::class.java)
            listOf(10, 20).forEachIndexed { index, minutes ->
                val ready = record().put("due", System.currentTimeMillis() - 1_000).put("fired", false)
                prefs.edit().putString(task.id, ready.toString()).commit()
                manager.fire(task.id)
                var notification = notifications.activeNotifications.firstOrNull { it.tag == task.id }?.notification
                repeat(100) {
                    if (notification == null) {
                        android.os.SystemClock.sleep(20)
                        notification = notifications.activeNotifications.firstOrNull { item -> item.tag == task.id }?.notification
                    }
                }
                manager.fire(task.id)
                check(notifications.activeNotifications.count { it.tag == task.id } == 1)
                val before = System.currentTimeMillis()
                check(requireNotNull(notification).actions[index].title.toString() == "$minutes minutos")
                val token = context.getSharedPreferences("reminder_intents", Context.MODE_PRIVATE).getString("token", null)
                TaskReminderReceiver().onReceive(context, Intent().setAction("snooze_$minutes")
                    .setData(android.net.Uri.parse("agenda://reminder/${task.id}"))
                    .putExtra("reminder_token", token))
                val after = System.currentTimeMillis()
                check(record().getLong("due") in (before + minutes * 60_000L)..(after + minutes * 60_000L))
                check(record().getString("plan") == "${date}T$time")
                manager.reconcile(tasks)
                check(record().getLong("due") in (before + minutes * 60_000L)..(after + minutes * 60_000L))
                val scheduled = record().getLong("due")
                TaskReminderReceiver().onReceive(context, Intent().setAction("snooze_10")
                    .setData(android.net.Uri.parse("agenda://reminder/${task.id}"))
                    .putExtra("reminder_token", token))
                check(record().getLong("due") == scheduled)
            }
            manager.update(task.copy(completed = true))
            check(!prefs.contains(task.id))
            val aiPrefs = context.getSharedPreferences("ai_reminders", Context.MODE_PRIVATE)
            val ai = AiRecovery("9af91d12-a342-4891-8d53-86d22c6f374e", "codex", "short",
                java.time.Instant.now().plusSeconds(18_000).toString(), "continue", "gemini", task.id, false,
                java.time.Instant.now().toString(), 1)
            val weekly = ai.copy(id = "8a2390d4-999a-4d78-a144-7c6e60676720", window = "weekly", taskId = null)
            manager.reconcileAi(listOf(ai, weekly))
            check(aiPrefs.all.size == 2)
            manager.update(task.copy(completed = true))
            check(aiPrefs.all.size == 2)
            val revised = ai.copy(expectedAt = java.time.Instant.parse(ai.expectedAt).plusSeconds(3_600).toString())
            val weeklyDue = org.json.JSONObject(aiPrefs.getString(weekly.id, null)!!).getLong("due")
            manager.reconcileAi(listOf(revised, weekly))
            manager.reconcileAi(listOf(revised, weekly))
            check(org.json.JSONObject(aiPrefs.getString(ai.id, null)!!).getLong("due") == java.time.Instant.parse(revised.expectedAt).toEpochMilli())
            check(org.json.JSONObject(aiPrefs.getString(weekly.id, null)!!).getLong("due") == weeklyDue)
            manager.restore()
            check(org.json.JSONObject(aiPrefs.getString(weekly.id, null)!!).getLong("due") == weeklyDue)
            manager.reconcileAi(listOf(revised.copy(cancelled = true), weekly))
            check(!aiPrefs.contains(ai.id) && aiPrefs.contains(weekly.id))
            manager.update(task)
            manager.clear()
            check(!prefs.contains(task.id))
            check(aiPrefs.all.isEmpty())
            manager.update(task)
            check(manager.snooze(task.id, System.currentTimeMillis() + 5000))
            Log.i("AgendaReminderTest", "PASS")
        } catch (error: Throwable) {
            Log.e("AgendaReminderTest", "FAIL", error)
        }
    }
}
