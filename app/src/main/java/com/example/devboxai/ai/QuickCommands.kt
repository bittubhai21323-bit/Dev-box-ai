package com.example.devboxai.ai

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local slash commands answered instantly inside the app (no AI server needed).
 * Returns null when the text is not a command, so the chat falls back to the AI server.
 */
object QuickCommands {

    private val client = OkHttpClient()

    private val jokes = listOf(
        "Teacher: Batao, samundar me nimbu ka ped kyun nahi ugta? Student: Kyunki samundar me machhliyan nimbu paani pee jati hain! 😄",
        "Pappu: Maa, aaj school me teacher ne mujhe khada kar diya. Maa: Kyun? Pappu: Kyunki main baitha hua tha! 😄",
        "Doctor: Tumhe roz subah khali pet ek glass paani peena chahiye. Patient: Doctor sahab, main to roz peeta hoon — bas glass me chai hoti hai! 😄",
        "Ek baar ek cheenti haathi ke upar baithi thi. Haathi bola: 'Aaj kuch zyada hi bhaar lag raha hai!' 😄",
        "Santa: Meri biwi kho gayi hai. Banta: Police me report karo. Santa: Pichhli baar bhi ki thi, wo mil gayi thi! 😄"
    )

    private val quotes = listOf(
        "Mehnat kabhi bekaar nahi jaati — boond-boond se hi ghada bharta hai. 💧",
        "Sukoon bahar nahi milta, jab man rukta hai tabhi zindagi halki lagti hai. 🌿",
        "Haar se seekho, kyunki jeetne wale bhi kabhi haare hue the. 🔥",
        "Khud par bharosa rakho — duniya tum par tabhi bharosa karegi. 💪",
        "Waqt sabka aata hai, bas rukna mat. ⏳",
        "Badlav se daro mat, badlav hi zindagi hai. 🦋"
    )

