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
import androidx.compose.ui.text.style.TextAlign
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
import java.util.Date
import java.util.Locale

private val HudGreen = Color(0xFF39FF88)
private val HudCyan = Color(0xFF00E5FF)
private val PatchBg = Color(0xF202140C)

/**
 * Hacker HUD home screen (no photo): all panels/text stay at the same spots
 * as the reference poster, drawn directly on a black background with a
 * rotating HUD ring in the centre.
 */
@Composable
fun HudScreen(
    onOpenTerminal: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val hackerBitmap = remember {
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
    var netType by remember { mutableStateOf("") }
    var netNodes by remember { mutableStateOf(0) }
    var batteryPct by remember { mutableStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    var weatherText by remember { mutableStateOf("") }
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
        launch {
            try {
                weatherText = withContext(Dispatchers.IO) { fetchWeather() }
            } catch (_: Throwable) { weatherText = "" }
        }
        val lines = listOf(
            "> INITIATING DEVBOX_AI_CORE...",
            "> LOADING MODULES: net_scanner,",
            "  exploit_tools, crypto_layer...",
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
            val batt = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryPct = batt?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val status = batt?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            tick++
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
              liveLog = (liveLog + "> ⚠ ${t.javaClass.simpleName}: ${t.message}").takeLast(5)
          }
            delay(1000)
        }
    }

    val transition = rememberInfiniteTransition(label = "hud")
    val blink by transition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "blink"
    )
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring"
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        // Fit the 9:16 poster exactly (no crop, no stretch) so live text
        // lands inside the photo's own panels on any screen.
        val imgAspect = 0.5625f
        val containerAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else imgAspect
        val imgW = if (containerAspect > imgAspect) maxHeight * imgAspect else maxWidth
        val imgH = if (containerAspect > imgAspect) maxHeight else maxWidth / imgAspect
        val ox = (maxWidth - imgW) / 2
        val oy = (maxHeight - imgH) / 2
        val fs = { f: Float -> (imgW.value * f).sp }

        // Hacker photo inside the circle (simple, centred)
        val ringSize = imgW * 0.85f
        val ringY = oy + imgH * 0.245f
        if (hackerBitmap != null) {
            val photoSize = imgW * 0.64f
            Image(
                bitmap = hackerBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .offset(x = ox + (imgW - photoSize) / 2, y = ringY + (ringSize - photoSize) / 2)
                    .size(photoSize)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        // Rotating HUD ring around the hacker photo
        Canvas(
            Modifier.offset(x = ox + (imgW - ringSize) / 2, y = ringY)
                .size(ringSize).rotate(rotation)
        ) {
            val r = size.minDimension / 2f
            drawCircle(HudCyan.copy(alpha = 0.5f), radius = r, style = Stroke(2.5f))
            drawCircle(HudGreen.copy(alpha = 0.4f), radius = r * 0.84f, style = Stroke(2f))
            drawCircle(HudCyan.copy(alpha = 0.3f), radius = r * 0.66f, style = Stroke(1.5f))
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

        // Title (where the poster's title bar was)
        Column(
            Modifier.offset(x = ox + imgW * 0.10f, y = oy + imgH * 0.125f).width(imgW * 0.80f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "DEVBOX AI", color = HudCyan, fontFamily = FontFamily.Monospace,
                fontSize = fs(0.085f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
            Text(
                "HUD • HOME SCREEN • SECURE SESSION", color = HudGreen,
                fontFamily = FontFamily.Monospace, fontSize = fs(0.026f), textAlign = TextAlign.Center
            )
        }

        // Top-left: SYS STATUS panel of the photo
        Patch(Modifier.offset(x = ox + imgW * 0.055f, y = oy + imgH * 0.032f).width(imgW * 0.35f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SYS STATUS: ONLINE ", color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = fs(0.030f))
                Box(Modifier.size(7.dp).clip(CircleShape).background(HudGreen.copy(alpha = blink)))
            }
            Text("ENCRYPTION: AES-256 | ACTIVE", color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = fs(0.026f))
        }

        // Top-right: TIME panel of the photo (+ weather & battery below it)
        Patch(Modifier.offset(x = ox + imgW * 0.715f, y = oy + imgH * 0.032f).width(imgW * 0.24f)) {
            Text("TIME: $clock", color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = fs(0.030f))
            Text(
                if (uplink) "UPLINK: STABLE ${if (netType.isNotBlank()) "($netType)" else ""}" else "UPLINK: OFFLINE",
                color = if (uplink) HudGreen else Color(0xFFFF5252),
                fontFamily = FontFamily.Monospace, fontSize = fs(0.026f)
            )
            if (weatherText.isNotBlank()) {
                Text(weatherText, color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = fs(0.022f))
            }
            Text(
                "BATTERY: ${if (batteryPct >= 0) "$batteryPct%${if (charging) " ⚡" else ""}" else "—"}",
                color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = fs(0.022f)
            )
        }

        // SCAN status text of the photo
        Patch(Modifier.offset(x = ox + imgW * 0.685f, y = oy + imgH * 0.253f).width(imgW * 0.19f)) {
            Text(
                when {
                    scanProgress < 0f -> "SCAN: READY"
                    scanProgress >= 1f -> "SCAN: 100% • COMPLETE"
                    else -> "SCAN: ${(scanProgress * 100).toInt()}% • RUNNING"
                },
                color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = fs(0.028f)
            )
            if (scanProgress >= 0f) HudBar(scanProgress)
        }

        // NETWORK NODES text on the right edge of the photo
        Patch(Modifier.offset(x = ox + imgW * 0.850f, y = oy + imgH * 0.416f).width(imgW * 0.145f)) {
            Text(
                "NETWORK NODES:\n$netNodes LINKED",
                color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = fs(0.022f)
            )
        }

        // Update banner just below the photo's title bar
        if (updateAvailable) {
            Patch(Modifier.offset(x = ox + imgW * 0.20f, y = oy + imgH * 0.222f).width(imgW * 0.60f)) {
                Text(
                    "⬆ NAYA UPDATE HAI!",
                    color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = fs(0.030f),
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                if (updateProgress in 0..100) {
                    LinearProgressIndicator(
                        progress = { updateProgress / 100f },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        color = HudGreen
                    )
                    Text(
                        "Download: $updateProgress%", color = HudGreen,
                        fontFamily = FontFamily.Monospace, fontSize = fs(0.024f),
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
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
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = HudCyan, contentColor = Color.Black)
                    ) { Text("UPDATE KARO", fontFamily = FontFamily.Monospace, fontSize = fs(0.028f)) }
                }
            }
        }

        // Bottom-left: terminal log panel of the photo
        Patch(Modifier.offset(x = ox + imgW * 0.035f, y = oy + imgH * 0.520f).width(imgW * 0.215f)) {
            bootLines.forEach { line ->
                Text(line, color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = fs(0.021f))
            }
            if (scanReport.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(scanReport, color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = fs(0.021f))
            }
            if (liveLog.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("LIVE FEED:", color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = fs(0.021f))
                liveLog.forEach { line ->
                    Text(line, color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = fs(0.018f))
                }
            }
        }

        // Bottom-right: CPU/MEM panel of the photo
        Patch(Modifier.offset(x = ox + imgW * 0.725f, y = oy + imgH * 0.680f).width(imgW * 0.23f)) {
            Text("CPU LOAD: ${(cpuLoad * 100).toInt()}%", color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = fs(0.028f))
            HudBar(cpuLoad)
            Spacer(Modifier.height(5.dp))
            Text(
                "MEM: %.1fGB / %.1fGB".format(memUsedGb, memTotalGb),
                color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = fs(0.028f)
            )
            HudBar(if (memTotalGb > 0) (memUsedGb / memTotalGb).toFloat() else 0f)
            Spacer(Modifier.height(5.dp))
            Text(
                "PROCESS:\ndevbox_core (PID $myPid) | RUNNING",
                color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = fs(0.024f)
            )
            Text(
                "PACKETS: %.1f MB RX | %.1f MB TX".format(rxMb, txMb),
                color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = fs(0.024f)
            )
        }

        // Bottom ACCESS bar of the photo
        Patch(Modifier.offset(x = ox + imgW * 0.070f, y = oy + imgH * 0.850f).width(imgW * 0.86f)) {
            Text(
                "ACCESS LEVEL: ${if (isRoot) "ROOT" else "USER"} • LOCATION: ENCRYPTED • IP: $ip",
                color = HudCyan, fontFamily = FontFamily.Monospace, fontSize = fs(0.024f),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), maxLines = 1
            )
        }

        // Version line of the photo
        Patch(Modifier.offset(x = ox + imgW * 0.060f, y = oy + imgH * 0.893f).width(imgW * 0.33f)) {
            Text(
                "DEVBOX AI v11.0 — SECURE MODE",
                color = HudGreen, fontFamily = FontFamily.Monospace, fontSize = fs(0.022f), maxLines = 1
            )
        }

        // Buttons of the photo (real, tappable)
        Box(
            Modifier.offset(x = ox + imgW * 0.075f, y = oy + imgH * 0.928f)
                .size(width = imgW * 0.39f, height = imgH * 0.044f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xE606351F))
                .border(1.5.dp, HudGreen, RoundedCornerShape(8.dp))
                .clickable {
                    if (scanProgress in 0f..0.99f) return@clickable
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
                            "> VULNERABILITIES: 0\n> SYSTEM HEALTH: GOOD ✓\n> ACCESS: GRANTED"
                        else
                            "> VULNERABILITIES: ${warnings.size}\n" + warnings.joinToString("\n") { "> ⚠ $it" }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "[ START SCAN ]", color = HudGreen, fontFamily = FontFamily.Monospace,
                fontSize = fs(0.034f), fontWeight = FontWeight.Bold
            )
        }
        Box(
            Modifier.offset(x = ox + imgW * 0.535f, y = oy + imgH * 0.928f)
                .size(width = imgW * 0.39f, height = imgH * 0.044f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xE6062A35))
                .border(1.5.dp, HudCyan, RoundedCornerShape(8.dp))
                .clickable { onOpenTerminal() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "[ TERMINAL ACCESS ]", color = HudCyan, fontFamily = FontFamily.Monospace,
                fontSize = fs(0.034f), fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun Patch(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PatchBg)
            .border(1.dp, HudCyan.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        content = content
    )
}

@Composable
private fun HudBar(fraction: Float) {
    Box(
        Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
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
        if (!res.isSuccessful) return ""
        val body = res.body?.string() ?: return ""
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
} catch (_: Exception) { "" }

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
    val first = readStat()
    if (first != null && first.sum() > 0) {
        Thread.sleep(250)
        val second = readStat()
        if (second != null) {
            val idle1 = first.getOrElse(3) { 0 } + first.getOrElse(4) { 0 }
            val idle2 = second.getOrElse(3) { 0 } + second.getOrElse(4) { 0 }
            val totalDiff = second.sum() - first.sum()
            if (totalDiff > 0) {
                return ((totalDiff - (idle2 - idle1)).toFloat() / totalDiff).coerceIn(0f, 1f)
            }
        }
    }
    // Fallback (Android 10+ blocks /proc/stat on many phones): this app's own CPU time
    return try {
        val t1 = android.os.Process.getElapsedCpuTime()
        val w1 = System.nanoTime()
        Thread.sleep(250)
        val t2 = android.os.Process.getElapsedCpuTime()
        val w2 = System.nanoTime()
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val wallMs = (w2 - w1) / 1_000_000.0
        if (wallMs <= 0) 0f else (((t2 - t1) / wallMs) / cores).toFloat().coerceIn(0f, 1f)
    } catch (_: Exception) { 0f }
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
