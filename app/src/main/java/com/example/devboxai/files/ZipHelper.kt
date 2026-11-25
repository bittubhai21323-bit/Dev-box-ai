package com.example.devboxai.files

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipHelper {

    fun zip(context: Context, sourceDir: DocumentFile, dest: DocumentFile) {
        context.contentResolver.openOutputStream(dest.uri)?.use { out ->
            ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                addTree(context, sourceDir, "", zip)
            }
        }
    }

    private fun addTree(ctx: Context, dir: DocumentFile, base: String, zip: ZipOutputStream) {
        dir.listFiles().forEach { f ->
            val path = "$base${f.name}"
            if (f.isDirectory) {
                zip.putNextEntry(ZipEntry("$path/")); zip.closeEntry()
                addTree(ctx, f, "$path/", zip)
            } else {
                zip.putNextEntry(ZipEntry(path))
                ctx.contentResolver.openInputStream(f.uri)?.use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun unzip(context: Context, zipFile: DocumentFile, destDir: DocumentFile) {
        context.contentResolver.openInputStream(zipFile.uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val parts = entry.name.split('/').filter { it.isNotEmpty() }
                    var current = destDir
                    for (i in parts.indices) {
                        val isLast = i == parts.lastIndex
                        current = current.findFile(parts[i])
                            ?: if (isLast && !entry.isDirectory)
                                current.createFile("application/octet-stream", parts[i]) ?: current
                            else current.createDirectory(parts[i]) ?: current
                    }
                    if (!entry.isDirectory)
                        context.contentResolver.openOutputStream(current.uri)?.use { zip.copyTo(it) }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }
}