    suspend fun handle(context: Context?, rawText: String): String? {
        val text = rawText.trim()
        if (!text.startsWith("/")) return null
        val parts = text.removePrefix("/").split(" ", limit = 2)
        val cmd = parts[0].lowercase()
        val arg = parts.getOrNull(1)?.trim().orEmpty()

        return when (cmd) {
            "help", "madad" -> """
                ⚡ Quick Commands:
                /mausam <sheher> — mausam jaano
                /calc <hisaab> — calculator (jaise /calc 25*4+10)
                /battery — battery kitni hai
                /storage — phone me kitni jagah bachi
                /ram — RAM ki jaankari
                /device — phone ki jaankari
                /time — abhi ka time
                /date — aaj ki tareekh
                /torch on / /torch off — flashlight
                /joke — ek joke suno
                /quote — ek achhi baat
                /board — motherboard/chipset jaankari
                /cpu — CPU cores aur usage
                /temp — phone ka temperature
                /uptime — phone kab se chal raha hai
                /kernel — kernel jaankari
                /process — chal rahe processes (Termux se)
                /system — poori system report
            """.trimIndent()

            "mausam", "weather" -> {
                if (arg.isBlank()) "Sheher ka naam likho, jaise: /mausam Patna"
                else withContext(Dispatchers.IO) {
                    try {
                        val req = Request.Builder()
                            .url("https://wttr.in/${arg.replace(" ", "%20")}?format=3")
                            .build()
                        client.newCall(req).execute().use { res ->
                            if (res.isSuccessful) res.body?.string()?.trim() ?: "Mausam nahi mila"
                            else "Mausam nahi mila (internet check karo)"
                        }
                    } catch (_: Exception) {
                        "Mausam nahi mila (internet check karo)"
                    }
                }
            }

            "calc", "hisaab" -> {
                if (arg.isBlank()) "Hisaab likho, jaise: /calc 25*4+10"
                else try {
                    val result = Calculator.eval(arg)
                    val pretty = if (result == result.toLong().toDouble()) result.toLong().toString() else result.toString()
                    "$arg = $pretty"
                } catch (_: Exception) {
                    "Hisaab samajh nahi aaya. Sirf + - * / % aur ( ) use karo."
                }
            }

            "battery" -> {
                if (context == null) "Battery jaankari nahi mil payi"
                else {
                    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    val pct = if (level >= 0) level * 100 / scale else -1
                    if (pct >= 0) "🔋 Battery: $pct%${if (charging) " (charging ho rahi hai ⚡)" else ""}"
                    else "Battery jaankari nahi mil payi"
                }
            }

            "storage" -> {
                val stat = StatFs(Environment.getDataDirectory().path)
                val totalGb = stat.totalBytes / (1024.0 * 1024 * 1024)
                val freeGb = stat.availableBytes / (1024.0 * 1024 * 1024)
                "💾 Storage: kul %.1f GB, khaali %.1f GB".format(totalGb, freeGb)
            }

            "ram" -> {
                if (context == null) "RAM jaankari nahi mil payi"
                else {
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val info = ActivityManager.MemoryInfo()
                    am.getMemoryInfo(info)
                    val totalGb = info.totalMem / (1024.0 * 1024 * 1024)
                    val freeGb = info.availMem / (1024.0 * 1024 * 1024)
                    "🧠 RAM: kul %.1f GB, khaali %.1f GB".format(totalGb, freeGb)
                }
            }

            "device", "phone" ->
                "📱 ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

            "time", "samay" ->
                "🕐 Abhi ka time: " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

            "date", "tareekh" ->
                "📅 Aaj: " + SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date())

            "torch", "flashlight" -> {
                if (context == null) "Flashlight nahi chal payi"
                else try {
                    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val camId = cm.cameraIdList.firstOrNull { id ->
                        cm.getCameraCharacteristics(id)
                            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    }
                    if (camId == null) "Is phone me flashlight nahi mili"
                    else {
                        val on = arg.lowercase() != "off"
                        cm.setTorchMode(camId, on)
                        if (on) "🔦 Flashlight ON" else "🔦 Flashlight OFF"
                    }
                } catch (_: Exception) {
                    "Flashlight nahi chal payi"
                }
            }

            "joke" -> jokes.random()
            "quote", "baat" -> quotes.random()

            "board", "motherboard" -> boardInfo()
            "cpu" -> cpuInfo()
            "temp", "temperature" -> tempInfo()
            "uptime" -> uptimeInfo()
            "kernel" -> kernelInfo()

            "process", "processes" -> {
                if (context == null) "Process jaankari nahi mil payi"
                else if (!com.example.devboxai.termux.TermuxHelper.isTermuxInstalled(context))
                    "Process list ke liye Termux app chahiye (F-Droid se lagao)"
                else {
                    val result = com.example.devboxai.termux.TermuxHelper.runAndWait(
                        context,
                        "top -b -n1 -o PID,%CPU,%MEM,NAME 2>/dev/null | head -25",
                        timeoutMs = 15_000
                    )
                    val out = result?.stdout?.trim().orEmpty()
                    if (out.isNotBlank()) "⚙️ Processes:\n$out"
                    else "Process list nahi mili (Termux me 'allow-external-apps' on karo, ya root chahiye hoga)"
                }
            }

            "system", "report" -> buildString {
                appendLine("🖥️ DevBox System Report")
                appendLine(boardInfo())
                appendLine(cpuInfo())
                appendLine(tempInfo())
                appendLine(uptimeInfo())
                append(kernelInfo())
            }

            else -> null // unknown command -> let the AI server answer
        }
    }

    private fun boardInfo(): String = buildString {
        appendLine("🔌 Motherboard / Chipset:")
        appendLine("Board: ${Build.BOARD}")
        appendLine("Hardware: ${Build.HARDWARE}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appendLine("SoC: ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
        }
        appendLine("CPU cores: ${Runtime.getRuntime().availableProcessors()}")
        append("CPU type: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}")
    }

    private suspend fun cpuInfo(): String = withContext(Dispatchers.IO) {
        fun readStat(): LongArray? = try {
            val line = java.io.File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") }
            line?.trim()?.split(Regex("\\s+"))?.drop(1)?.map { it.toLong() }?.toLongArray()
        } catch (_: Exception) { null }

        val cores = Runtime.getRuntime().availableProcessors()
        val first = readStat() ?: return@withContext "⚙️ CPU cores: $cores (usage nahi mil paya)"
        // small sample window for a live usage reading
        Thread.sleep(300)
        val second = readStat() ?: return@withContext "⚙️ CPU cores: $cores (usage nahi mil paya)"
        val idle1 = first.getOrElse(3) { 0 } + first.getOrElse(4) { 0 }
        val idle2 = second.getOrElse(3) { 0 } + second.getOrElse(4) { 0 }
        val total1 = first.sum()
        val total2 = second.sum()
        val totalDiff = total2 - total1
        val usage = if (totalDiff > 0) (100.0 * (totalDiff - (idle2 - idle1)) / totalDiff) else 0.0
        "⚙️ CPU: $cores cores, abhi usage %.1f%%".format(usage)
    }

    private fun tempInfo(): String {
        val zones = try {
            java.io.File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
                ?.sortedBy { it.name }
                ?.mapNotNull { zone ->
                    try {
                        val type = java.io.File(zone, "type").readText().trim()
                        val raw = java.io.File(zone, "temp").readText().trim().toLong()
                        val celsius = if (raw > 1000) raw / 1000.0 else raw.toDouble()
                        "$type: %.1f°C".format(celsius)
                    } catch (_: Exception) { null }
                }
                .orEmpty()
        } catch (_: Exception) { emptyList() }
        return if (zones.isEmpty()) "🌡️ Temperature jaankari is phone par nahi milti"
        else "🌡️ Temperature:\n" + zones.take(8).joinToString("\n")
    }

    private fun uptimeInfo(): String = try {
        val seconds = java.io.File("/proc/uptime").readText().trim().split(" ")[0].toDouble().toLong()
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val mins = (seconds % 3600) / 60
        "⏱️ Phone chal raha hai: ${days}d ${hours}h ${mins}m se"
    } catch (_: Exception) { "Uptime nahi mil paya" }

    private fun kernelInfo(): String = try {
        "🐧 Kernel: " + java.io.File("/proc/version").readText().trim().take(120)
    } catch (_: Exception) { "Kernel jaankari nahi mil payi" }
}

