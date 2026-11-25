package com.example.devboxai.ai

sealed class PhoneIntent {
    data class OpenApp(val name: String) : PhoneIntent()
    object Back : PhoneIntent()
    object Home : PhoneIntent()
    object Recents : PhoneIntent()
    object Notifications : PhoneIntent()
    object QuickSettings : PhoneIntent()
    object PowerDialog : PhoneIntent()
    object LockScreen : PhoneIntent()
    object Screenshot : PhoneIntent()
    data class Click(val text: String) : PhoneIntent()
    data class TypeText(val text: String) : PhoneIntent()
    data class Scroll(val down: Boolean) : PhoneIntent()
    object VolumeUp : PhoneIntent()
    object VolumeDown : PhoneIntent()
    object VolumeMute : PhoneIntent()
    object FlashlightOn : PhoneIntent()
    object FlashlightOff : PhoneIntent()
    data class Call(val contact: String) : PhoneIntent()
    data class Dial(val number: String) : PhoneIntent()
    data class Search(val query: String) : PhoneIntent()
    data class WebSearch(val query: String) : PhoneIntent()
    data class Sms(val to: String, val body: String) : PhoneIntent()
    data class OpenUrl(val url: String) : PhoneIntent()
    object ReadScreen : PhoneIntent()
    data class Repeat(val times: Int, val intent: PhoneIntent) : PhoneIntent()
    object Unknown : PhoneIntent()
}

object VoiceCommandParser {

    private val openPatterns = listOf(
        Regex("""(?:open|launch|start)\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""(.+?)\s+(?:kholo|khol|chalu karo|open karo)""", RegexOption.IGNORE_CASE)
    )

    fun parse(raw: String): PhoneIntent {
        val text = raw.trim().lowercase()
            .replace("please", "").replace("devbox", "")
            .replace("jarvis", "").replace("जरा", "")
            .trim().trim(',')

        if (text.isBlank()) return PhoneIntent.Unknown

        Regex("""(?:repeat|dohrao)\s+(\d+)\s+(.+)""").find(text)?.let {
            val n = it.groupValues[1].toIntOrNull() ?: 1
            return PhoneIntent.Repeat(n, parse(it.groupValues[2]))
        }

        if (Regex("""(?:read|padho|screen padho|what's on screen|screen par kya)""").containsMatchIn(text))
            return PhoneIntent.ReadScreen

        if (Regex("""^(?:go\s+)?back$|wapas|peeche""").containsMatchIn(text)) return PhoneIntent.Back
        if (Regex("""home|ghar jao|go home""").containsMatchIn(text)) return PhoneIntent.Home
        if (Regex("""recents|recent apps|recent""").containsMatchIn(text)) return PhoneIntent.Recents
        if (Regex("""notification|notif""").containsMatchIn(text)) return PhoneIntent.Notifications
        if (Regex("""quick settings|quick panel|notification panel""").containsMatchIn(text)) return PhoneIntent.QuickSettings
        if (Regex("""power menu|power dialog|shutdown menu""").containsMatchIn(text)) return PhoneIntent.PowerDialog
        if (Regex("""lock (?:screen|phone)|screen lock|phone lock""").containsMatchIn(text)) return PhoneIntent.LockScreen
        if (Regex("""screenshot|screen shot|screen capture""").containsMatchIn(text)) return PhoneIntent.Screenshot

        if (Regex("""volume up|volume badhao|awaaz badhao|louder""").containsMatchIn(text)) return PhoneIntent.VolumeUp
        if (Regex("""volume down|volume kam|awaaz kam|quieter|volume ghatao""").containsMatchIn(text)) return PhoneIntent.VolumeDown
        if (Regex("""mute|silent|volume off""").containsMatchIn(text)) return PhoneIntent.VolumeMute

        if (Regex("""flashlight on|torch on|flashlight chalu|light on""").containsMatchIn(text)) return PhoneIntent.FlashlightOn
        if (Regex("""flashlight off|torch off|flashlight band|light off""").containsMatchIn(text)) return PhoneIntent.FlashlightOff

        if (Regex("""scroll down|neeche scroll""").containsMatchIn(text)) return PhoneIntent.Scroll(true)
        if (Regex("""scroll up|upar scroll""").containsMatchIn(text)) return PhoneIntent.Scroll(false)

        Regex("""(?:click|tap|press|dabao)\s+(?:on\s+)?"?(.+?)"?$""").find(text)?.let {
            val target = it.groupValues[1].trim().trim('"')
            if (target.isNotBlank()) return PhoneIntent.Click(target)
        }

        Regex("""(?:type|write|likho|likh)\s+"?(.+?)"?$""").find(text)?.let {
            return PhoneIntent.TypeText(it.groupValues[1])
        }

        Regex("""(?:call|phone|dial)\s+(.+)""").find(text)?.let {
            val who = it.groupValues[1].trim()
            return if (who.matches(Regex("""[\d+\- ]+"""))) PhoneIntent.Dial(who)
            else PhoneIntent.Call(who)
        }

        Regex("""(?:send sms to|sms to|message)\s+(\S+)\s+(?:saying|saying:|that|:)\s+(.+)""").find(text)?.let {
            return PhoneIntent.Sms(it.groupValues[1], it.groupValues[2])
        }

        Regex("""(?:google|web search|search google for|internet par search)\s+(.+)""").find(text)?.let {
            return PhoneIntent.WebSearch(it.groupValues[1])
        }
        Regex("""search (?:for )?(.+)""").find(text)?.let {
            return PhoneIntent.Search(it.groupValues[1])
        }

        Regex("""(?:open|go to)\s+(https?://\S+)""").find(text)?.let {
            return PhoneIntent.OpenUrl(it.groupValues[1])
        }

        for (pattern in openPatterns) {
            pattern.find(text)?.let {
                val name = it.groupValues[1].trim().trim('"')
                if (name.isNotBlank()) return PhoneIntent.OpenApp(name)
            }
        }

        if (text.split(" ").size <= 2) return PhoneIntent.OpenApp(text)
        return PhoneIntent.Unknown
    }
}
