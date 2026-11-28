package com.example.devboxai.apk

import android.content.Context
import com.example.devboxai.termux.TermuxHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class KeystoreManager(private val context: Context) {

    data class Result(val ok: Boolean, val message: String, val alias: String = "devbox")

    suspend fun exists(): Boolean = File(ApkModder.KEYSTORE).exists()

    suspend fun generate(password: String): Result = withContext(Dispatchers.IO) {
        if (password.length < 6) return@withContext Result(false, "Password min 6 chars")
        if (exists()) return@withContext Result(true, "Keystore already exists", "devbox")

        val cmd = "cd ${ApkModder.WORK_DIR} && openssl req -x509 -newkey rsa:2048 -keyout _k.pem -out _c.pem -days 10000 -nodes -subj '/CN=DevBox/O=DevBox/C=IN' 2>/dev/null && openssl pkcs12 -export -inkey _k.pem -in _c.pem -out devbox_keystore.p12 -passout pass:$password -name devbox 2>/dev/null && rm -f _k.pem _c.pem && echo __DONE__"

        val r = TermuxHelper.runAndWait(context, cmd, timeoutMs = 60_000)
            ?: return@withContext Result(false, "Termux se jawab nahi aaya (timeout). ${TermuxHelper.lastError ?: ""}")

        if (r.stdout.contains("__DONE__") && File(ApkModder.KEYSTORE).exists()) {
            Result(true, "Keystore created: devbox_keystore.p12", "devbox")
        } else {
            Result(false, "Failed: ${r.stdout.takeLast(500)}")
        }
    }

    fun delete(): Boolean = File(ApkModder.KEYSTORE).delete()
    fun keystorePath(): String = ApkModder.KEYSTORE
}
