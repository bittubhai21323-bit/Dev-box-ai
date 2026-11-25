package com.example.devboxai.selfheal

import android.content.Context
import android.util.Log
import com.example.devboxai.DevBoxApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HealthChecker(private val context: Context) {

    private val app = context.applicationContext as DevBoxApp

    data class Issue(val name: String, val detail: String, val autoFixable: Boolean)
    data class Report(val healthy: Boolean, val issues: List<Issue>, val fixes: List<String>)

    suspend fun check(): Report = withContext(Dispatchers.IO) {
        val issues = mutableListOf<Issue>()
        val fixes = mutableListOf<String>()

        val dbOk = try {
            val result = app.db.openHelper.writableDatabase
                .query("PRAGMA integrity_check").use { c ->
                    if (c.moveToFirst()) c.getString(0) else "no-result"
                }
            result == "ok"
        } catch (e: Exception) { Log.e("Health", "db check failed", e); false }

        if (!dbOk) {
            issues.add(Issue("Database corrupted", "integrity_check failed", true))
            val lastBackup = com.example.devboxai.backup.BackupManager(context)
                .listBackups().firstOrNull()
            if (lastBackup != null) fixes.add("Restore from ${lastBackup.name} (password needed).")
            else fixes.add("No backup available. Use reset option below.")
        }

        val cache = context.cacheDir
        val cacheSize = cache.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        if (cacheSize > 50L * 1024 * 1024) {
            issues.add(Issue("Cache large", "${cacheSize / 1024 / 1024} MB — auto-cleanable", true))
            cache.listFiles()?.forEach { it.deleteRecursively() }
            fixes.add("Cache cleared (${cacheSize / 1024 / 1024}MB freed)")
        }

        val crashDir = java.io.File(context.filesDir, "crashes")
        val crashCount = crashDir.listFiles()?.size ?: 0
        if (crashCount > 0) {
            issues.add(Issue("Pending crash reports", "$crashCount unresolved", true))
            fixes.add("$crashCount crash logs — will upload on next network")
        }

        Report(issues.isEmpty(), issues, fixes)
    }

    suspend fun resetDatabase(): Boolean = withContext(Dispatchers.IO) {
        try {
            val dbPath = context.getDatabasePath("devbox.db")
            try { app.db.close() } catch (_: Exception) {}
            dbPath.delete()
            java.io.File(dbPath.path + "-wal").delete()
            java.io.File(dbPath.path + "-shm").delete()
            true
        } catch (_: Exception) { false }
    }
}
