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

    companion object {
        private const val RELEASE_API =
            "https://api.github.com/repos/bittubhai21323-bit/Dev-box-ai/releases/tags/devbox-v11-debug"
        private const val PREF_STAMP = "installed_asset_stamp"
    }

    /**
     * Checks the GitHub release for a newer APK. The release asset is replaced
     * on every build, so its "updated_at" stamp identifies the build.
     * On the very first check the current stamp is stored (the installed APK
     * is treated as current). Call markInstalled() right before installing
     * a downloaded update so the new version does not re-report it.
     */
    suspend fun checkFromGitHub(): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(RELEASE_API)
                .header("Accept", "application/vnd.github+json").build()
            OkHttpClient().newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext UpdateInfo(false)
                val body = res.body?.string() ?: return@withContext UpdateInfo(false)
                val json = org.json.JSONObject(body)
                val assets = json.optJSONArray("assets") ?: return@withContext UpdateInfo(false)
                var stamp = ""
                var url = ""
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    if (a.optString("name") == "app-debug.apk") {
                        stamp = a.optString("updated_at")
                        url = a.optString("browser_download_url")
                        break
                    }
                }
                if (stamp.isBlank() || url.isBlank()) return@withContext UpdateInfo(false)
                val prefs = context.getSharedPreferences("devbox_update", Context.MODE_PRIVATE)
                val stored = prefs.getString(PREF_STAMP, null)
                // If we never recorded an install stamp, compare the release
                // asset time with this APK's last-update time instead of
                // blindly treating the current build as up-to-date.
                val available = if (stored != null) {
                    stored != stamp
                } else {
                    val assetTime = try {
                        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                            .parse(stamp)?.time ?: 0L
                    } catch (_: Exception) { 0L }
                    val installedAt = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
                    } catch (_: Exception) { 0L }
                    assetTime > installedAt + 120_000
                }
                UpdateInfo(
                    available = available,
                    versionName = "naya build",
                    apkUrl = url,
                    changelog = "Stamp: $stamp"
                )
            }
        } catch (e: Exception) {
            Log.w("UpdateManager", "GitHub check failed", e)
            UpdateInfo(false)
        }
    }

    fun markInstalled(stamp: String) {
        context.getSharedPreferences("devbox_update", Context.MODE_PRIVATE)
            .edit().putString(PREF_STAMP, stamp).apply()
    }

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
