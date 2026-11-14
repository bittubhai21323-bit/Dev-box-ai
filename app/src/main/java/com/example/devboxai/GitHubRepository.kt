package com.example.devboxai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class GitHubRepository {
    private val client = OkHttpClient()

    suspend fun repos(token: String): String = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext "Settings me GitHub token daalo."

        val request = Request.Builder()
            .url("https://api.github.com/user/repos?per_page=10")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "No response"
            if (response.isSuccessful) body else "GitHub error: $body"
        }
    }
}
