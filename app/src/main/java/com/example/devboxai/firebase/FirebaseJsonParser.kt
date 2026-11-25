package com.example.devboxai.firebase

import org.json.JSONObject

object FirebaseJsonParser {
    fun parse(json: String): Result<FirebaseCredentials> = try {
        val root = JSONObject(json.trim())
        val projectInfo = root.getJSONObject("project_info")
        val client = root.getJSONArray("client").getJSONObject(0)
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
