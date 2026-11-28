package com.example.devboxai.firebase

import android.util.Log
import com.example.devboxai.DevBoxApp
import com.example.devboxai.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d("FCM", "New token: $token")
        if (!FirebaseBootstrap.isRunning()) return
        val app = applicationContext as DevBoxApp
        FirebaseInit.firestore?.collection(FirebaseInit.COLL_DEVICES)
            ?.document(app.deviceId)
            ?.set(mapOf(
                "token" to token,
                "model" to android.os.Build.MODEL,
                "manufacturer" to android.os.Build.MANUFACTURER,
                "android" to android.os.Build.VERSION.SDK_INT,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            ))
            ?.addOnFailureListener { e -> Log.w("FCM", "Device save fail (sign-in chahiye?): ${e.message}") }
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        if (!FirebaseBootstrap.isRunning()) return
        val data = msg.data
        val action = data["action"] ?: return
        val pkg = data["packageName"] ?: ""
        val text = data["text"] ?: ""
        val app = applicationContext as DevBoxApp

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val result = when (action) {
                "notify" -> {
                    NotificationHelper.show(this@FcmService, "📩 Admin", text.ifBlank { "message" })
                    "notified"
                }
                "block_app" -> {
                    app.db.blockedAppDao().insert(
                        com.example.devboxai.data.BlockedApp(packageName = pkg, appLabel = pkg, reason = text)
                    )
                    "blocked $pkg"
                }
                "kill_app" -> {
                    val ok = com.example.devboxai.security.RootProcessManager.killApp(
                        this@FcmService, pkg
                    )
                    if (ok) "kill sent" else "root needed"
                }
                else -> "queued via firestore"
            }
            NotificationHelper.show(this@FcmService, "FCM: $action", result)
        }
    }
}
