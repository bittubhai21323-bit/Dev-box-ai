package com.example.devboxai.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.devboxai.termux.TermuxHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HudGreen = Color(0xFF39FF88)
private val HudCyan = Color(0xFF00E5FF)
private val HudPanel = Color(0xCC02140C)

@Composable
fun HudScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bgBitmap = remember {
        try {
            val b64 = context.assets.open("hud_bg.b64").bufferedReader().readText()
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) { null }
    }

    var clock by remember { mutableStateOf("") }
    var uplink by remember { mutableStateOf(false) }
    var cpuLoad by remember { mutableStateOf(0f) }
    var memUsedGb by remember { mutableStateOf(0.0) }
    var memTotalGb by remember { mutableStateOf(0.0) }
    var rxMb by remember { mutableStateOf(0.0) }
    var txMb by remember { mutableStateOf(0.0) }
    var ip by remember { mutableStateOf("—") }
    val isRoot = remember {
        listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/app/Superuser.apk")
            .any { File(it).exists() }
    }

    var scanProgress by remember { mutableStateOf(-1f) } // -1 = idle
    var scanReport by remember { mutableStateOf("") }
    var bootLines by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(Unit) {
        val lines = listOf(
            "> INITIATING DEVBOX_AI_CORE...",
            "> LOADING MODULES: net_scanner, crypto_layer, sys_monitor...",
            "> CONNECTION ESTABLISHED",
            "> ENCRYPTION: AES-256 ACTIVE",
            "> ACCESS: GRANTED"
        )
        for (line in lines) {
            delay(450)
            bootLines = bootLines + line
        }
        ip = withContext(Dispatchers.IO) { deviceIp() }
        while (true) {
            clock = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            uplink = isOnline(context)
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            memTotalGb = info.totalMem / (1024.0 * 1024 * 1024)
            memUsedGb = (info.totalMem - info.availMem) / (1024.0 * 1024 * 1024)
            rxMb = TrafficStats.getTotalRxBytes().coerceAtLeast(0) / (1024.0 * 1024)
            txMb = TrafficStats.getTotalTxBytes().coerceAtLeast(0) / (1024.0 * 1024)
            cpuLoad = withContext(Dispatchers.IO) { cpuUsage() }
            delay(2000)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (bgBitmap != null) {
            Image(
                bitmap = bgBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.55f
            )
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top status row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HudPanelBox {
                    Text("SYS STATUS: ONLINE ●", color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Text("ENCRYPTION: AES-256 | ACTIVE", color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                HudPanelBox {
                    Text("TIME: $clock", color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Text(
                        if (uplink) "UPLINK: STABLE" else "UPLINK: OFFLINE",
                        color = if (uplink) HudGreen else Color(0xFFFF5252),
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp
                    )
                }
            }

            // Title
            HudPanelBox(Modifier.fillMaxWidth()) {
                Text(
                    "DEVBOX AI", color = HudCyan, fontSize = 30.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    "HUD • HOME SCREEN • SECURE SESSION", color = HudGreen, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            // Boot log
            HudPanelBox(Modifier.fillMaxWidth()) {
                bootLines.forEach { line ->
                    Text(line, color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }

            // Live meters
            HudPanelBox(Modifier.fillMaxWidth()) {
                Text("CPU LOAD: ${(cpuLoad * 100).toInt()}%", color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                HudBar(cpuLoad)
                Spacer(Modifier.height(8.dp))
                Text(
                    "MEM: %.1fGB / %.1fGB".format(memUsedGb, memTotalGb),
                    color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 13.sp
                )
                HudBar(if (memTotalGb > 0) (memUsedGb / memTotalGb).toFloat() else 0f)
                Spacer(Modifier.height(8.dp))
                Text(
                    "PACKETS: %.1f MB RX | %.1f MB TX".format(rxMb, txMb),
                    color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = 12.sp
                )
                Text(
                    "PROCESS: devbox_core | RUNNING",
                    color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp
                )
            }

            // Access bar
            HudPanelBox(Modifier.fillMaxWidth()) {
                Text(
                    "ACCESS LEVEL: ${if (isRoot) "ROOT" else "USER"} • IP: $ip",
                    color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            // Scan
            if (scanProgress >= 0f) {
                HudPanelBox(Modifier.fillMaxWidth()) {
                    Text(
                        "SCAN: ${(scanProgress * 100).toInt()}% ${if (scanProgress >= 1f) "• COMPLETE" else "• RUNNING..."}",
                        color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = 13.sp
                    )
                    HudBar(scanProgress)
                    if (scanReport.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(scanReport, color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }

            // Buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (scanProgress in 0f..0.99f) return@Button
                        scanReport = ""
                        scope.launch {
                            scanProgress = 0f
                            val warnings = mutableListOf<String>()
                            // storage check
                            val stat = StatFs(Environment.getDataDirectory().path)
                            val freePct = if (stat.totalBytes > 0) stat.availableBytes * 100 / stat.totalBytes else 100
                            if (freePct < 10) warnings += "LOW STORAGE ($freePct% free)"
                            scanProgress = 0.25f; delay(400)
                            // battery check
                            val batt = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                            val level = batt?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
                            if (level < 15) warnings += "LOW BATTERY ($level%)"
                            scanProgress = 0.5f; delay(400)
                            // temperature check
                            val maxTemp = withContext(Dispatchers.IO) { maxTempC() }
                            if (maxTemp > 45) warnings += "HIGH TEMP (%.0f°C)".format(maxTemp)
                            scanProgress = 0.75f; delay(400)
                            // network check
                            if (!isOnline(context)) warnings += "NO NETWORK"
                            scanProgress = 1f
                            scanReport = if (warnings.isEmpty())
                                "> VULNERABILITIES: 0\n> SYSTEM HEALTH: GOOD ✓"
                            else
                                "> VULNERABILITIES: ${warnings.size}\n" + warnings.joinToString("\n") { "> ⚠ $it" }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06351F))
                ) {
                    Text("[ START SCAN ]", color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
                Button(
                    onClick = { TermuxHelper.openTermux(context) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF062A35))
                ) {
                    Text("[ TERMINAL ]", color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }

            Text(
                "DEVBOX AI — SECURE MODE", color = HudGreen, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun HudPanelBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HudPanel)
            .border(1.dp, HudCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        content = content
    )
}

@Composable
private fun HudBar(fraction: Float) {
    Box(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF0A2E1F))
    ) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(4.dp)).background(HudCyan)
        )
    }
}

private fun isOnline(context: Context): Boolean = try {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    cm.activeNetwork != null
} catch (_: Exception) { false }

private fun deviceIp(): String = try {
    NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(":") == false }
        ?.hostAddress ?: "—"
} catch (_: Exception) { "—" }

private fun cpuUsage(): Float {
    fun readStat(): LongArray? = try {
        val line = File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") }
        line?.trim()?.split(Regex("\\s+"))?.drop(1)?.map { it.toLong() }?.toLongArray()
    } catch (_: Exception) { null }
    val first = readStat() ?: return 0f
    Thread.sleep(250)
    val second = readStat() ?: return 0f
    val idle1 = first.getOrElse(3) { 0 } + first.getOrElse(4) { 0 }
    val idle2 = second.getOrElse(3) { 0 } + second.getOrElse(4) { 0 }
    val totalDiff = second.sum() - first.sum()
    if (totalDiff <= 0) return 0f
    return ((totalDiff - (idle2 - idle1)).toFloat() / totalDiff).coerceIn(0f, 1f)
}

private fun maxTempC(): Double {
    var max = 0.0
    try {
        File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
            ?.forEach { zone ->
                try {
                    val raw = File(zone, "temp").readText().trim().toLong()
                    val c = if (raw > 1000) raw / 1000.0 else raw.toDouble()
                    if (c > max) max = c
                } catch (_: Exception) {}
            }
    } catch (_: Exception) {}
    return max
}
