package com.example.devboxai.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class GitHubRepository {
    private val client = OkHttpClient()
    suspend fun repos(token: String): String = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext "Settings → GitHub token daalo."
        try {
            val req = Request.Builder()
                .url("https://api.github.com/user/repos?per_page=10")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json").build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (res.isSuccessful) body else "GitHub error: $body"
            }
        } catch (e: Exception) { "GitHub error: ${e.message}" }
    }
}
