package com.example.devboxai.productivity

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.devboxai.NotificationHelper
import com.example.devboxai.data.Reminder

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Reminder"
        NotificationHelper.show(context, "Reminder", title)
    }
}

object ReminderScheduler {
    fun schedule(context: Context, reminder: Reminder) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, reminder.id.toInt(),
            Intent(context, ReminderReceiver::class.java).putExtra("title", reminder.title),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pi)
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pi)
        }
    }

    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, id.toInt(), Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }
}
