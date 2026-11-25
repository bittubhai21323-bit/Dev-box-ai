package com.example.devboxai.files

import androidx.documentfile.provider.DocumentFile

object CodeFileManager {
    private val codeExt = setOf(
        "kt","java","py","js","ts","jsx","tsx","rs","go","c","cpp","h","hpp","cs",
        "html","css","scss","json","xml","gradle","kts","md","yml","yaml","sh","rb","php","sql","toml","swift","dart"
    )
    fun isCode(file: DocumentFile): Boolean {
        val name = file.name ?: return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in codeExt
    }
    fun filter(files: List<DocumentFile>): List<DocumentFile> =
        files.filter { !it.isDirectory && isCode(it) }
}
