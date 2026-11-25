package com.example.devboxai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsHelper(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            ready = true
        }
    }

    fun speak(text: String) {
        if (ready && text.isNotBlank())
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "devbox")
    }

    fun shutdown() { tts.stop(); tts.shutdown() }
}
