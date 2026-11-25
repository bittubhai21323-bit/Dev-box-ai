package com.example.devboxai.files

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class FileManager(private val context: Context) {
    fun fromTree(treeUri: Uri): DocumentFile? = DocumentFile.fromTreeUri(context, treeUri)
    fun list(dir: DocumentFile): List<DocumentFile> =
        dir.listFiles().sortedWith(compareBy({ !it.isDirectory }, { it.name ?: "" }))
    fun createFile(dir: DocumentFile, name: String): DocumentFile? =
        dir.createFile("application/octet-stream", name)
    fun createDir(dir: DocumentFile, name: String): DocumentFile? = dir.createDirectory(name)
    fun delete(file: DocumentFile): Boolean = file.delete()
    fun rename(file: DocumentFile, newName: String): Boolean = file.renameTo(newName)
    fun readText(file: DocumentFile): String = try {
        context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() } ?: ""
    } catch (_: Exception) { "" }
}
