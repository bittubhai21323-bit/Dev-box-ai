package com.example.devboxai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.devboxai.DevBoxApp
import com.example.devboxai.firebase.FirebaseBootstrap
import com.example.devboxai.firebase.FirebaseConfigStore
import com.example.devboxai.firebase.FirebaseCredentials
import com.example.devboxai.firebase.FirebaseJsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FirebaseSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as DevBoxApp
    var mode by rememberSaveable { mutableStateOf(0) }
    var jsonInput by rememberSaveable { mutableStateOf("") }
    var projectId by rememberSaveable { mutableStateOf("") }
    var appId by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var senderId by rememberSaveable { mutableStateOf("") }
    var bucket by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var isError by rememberSaveable { mutableStateOf(false) }

    fun save(creds: FirebaseCredentials) {
        val store = FirebaseConfigStore(context)
        store.save(creds)
        val ok = FirebaseBootstrap.init(context)
        if (ok) {
            app.restartFirebase()
            message = "✓ Firebase connected! Project: ${creds.projectId}"
            isError = false
            CoroutineScope(Dispatchers.Main).launch { delay(1200); onDone() }
        } else {
            message = "✗ Init failed — check values"
            isError = true
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Firebase Setup", style = MaterialTheme.typography.headlineSmall)
        Text("google-services.json file ki zaroorat nahi. Bas values paste karo.", style = MaterialTheme.typography.bodySmall)

        TabRow(selectedTabIndex = mode) {
            Tab(selected = mode == 0, onClick = { mode = 0 }, text = { Text("Paste JSON") })
            Tab(selected = mode == 1, onClick = { mode = 1 }, text = { Text("Manual") })
        }

        when (mode) {
            0 -> {
                OutlinedTextField(value = jsonInput, onValueChange = { jsonInput = it }, label = { Text("Paste JSON content") }, modifier = Modifier.fillMaxWidth().height(220.dp), textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                Button(onClick = { FirebaseJsonParser.parse(jsonInput).onSuccess { save(it) }.onFailure { message = it.message ?: "Parse error"; isError = true } }, modifier = Modifier.fillMaxWidth()) { Text("Parse & Connect") }
            }
            1 -> {
                OutlinedTextField(value = projectId, onValueChange = { projectId = it }, label = { Text("Project ID") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = appId, onValueChange = { appId = it }, label = { Text("Application ID") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = senderId, onValueChange = { senderId = it }, label = { Text("Sender ID") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = bucket, onValueChange = { bucket = it }, label = { Text("Storage Bucket") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val creds = FirebaseCredentials(projectId.trim(), appId.trim(), apiKey.trim(), senderId.trim(), bucket.trim())
                    if (creds.isValid()) save(creds) else { message = "Saare required fields bharo"; isError = true }
                }, modifier = Modifier.fillMaxWidth()) { Text("Save & Connect") }
            }
        }

        if (message.isNotBlank()) Text(message, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)

        if (FirebaseConfigStore(context).isConfigured()) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { FirebaseBootstrap.reset(context); message = "Cleared"; isError = false }, modifier = Modifier.fillMaxWidth()) { Text("Clear saved config") }
        }
    }
}
