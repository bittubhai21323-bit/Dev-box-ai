package com.example.devboxai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.devboxai.SettingsStore

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    var openAi by remember { mutableStateOf(store.openAiKey) }
    var gh by remember { mutableStateOf(store.githubToken) }
    var lang by remember { mutableStateOf(store.preferredLang) }
    var personality by remember { mutableStateOf(store.personality) }
    var saved by remember { mutableStateOf(false) }

    val langs = listOf("" to "Auto-detect", "hi" to "हिन्दी", "en" to "English", "es" to "Español", "fr" to "Français", "ar" to "العربية", "zh" to "中文", "ja" to "日本語")
    val personalities = listOf("boss" to "Boss", "friend" to "Friend", "professional" to "Pro", "minimal" to "Minimal")

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(value = openAi, onValueChange = { openAi = it; saved = false }, label = { Text("OpenAI API key") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = gh, onValueChange = { gh = it; saved = false }, label = { Text("GitHub token") }, modifier = Modifier.fillMaxWidth())

        Text("Voice language", style = MaterialTheme.typography.titleSmall)
        var langExpanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { langExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(langs.firstOrNull { it.first == lang }?.second ?: "Auto") }
            DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                langs.forEach { (code, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { lang = code; langExpanded = false; saved = false }) }
            }
        }

        Text("Personality", style = MaterialTheme.typography.titleSmall)
        var pExpanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { pExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(personalities.firstOrNull { it.first == personality }?.second ?: "Boss") }
            DropdownMenu(expanded = pExpanded, onDismissRequest = { pExpanded = false }) {
                personalities.forEach { (code, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { personality = code; pExpanded = false; saved = false }) }
            }
        }

        Button(onClick = { store.openAiKey = openAi; store.githubToken = gh; store.preferredLang = lang; store.personality = personality; saved = true }) { Text("Save") }
        if (saved) Text("Saved ✓", color = MaterialTheme.colorScheme.primary)
    }
}
