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

            else -> null // unknown command -> let the AI server answer
        }
    }
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
