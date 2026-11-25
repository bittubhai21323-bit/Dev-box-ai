package com.example.devboxai.crash

import android.content.Context
import android.util.Log
import com.example.devboxai.DevBoxApp
import com.example.devboxai.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(
    private val context: Context,
    private val previous: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            saveCrash(throwable)
            CoroutineScope(Dispatchers.IO).launch {
                try { BugReporter(context).uploadPending() } catch (_: Exception) {}
            }
            try {
                NotificationHelper.show(context, "⚠️ DevBox crashed",
                    "Bug report saved. Next launch will auto-heal.")
            } catch (_: Exception) {}
        } catch (_: Exception) {}
        finally { previous?.uncaughtException(thread, throwable) }
    }

    private fun saveCrash(t: Throwable) {
        val dir = File(context.filesDir, "crashes").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash_$stamp.txt")
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        val info = buildString {
            appendLine("timestamp=${System.currentTimeMillis()}")
            appendLine("date=$stamp")
            appendLine("version=${appVersion()}")
            appendLine("android=${android.os.Build.VERSION.SDK_INT}")
            appendLine("device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("deviceId=${(context.applicationContext as? DevBoxApp)?.deviceId}")
            appendLine()
            appendLine(sw.toString())
        }
        file.writeText(info)
        Log.e("CrashHandler", "Crash saved: ${file.name}")
    }

    private fun appVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }
}
