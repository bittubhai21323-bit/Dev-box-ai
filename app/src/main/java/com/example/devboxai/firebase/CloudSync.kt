package com.example.devboxai.firebase

import android.content.Context
import android.util.Log
import com.example.devboxai.DevBoxApp
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * App ka data (tasks, journal, reminders, routines, blocked apps) server
 * (Firestore) par save karta hai, aur latest encrypted backup file bhi
 * Firebase Storage par bhejta hai agar Storage on hai.
 */
object CloudSync {
    private const val TAG = "CloudSync"
    const val COLL_DATA = "devbox_data"

    fun lastSyncTime(context: Context): Long =
        context.getSharedPreferences("devbox_update", Context.MODE_PRIVATE)
            .getLong("last_cloud_sync", 0L)

    suspend fun syncNow(context: Context): String {
        if (!FirebaseBootstrap.isRunning()) return "Firebase connect nahi hai — pehle Parse & Connect karo"
        val db = FirebaseInit.firestore ?: return "Firestore nahi mila"
        if (AdminAuth.currentUid == null) return "Admin sign-in nahi hai — pehle sign-up/sign-in karo"
        val app = context.applicationContext as DevBoxApp
        return try {
            val data = mapOf(
                "tasks" to app.db.taskDao().all().first().map {
                    mapOf("title" to it.title, "done" to it.done, "date" to it.date)
                },
                "journal" to app.db.journalDao().all().first().map {
                    mapOf("title" to it.title, "content" to it.content, "mood" to it.mood, "timestamp" to it.timestamp)
                },
                "reminders" to app.db.reminderDao().all().first().map {
                    mapOf("title" to it.title, "triggerAt" to it.triggerAt, "active" to it.active)
                },
                "routines" to app.db.routineDao().all().first().map {
                    mapOf("name" to it.name, "description" to it.description, "hour" to it.hour,
                        "minute" to it.minute, "daysOfWeek" to it.daysOfWeek, "active" to it.active)
                },
                "blockedApps" to app.db.blockedAppDao().all().first().map {
                    mapOf("packageName" to it.packageName, "appLabel" to it.appLabel, "reason" to it.reason)
                },
                "model" to android.os.Build.MODEL,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection(COLL_DATA).document(app.deviceId).set(data).await()

            // Latest encrypted backup file bhi server par (Storage on hai to; nahi to chup-chaap skip)
            var fileMsg = ""
            try {
                val latest = File(context.filesDir, "backups").listFiles()
                    ?.filter { it.name.endsWith(".dbk") }?.maxByOrNull { it.lastModified() }
                val fbApp = FirebaseBootstrap.app()
                if (latest != null && fbApp != null) {
                    val ref = com.google.firebase.storage.FirebaseStorage.getInstance(fbApp)
                        .reference.child("backups/${app.deviceId}/${latest.name}")
                    ref.putFile(android.net.Uri.fromFile(latest)).await()
                    fileMsg = " + backup file"
                }
            } catch (e: Exception) {
                Log.w(TAG, "Storage upload skip: ${e.message}")
            }

            context.getSharedPreferences("devbox_update", Context.MODE_PRIVATE)
                .edit().putLong("last_cloud_sync", System.currentTimeMillis()).apply()
            "✓ Data server par save ho gaya$fileMsg"
        } catch (e: Exception) {
            Log.w(TAG, "sync fail", e)
            "✗ Server save fail: ${e.message}"
        }
    }
}
