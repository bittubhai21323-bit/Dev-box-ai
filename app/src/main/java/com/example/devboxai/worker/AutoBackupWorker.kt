package com.example.devboxai.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.devboxai.NotificationHelper
import com.example.devboxai.SettingsStore
import com.example.devboxai.backup.BackupManager
import kotlinx.coroutines.runBlocking

class AutoBackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val store = SettingsStore(applicationContext)
            val pwd = store.backupPassword.ifBlank { "devbox-internal" }
            if (store.backupPassword.isBlank()) store.backupPassword = pwd
            val r = runBlocking { BackupManager(applicationContext).createBackup(pwd) }
            if (r.ok) {
                // Backup ke saath data server par bhi save karo (best effort)
                val syncMsg = runBlocking { com.example.devboxai.firebase.CloudSync.syncNow(applicationContext) }
                NotificationHelper.show(applicationContext, "Auto-backup", r.message + " | " + syncMsg)
                Result.success()
            } else Result.retry()
        } catch (_: Exception) { Result.retry() }
    }
}
