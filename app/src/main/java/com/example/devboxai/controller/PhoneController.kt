package com.example.devboxai.controller

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import com.example.devboxai.DevAccessibilityService
import com.example.devboxai.ai.PhoneIntent
import kotlinx.coroutines.delay
import java.net.URLEncoder

class PhoneController(private val context: Context) {

    suspend fun execute(intent: PhoneIntent): String {
        return try {
            when (intent) {
                is PhoneIntent.OpenApp -> openApp(intent.name)
                PhoneIntent.Back -> global { it.globalBack() } ?: "Back OK"
                PhoneIntent.Home -> global { it.globalHome() } ?: "Home OK"
                PhoneIntent.Recents -> global { it.globalRecents() } ?: "Recents OK"
                PhoneIntent.Notifications -> global { it.globalNotifications() } ?: "Notifications OK"
                PhoneIntent.QuickSettings -> global { it.globalQuickSettings() } ?: "Quick settings OK"
                PhoneIntent.PowerDialog -> global { it.globalPowerDialog() } ?: "Power menu OK"
                PhoneIntent.LockScreen -> global { it.globalLockScreen() } ?: "Locked"
                PhoneIntent.Screenshot -> global { it.globalScreenshot() } ?: "Screenshot taken"
                is PhoneIntent.Click -> click(intent.text)
                is PhoneIntent.TypeText -> type(intent.text)
                is PhoneIntent.Scroll -> scroll(intent.down)
                PhoneIntent.VolumeUp -> adjustVolume(AudioManager.ADJUST_RAISE)
                PhoneIntent.VolumeDown -> adjustVolume(AudioManager.ADJUST_LOWER)
                PhoneIntent.VolumeMute -> adjustVolume(AudioManager.ADJUST_MUTE)
                PhoneIntent.FlashlightOn -> setFlashlight(true)
                PhoneIntent.FlashlightOff -> setFlashlight(false)
                is PhoneIntent.Call -> callContact(intent.contact)
                is PhoneIntent.Dial -> dial(intent.number)
                is PhoneIntent.Search -> search(intent.query)
                is PhoneIntent.WebSearch -> webSearch(intent.query)
                is PhoneIntent.Sms -> sendSms(intent.to, intent.body)
                is PhoneIntent.OpenUrl -> openUrl(intent.url)
                PhoneIntent.ReadScreen -> readScreen()
                is PhoneIntent.Repeat -> {
                    repeat(intent.times) { execute(intent.intent) }
                    "Repeated ${intent.times}×"
                }
                PhoneIntent.Unknown -> "Command samajh nahi aaya"
            }
        } catch (e: Exception) {
            Log.e("PhoneController", "execute failed", e)
            "Error: ${e.message}"
        }
    }

    private fun openApp(name: String): String {
        val pkg = findPackage(name) ?: return "App nahi mila: $name"
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return "$pkg launchable nahi hai"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "Opening $name"
    }

