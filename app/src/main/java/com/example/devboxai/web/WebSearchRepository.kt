package com.example.devboxai.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class WebSearchRepository {
    private val client = OkHttpClient()

    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://api.duckduckgo.com/?q=$q&format=json&no_html=1&skip_disambig=1").build()
            client.newCall(req).execute().use { res ->
                val obj = JSONObject(res.body?.string().orEmpty())
                val abstract = obj.optString("AbstractText", "")
                if (abstract.isNotBlank()) return@withContext abstract
                val related = obj.optJSONArray("RelatedTopics")
                if (related != null && related.length() > 0) {
                    val sb = StringBuilder()
                    for (i in 0 until minOf(5, related.length())) {
                        val t = related.optJSONObject(i)?.optString("Text", "").orEmpty()
                        if (t.isNotBlank()) sb.append("• ").append(t).append("\n")
                    }
                    return@withContext sb.toString().ifBlank { "No results." }
                }
                "No results for: $query"
            }
        } catch (e: Exception) { "Search error: ${e.message}" }
    }
}
