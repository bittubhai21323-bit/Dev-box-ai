package com.example.devboxai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceInputHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    private var recognizer: SpeechRecognizer? = null

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition unavailable"); return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(r: Bundle?) {
                    val text = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    onResult(text)
                    destroy()
                }
                override fun onError(error: Int) {
                    onError("Voice error: $error"); destroy()
                }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            }
            startListening(intent)
        }
    }

    fun destroy() { recognizer?.destroy(); recognizer = null }
}
