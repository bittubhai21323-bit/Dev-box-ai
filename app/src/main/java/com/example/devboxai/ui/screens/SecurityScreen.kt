package com.example.devboxai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.devboxai.DevBoxApp
import com.example.devboxai.security.AppBlocker
import com.example.devboxai.security.ProcessGuardian
import com.example.devboxai.security.RootProcessManager
import kotlinx.coroutines.launch

@Composable
fun SecurityScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as DevBoxApp
    val scope = rememberCoroutineScope()
    val blocker = remember { AppBlocker(context, app.db.blockedAppDao()) }
    val guardian = remember { ProcessGuardian(context, app.db.blockedAppDao(), app.db.securityLogDao()) }

    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Blocked", "Scanner", "Web Scan", "Logs", "Root Ops")

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> BlockedTab(app, blocker, scope)
            1 -> ScannerTab(guardian, scope)
            2 -> WebScanTab()
            3 -> LogsTab(app)
            4 -> RootOpsTab(context)
        }
    }
}

@Composable
private fun BlockedTab(app: DevBoxApp, blocker: AppBlocker, scope: kotlinx.coroutines.CoroutineScope) {
    val blocked by app.db.blockedAppDao().all().collectAsState(initial = emptyList())
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Blocked apps: ${blocked.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Button(onClick = { showPicker = true }) { Icon(Icons.Default.Add, null); Text("Block") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(blocked) { b ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.appLabel, style = MaterialTheme.typography.titleSmall)
                            Text(b.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            if (b.reason.isNotBlank()) Text("Reason: ${b.reason}", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { scope.launch { blocker.unblock(b.packageName) } }) {
                            Icon(Icons.Default.Delete, "Unblock", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    if (showPicker) AppPickerDialog(blocker, { showPicker = false }) { pkg, label, reason ->
        scope.launch { blocker.block(pkg, label, reason) }; showPicker = false
    }
}

@Composable
private fun AppPickerDialog(blocker: AppBlocker, onDismiss: () -> Unit, onPick: (String, String, String) -> Unit) {
    var apps by remember { mutableStateOf<List<AppBlocker.AppInfo>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = blocker.installedApps() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block app") },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(300.dp)) {
                    items(apps.filter { it.label.contains(query, true) }) { a ->
                        ListItem(
                            headlineContent = { Text(a.label) },
                            supportingContent = { Text(a.pkg, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.clickable { onPick(a.pkg, a.label, reason) }
                        )
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ScannerTab(guardian: ProcessGuardian, scope: kotlinx.coroutines.CoroutineScope) {
    var results by remember { mutableStateOf<List<ProcessGuardian.RiskyApp>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Button(onClick = { scanning = true; scope.launch { results = guardian.scanRiskyApps(); scanning = false } }, modifier = Modifier.fillMaxWidth()) {
            Text(if (scanning) "Scanning…" else "Scan all apps")
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn {
            items(results) { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.label, style = MaterialTheme.typography.titleSmall)
                        Text(r.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        r.risks.forEach { risk -> Text("⚠ $risk", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebScanTab() {
    val state = com.example.devboxai.security.WebScanState
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("🌐 Website Scan — URL dalo, website ki jaankari aur security grade milega", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.url, onValueChange = { state.url = it },
            label = { Text("Website URL (jaise https://example.com)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Button(
            onClick = { state.startScan() },
            enabled = !state.scanning && state.url.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (state.scanning) "Scanning…" else "Scan karo") }
        Spacer(Modifier.height(8.dp))
        Text(
            state.report, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun LogsTab(app: DevBoxApp) {
    val logs by app.db.securityLogDao().all().collectAsState(initial = emptyList())
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        items(logs) { l ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text(l.event, style = MaterialTheme.typography.labelSmall, color = when (l.severity) {
                        "danger" -> MaterialTheme.colorScheme.error; "warn" -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.primary
                    })
                    Text(l.details, style = MaterialTheme.typography.bodySmall)
                    if (l.packageName != null) Text(l.packageName, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun RootOpsTab(context: android.content.Context) {
    var pkg by rememberSaveable { mutableStateOf("com.example.package") }
    var out by rememberSaveable { mutableStateOf("") }
    var rootStatus by rememberSaveable { mutableStateOf("Root check ho raha hai…") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { rootStatus = RootProcessManager.checkRoot(context) }
    fun runOp(cmd: String, okText: String) {
        out = "Chal raha hai…"
        scope.launch { out = RootProcessManager.runRoot(context, cmd, okText) }
    }
    Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
        Text("⚠ Root required (Termux + su)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Text(rootStatus, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        OutlinedTextField(value = pkg, onValueChange = { pkg = it }, label = { Text("Package") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { runOp("pkill -9 -f $pkg || true", "✓ Kill ho gaya: $pkg") }, modifier = Modifier.fillMaxWidth()) { Text("Kill") }
        Button(onClick = { runOp("pkill -STOP -f $pkg || true", "✓ Freeze ho gaya: $pkg") }, modifier = Modifier.fillMaxWidth()) { Text("Freeze") }
        Button(onClick = { runOp("pkill -CONT -f $pkg || true", "✓ Unfreeze ho gaya: $pkg") }, modifier = Modifier.fillMaxWidth()) { Text("Unfreeze") }
        Button(onClick = { runOp("renice 19 -p \$(pidof $pkg) 2>/dev/null", "✓ CPU priority kam ho gayi: $pkg") }, modifier = Modifier.fillMaxWidth()) { Text("Deprioritize CPU") }
        Text(out, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}
