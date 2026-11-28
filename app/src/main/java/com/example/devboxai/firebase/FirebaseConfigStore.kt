package com.example.devboxai.firebase

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class FirebaseConfigStore(context: Context) {

    // Encrypted prefs can throw on some devices (Keystore issues) — never let that crash the app.
    private val prefs: android.content.SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context, "devbox_firebase",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        context.getSharedPreferences("devbox_firebase_fallback", Context.MODE_PRIVATE)
    }

    fun isConfigured(): Boolean =
        prefs.getBoolean("configured", false) && load()?.isValid() == true

    fun save(creds: FirebaseCredentials) {
        prefs.edit().apply {
            putString("project_id", creds.projectId)
            putString("app_id", creds.applicationId)
            putString("api_key", creds.apiKey)
            putString("sender_id", creds.gcmSenderId)
            putString("storage_bucket", creds.storageBucket)
            putString("database_url", creds.databaseUrl)
            putBoolean("configured", true)
        }.apply()
    }

    fun load(): FirebaseCredentials? {
        if (!prefs.getBoolean("configured", false)) return null
        return FirebaseCredentials(
            projectId = prefs.getString("project_id", "").orEmpty(),
            applicationId = prefs.getString("app_id", "").orEmpty(),
            apiKey = prefs.getString("api_key", "").orEmpty(),
            gcmSenderId = prefs.getString("sender_id", "").orEmpty(),
            storageBucket = prefs.getString("storage_bucket", "").orEmpty(),
            databaseUrl = prefs.getString("database_url", "").orEmpty()
        )
    }

    fun clear() { prefs.edit().clear().apply() }
}
