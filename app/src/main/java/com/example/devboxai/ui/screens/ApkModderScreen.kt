package com.example.devboxai.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.devboxai.apk.ApkModder
import com.example.devboxai.apk.KeystoreManager
import com.example.devboxai.termux.TermuxHelper
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ApkModderScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modder = remember { ApkModder(context) }
    val ks = remember { KeystoreManager(context) }

    var status by rememberSaveable { mutableStateOf("Ready") }
    var log by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Workflow", "Files", "Settings")

    var allFilesGranted by remember { mutableStateOf(Environment.isExternalStorageManager()) }

    // Termux RUN_COMMAND is a runtime permission; reinstalling the app resets it.
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = if (granted) "✓ Termux permission mil gayi — ab dobara try karo"
                 else "✗ Termux permission nahi mili — Settings > Apps > DevBox AI > Permissions me Allow karo"
    }

    fun run(label: String, termux: Boolean = false, block: suspend () -> Unit) {
        if (busy) return
        if (termux && !TermuxHelper.hasRunPermission(context)) {
            status = "Termux permission chahiye — Allow karo, phir dobara try karo"
            permLauncher.launch(TermuxHelper.RUN_PERMISSION)
            return
        }
        scope.launch {
            busy = true; status = "$label…"; log = ""
            try { block() } catch (e: Exception) { status = "Error: ${e.message}" } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }

        when (tab) {
            0 -> WorkflowTab(context, modder, ks, status, log, busy, allFilesGranted, {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).setData(Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }, { uri ->
                run("Import APK") { val r = modder.importApk(uri); status = r.log }
            }, {
                run("Decompiling", termux = true) { modder.ensureWorkspace(); val r = modder.decompile(); status = if (r.ok) "✓ Decompiled" else "✗ Failed"; log = r.log }
            }, { tab = 1 }, {
                run("Recompiling", termux = true) { val r = modder.recompile(); status = if (r.ok) "✓ Built" else "✗ Failed"; log = r.log }
            }, {
                run("Aligning", termux = true) { val r = modder.zipalign(); status = if (r.ok) "✓ Aligned" else "✗ Failed"; log = r.log }
            }, { pw ->
                run("Signing", termux = true) {
                    val ksOk = ks.generate(pw)
                    if (!ksOk.ok) { status = "Keystore: ${ksOk.message}"; return@run }
                    val r = modder.sign(pw); status = if (r.ok) "✓ Signed" else "✗ Failed"; log = r.log
                }
            }, {
                run("Verifying", termux = true) { val r = modder.verify(); status = if (r.ok) "✓ Verified" else "⚠ Issues"; log = r.log }
            }, {
                val apk = File(ApkModder.SIGNED_APK)
                if (apk.exists()) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else status = "No signed.apk yet"
            })

            1 -> FilesTab(modder, status, { status = it }, busy)
            2 -> SettingsTab(context, ks, status, { status = it })
        }
    }
    LaunchedEffect(Unit) { while (true) { allFilesGranted = Environment.isExternalStorageManager(); kotlinx.coroutines.delay(2000) } }
}

@Composable
private fun WorkflowTab(
    context: android.content.Context, modder: ApkModder, ks: KeystoreManager,
    status: String, log: String, busy: Boolean, allFilesGranted: Boolean,
    onGrantFiles: () -> Unit, onImport: (Uri) -> Unit, onDecompile: () -> Unit,
    onGoToFiles: () -> Unit,
    onRecompile: () -> Unit, onZipalign: () -> Unit, onSign: (String) -> Unit,
    onVerify: () -> Unit, onOpenFolder: () -> Unit
) {
    var password by rememberSaveable { mutableStateOf("boss12345") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onImport(uri) }

    Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!allFilesGranted) Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("⚠ All-Files Access required", color = MaterialTheme.colorScheme.error); Button(onClick = onGrantFiles) { Text("Grant") } } }
        if (!TermuxHelper.isTermuxInstalled(context)) Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("⚠ Termux not installed", color = MaterialTheme.colorScheme.error) } }

        Text(status, style = MaterialTheme.typography.titleSmall)

        StepCard(1, "Pick APK", "SAF picker", "Pick", { picker.launch(arrayOf("application/vnd.android.package-archive")) }, !busy)
        StepCard(2, "Decompile", "apktool d", "Decompile", onDecompile, !busy)
        StepCard(3, "Edit", "Smali, XML", "Go to Files", onGoToFiles, !busy)
        StepCard(4, "Recompile", "apktool b", "Build", onRecompile, !busy)
        StepCard(5, "Zipalign", "zipalign -p", "Align", onZipalign, !busy)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("6. Sign", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Keystore password") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onSign(password) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Sign") }
            }
        }
        StepCard(7, "Verify", "apksigner verify", "Verify", onVerify, !busy)
        StepCard(8, "Install", "Install signed APK", "Install", onOpenFolder, !busy)

        if (log.isNotBlank()) Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("Log", style = MaterialTheme.typography.titleSmall); Text(log, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) } }
    }
}

