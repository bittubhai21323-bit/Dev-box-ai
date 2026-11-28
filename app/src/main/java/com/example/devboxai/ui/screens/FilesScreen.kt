package com.example.devboxai.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.example.devboxai.files.CodeFileManager
import com.example.devboxai.files.FileManager
import com.example.devboxai.files.ZipHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FilesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fm = remember { FileManager(context) }
    var current by remember { mutableStateOf<DocumentFile?>(null) }
    var files by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    val pathStack = remember { mutableStateListOf<DocumentFile>() }
    var zipStatus by rememberSaveable { mutableStateOf("") }
    var zipping by remember { mutableStateOf(false) }
    var codeOnly by rememberSaveable { mutableStateOf(false) }
    // Remember the picked root folder so returning to this tab restores it
    var rootUri by rememberSaveable { mutableStateOf<String?>(null) }

    fun load(dir: DocumentFile) {
        current = dir
        val raw = fm.list(dir)
        files = if (codeOnly) CodeFileManager.filter(raw) else raw
    }

    fun openDir(dir: DocumentFile) {
        current?.let { pathStack.add(it) }
        load(dir)
    }

    fun goBack() {
        if (pathStack.isNotEmpty()) load(pathStack.removeAt(pathStack.lastIndex))
    }

    BackHandler(enabled = pathStack.isNotEmpty()) { goBack() }

    LaunchedEffect(Unit) {
        if (current == null) {
            // Restore from saved state, else from prefs (survives app restart/update)
            val saved = rootUri ?: context.getSharedPreferences("devbox_files", android.content.Context.MODE_PRIVATE)
                .getString("root_uri", null)
            saved?.let {
                rootUri = it
                try { fm.fromTree(android.net.Uri.parse(it))?.let { dir -> load(dir) } } catch (_: Exception) {}
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
            rootUri = uri.toString()
            context.getSharedPreferences("devbox_files", android.content.Context.MODE_PRIVATE)
                .edit().putString("root_uri", uri.toString()).apply()
            pathStack.clear()
            fm.fromTree(uri)?.let { load(it) }
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(null) }) { Text("Pick folder") }
            Button(onClick = { current?.let { load(it) } }) { Text("Refresh") }
            Button(onClick = { codeOnly = !codeOnly; current?.let { load(it) } }) { Text(if (codeOnly) "All" else "Code only") }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (pathStack.isNotEmpty()) {
                TextButton(onClick = { goBack() }) { Text("⬆ Upar") }
            }
            Text(current?.name ?: "No folder", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
        }
        HorizontalDivider()
        if (current != null && files.isEmpty()) {
            Text("Folder khali hai", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            items(files, key = { it.uri.toString() }) { f ->
                ListItem(
                    headlineContent = { Text((if (f.isDirectory) "📁 " else "📄 ") + (f.name ?: "")) },
                    modifier = Modifier.clickable {
                        if (f.isDirectory) openDir(f)
                        else {
                            // File ko uske app me kholo
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(f.uri, f.type ?: "*/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Open with"))
                            } catch (_: Exception) {
                                zipStatus = "Ye file khul nahi payi — is type ka app nahi mila"
                            }
                        }
                    }
                )
            }
        }
        Button(onClick = {
            val c = current ?: return@Button
            zipping = true; zipStatus = "Zip ban raha hai…"
            scope.launch(Dispatchers.IO) {
                val target = fm.createFile(c, "archive-${System.currentTimeMillis()}.zip")
                if (target == null) {
                    withContext(Dispatchers.Main) { zipping = false; zipStatus = "✗ Zip file ban nahi payi" }
                    return@launch
                }
                try {
                    ZipHelper.zip(context, c, target)
                    withContext(Dispatchers.Main) { zipping = false; zipStatus = "✓ Zip ban gaya: ${target.name}"; load(c) }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { zipping = false; zipStatus = "✗ Zip fail: ${e.message}" }
                }
            }
        }, enabled = !zipping, modifier = Modifier.padding(top = 8.dp)) { Text(if (zipping) "Zip ban raha hai…" else "Zip this folder") }
        if (zipStatus.isNotBlank()) Text(zipStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    }
}
