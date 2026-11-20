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
            check(reminderInstant(date, time) != null)
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
            manager.update(task.copy(completed = true))
            check(!prefs.contains(task.id))
            manager.update(task)
            manager.clear()
            check(!prefs.contains(task.id))
            manager.update(task)
            check(manager.snooze(task.id, System.currentTimeMillis() + 5000))
            Log.i("AgendaReminderTest", "PASS")
        } catch (error: Throwable) {
            Log.e("AgendaReminderTest", "FAIL", error)
        }
    }
}
