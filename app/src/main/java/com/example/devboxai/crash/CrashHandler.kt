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
        // Also copy to external files dir so the user can share the crash report
        try {
            val extDir = File(context.getExternalFilesDir(null), "crashes").apply { mkdirs() }
            File(extDir, file.name).writeText(info)
        } catch (_: Exception) {}
        // And to public Downloads/DevBoxCrashes (readable from Termux / Files app)
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/DevBoxCrashes")
                }
                val uri = context.contentResolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                )
                uri?.let { u ->
                    context.contentResolver.openOutputStream(u)?.use { it.write(info.toByteArray()) }
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    ), "DevBoxCrashes"
                ).apply { mkdirs() }
                File(dir, file.name).writeText(info)
            }
        } catch (_: Exception) {}
        Log.e("CrashHandler", "Crash saved: ${file.name}")
    }

    private fun appVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }
}
