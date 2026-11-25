package com.example.devboxai.firebase

data class FirebaseCredentials(
    val projectId: String,
    val applicationId: String,
    val apiKey: String,
    val gcmSenderId: String,
    val storageBucket: String,
    val databaseUrl: String = ""
) {
    fun isValid(): Boolean =
        projectId.isNotBlank() && applicationId.isNotBlank() &&
        apiKey.isNotBlank() && gcmSenderId.isNotBlank()

    fun masked(): String = """
        projectId: $projectId
        appId: ${applicationId.take(20)}…
        apiKey: ${apiKey.take(8)}…
        senderId: $gcmSenderId
        bucket: $storageBucket
    """.trimIndent()
}
