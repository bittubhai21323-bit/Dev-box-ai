package com.example.devboxai.productivity

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.devboxai.data.Routine
import java.util.Calendar

object RoutineScheduler {
    fun schedule(context: Context, routine: Routine) {
        if (!routine.active) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, routine.hour)
            set(Calendar.MINUTE, routine.minute)
            set(Calendar.SECOND, 0)
        }
        if (cal.timeInMillis < System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, routine.id.toInt(),
            Intent(context, ReminderReceiver::class.java).putExtra("title", "Routine: ${routine.name}"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis,
            AlarmManager.INTERVAL_DAY, pi)
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
