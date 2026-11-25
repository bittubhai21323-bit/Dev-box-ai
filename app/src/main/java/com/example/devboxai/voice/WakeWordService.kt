package com.example.devboxai.voice

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.devboxai.NotificationHelper
import com.example.devboxai.controller.VoiceCommandManager

class WakeWordService : Service() {

    private var recognizer: SpeechRecognizer? = null
    private var listening = true
    private lateinit var manager: VoiceCommandManager

    private val keywords = listOf(
        "hey devbox", "hi devbox", "ok devbox", "devbox",
        "hey jarvis", "jarvis",
        "हे देवबॉक्स", "देवबॉक्स", "हे जार्विस", "जार्विस",
        "oye devbox", "arre devbox"
    )

    private val listener = object : RecognitionListener {
        override fun onResults(r: Bundle?) {
            val text = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.joinToString(" ")?.lowercase().orEmpty()

            if (keywords.any { text.contains(it) }) {
                NotificationHelper.show(this@WakeWordService, "DevBox", "Boss woke up ✨")
                manager.greet { _, resp, _ ->
                    NotificationHelper.show(this@WakeWordService, "DevBox says", resp)
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    manager.listenAndExecute { cmd, res, lang ->
                        NotificationHelper.show(this@WakeWordService,
                            "Command ($lang)", "$cmd → $res")
                    }
                }, 1800)
            }
            restart()
        }
        override fun onError(error: Int) { restart() }
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(p: Bundle?) {}
        override fun onEvent(t: Int, p: Bundle?) {}
    }

    private fun restart() {
        if (!listening) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer?.cancel()
            recognizer?.startListening(intent)
        } catch (_: Exception) {}
    }

    override fun onCreate() {
        super.onCreate()
        manager = VoiceCommandManager(this)
        NotificationHelper.createChannel(this)
        val notif: Notification = Notification.Builder(this, NotificationHelper.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("DevBox is listening")
            .setContentText("Say \"Hey DevBox\" — I'll reply in your language")
            .setOngoing(true).build()
        startForeground(200, notif)

        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(listener)
        }
        restart()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        listening = false
        recognizer?.destroy()
        if (this::manager.isInitialized) manager.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
