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
    private val apiKeyProvider: () -> String
) {
    private val client = OkHttpClient()

    fun newConversationId(): String = UUID.randomUUID().toString()

    suspend fun send(conversationId: String, userText: String): String {
        val key = apiKeyProvider()
        if (key.isBlank()) return "Settings → Gemini API key daalo. (Free key: aistudio.google.com)"

        chatDao.insert(ChatMessage(conversationId = conversationId, role = "user", content = userText))

        val history = chatDao.lastMessages(conversationId, 20).reversed()
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

        val reply = try {
            JSONObject(response).getJSONArray("candidates")
                .getJSONObject(0).getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0).getString("text")
        } catch (_: Exception) { response }

        chatDao.insert(ChatMessage(conversationId = conversationId, role = "assistant", content = reply))
        return reply
    }
}
