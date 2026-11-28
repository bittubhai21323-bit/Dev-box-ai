package com.example.devboxai

import android.app.Application
import android.provider.Settings
import android.util.Log
import com.example.devboxai.backup.BackupManager
import com.example.devboxai.crash.BugReporter
import com.example.devboxai.crash.CrashHandler
import com.example.devboxai.data.DevBoxDatabase
import com.example.devboxai.firebase.FirebaseBootstrap
import com.example.devboxai.firebase.FirebaseConfigStore
import com.example.devboxai.firebase.FirebaseInit
import com.example.devboxai.firebase.RemoteCommandHandler
import com.example.devboxai.firebase.RemoteConfigManager
import com.example.devboxai.worker.AutoBackupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DevBoxApp : Application() {

    val db: DevBoxDatabase by lazy { DevBoxDatabase.get(this) }

    val deviceId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-${System.currentTimeMillis()}"
    }

    val remoteConfig by lazy { RemoteConfigManager() }
    val remoteCommands by lazy { RemoteCommandHandler(this, deviceId) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var firebaseReady: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler(
            CrashHandler(this, Thread.getDefaultUncaughtExceptionHandler())
        )

        appScope.launch {
            try { BugReporter(this@DevBoxApp).uploadPending() } catch (_: Throwable) {}
        }

        try {
            val backupRequest = androidx.work.PeriodicWorkRequestBuilder<AutoBackupWorker>(
                12, java.util.concurrent.TimeUnit.HOURS
            ).build()
            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "devbox_auto_backup",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                backupRequest
            )
        } catch (e: Exception) {
            Log.w("DevBoxApp", "WorkManager setup failed", e)
        }

        val store = try {
            FirebaseConfigStore(this)
        } catch (e: Exception) {
            Log.w("DevBoxApp", "FirebaseConfigStore failed — local only", e)
            return
        }
        if (!store.isConfigured()) {
            Log.i("DevBoxApp", "Firebase not configured — local only")
            return
        }

        firebaseReady = FirebaseBootstrap.init(this)
        if (!firebaseReady) {
            Log.w("DevBoxApp", "Firebase bootstrap failed")
            return
        }

        FirebaseInit.registerFcmToken { token ->
            if (token == null) return@registerFcmToken
            FirebaseInit.firestore
                ?.collection(FirebaseInit.COLL_DEVICES)
                ?.document(deviceId)
                ?.set(
                    mapOf(
                        "token" to token,
                        "model" to android.os.Build.MODEL,
                        "manufacturer" to android.os.Build.MANUFACTURER,
                        "android" to android.os.Build.VERSION.SDK_INT,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                )
        }

        appScope.launch { remoteConfig.init() }
        remoteCommands.startListening()
    }

    fun restartFirebase() {
        firebaseReady = FirebaseBootstrap.init(this)
        if (firebaseReady) {
            appScope.launch { remoteConfig.init() }
            remoteCommands.startListening()
        }
    }
}
