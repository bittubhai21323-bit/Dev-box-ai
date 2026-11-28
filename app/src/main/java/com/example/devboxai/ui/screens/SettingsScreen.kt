package com.example.devboxai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.devboxai.SettingsStore
import com.example.devboxai.update.UpdateManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    var openAi by rememberSaveable { mutableStateOf(store.openAiKey) }
    var gh by rememberSaveable { mutableStateOf(store.githubToken) }
    var lang by rememberSaveable { mutableStateOf(store.preferredLang) }
    var personality by rememberSaveable { mutableStateOf(store.personality) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var showSecrets by rememberSaveable { mutableStateOf(false) }

    val langs = listOf("" to "Auto-detect", "hi" to "हिन्दी", "en" to "English", "es" to "Español", "fr" to "Français", "ar" to "العربية", "zh" to "中文", "ja" to "日本語")
    val personalities = listOf("boss" to "Boss", "friend" to "Friend", "professional" to "Pro", "minimal" to "Minimal")

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        // Key/token screen par saaf dikhte the — screenshot se leak ho sakte hai, isliye chhupa kar rakho
        OutlinedTextField(
            value = openAi, onValueChange = { openAi = it; saved = false },
            label = { Text("Gemini API key (free: aistudio.google.com)") },
            visualTransformation = if (showSecrets) androidx.compose.ui.text.input.VisualTransformation.None
                else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = gh, onValueChange = { gh = it; saved = false },
            label = { Text("GitHub token") },
            visualTransformation = if (showSecrets) androidx.compose.ui.text.input.VisualTransformation.None
                else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = showSecrets, onCheckedChange = { showSecrets = it })
            Text("Key/Token dikhao", style = MaterialTheme.typography.bodySmall)
        }

        Text("Voice language", style = MaterialTheme.typography.titleSmall)
        var langExpanded by rememberSaveable { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { langExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(langs.firstOrNull { it.first == lang }?.second ?: "Auto") }
            DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                langs.forEach { (code, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { lang = code; langExpanded = false; saved = false }) }
            }
        }

        Text("Personality", style = MaterialTheme.typography.titleSmall)
        var pExpanded by rememberSaveable { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { pExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(personalities.firstOrNull { it.first == personality }?.second ?: "Boss") }
            DropdownMenu(expanded = pExpanded, onDismissRequest = { pExpanded = false }) {
                personalities.forEach { (code, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { personality = code; pExpanded = false; saved = false }) }
            }
        }

        // trim: paste ki hui key ke aakhir me space/newline aa jata hai, usse key fail hoti thi
        Button(onClick = { store.openAiKey = openAi.trim(); store.githubToken = gh.trim(); store.preferredLang = lang; store.personality = personality; openAi = openAi.trim(); gh = gh.trim(); saved = true }) { Text("Save") }
        if (saved) Text("Saved ✓", color = MaterialTheme.colorScheme.primary)

        HorizontalDivider()
        Text("App Update", style = MaterialTheme.typography.titleSmall)
        val scope = rememberCoroutineScope()
        val updater = remember { UpdateManager(ctx) }
        var updateStatus by rememberSaveable { mutableStateOf("Version ${updater.currentVersionName()}") }
        var updateUrl by rememberSaveable { mutableStateOf("") }
        var updateStamp by rememberSaveable { mutableStateOf("") }
        var downloading by remember { mutableStateOf(false) }
        var progress by rememberSaveable { mutableStateOf(0) }

        OutlinedButton(onClick = {
            updateStatus = "Check kar raha hoon..."
            scope.launch {
                val info = updater.checkFromGitHub()
                if (info.available) {
                    updateUrl = info.apkUrl
                    updateStamp = info.changelog.removePrefix("Stamp: ")
                    updateStatus = "⬆ Naya update hai!"
                } else {
                    updateStatus = "✓ App up-to-date hai"
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Update check karo") }

        if (updateUrl.isNotBlank() && !downloading) {
            Button(onClick = {
                if (!updater.canInstallPackages()) {
                    updater.requestInstallPermission()
                    updateStatus = "Pehle 'Install unknown apps' ki permission do, phir dobara dabao"
                } else {
                    downloading = true; progress = 0
                    updateStatus = "Download ho raha hai..."
                    scope.launch {
                        val file = updater.downloadApk(updateUrl) { p -> progress = p }
                        downloading = false
                        if (file != null) {
                            // markInstalled sirf tab, jab installer khul jaye —
                            // pehle mark karne se cancel karne par update gayab ho jata tha
                            val launched = updater.installApk(file)
                            if (launched) {
                                updater.markInstalled(updateStamp)
                                updateUrl = ""
                                updateStatus = "Install ho raha hai..."
                            } else updateStatus = "Install fail — dobara try karo"
                        } else updateStatus = "Download fail — internet check karo"
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Update install karo") }
        }
        if (downloading) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            Text("Download: $progress%", style = MaterialTheme.typography.bodySmall)
        }
        Text(updateStatus, style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()
        Text("Crash Reports", style = MaterialTheme.typography.titleSmall)
        var crashText by rememberSaveable { mutableStateOf<String?>(null) }
        OutlinedButton(onClick = {
            val dirs = listOfNotNull(
                java.io.File(ctx.filesDir, "crashes"),
                ctx.getExternalFilesDir(null)?.let { java.io.File(it, "crashes") }
            )
            val files = dirs.flatMap { d -> d.listFiles()?.toList() ?: emptyList() }
                .sortedByDescending { it.lastModified() }
            val heartbeat = try {
                val hb = java.io.File(ctx.filesDir, "hud_heartbeat.log")
                if (hb.exists()) "\n\n══ HUD HEARTBEAT (aakhri lines) ══\n" + hb.readLines().takeLast(25).joinToString("\n")
                else ""
            } catch (_: Exception) { "" }
            crashText = (if (files.isEmpty()) "Koi crash report nahi hai ✓"
            else files.take(3).joinToString("\n\n══════════\n\n") { f ->
                "📄 ${f.name}\n" + try { f.readText().take(3000) } catch (_: Exception) { "(padh nahi paya)" }
            }) + heartbeat
        }, modifier = Modifier.fillMaxWidth()) { Text("Crash reports dekho") }
        if (crashText != null) {
            AlertDialog(
                onDismissRequest = { crashText = null },
                title = { Text("Crash Reports") },
                text = {
                    Box(Modifier.fillMaxWidth().heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
                        Text(crashText!!, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = { TextButton(onClick = { crashText = null }) { Text("Band karo") } }
            )
        }
    }
}
