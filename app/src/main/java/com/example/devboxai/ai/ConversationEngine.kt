package com.example.devboxai.ai

import com.example.devboxai.data.ChatDao
import com.example.devboxai.data.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ConversationEngine(
    private val chatDao: ChatDao,
    private val apiKeyProvider: () -> String,
    private val contextProvider: () -> android.content.Context? = { null }
) {
    private val client = OkHttpClient()

    fun newConversationId(): String = UUID.randomUUID().toString()

    suspend fun send(conversationId: String, userText: String): String {
        chatDao.insert(ChatMessage(conversationId = conversationId, role = "user", content = userText))

        // Instant local commands (/mausam, /calc, /battery, ...) — no server needed
        QuickCommands.handle(contextProvider(), userText)?.let { localReply ->
            chatDao.insert(ChatMessage(conversationId = conversationId, role = "assistant", content = localReply))
            return localReply
        }

        // APK AI editor: "/apk edit ..." or natural language ("apk ka naam badal do")
        val ctx = contextProvider()
        val apkInstruction = com.example.devboxai.apk.ApkAiEditor.detectInstruction(userText)
        if (ctx != null && apkInstruction != null) {
            val editor = com.example.devboxai.apk.ApkAiEditor(ctx) { prompt -> askAiRaw(prompt) }
            val result = try { editor.handle(apkInstruction) }
                         catch (e: Exception) { "APK edit me error: ${e.message}" }
            chatDao.insert(ChatMessage(conversationId = conversationId, role = "assistant", content = result))
            return result
        }

        val history = chatDao.lastMessages(conversationId, 20).reversed()
        val key = apiKeyProvider()

        val reply = if (key.isNotBlank()) {
            askGemini(key, history)
        } else {
            askFreeServer(history)
        }

        chatDao.insert(ChatMessage(conversationId = conversationId, role = "assistant", content = reply))
        return reply
    }

    /** Single-prompt AI call (Gemini key if set, else free server). Used by the APK AI editor. */
    suspend fun askAiRaw(prompt: String): String {
        val one = listOf(ChatMessage(conversationId = "raw", role = "user", content = prompt))
        val key = apiKeyProvider()
        return if (key.isNotBlank()) askGemini(key, one) else askFreeServer(one)
    }

    /** Free no-key AI server (Pollinations). Used when no Gemini key is set. */
    private suspend fun askFreeServer(history: List<ChatMessage>): String {
        val messagesJson = JSONArray()
        history.forEach {
            messagesJson.put(JSONObject().put("role", it.role).put("content", it.content))
        }
        val payload = JSONObject().apply {
            put("model", "openai")
            put("messages", messagesJson)
        }
        val response = withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("https://text.pollinations.ai/openai")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) "Error: ${res.body?.string()}"
                else res.body?.string() ?: ""
            }
        }
        return try {
            JSONObject(response).getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message").getString("content")
        } catch (_: Exception) { response }
    }

    /** Google Gemini (free tier) — used when a Gemini API key is set in Settings. */
    private suspend fun askGemini(key: String, history: List<ChatMessage>): String {
        val contentsJson = JSONArray()
        history.forEach {
            val geminiRole = if (it.role == "assistant") "model" else "user"
            contentsJson.put(
                JSONObject()
                    .put("role", geminiRole)
                    .put("parts", JSONArray().put(JSONObject().put("text", it.content)))
            )
        }
        val payload = JSONObject().apply {
            put("contents", contentsJson)
        }
        val response = withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent")
                .header("x-goog-api-key", key)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) "Error: ${res.body?.string()}"
                else res.body?.string() ?: ""
            }
        }
        return try {
            JSONObject(response).getJSONArray("candidates")
                .getJSONObject(0).getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0).getString("text")
        } catch (_: Exception) { response }
    }
}
