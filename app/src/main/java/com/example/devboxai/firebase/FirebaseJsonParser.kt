package com.example.devboxai.firebase

import org.json.JSONObject

object FirebaseJsonParser {
    fun parse(json: String): Result<FirebaseCredentials> = try {
        val root = JSONObject(json.trim())
        val projectInfo = root.getJSONObject("project_info")
        // File me 2 apps ho sakte hai (devboxai + dady.me) — sahi wala package se chuno,
        // pehla wala blindly lene se galat app connect ho jata tha
        val clients = root.getJSONArray("client")
        var client = clients.getJSONObject(0)
        for (i in 0 until clients.length()) {
            val c = clients.optJSONObject(i) ?: continue
            val pkg = c.optJSONObject("client_info")
                ?.optJSONObject("android_client_info")?.optString("package_name", "")
            if (pkg == "com.example.devboxai") { client = c; break }
        }
        val projectId = projectInfo.optString("project_id", "")
        val gcmSenderId = projectInfo.optString("project_number", "")
        val storageBucket = projectInfo.optString("storage_bucket", "")
        val databaseUrl = projectInfo.optString("firebase_url", "")
        val clientInfo = client.getJSONObject("client_info")
        val appId = clientInfo.optString("mobilesdk_app_id", "")
        val apiKey = client.optJSONArray("api_key")
            ?.optJSONObject(0)?.optString("current_key", "").orEmpty()
        val creds = FirebaseCredentials(projectId, appId, apiKey, gcmSenderId, storageBucket, databaseUrl)
        if (!creds.isValid()) Result.failure(IllegalArgumentException("Missing fields"))
        else Result.success(creds)
    } catch (e: Exception) { Result.failure(IllegalArgumentException("Invalid JSON: ${e.message}")) }
}
