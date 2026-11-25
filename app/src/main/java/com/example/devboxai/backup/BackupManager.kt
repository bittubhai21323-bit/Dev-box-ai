package com.example.devboxai.backup

import android.content.Context
import android.util.Log
import com.example.devboxai.DevBoxApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupManager(private val context: Context) {

    private val app = context.applicationContext as DevBoxApp

    data class Result(val ok: Boolean, val file: File? = null, val message: String = "")

    suspend fun createBackup(password: String): Result = withContext(Dispatchers.IO) {
        try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
            val tmpZip = File(backupDir, "tmp_$stamp.zip")
            val finalEnc = File(backupDir, "devbox_$stamp.dbk")

            try {
                app.db.openHelper.writableDatabase
                    .query("PRAGMA wal_checkpoint(TRUNCATE)").use { }
            } catch (e: Exception) { Log.w("Backup", "checkpoint failed", e) }

            ZipOutputStream(tmpZip.outputStream().buffered()).use { zip ->
                addFile(zip, context.getDatabasePath("devbox.db"), "db/devbox.db")
                context.filesDir.parentFile?.let { dataDir ->
                    File(dataDir, "shared_prefs").listFiles()?.forEach { f ->
                        if (f.name.startsWith("devbox")) addFile(zip, f, "prefs/${f.name}")
                    }
                }
                val manifest = buildString {
                    appendLine("version=${appVersion()}")
                    appendLine("created=${System.currentTimeMillis()}")
                    appendLine("deviceId=${app.deviceId}")
                    appendLine("android=${android.os.Build.VERSION.SDK_INT}")
                }
                zip.putNextEntry(ZipEntry("manifest.txt"))
                zip.write(manifest.toByteArray()); zip.closeEntry()
            }

            BackupCrypto.encrypt(tmpZip, finalEnc, password)
            tmpZip.delete()
            pruneOldBackups(backupDir)

            Result(true, finalEnc, "Backup created: ${finalEnc.name} (${finalEnc.length() / 1024}KB)")
        } catch (e: Exception) {
            Log.e("Backup", "backup failed", e)
            Result(false, null, "Backup error: ${e.message}")
        }
    }

    suspend fun restoreBackup(encFile: File, password: String): Result =
        withContext(Dispatchers.IO) {
            try {
                val tmpZip = File(context.cacheDir, "restore_${System.currentTimeMillis()}.zip")
                BackupCrypto.decrypt(encFile, tmpZip, password)
                var restored = 0
                java.util.zip.ZipInputStream(tmpZip.inputStream().buffered()).use { zip ->
                    var e = zip.nextEntry
                    while (e != null) {
                        val name = e.name
                        if (name.startsWith("db/")) {
                            val target = context.getDatabasePath("devbox.db")
                            target.parentFile?.mkdirs()
                            try { app.db.close() } catch (_: Exception) {}
                            target.outputStream().use { zip.copyTo(it) }
                            File(target.path + "-wal").delete()
                            File(target.path + "-shm").delete()
                            restored++
                        } else if (name.startsWith("prefs/")) {
                            val fileName = name.removePrefix("prefs/")
                            val target = File(context.filesDir.parentFile, "shared_prefs/$fileName")
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                            restored++
                        }
                        zip.closeEntry(); e = zip.nextEntry
                    }
                }
                tmpZip.delete()
                Result(true, null, "Restored $restored files. Restart app to load.")
            } catch (e: Exception) {
                Result(false, null, "Restore error: ${e.message}")
            }
        }

    fun listBackups(): List<File> {
        val dir = File(context.filesDir, "backups")
        return dir.listFiles { f -> f.extension == "dbk" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    private fun addFile(zip: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists()) return
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
    }

    private fun pruneOldBackups(dir: File) {
        dir.listFiles { f -> f.extension == "dbk" }
            ?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
    }

    private fun appVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }
}
