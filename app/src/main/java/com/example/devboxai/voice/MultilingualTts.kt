package com.example.devboxai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class MultilingualTts(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) ready = true
    }

    fun speak(text: String, langTag: String) {
        if (!ready || text.isBlank()) return
        val locale = Locale.forLanguageTag(langTag)
        val result = tts.isLanguageAvailable(locale)
        val used = if (result >= TextToSpeech.LANG_AVAILABLE) {
            tts.language = locale; locale
        } else {
            val base = Locale(langTag.substringBefore("-"))
            if (tts.isLanguageAvailable(base) >= TextToSpeech.LANG_AVAILABLE) {
                tts.language = base; base
            } else {
                tts.language = Locale.US; Locale.US
            }
        }
        Log.d("MultilingualTts", "speak lang=$langTag used=${used.toLanguageTag()}")
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ml_tts_${System.nanoTime()}")
    }

    fun stop() = tts.stop()
    fun shutdown() { tts.stop(); tts.shutdown() }
}
