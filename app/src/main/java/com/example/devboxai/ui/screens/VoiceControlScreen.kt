package com.example.devboxai.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.devboxai.DevAccessibilityService
import com.example.devboxai.R
import com.example.devboxai.controller.VoiceCommandManager
import com.example.devboxai.voice.WakeWordService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class VoiceRow(val cmd: String, val res: String, val lang: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceControlScreen() {
    val context = LocalContext.current
    val manager = remember { VoiceCommandManager(context) }
    val accessibilityOn by produceState(DevAccessibilityService.isRunning()) {
        while (true) { value = DevAccessibilityService.isRunning(); delay(1000) }
    }
    val history = remember { mutableStateListOf<VoiceRow>() }
    var typed by remember { mutableStateOf("") }
    var lastLang by remember { mutableStateOf("—") }

    Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(painter = painterResource(id = R.drawable.avatar), contentDescription = "DevBox",
                modifier = Modifier.size(130.dp).clip(CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(if (accessibilityOn) "✓ Accessibility ON — Boss ready" else "✗ Accessibility OFF",
                    color = if (accessibilityOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                if (!accessibilityOn) {
                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                        Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(4.dp)); Text("Enable Accessibility")
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AssistChip(onClick = { }, label = { Text("Last detected: $lastLang", fontFamily = FontFamily.Monospace) })
        Spacer(Modifier.height(10.dp))

        Button(onClick = { manager.listenAndExecute { cmd, res, lang -> lastLang = lang; history.add(0, VoiceRow(cmd, res, lang)) } },
            modifier = Modifier.fillMaxWidth().height(80.dp)) {
            Icon(Icons.Default.Mic, null, modifier = Modifier.size(32.dp)); Spacer(Modifier.width(12.dp)); Text("Speak (any language)", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = typed, onValueChange = { typed = it }, label = { Text("Type to test") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { if (typed.isNotBlank()) { manager.execute(typed) { cmd, res, lang -> lastLang = lang; history.add(0, VoiceRow(cmd, res, lang)) }; typed = "" } }) { Text("Run") }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { context.startForegroundService(Intent(context, WakeWordService::class.java)) }, modifier = Modifier.fillMaxWidth()) { Text("\"Hey DevBox\" hands-free ON") }
        Spacer(Modifier.height(16.dp))

        Text("History (${history.size})", style = MaterialTheme.typography.titleSmall)
        Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp).padding(8.dp)) {
                items(history) { row ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(onClick = { }, label = { Text(row.lang, style = MaterialTheme.typography.labelSmall) })
                            Spacer(Modifier.width(6.dp)); Text("🎤 ${row.cmd}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                        Text("→ ${row.res}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { manager.shutdown() } }
}