/** Tiny arithmetic evaluator: + - * / % parentheses, decimals, unary minus. */
private object Calculator {
    fun eval(expr: String): Double {
        val parser = Parser(expr.replace(" ", ""))
        val value = parser.parseExpression()
        if (parser.pos != parser.text.length) throw IllegalArgumentException("bad expr")
        return value
    }

    private class Parser(val text: String) {
        var pos = 0

        fun parseExpression(): Double {
            var value = parseTerm()
            while (pos < text.length && (text[pos] == '+' || text[pos] == '-')) {
                val op = text[pos++]
                val rhs = parseTerm()
                value = if (op == '+') value + rhs else value - rhs
            }
            return value
        }

        fun parseTerm(): Double {
            var value = parseFactor()
            while (pos < text.length && (text[pos] == '*' || text[pos] == '/' || text[pos] == '%')) {
                val op = text[pos++]
                val rhs = parseFactor()
                value = when (op) {
                    '*' -> value * rhs
                    '/' -> value / rhs
                    else -> value % rhs
                }
            }
            return value
        }

        fun parseFactor(): Double {
            if (pos >= text.length) throw IllegalArgumentException("eof")
            return when (text[pos]) {
                '-' -> { pos++; -parseFactor() }
                '+' -> { pos++; parseFactor() }
                '(' -> {
                    pos++
                    val value = parseExpression()
                    if (pos >= text.length || text[pos] != ')') throw IllegalArgumentException(")")
                    pos++
                    value
                }
                else -> {
                    val start = pos
                    while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
                    if (start == pos) throw IllegalArgumentException("num")
                    text.substring(start, pos).toDouble()
                }
            }
        }
    }
}
