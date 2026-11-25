package com.example.devboxai.firebase

import kotlinx.coroutines.tasks.await

object AdminAuth {
    suspend fun signIn(email: String, password: String): Result<String> = try {
        val auth = FirebaseInit.auth ?: return Result.failure(Exception("Firebase not configured"))
        val result = auth.signInWithEmailAndPassword(email, password).await()
        Result.success(result.user?.uid ?: "")
    } catch (e: Exception) { Result.failure(e) }

    suspend fun signUp(email: String, password: String): Result<String> = try {
        val auth = FirebaseInit.auth ?: return Result.failure(Exception("Firebase not configured"))
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        Result.success(result.user?.uid ?: "")
    } catch (e: Exception) { Result.failure(e) }

    fun signOut() = FirebaseInit.auth?.signOut()
    val currentUid: String? get() = FirebaseInit.auth?.currentUser?.uid
    val isSignedIn: Boolean get() = FirebaseInit.auth?.currentUser != null
}
