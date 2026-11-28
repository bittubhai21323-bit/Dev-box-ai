package com.example.devboxai.security

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Holds app-scan state outside the composable so switching tabs
 * never loses the scan or its results.
 */
object AppScanState {
    var results by mutableStateOf<List<ProcessGuardian.RiskyApp>>(emptyList())
    var scanning by mutableStateOf(false)
    var scanned by mutableStateOf(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startScan(guardian: ProcessGuardian) {
        if (scanning) return
        scanning = true
        scope.launch {
            results = try { guardian.scanRiskyApps() } catch (_: Exception) { emptyList() }
            scanned = true
            scanning = false
        }
    }
}
