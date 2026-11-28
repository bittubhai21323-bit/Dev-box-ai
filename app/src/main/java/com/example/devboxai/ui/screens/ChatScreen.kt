package com.example.devboxai.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.devboxai.DevBoxApp
import com.example.devboxai.R
import com.example.devboxai.SettingsStore
import com.example.devboxai.ai.ConversationEngine
import com.example.devboxai.voice.TtsHelper
import com.example.devboxai.voice.VoiceInputHelper
import kotlinx.coroutines.launch

@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as DevBoxApp
    val store = remember { SettingsStore(context) }
    val engine = remember { ConversationEngine(app.db.chatDao(), { store.openAiKey }, { context }) }
    val scope = rememberCoroutineScope()
    val tts = remember { TtsHelper(context) }

    val conversationId = remember { engine.newConversationId() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val messages by app.db.chatDao().messages(conversationId).collectAsState(initial = emptyList())

    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) VoiceInputHelper(context, onResult = { input = it }).start()
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        LazyColumn(Modifier.weight(1f)) {
            items(messages) { m ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                        if (m.role != "user") {
                            Image(
                                painter = painterResource(id = R.drawable.avatar),
                                contentDescription = null,
                                modifier = Modifier.size(36.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Column {
                            Text(if (m.role == "user") "You (Boss)" else "DevBox",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(m.content, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { micPerm.launch(Manifest.permission.RECORD_AUDIO) }) {
                Icon(Icons.Default.Mic, "Voice")
            }
            OutlinedTextField(value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Ask DevBox...") })
            IconButton(enabled = !busy && input.isNotBlank(), onClick = {
                val q = input; input = ""; busy = true
                scope.launch {
                    val reply = engine.send(conversationId, q)
                    tts.speak(reply)
                    busy = false
                }
            }) { Icon(Icons.Default.Send, "Send") }
            IconButton(onClick = { messages.lastOrNull()?.let { tts.speak(it.content) } }) {
                Icon(Icons.Default.VolumeUp, "Speak")
            }
        }
    }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
}
