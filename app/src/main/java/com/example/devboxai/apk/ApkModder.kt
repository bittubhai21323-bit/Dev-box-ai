package com.example.devboxai.apk

import android.content.Context
import android.net.Uri
import com.example.devboxai.termux.TermuxHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ApkModder(private val context: Context) {

    companion object {
        const val WORK_DIR = "/sdcard/DevBoxAPK"
        const val INPUT_APK = "$WORK_DIR/input.apk"
        const val DECODED_DIR = "$WORK_DIR/decoded"
        const val UNSIGNED_APK = "$WORK_DIR/unsigned.apk"
        const val ALIGNED_APK = "$WORK_DIR/aligned.apk"
        const val SIGNED_APK = "$WORK_DIR/signed.apk"
        const val KEYSTORE = "$WORK_DIR/devbox_keystore.p12"
    }

    data class Step(val ok: Boolean, val log: String)

    private fun toolHint(out: String): String =
        if (out.contains("command not found") || out.contains("No such file"))
            "\n⚠ Tool nahi mila — APK tab ke Settings me 'Install in Termux' dabao, install pura hone do (2-3 minute), phir dubara try karo."
        else ""

    suspend fun ensureWorkspace(): Step = withContext(Dispatchers.IO) {
        val r = TermuxHelper.runAndWait(context, "mkdir -p $WORK_DIR && rm -rf $DECODED_DIR && echo READY")
        if (r == null) return@withContext Step(false, "Termux se jawab nahi aaya. ${TermuxHelper.lastError ?: ""} — Termux permission aur allow-external-apps check karo")
        Step(r.stdout.contains("READY"), r.stdout + r.stderr)
    }

    suspend fun importApk(uri: Uri): Step = withContext(Dispatchers.IO) {
        try {
            val target = File(INPUT_APK)
            target.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext Step(false, "Cannot open picked file")
            Step(true, "Imported ${target.name} (${target.length() / 1024 / 1024}MB)")
        } catch (e: Exception) { Step(false, "Copy failed: ${e.message}") }
    }

    suspend fun decompile(): Step = withContext(Dispatchers.IO) {
        val r = TermuxHelper.runAndWait(context, "cd $WORK_DIR && rm -rf decoded && apktool d -f input.apk -o decoded 2>&1 && echo __DONE__")
        if (r == null) return@withContext Step(false, "Termux se jawab nahi aaya (timeout). ${TermuxHelper.lastError ?: ""} — Termux permission aur allow-external-apps check karo")
        val ok = r.stdout.contains("__DONE__") || r.exit == 0
        Step(ok, "exit=${r.exit}\n${r.stdout.takeLast(1500)}\n${r.stderr.takeLast(800)}${toolHint(r.stdout + r.stderr)}")
    }

    suspend fun recompile(): Step = withContext(Dispatchers.IO) {
        val r = TermuxHelper.runAndWait(context, "cd $WORK_DIR && rm -f unsigned.apk && apktool b decoded -o unsigned.apk 2>&1 && echo __DONE__")
        if (r == null) return@withContext Step(false, "Termux se jawab nahi aaya (timeout). ${TermuxHelper.lastError ?: ""} — Termux permission aur allow-external-apps check karo")
        val ok = r.stdout.contains("__DONE__") && File(UNSIGNED_APK).exists()
        Step(ok, "exit=${r.exit}\n${r.stdout.takeLast(1500)}\n${r.stderr.takeLast(800)}${toolHint(r.stdout + r.stderr)}")
    }

    suspend fun zipalign(): Step = withContext(Dispatchers.IO) {
        val r = TermuxHelper.runAndWait(context, "cd $WORK_DIR && rm -f aligned.apk && zipalign -p -f 4 unsigned.apk aligned.apk && echo __DONE__")
        if (r == null) return@withContext Step(false, "Termux se jawab nahi aaya (timeout). ${TermuxHelper.lastError ?: ""} — Termux permission aur allow-external-apps check karo")
        val ok = r.stdout.contains("__DONE__") && File(ALIGNED_APK).exists()
        Step(ok, "exit=${r.exit}\n${r.stdout.takeLast(800)}${toolHint(r.stdout + r.stderr)}")
    }

    suspend fun sign(password: String, alias: String = "devbox"): Step = withContext(Dispatchers.IO) {
        val r = TermuxHelper.runAndWait(context, "cd $WORK_DIR && rm -f signed.apk && apksigner sign --ks devbox_keystore.p12 --ks-pass pass:$password --ks-key-alias $alias --out signed.apk aligned.apk 2>&1 && echo __DONE__")
        if (r == null) return@withContext Step(false, "Termux se jawab nahi aaya (timeout). ${TermuxHelper.lastError ?: ""} — Termux permission aur allow-external-apps check karo")
        val ok = r.stdout.contains("__DONE__") && File(SIGNED_APK).exists()
        Step(ok, "exit=${r.exit}\n${r.stdout.takeLast(1200)}\n${r.stderr.takeLast(800)}${toolHint(r.stdout + r.stderr)}")
    }

    suspend fun verify(): Step = withContext(Dispatchers.IO) {
        val r = TermuxHelper.runAndWait(context, "cd $WORK_DIR && apksigner verify --verbose signed.apk 2>&1")
        if (r == null) return@withContext Step(false, "Termux se jawab nahi aaya (timeout). ${TermuxHelper.lastError ?: ""} — Termux permission aur allow-external-apps check karo")
        Step(r.exit == 0, "exit=${r.exit}\n${r.stdout}\n${r.stderr}${toolHint(r.stdout + r.stderr)}")
    }

    suspend fun listDir(relativePath: String = ""): List<FileNode> = withContext(Dispatchers.IO) {
        val dir = if (relativePath.isBlank()) DECODED_DIR else "$DECODED_DIR/$relativePath"
        val r = TermuxHelper.runAndWait(context, "find '$dir' -mindepth 1 -maxdepth 1 -printf '%y|%s|%p\\n' 2>/dev/null | sort")
        if (r == null) return@withContext emptyList()
        r.stdout.lines().filter { it.length > 4 }.mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            if (parts.size < 3) return@mapNotNull null
            FileNode(
                name = parts[2].substringAfterLast("/"),
                relativePath = parts[2].removePrefix("$DECODED_DIR/"),
                isDirectory = parts[0] == "d",
                sizeBytes = parts[1].toLongOrNull() ?: 0L
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    suspend fun readFileText(relativePath: String): String? = withContext(Dispatchers.IO) {
        val r = TermuxHelper.runAndWait(context, "base64 '$DECODED_DIR/$relativePath' 2>/dev/null")
        if (r == null || r.exit != 0) return@withContext null
        try {
            android.util.Base64.decode(r.stdout.filter { !it.isWhitespace() }, android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    suspend fun writeFileText(relativePath: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val b64 = android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        val r = TermuxHelper.runAndWait(context, "echo '$b64' | base64 -d > '$DECODED_DIR/$relativePath' && echo OK")
        r?.stdout?.contains("OK") == true
    }

    data class FileNode(val name: String, val relativePath: String, val isDirectory: Boolean, val sizeBytes: Long)
}
