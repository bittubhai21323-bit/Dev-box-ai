package com.example.devboxai.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class UpdateManager(private val context: Context) {

    data class UpdateInfo(
        val available: Boolean,
        val versionName: String = "",
        val versionCode: Int = 0,
        val apkUrl: String = "",
        val changelog: String = "",
        val mandatory: Boolean = false
    )

    fun checkFromRemoteConfig(
        latestCode: Int, latestName: String, apkUrl: String, changelog: String, mandatory: Boolean
    ): UpdateInfo {
        val currentCode = currentVersionCode()
        val available = latestCode > currentCode && apkUrl.isNotBlank()
        return UpdateInfo(available, latestName, latestCode, apkUrl, changelog, mandatory)
    }

    suspend fun downloadApk(url: String, onProgress: (Int) -> Unit = {}): File? =
        withContext(Dispatchers.IO) {
            try {
                val out = File(context.cacheDir, "updates").apply { mkdirs() }
                    .resolve("devbox_update.apk")
                if (out.exists()) out.delete()

                val req = Request.Builder().url(url).build()
                OkHttpClient().newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@withContext null
                    val body = res.body ?: return@withContext null
                    val total = body.contentLength()
                    var read = 0L
                    body.byteStream().use { ins ->
                        out.outputStream().use { os ->
                            val buf = ByteArray(8192)
                            while (true) {
                                val n = ins.read(buf)
                                if (n <= 0) break
                                os.write(buf, 0, n)
                                read += n
                                if (total > 0) onProgress(((read * 100) / total).toInt())
                            }
                        }
                    }
                }
                out
            } catch (e: Exception) { null }
        }

    fun canInstallPackages(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.packageManager.canRequestPackageInstalls()
        else true

    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstallPackages()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun installApk(apkFile: File): Boolean {
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", apkFile
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val installer = context.packageManager.packageInstaller
                val sessionId = installer.createSession(
                    PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                )
                installer.openSession(sessionId).use { s ->
                    apkFile.inputStream().use { ins ->
                        s.openWrite("base.apk", 0, apkFile.length()).use { os -> ins.copyTo(os) }
                    }
                    val intent = Intent(context, InstallResultReceiver::class.java)
                    val pending = PendingIntent.getBroadcast(
                        context, sessionId, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                    s.commit(pending.intentSender)
                }
                true
            } else {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }
        } catch (e: Exception) { false }
    }

    fun installApkFallback(apkFile: File): Boolean {
        if (canInstallPackages()) return installApk(apkFile)
        requestInstallPermission()
        return false
    }

    fun currentVersionCode(): Int = try {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt()
        else @Suppress("DEPRECATION") pi.versionCode
    } catch (_: Exception) { 0 }

    fun currentVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }
}
