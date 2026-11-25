package com.example.devboxai.firebase

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class RemoteConfigManager {

    data class Flags(
        val voiceEnabled: Boolean = true,
        val wakeWordEnabled: Boolean = true,
        val blockModeEnabled: Boolean = false,
        val autoKillBlocked: Boolean = true,
        val autoScanRisky: Boolean = true,
        val scanIntervalHours: Long = 6,
        val forceLanguage: String = "",
        val personality: String = "boss",
        val remoteKillSignal: Boolean = false,
        val greetingText: String = "",
        val blockedPackagesCsv: String = ""
    )

    private val _flags = MutableStateFlow(Flags())
    val flags: StateFlow<Flags> = _flags
    private val _lastSync = MutableStateFlow(0L)
    val lastSync: StateFlow<Long> = _lastSync

    private fun rc(): FirebaseRemoteConfig? {
        val app = FirebaseBootstrap.app() ?: return null
        return FirebaseRemoteConfig.getInstance(app)
    }

    suspend fun init() {
        val config = rc() ?: return
        try {
            val settings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = TimeUnit.MINUTES.toSeconds(1)
            }
            config.setConfigSettingsAsync(settings)
            config.setDefaultsAsync(defaults())
            refresh()
        } catch (_: Exception) {}
    }

    suspend fun refresh() {
        val config = rc() ?: return
        try {
            config.fetchAndActivate().await()
            _flags.value = Flags(
                voiceEnabled = config.getBoolean("voice_enabled"),
                wakeWordEnabled = config.getBoolean("wake_word_enabled"),
                blockModeEnabled = config.getBoolean("block_mode_enabled"),
                autoKillBlocked = config.getBoolean("auto_kill_blocked"),
                autoScanRisky = config.getBoolean("auto_scan_risky"),
                scanIntervalHours = config.getLong("scan_interval_hours"),
                forceLanguage = config.getString("force_language"),
                personality = config.getString("personality"),
                remoteKillSignal = config.getBoolean("remote_kill_signal"),
                greetingText = config.getString("greeting_text"),
                blockedPackagesCsv = config.getString("blocked_packages_csv")
            )
            _lastSync.value = System.currentTimeMillis()
        } catch (_: Exception) {}
    }

    private fun defaults() = mapOf<String, Any>(
        "voice_enabled" to true, "wake_word_enabled" to true,
        "block_mode_enabled" to false, "auto_kill_blocked" to true,
        "auto_scan_risky" to true, "scan_interval_hours" to 6L,
        "force_language" to "", "personality" to "boss",
        "remote_kill_signal" to false, "greeting_text" to "",
        "blocked_packages_csv" to ""
    )
}
