package com.example.devboxai.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.devboxai.NotificationHelper

class DailyWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        NotificationHelper.show(applicationContext, "DevBox AI", "Daily task chala.")
        return Result.success()
    }
}
