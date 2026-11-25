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
        if (key.isBlank()) return "Settings → OpenAI API key daalo."

        chatDao.insert(ChatMessage(conversationId = conversationId, role = "user", content = userText))

        val history = chatDao.lastMessages(conversationId, 20).reversed()
        val messagesJson = JSONArray()
        history.forEach {
            messagesJson.put(JSONObject().put("role", it.role).put("content", it.content))
        }

        val payload = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", messagesJson)
        }

        val response = withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer $key")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) "Error: ${res.body?.string()}"
                else res.body?.string() ?: ""
            }
        }

        val reply = try {
            JSONObject(response).getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message").getString("content")
        } catch (_: Exception) { response }

        chatDao.insert(ChatMessage(conversationId = conversationId, role = "assistant", content = reply))
        return reply
    }
}
