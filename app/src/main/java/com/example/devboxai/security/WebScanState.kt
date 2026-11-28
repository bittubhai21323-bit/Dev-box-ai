package com.example.devboxai.security

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Holds web-scan state outside the composable so switching tabs or pages
 * never loses the URL, the running scan, or its report.
 */
object WebScanState {
    var url by mutableStateOf("")
    var report by mutableStateOf("")
    var scanning by mutableStateOf(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startScan() {
        if (scanning || url.isBlank()) return
        scanning = true
        report = "Scan ho raha hai… Deep scan me 30-60 second lag sakte hai. Page badal kar wapas aa sakte ho — report yahi milegi."
        scope.launch {
            report = try {
                WebScanner.scan(url)
            } catch (e: Exception) {
                "✗ Scan fail: ${e.message}\nCheck karo: URL sahi hai? Internet chal raha hai?"
            }
            scanning = false
        }
    }
}