    private fun findPackage(input: String): String? {
        val normalized = input.lowercase().trim()
        if (normalized.contains(".")) return normalized

        val aliases = mapOf(
            "whatsapp" to "com.whatsapp", "व्हाट्सएप" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android", "insta" to "com.instagram.android",
            "facebook" to "com.facebook.katana", "fb" to "com.facebook.katana",
            "chrome" to "com.android.chrome",
            "camera" to "com.android.camera", "कैमरा" to "com.android.camera",
            "settings" to "com.android.settings", "सेटिंग्स" to "com.android.settings",
            "photos" to "com.google.android.apps.photos",
            "gallery" to "com.google.android.apps.photos",
            "maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm", "email" to "com.google.android.gm",
            "telegram" to "org.telegram.messenger", "termux" to "com.termux",
            "phone" to "com.android.dialer", "dialer" to "com.android.dialer",
            "contacts" to "com.android.contacts",
            "messages" to "com.google.android.apps.messaging",
            "sms" to "com.google.android.apps.messaging",
            "calculator" to "com.google.android.calculator",
            "clock" to "com.google.android.deskclock",
            "alarm" to "com.google.android.deskclock",
            "calendar" to "com.google.android.calendar",
            "spotify" to "com.spotify.music",
            "twitter" to "com.twitter.android", "x" to "com.twitter.android",
            "netflix" to "com.netflix.mediaclient",
            "paytm" to "net.one97.paytm", "phonepe" to "com.phonepe.app",
            "gpay" to "com.google.android.apps.nbu.paisa.user",
            "google pay" to "com.google.android.apps.nbu.paisa.user",
            "play store" to "com.android.vending",
            "files" to "com.google.android.documentsui"
        )
        aliases[normalized]?.let { return it }

        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
        apps.firstOrNull { it.loadLabel(pm).toString().equals(normalized, true) }
            ?.let { return it.activityInfo.packageName }
        apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(normalized) }
            ?.let { return it.activityInfo.packageName }
        apps.firstOrNull { normalized.contains(it.loadLabel(pm).toString().lowercase()) }
            ?.let { return it.activityInfo.packageName }
        return null
    }

    private inline fun global(block: (DevAccessibilityService) -> Boolean): String? {
        val svc = DevAccessibilityService.instance ?: return accessibilityOff()
        return if (block(svc)) null else "Action failed"
    }

    private fun click(text: String): String {
        val svc = DevAccessibilityService.instance ?: return accessibilityOff()
        val node = svc.findText(text) ?: return "Text nahi mila: $text"
        return if (svc.clickNode(node)) "Clicked $text" else "Click failed"
    }

    private suspend fun type(text: String): String {
        val svc = DevAccessibilityService.instance ?: return accessibilityOff()
        delay(150)
        return if (svc.setTextOnFocused(text)) "Typed" else "Koi input field focused nahi hai"
    }

    private suspend fun scroll(down: Boolean): String {
        val svc = DevAccessibilityService.instance ?: return accessibilityOff()
        val m = context.resources.displayMetrics
        val cx = m.widthPixels / 2f
        val startY = if (down) m.heightPixels * 0.75f else m.heightPixels * 0.25f
        val endY = if (down) m.heightPixels * 0.25f else m.heightPixels * 0.75f
        svc.swipe(cx, startY, cx, endY)
        return if (down) "Scrolled down" else "Scrolled up"
    }

    private fun readScreen(): String {
        val svc = DevAccessibilityService.instance ?: return accessibilityOff()
        return svc.readScreenText().take(800).ifBlank { "Screen empty" }
    }

    private fun accessibilityOff() =
        "Accessibility service OFF — Settings → Accessibility → DevBox AI ON karo"

    private fun adjustVolume(direction: Int): String {
        val am = context.getSystemService(AudioManager::class.java)
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return when (direction) {
            AudioManager.ADJUST_RAISE -> "Volume up"
            AudioManager.ADJUST_LOWER -> "Volume down"
            else -> "Muted"
        }
    }

    private fun setFlashlight(on: Boolean): String = try {
        val cm = context.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "Flashlight nahi hai"
        cm.setTorchMode(id, on)
        if (on) "Flashlight ON" else "Flashlight OFF"
    } catch (e: Exception) { "Flashlight error: ${e.message}" }

    private fun dial(number: String): String {
        val clean = number.replace(Regex("[^+\\d]"), "")
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return "Dialing $clean"
    }

    private fun callContact(name: String): String =
        if (name.matches(Regex("""[\d+\- ]+"""))) dial(name)
        else "Contact '$name' → contacts app open karo"

    private fun sendSms(to: String, body: String): String {
        context.startActivity(
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$to"))
                .putExtra("sms_body", body)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return "SMS opened for $to"
    }

    private fun openUrl(url: String): String {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return "Opening URL"
    }

    private fun search(query: String): String {
        context.startActivity(
            Intent(Intent.ACTION_WEB_SEARCH)
                .putExtra("query", query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return "Searching: $query"
    }

    private fun webSearch(query: String): String {
        val url = "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")
        return openUrl(url)
    }
}
