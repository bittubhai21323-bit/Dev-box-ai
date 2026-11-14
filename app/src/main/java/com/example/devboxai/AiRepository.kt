package com.example.devboxai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class AiRepository {
    private val client = OkHttpClient()

    suspend fun ask(prompt: String, apiKey: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "Settings me OpenAI API key daalo."

        val json = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", JSONArray().put(
                JSONObject().put("role", "user").put("content", prompt)
            ))
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@withContext "No response"
            if (!response.isSuccessful) return@withContext "AI error: $body"

            JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }
}
