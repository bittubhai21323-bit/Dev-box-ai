package com.example.devboxai.backup

import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {

    private const val MAGIC = "DBBK"
    private const val ITERATIONS = 200_000
    private const val KEY_BITS = 256

    fun encrypt(inputZip: File, outputEnc: File, password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        }
        outputEnc.outputStream().use { raw ->
            raw.write(MAGIC.toByteArray()); raw.write(1)
            raw.write(salt); raw.write(iv)
            CipherOutputStream(raw, cipher).use { enc -> inputZip.inputStream().use { it.copyTo(enc) } }
        }
    }

    fun decrypt(inputEnc: File, outputZip: File, password: String) {
        inputEnc.inputStream().use { raw ->
            val magic = ByteArray(4).also { raw.readFully(it) }
            require(String(magic) == MAGIC) { "Not a DevBox backup" }
            raw.read()
            val salt = ByteArray(16).also { raw.readFully(it) }
            val iv = ByteArray(12).also { raw.readFully(it) }
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            }
            outputZip.outputStream().use { out -> CipherInputStream(raw, cipher).use { it.copyTo(out) } }
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun java.io.InputStream.readFully(buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = read(buf, off, buf.size - off)
            if (n < 0) throw java.io.EOFException()
            off += n
        }
    }
}
