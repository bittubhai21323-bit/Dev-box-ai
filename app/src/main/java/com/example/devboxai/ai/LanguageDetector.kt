package com.example.devboxai.ai

import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.tasks.await

object LanguageDetector {

    private val client = LanguageIdentification.getClient()

    suspend fun detect(text: String): String {
        if (text.isBlank()) return deviceLang()
        scriptGuess(text)?.let { return it }
        return try {
            val code = client.identifyLanguage(text).await()
            if (code == "und") deviceLang() else code
        } catch (_: Exception) { deviceLang() }
    }

    fun base(lang: String): String = lang.substringBefore("-").lowercase()

    private fun scriptGuess(text: String): String? {
        for (ch in text) {
            when (ch.code) {
                in 0x0900..0x097F -> return "hi"
                in 0x0980..0x09FF -> return "bn"
                in 0x0B80..0x0BFF -> return "ta"
                in 0x0C00..0x0C7F -> return "te"
                in 0x0A80..0x0AFF -> return "gu"
                in 0x0D00..0x0D7F -> return "ml"
                in 0x0E00..0x0E7F -> return "th"
                in 0x0600..0x06FF -> return "ar"
                in 0x0400..0x04FF -> return "ru"
                in 0x4E00..0x9FFF -> return "zh"
                in 0x3040..0x30FF -> return "ja"
                in 0xAC00..0xD7AF -> return "ko"
            }
        }
        return null
    }

    private fun deviceLang(): String =
        java.util.Locale.getDefault().language.ifBlank { "en" }
}