@Composable
private fun StepCard(n: Int, title: String, desc: String, buttonLabel: String, onClick: () -> Unit, enabled: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$n.", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(desc, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = onClick, enabled = enabled) { Text(buttonLabel) }
        }
    }
}

@Composable
private fun FilesTab(modder: ApkModder, status: String, setStatus: (String) -> Unit, busy: Boolean) {
    val scope = rememberCoroutineScope()
    var currentPath by rememberSaveable { mutableStateOf("") }
    var nodes by remember { mutableStateOf<List<ApkModder.FileNode>>(emptyList()) }
    var editorPath by rememberSaveable { mutableStateOf<String?>(null) }
    var editorText by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    fun load(path: String) { scope.launch { loading = true; nodes = modder.listDir(path); currentPath = path; loading = false } }
    LaunchedEffect(Unit) { load("") }

    if (editorPath != null) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { editorPath = null }) { Icon(Icons.Default.ArrowBack, null) }
                Text(editorPath!!, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
                TextButton(onClick = { scope.launch { val ok = modder.writeFileText(editorPath!!, editorText); setStatus(if (ok) "✓ Saved" else "✗ Save failed") } }) { Text("Save") }
            }
            OutlinedTextField(value = editorText, onValueChange = { editorText = it }, modifier = Modifier.fillMaxSize(), textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (currentPath.isNotBlank()) IconButton(onClick = { load(currentPath.substringBeforeLast("/", "")) }) { Icon(Icons.Default.ArrowBack, "Up") }
            Text("/decoded/${currentPath}".ifBlank { "/decoded" }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            IconButton(onClick = { load(currentPath) }, enabled = !busy) { Icon(Icons.Default.Refresh, "Refresh") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn {
            items(nodes) { node ->
                ListItem(
                    headlineContent = { Text((if (node.isDirectory) "📁 " else "📄 ") + node.name, style = MaterialTheme.typography.bodyMedium) },
                    supportingContent = { if (!node.isDirectory) Text("${node.sizeBytes} B", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.clickable {
                        if (node.isDirectory) load(node.relativePath)
                        else if (node.sizeBytes < 500_000) scope.launch { editorText = modder.readFileText(node.relativePath) ?: ""; editorPath = node.relativePath }
                        else setStatus("Too large to edit")
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsTab(context: android.content.Context, ks: KeystoreManager, status: String, setStatus: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var pw by rememberSaveable { mutableStateOf("boss12345") }

    Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Keystore", style = MaterialTheme.typography.titleSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Path: ${ks.keystorePath()}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Text(if (File(ks.keystorePath()).exists()) "✓ Exists" else "✗ Not created", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = pw, onValueChange = { pw = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { setStatus(ks.generate(pw).message) } }) { Text("Generate") }
                    OutlinedButton(onClick = { ks.delete(); setStatus("Deleted") }) { Text("Delete") }
                }
            }
        }
        Text("Termux tools needed", style = MaterialTheme.typography.titleSmall)
        Button(onClick = { TermuxHelper.installPackages(context, listOf("apktool", "apksigner", "zipalign", "aapt", "openssl")); setStatus("Installing in Termux…") }) { Text("Install in Termux") }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
