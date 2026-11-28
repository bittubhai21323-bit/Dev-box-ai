package com.example.devboxai.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

object TermuxHelper {

    private const val TERMUX_PKG = "com.termux"
    private const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
    private const val BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"
    private const val HOME = "/data/data/com.termux/files/home"

    const val RUN_PERMISSION = "com.termux.permission.RUN_COMMAND"

    /** Last failure reason, so the UI can show WHY a command failed. */
    var lastError: String? = null
        private set

    fun hasRunPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, RUN_PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun isTermuxInstalled(context: Context): Boolean =
        try { context.packageManager.getPackageInfo(TERMUX_PKG, 0); true }
        catch (_: Exception) { false }

    fun openTermux(context: Context): Boolean = try {
        val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PKG) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent); true
    } catch (_: Exception) { false }

    fun run(context: Context, command: String): Boolean = try {
        val intent = baseIntent().apply {
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
        }
        context.startService(intent); lastError = null; true
    } catch (e: Exception) { Log.e("TermuxHelper", "run", e); lastError = "${e.javaClass.simpleName}: ${e.message}"; false }

    fun runWithResult(
        context: Context,
        command: String,
        requestId: Int = (System.currentTimeMillis() % 100_000).toInt()
    ): Boolean = try {
        val pending = PendingIntent.getBroadcast(
            context, requestId,
            Intent(TermuxResultReceiver.ACTION)
                .setPackage(context.packageName)
                .putExtra(TermuxResultReceiver.EXTRA_REQUEST_ID, requestId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val intent = baseIntent().apply {
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pending)
        }
        context.startService(intent); lastError = null; true
    } catch (e: Exception) { Log.e("TermuxHelper", "runWithResult", e); lastError = "${e.javaClass.simpleName}: ${e.message}"; false }

    suspend fun runAndWait(
        context: Context,
        command: String,
        timeoutMs: Long = 180_000
    ): TermuxResultBus.Result? {
        val id = Random.nextInt(100_000, 900_000)
        val job: Job = CoroutineScope(Dispatchers.IO).launch { }
        job.cancel()

        var captured: TermuxResultBus.Result? = null
        val collectorJob = CoroutineScope(Dispatchers.IO).launch {
            TermuxResultBus.results.collect { r -> if (r.requestId == id) captured = r }
        }
        if (!runWithResult(context, command, id)) {
            collectorJob.cancel(); return null
        }
        val result = withTimeoutOrNull(timeoutMs) {
            while (captured == null) { kotlinx.coroutines.delay(100) }
            captured
        }
        collectorJob.cancel()
        return result
    }

    fun installPackages(context: Context, packages: List<String>): Boolean {
        val list = packages.joinToString(" ")
        return run(context, "export DEBIAN_FRONTEND=noninteractive; yes | pkg update -y; pkg install -y $list")
    }

    private fun baseIntent(): Intent = Intent().apply {
        setClassName(TERMUX_PKG, TERMUX_SERVICE)
        action = "com.termux.RUN_COMMAND"
        putExtra("com.termux.RUN_COMMAND_PATH", BASH_PATH)
        putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
        putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
    }
}
