package com.example.devboxai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.devboxai.productivity.ReminderScheduler
import com.example.devboxai.productivity.RoutineScheduler
import com.example.devboxai.worker.DailyWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        NotificationHelper.createChannel(context)
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<DailyWorker>().build())

        val app = context.applicationContext as DevBoxApp
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                app.db.reminderDao().all().first().forEach { r ->
                    if (r.active && r.triggerAt > System.currentTimeMillis())
                        ReminderScheduler.schedule(context, r)
                }
                app.db.routineDao().all().first().forEach { r ->
                    if (r.active) RoutineScheduler.schedule(context, r)
                }
            }
        }
    }
}
