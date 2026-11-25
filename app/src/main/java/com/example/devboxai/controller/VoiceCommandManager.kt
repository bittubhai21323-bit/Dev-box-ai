package com.example.devboxai.controller

import android.content.Context
import com.example.devboxai.DevBoxApp
import com.example.devboxai.SettingsStore
import com.example.devboxai.ai.ConversationEngine
import com.example.devboxai.ai.LanguageDetector
import com.example.devboxai.ai.Personality
import com.example.devboxai.ai.PhoneIntent
import com.example.devboxai.ai.VoiceCommandParser
import com.example.devboxai.voice.MultilingualTts
import com.example.devboxai.voice.VoiceInputHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VoiceCommandManager(private val context: Context) {

    private val controller = PhoneController(context)
    private val tts = MultilingualTts(context)
    private val app = context.applicationContext as DevBoxApp
    private val store = SettingsStore(context)
    private val aiEngine = ConversationEngine(app.db.chatDao()) { store.openAiKey }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val aiConversationId = "voice-fallback"

    fun speak(text: String, lang: String = "en") { tts.speak(text, lang) }

    fun greet(onHistory: (String, String, String) -> Unit = { _, _, _ -> }) {
        scope.launch {
            val lang = LanguageDetector.detect("hello")
            val tmpl = Personality.forLang(lang)
            speak(tmpl.onWake, lang)
            onHistory("", tmpl.onWake, lang)
        }
    }

    fun listenAndExecute(onHistory: (String, String, String) -> Unit = { _, _, _ -> }) {
        val helper = VoiceInputHelper(
            context,
            onResult = { raw ->
                scope.launch {
                    val lang = LanguageDetector.detect(raw)
                    val tmpl = Personality.forLang(lang)
                    speak(tmpl.onListenting, lang)
                    val response = executeInternal(raw, lang)
                    speak(response, lang)
                    onHistory(raw, response, lang)
                }
                helper.destroy()
            },
            onError = { err -> onHistory("", err, "en") }
        )
        helper.start()
    }

    fun execute(text: String, onHistory: (String, String, String) -> Unit = { _, _, _ -> }) {
        scope.launch {
            val lang = LanguageDetector.detect(text)
            val response = executeInternal(text, lang)
            speak(response, lang)
            onHistory(text, response, lang)
        }
    }

    private suspend fun executeInternal(text: String, lang: String): String {
        val tmpl = Personality.forLang(lang)
        val intent: PhoneIntent = VoiceCommandParser.parse(text)
        return if (intent is PhoneIntent.Unknown) {
            aiFallback(text, lang)
        } else {
            val result = controller.execute(intent)
            val successKeywords = listOf("OK", "done", "Opening", "Dialing", "Typed",
                "Clicked", "Scrolled", "Volume", "Flashlight", "Searching", "Screenshot", "Locked")
            val isSuccess = successKeywords.any { result.contains(it, ignoreCase = true) }
            if (isSuccess) tmpl.success(result) else tmpl.error(result)
        }
    }

    private suspend fun aiFallback(text: String, lang: String): String {
        val langName = when (LanguageDetector.base(lang)) {
            "hi" -> "Hindi"; "es" -> "Spanish"; "fr" -> "French"; "de" -> "German"
            "pt" -> "Portuguese"; "ru" -> "Russian"; "ar" -> "Arabic"; "zh" -> "Chinese"
            "ja" -> "Japanese"; "ko" -> "Korean"; "it" -> "Italian"; "tr" -> "Turkish"
            "bn" -> "Bengali"; "ta" -> "Tamil"; "te" -> "Telugu"; "ur" -> "Urdu"
            "id" -> "Indonesian"; else -> "English"
        }
        val prompt = "You are DevBox, a friendly phone assistant. The user speaks $langName. " +
            "Reply ONLY in $langName, maximum 2 short sentences. Address user warmly as 'Boss' " +
            "(or local equivalent: जी बॉस / jefe / chef / босс / 老板 / ボス). User said: $text"
        return try { aiEngine.send(aiConversationId, prompt).trim() }
        catch (e: Exception) { Personality.forLang(lang).error("AI unavailable") }
    }

    fun shutdown() { tts.shutdown() }
}
