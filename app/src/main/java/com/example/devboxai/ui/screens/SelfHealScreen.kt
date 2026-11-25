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
import androidx.compose.ui.unit.dp
import com.example.devboxai.backup.BackupManager
import com.example.devboxai.crash.BugReporter
import com.example.devboxai.selfheal.HealthChecker
import com.example.devboxai.update.UpdateManager
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SelfHealScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bm = remember { BackupManager(context) }
    val hc = remember { HealthChecker(context) }
    val um = remember { UpdateManager(context) }
    val br = remember { BugReporter(context) }

    var status by remember { mutableStateOf("") }
    var backups by remember { mutableStateOf<List<File>>(emptyList()) }
    var crashes by remember { mutableStateOf<List<File>>(emptyList()) }
    var pw by remember { mutableStateOf("boss12345") }
    var health by remember { mutableStateOf<HealthChecker.Report?>(null) }
    var dlProgress by remember { mutableStateOf(0) }

    fun refresh() { backups = bm.listBackups(); crashes = br.localCrashes() }
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
        Text("Self-Heal Dashboard", style = MaterialTheme.typography.titleMedium)
        Text("Version ${um.currentVersionName()}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Health check", style = MaterialTheme.typography.titleSmall)
                Button(onClick = { scope.launch { status = "Checking…"; health = hc.check(); status = if (health!!.healthy) "✓ OK" else "⚠ Issues" } }) { Text("Run check") }
                health?.let { r ->
                    Text(if (r.healthy) "✓ OK" else "Issues: " + r.issues.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Backup", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(value = pw, onValueChange = { pw = it }, label = { Text("Backup password") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { status = "Creating…"; status = bm.createBackup(pw).message; refresh() } }) { Text("Create") }
                    OutlinedButton(onClick = { refresh() }) { Text("Refresh") }
                }
                backups.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.name, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { status = "Restoring…"; status = bm.restoreBackup(f, pw).message } }) { Text("Restore") }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Crash reports: ${crashes.size}", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { status = "Uploading…"; status = "Uploaded ${br.uploadPending()}"; refresh() } }) { Text("Upload") }
                    OutlinedButton(onClick = { br.clearLocal(); refresh(); status = "Cleared" }) { Text("Clear") }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Update", style = MaterialTheme.typography.titleSmall)
                Text("Managed via Firebase Remote Config", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { dlProgress / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                Button(onClick = { scope.launch {
                    val info = um.checkFromRemoteConfig(um.currentVersionCode() + 1, "next", "https://example.com/devbox.apk", "Bug fixes", false)
                    if (info.available) {
                        status = "Downloading…"
                        val apk = um.downloadApk(info.apkUrl) { dlProgress = it }
                        if (apk != null) { status = "Installing…"; um.installApkFallback(apk) }
                    } else status = "Already up to date"
                } }) { Text("Check update") }
            }
        }
        Spacer(Modifier.height(10.dp))

        Button(onClick = { scope.launch { hc.resetDatabase(); status = "DB wiped" } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Reset database") }

        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 12.dp))
    }
}
