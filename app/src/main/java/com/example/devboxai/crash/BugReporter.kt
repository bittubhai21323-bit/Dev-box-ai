package com.example.devboxai.crash

import android.content.Context
import android.util.Log
import com.example.devboxai.DevBoxApp
import com.example.devboxai.firebase.FirebaseBootstrap
import com.example.devboxai.firebase.FirebaseInit
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import java.io.File

class BugReporter(private val context: Context) {

    private val app = context.applicationContext as DevBoxApp

    suspend fun uploadPending(): Int {
        if (!FirebaseBootstrap.isRunning()) return 0
        val db = FirebaseInit.firestore ?: return 0
        val files = File(context.filesDir, "crashes").listFiles() ?: return 0
        var uploaded = 0

        for (f in files) {
            try {
                val content = f.readText()
                val lines = content.lines()
                val meta = lines.takeWhile { it.contains("=") }
                    .associate { it.substringBefore("=") to it.substringAfter("=") }
                val stack = content.substringAfter("\n\n", "")

                db.collection("devbox_crashes").add(
                    mapOf(
                        "deviceId" to app.deviceId,
                        "version" to (meta["version"] ?: "?"),
                        "android" to (meta["android"] ?: "?"),
                        "device" to (meta["device"] ?: "?"),
                        "timestamp" to FieldValue.serverTimestamp(),
                        "stack" to stack.take(8000),
                        "filename" to f.name
                    )
                ).await()
                f.delete()
                uploaded++
            } catch (e: Exception) {
                Log.w("BugReporter", "upload failed: ${f.name}", e)
            }
        }
        return uploaded
    }

    fun localCrashes(): List<File> =
        File(context.filesDir, "crashes").listFiles()?.toList() ?: emptyList()

    fun clearLocal() {
        File(context.filesDir, "crashes").listFiles()?.forEach { it.delete() }
    }
}
