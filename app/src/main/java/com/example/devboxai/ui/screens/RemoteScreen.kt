package com.example.devboxai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.devboxai.DevBoxApp
import com.example.devboxai.firebase.AdminAuth
import com.example.devboxai.firebase.FirebaseConfigStore
import com.example.devboxai.firebase.FirebaseInit
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RemoteScreen(onGoToSetup: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as DevBoxApp
    val scope = rememberCoroutineScope()
    val store = remember { FirebaseConfigStore(context) }
    var configured by remember { mutableStateOf(store.isConfigured()) }

    if (!configured) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Firebase not configured", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Remote control enable karne ke liye credentials save karo.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onGoToSetup) { Text("Configure Firebase") }
            TextButton(onClick = { configured = store.isConfigured() }) { Text("Recheck") }
        }
        return
    }

    val flags by app.remoteConfig.flags.collectAsState()
    val lastSync by app.remoteConfig.lastSync.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var authMsg by remember { mutableStateOf("") }
    var signedIn by remember { mutableStateOf(AdminAuth.isSignedIn) }

    Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
        Text("Firebase Live Control", style = MaterialTheme.typography.titleMedium)
        Text("Device: ${app.deviceId}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(8.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Remote Config (live)", style = MaterialTheme.typography.titleSmall)
                Text("""
                    Voice      : ${flags.voiceEnabled}
                    Wake word  : ${flags.wakeWordEnabled}
                    Block mode : ${flags.blockModeEnabled}
                    Auto kill  : ${flags.autoKillBlocked}
                    Lang       : ${flags.forceLanguage.ifBlank { "auto" }}
                    Last sync: ${if (lastSync == 0L) "never" else SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(lastSync))}
                """.trimIndent(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { scope.launch { app.remoteConfig.refresh() } }) { Text("Refresh now") }
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(if (signedIn) "✓ Admin signed in" else "Admin login", style = MaterialTheme.typography.titleSmall)
                if (!signedIn) {
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { scope.launch { AdminAuth.signIn(email, password).onSuccess { signedIn = true; authMsg = "Signed in" }.onFailure { authMsg = "Error: ${it.message}" } } }) { Text("Sign in") }
                        OutlinedButton(onClick = { scope.launch { AdminAuth.signUp(email, password).onSuccess { signedIn = true; authMsg = "Created" }.onFailure { authMsg = "Error: ${it.message}" } } }) { Text("Create") }
                    }
                } else {
                    Button(onClick = { AdminAuth.signOut(); signedIn = false }) { Text("Sign out") }
                }
                if (authMsg.isNotBlank()) Text(authMsg, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(12.dp))

        Text("Quick tests", style = MaterialTheme.typography.titleSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Button(onClick = { sendCmd("notify", text = "Hello from admin") }, modifier = Modifier.fillMaxWidth()) { Text("Notify") }
                Button(onClick = { sendCmd("scan") }, modifier = Modifier.fillMaxWidth()) { Text("Scan") }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onGoToSetup, modifier = Modifier.fillMaxWidth()) { Text("Change Firebase credentials") }
    }
}

private fun sendCmd(action: String, packageName: String = "", text: String = "") {
    val data = mutableMapOf<String, Any>("action" to action, "status" to "pending", "targetDeviceId" to "*")
    if (packageName.isNotBlank()) data["packageName"] = packageName
    if (text.isNotBlank()) data["text"] = text
    FirebaseInit.firestore?.collection(FirebaseInit.COLL_COMMANDS)?.add(data)
}
