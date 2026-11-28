package com.example.devboxai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsStore(context: Context) {

    // Encrypted prefs can throw on some devices (Keystore issues) — never let that crash the app.
    private val prefs: android.content.SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context, "devbox_secure",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        context.getSharedPreferences("devbox_secure_fallback", Context.MODE_PRIVATE)
    }

    var openAiKey: String
        get() = prefs.getString("openai_key", "") ?: ""
        set(v) { prefs.edit().putString("openai_key", v).apply() }

    var githubToken: String
        get() = prefs.getString("github_token", "") ?: ""
        set(v) { prefs.edit().putString("github_token", v).apply() }

    var preferredLang: String
        get() = prefs.getString("preferred_lang", "") ?: ""
        set(v) { prefs.edit().putString("preferred_lang", v).apply() }

    var personality: String
        get() = prefs.getString("personality", "boss") ?: "boss"
        set(v) { prefs.edit().putString("personality", v).apply() }

    var backupPassword: String
        get() = prefs.getString("backup_password", "") ?: ""
        set(v) { prefs.edit().putString("backup_password", v).apply() }
}
