package com.example.devboxai.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseBootstrap {

    private const val TAG = "FirebaseBootstrap"
    private const val APP_NAME = "devbox_main"

    @Volatile private var initialized = false

    fun isRunning(): Boolean = initialized

    fun init(context: Context): Boolean {
        val creds = FirebaseConfigStore(context).load()
        if (creds == null || !creds.isValid()) {
            Log.d(TAG, "No credentials saved — Firebase disabled")
            initialized = false; return false
        }
        return try {
            FirebaseApp.getApps(context).forEach {
                if (it.name == APP_NAME) try { it.delete() } catch (_: Exception) {}
            }
            val options = FirebaseOptions.Builder()
                .setProjectId(creds.projectId)
                .setApplicationId(creds.applicationId)
                .setApiKey(creds.apiKey)
                .setGcmSenderId(creds.gcmSenderId)
                .setStorageBucket(creds.storageBucket)
                .apply { if (creds.databaseUrl.isNotBlank()) setDatabaseUrl(creds.databaseUrl) }
                .build()
            FirebaseApp.initializeApp(context, options, APP_NAME)
            initialized = true
            Log.i(TAG, "Firebase initialized ✓ project=${creds.projectId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init failed", e)
            initialized = false; false
        }
    }

    fun app(): FirebaseApp? {
        return try {
            FirebaseApp.getInstance(APP_NAME)
        } catch (_: IllegalStateException) {
            try {
                FirebaseApp.getInstance()
            } catch (_: IllegalStateException) {
                null
            }
        }
    }

    fun reset(context: Context) {
        try {
            FirebaseApp.getApps(context).forEach {
                if (it.name == APP_NAME) it.delete()
            }
        } catch (_: Exception) {}
        FirebaseConfigStore(context).clear()
        initialized = false
    }
}
