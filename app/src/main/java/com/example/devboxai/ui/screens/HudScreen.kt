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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val HudGreen = Color(0xFF39FF88)
private val HudCyan = Color(0xFF00E5FF)
private val HudPanel = Color(0xD902140C)

/**
 * DevBox home — Maya-style layout with the hacker + full HUD in the centre:
 * greeting on top, hacker with rotating ring in the middle, live HUD panels
 * at the corners, weather/battery/storage widgets and feature cards below.
 */
@Composable
fun HudScreen(
    onOpenTerminal: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
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
    var dateLine by remember { mutableStateOf("") }
    var greeting by remember { mutableStateOf("Good morning, Boss") }
    var uplink by remember { mutableStateOf(false) }
    var cpuLoad by remember { mutableStateOf(0f) }
    var memUsedGb by remember { mutableStateOf(0.0) }
    var memTotalGb by remember { mutableStateOf(0.0) }
    var rxMb by remember { mutableStateOf(0.0) }
    var txMb by remember { mutableStateOf(0.0) }
    var ip by remember { mutableStateOf("—") }
    var netType by remember { mutableStateOf("") }
    var netNodes by remember { mutableStateOf(0) }
    var batteryPct by remember { mutableStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    var storageFreePct by remember { mutableStateOf(-1) }
    var weatherText by remember { mutableStateOf("Mausam load ho raha hai...") }
    var liveLog by remember { mutableStateOf(listOf<String>()) }
    val myPid = remember { android.os.Process.myPid() }
    val isRoot = remember {
        listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/app/Superuser.apk")
            .any { File(it).exists() }
    }

    var scanProgress by remember { mutableStateOf(-1f) } // -1 = idle
    var scanReport by remember { mutableStateOf("") }
    var bootLines by remember { mutableStateOf(listOf<String>()) }
    var updateAvailable by remember { mutableStateOf(false) }
    var updateUrl by remember { mutableStateOf("") }
    var updateStamp by remember { mutableStateOf("") }
    var updateProgress by remember { mutableStateOf(-1) }

    LaunchedEffect(Unit) {
        // Silent update check on launch
        launch {
            try {
                val info = com.example.devboxai.update.UpdateManager(context).checkFromGitHub()
                if (info.available) {
                    updateAvailable = true
                    updateUrl = info.apkUrl
                    updateStamp = info.changelog.removePrefix("Stamp: ")
                }
            } catch (_: Throwable) { /* update check must never crash the app */ }
        }
        // Weather (auto location by IP)
        launch {
            try {
                weatherText = withContext(Dispatchers.IO) { fetchWeather() }
            } catch (_: Throwable) { weatherText = "Mausam nahi mila" }
        }
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
        var tick = 0
        while (true) {
          try {
            clock = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            dateLine = SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault()).format(Date())
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            greeting = when (hour) {
                in 5..11 -> "Good morning, Boss"
                in 12..16 -> "Good afternoon, Boss"
                in 17..20 -> "Good evening, Boss"
                else -> "Good night, Boss"
            }
            uplink = isOnline(context)
            netType = networkType(context)
            netNodes = withContext(Dispatchers.IO) { upInterfaces() }
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            memTotalGb = info.totalMem / (1024.0 * 1024 * 1024)
            memUsedGb = (info.totalMem - info.availMem) / (1024.0 * 1024 * 1024)
            rxMb = TrafficStats.getTotalRxBytes().coerceAtLeast(0) / (1024.0 * 1024)
            txMb = TrafficStats.getTotalTxBytes().coerceAtLeast(0) / (1024.0 * 1024)
            cpuLoad = withContext(Dispatchers.IO) { cpuUsage() }
            // Battery + storage widgets
            val batt = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryPct = batt?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val status = batt?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val stat = StatFs(Environment.getDataDirectory().path)
            storageFreePct = if (stat.totalBytes > 0) (stat.availableBytes * 100 / stat.totalBytes).toInt() else -1
            tick++
            // Heartbeat log: if the system kills the app, the last lines show when/why
            try {
                val hb = File(context.filesDir, "hud_heartbeat.log")
                val rt = Runtime.getRuntime()
                val freeMb = (rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())) / (1024 * 1024)
                hb.appendText("$clock tick=$tick cpu=${(cpuLoad * 100).toInt()}% heapFree=${freeMb}MB\n")
                if (hb.length() > 20_000) {
                    val tail = hb.readLines().takeLast(60).joinToString("\n")
                    hb.writeText(tail + "\n")
                }
            } catch (_: Throwable) {}
            if (tick % 3 == 0) {
                val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val line = "> [$stamp] CPU ${(cpuLoad * 100).toInt()}%% • MEM %.1fGB • RX %.1fMB • TX %.1fMB"
                    .format(memUsedGb, rxMb, txMb)
                liveLog = (liveLog + line).takeLast(5)
            }
          } catch (t: Throwable) {
              // Surface the error on screen instead of crashing
              liveLog = (liveLog + "> ⚠ ${t.javaClass.simpleName}: ${t.message}").takeLast(5)
          }
            delay(1000)
        }
    }

    // Animations: rotating ring + blinking status dot
    val transition = rememberInfiniteTransition(label = "hud")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring"
    )
    val blink by transition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "blink"
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Hacker photo background
        if (bgBitmap != null) {
            Image(
                bitmap = bgBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.85f
            )
        }

        // Big rotating HUD ring around the hacker (centre, like the photo)
        Canvas(
            Modifier.size(350.dp).align(Alignment.Center).offset(y = (-70).dp).rotate(rotation)
        ) {
            val r = size.minDimension / 2f
            drawCircle(HudCyan.copy(alpha = 0.45f), radius = r, style = Stroke(2.5f))
            drawCircle(HudGreen.copy(alpha = 0.38f), radius = r * 0.84f, style = Stroke(2f))
            drawCircle(HudCyan.copy(alpha = 0.28f), radius = r * 0.66f, style = Stroke(1.5f))
            for (i in 0 until 36) {
                val angle = Math.toRadians((i * 10).toDouble())
                val inner = if (i % 3 == 0) r * 0.90f else r * 0.95f
                drawLine(
                    HudGreen.copy(alpha = 0.6f),
                    Offset(center.x + (inner * kotlin.math.cos(angle)).toFloat(), center.y + (inner * kotlin.math.sin(angle)).toFloat()),
                    Offset(center.x + (r * kotlin.math.cos(angle)).toFloat(), center.y + (r * kotlin.math.sin(angle)).toFloat()),
                    strokeWidth = 2f
                )
            }
            drawCircle(HudGreen, radius = 6f, center = Offset(center.x + r, center.y))
            drawCircle(HudCyan, radius = 4f, center = Offset(center.x - r * 0.84f, center.y))
        }

        // Greeting + title (top centre, Maya-style)
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                greeting, color = HudGreen, fontSize = 15.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "DEVBOX AI", color = HudCyan, fontSize = 30.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
            )
            Text(
                "$dateLine • $clock", color = HudGreen, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Top-left panel: SYS STATUS / ENCRYPTION
        HudPanelBox(Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 92.dp).widthIn(max = 180.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SYS STATUS: ONLINE ", color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Box(Modifier.size(8.dp).clip(CircleShape).background(HudGreen.copy(alpha = blink)))
            }
            Text("ENCRYPTION: AES-256 | ACTIVE", color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }

        // Top-right panel: TIME / UPLINK
        HudPanelBox(Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 92.dp).widthIn(max = 180.dp)) {
            Text("TIME: $clock", color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Text(
                if (uplink) "UPLINK: STABLE ${if (netType.isNotBlank()) "($netType)" else ""}" else "UPLINK: OFFLINE",
                color = if (uplink) HudGreen else Color(0xFFFF5252),
                fontFamily = FontFamily.Monospace, fontSize = 10.sp
            )
        }

        // Left panel: boot log + live feed
        HudPanelBox(Modifier.align(Alignment.CenterStart).padding(start = 8.dp).offset(y = (-40).dp).widthIn(max = 200.dp)) {
            bootLines.forEach { line ->
                Text(line, color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
            if (liveLog.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("LIVE FEED:", color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                liveLog.forEach { line ->
                    Text(line, color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
                }
            }
        }

        // Right panel: live meters
        HudPanelBox(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).offset(y = (-40).dp).widthIn(max = 190.dp)) {
            Text("CPU LOAD: ${(cpuLoad * 100).toInt()}%", color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            HudBar(cpuLoad)
            Spacer(Modifier.height(6.dp))
            Text(
                "MEM: %.1fGB / %.1fGB".format(memUsedGb, memTotalGb),
                color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp
            )
            HudBar(if (memTotalGb > 0) (memUsedGb / memTotalGb).toFloat() else 0f)
            Spacer(Modifier.height(6.dp))
            Text(
                "PACKETS: %.1f MB RX | %.1f MB TX".format(rxMb, txMb),
                color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = 10.sp
            )
            Text(
                "PROCESS: devbox_core (PID $myPid) | RUNNING",
                color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp
            )
            Text(
                "NETWORK NODES: $netNodes LINKED",
                color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = 10.sp
            )
            Text(
                "ACCESS: ${if (isRoot) "ROOT" else "USER"} • IP: $ip",
                color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp
            )
        }

        // Update banner (below title when a newer build exists)
        if (updateAvailable) {
            HudPanelBox(Modifier.align(Alignment.TopCenter).padding(top = 142.dp).widthIn(max = 260.dp)) {
                Text(
                    "⬆ NAYA UPDATE HAI!",
                    color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp
                )
                if (updateProgress in 0..100) {
                    LinearProgressIndicator(
                        progress = { updateProgress / 100f },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        color = HudGreen
                    )
                    Text("Download: $updateProgress%", color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                } else {
                    Button(
                        onClick = {
                            val updater = com.example.devboxai.update.UpdateManager(context)
                            if (!updater.canInstallPackages()) {
                                updater.requestInstallPermission()
                            } else {
                                updateProgress = 0
                                scope.launch {
                                    val file = updater.downloadApk(updateUrl) { p -> updateProgress = p }
                                    if (file != null) {
                                        updater.markInstalled(updateStamp)
                                        updater.installApk(file)
                                    }
                                    updateProgress = -1
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HudCyan, contentColor = Color.Black)
                    ) { Text("UPDATE KARO", fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                }
            }
        }

        // Bottom area: widgets + feature cards + scan/buttons (Maya-style)
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (scanProgress >= 0f) {
                HudPanelBox(Modifier.fillMaxWidth()) {
                    Text(
                        "SCAN: ${(scanProgress * 100).toInt()}% ${if (scanProgress >= 1f) "• COMPLETE" else "• RUNNING..."}",
                        color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = 12.sp
                    )
                    HudBar(scanProgress)
                    if (scanReport.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(scanReport, color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
            }

            // Widgets row: Weather | Battery | Storage
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudPanelBox(Modifier.weight(1.4f)) {
                    Text("WEATHER", color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                    Text(weatherText, color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                HudPanelBox(Modifier.weight(1f)) {
                    Text("BATTERY", color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                    Text(
                        if (batteryPct >= 0) "$batteryPct%${if (charging) " ⚡" else ""}" else "—",
                        color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = 13.sp
                    )
                }
                HudPanelBox(Modifier.weight(1f)) {
                    Text("STORAGE FREE", color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                    Text(
                        if (storageFreePct >= 0) "$storageFreePct%" else "—",
                        color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = 13.sp
                    )
                }
            }

            // Feature cards row (tap to open)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureCard("AI CHAT", Modifier.weight(1f)) { onNavigate("chat") }
                FeatureCard("TERMINAL", Modifier.weight(1f)) { onOpenTerminal() }
                FeatureCard("CLI TOOLS", Modifier.weight(1f)) { onNavigate("termux") }
                FeatureCard("FILES", Modifier.weight(1f)) { onNavigate("files") }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (scanProgress in 0f..0.99f) return@Button
                        scanReport = ""
                        scope.launch {
                            scanProgress = 0f
                            val warnings = mutableListOf<String>()
                            val stat = StatFs(Environment.getDataDirectory().path)
                            val freePct = if (stat.totalBytes > 0) stat.availableBytes * 100 / stat.totalBytes else 100
                            if (freePct < 10) warnings += "LOW STORAGE ($freePct% free)"
                            scanProgress = 0.25f; delay(400)
                            val batt = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                            val level = batt?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
                            if (level < 15) warnings += "LOW BATTERY ($level%)"
                            scanProgress = 0.5f; delay(400)
                            val maxTemp = withContext(Dispatchers.IO) { maxTempC() }
                            if (maxTemp > 45) warnings += "HIGH TEMP (%.0f°C)".format(maxTemp)
                            scanProgress = 0.75f; delay(400)
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
                    onClick = { onNavigate("voice") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF062A35))
                ) {
                    Text("[ VOICE CONTROL ]", color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
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
private fun FeatureCard(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HudPanel)
            .border(1.dp, HudGreen.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HudPanelBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HudPanel)
            .border(1.dp, HudCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(8.dp),
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

private fun fetchWeather(): String = try {
    val req = Request.Builder().url("https://wttr.in/?format=j1").build()
    OkHttpClient().newCall(req).execute().use { res ->
        if (!res.isSuccessful) return "Mausam nahi mila"
        val body = res.body?.string() ?: return "Mausam nahi mila"
        val json = JSONObject(body)
        val cur = json.getJSONArray("current_condition").getJSONObject(0)
        val temp = cur.optString("temp_C")
        val desc = cur.getJSONArray("weatherDesc").getJSONObject(0).optString("value")
        val area = try {
            json.getJSONArray("nearest_area").getJSONObject(0)
                .getJSONArray("areaName").getJSONObject(0).optString("value")
        } catch (_: Exception) { "" }
        if (area.isNotBlank()) "$area • ${temp}°C $desc" else "${temp}°C $desc"
    }
} catch (_: Exception) { "Mausam nahi mila" }

private fun isOnline(context: Context): Boolean = try {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    cm.activeNetwork != null
} catch (_: Exception) { false }

private fun networkType(context: Context): String = try {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    when {
        caps == null -> ""
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> ""
    }
} catch (_: Exception) { "" }

private fun upInterfaces(): Int = try {
    NetworkInterface.getNetworkInterfaces().toList().count { it.isUp && !it.isLoopback }
} catch (_: Exception) { 0 }

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
