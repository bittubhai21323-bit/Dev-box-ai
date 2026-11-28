package com.example.devboxai.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.devboxai.termux.TermuxHelper
import com.example.devboxai.termux.TermuxResultBus
import com.example.devboxai.tools.CliTool
import com.example.devboxai.tools.ToolsCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermuxToolsScreen() {
    val context = LocalContext.current
    var output by rememberSaveable { mutableStateOf("") }
    var showOutput by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf("All") }

    val categories = remember { listOf("All") + ToolsCatalog.tools.map { it.category }.distinct().sorted() }
    val filtered = remember(selectedCategory) { if (selectedCategory == "All") ToolsCatalog.tools else ToolsCatalog.tools.filter { it.category == selectedCategory } }

    // Termux RUN_COMMAND is a runtime permission; reinstalling the app resets it.
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        busy = false
        output = if (granted)
            "✓ Termux permission mil gayi.\nAb dobara Run dabao."
        else
            "✗ Permission nahi mili.\nSettings > Apps > DevBox AI > Permissions me jaake Termux permission Allow karo, phir dobara try karo."
        showOutput = true
    }

    LaunchedEffect(Unit) {
        TermuxResultBus.results.collect { r ->
            busy = false
            output = buildString {
                if (r.stdout.isNotBlank()) append(r.stdout.trimEnd())
                if (r.stderr.isNotBlank()) { if (isNotEmpty()) append("\n\n"); append("── STDERR ──\n").append(r.stderr.trimEnd()) }
                if (isEmpty()) append("(no output)")
                append("\n\n[exit=${r.exit}]")
            }
            showOutput = true
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (!TermuxHelper.openTermux(context)) { output = "Termux not installed.\nF-Droid se install karo."; showOutput = true }
            }) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(4.dp)); Text("Open Termux") }
            OutlinedButton(onClick = {
                if (!TermuxHelper.hasRunPermission(context)) {
                    permLauncher.launch(TermuxHelper.RUN_PERMISSION)
                } else if (TermuxHelper.installPackages(context, ToolsCatalog.installList)) {
                    output = "Installing tools... check Termux app."; showOutput = true
                } else {
                    output = "Termux command failed:\n${TermuxHelper.lastError ?: "unknown error"}"; showOutput = true
                }
            }) { Text("Install tools") }
        }
        Text(if (TermuxHelper.isTermuxInstalled(context)) "✓ Termux installed" else "✗ Termux not installed", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))

        LazyRow(contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(categories) { c -> FilterChip(selected = selectedCategory == c, onClick = { selectedCategory = c }, label = { Text(c) }) }
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered) { tool ->
                ToolCard(tool) { cmd ->
                    if (!TermuxHelper.hasRunPermission(context)) {
                        output = "Termux permission chahiye — Allow karo, phir dobara Run dabao."
                        showOutput = true
                        permLauncher.launch(TermuxHelper.RUN_PERMISSION)
                    } else {
                        busy = true; output = "Running:\n$cmd"; showOutput = true
                        if (!TermuxHelper.runWithResult(context, cmd)) {
                            busy = false
                            output = "Termux command failed:\n${TermuxHelper.lastError ?: "unknown error"}\n\n" +
                                "Check karo: Termux me allow-external-apps=true hai ya nahi " +
                                "(~/.termux/termux.properties), aur Termux app ek baar khula ho."
                        }
                    }
                }
            }
        }
    }

    if (showOutput) {
        AlertDialog(
            onDismissRequest = { showOutput = false },
            title = { Text("Output") },
            text = { Box(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())) { Text(output, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) } },
            confirmButton = { TextButton(onClick = { showOutput = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun ToolCard(tool: CliTool, onRun: (String) -> Unit) {
    var arg by remember(tool.name) { mutableStateOf(tool.defaultArg) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tool.name, style = MaterialTheme.typography.titleSmall)
                    Text(tool.description, style = MaterialTheme.typography.bodySmall)
                    if (tool.needsRoot) Text("⚠ needs root", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = { expanded = !expanded }) { Icon(Icons.Default.PlayArrow, "Expand") }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = arg, onValueChange = { arg = it }, label = { Text("Argument") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("> " + tool.template.replace("{arg}", arg.ifBlank { "<arg>" }), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onRun(tool.template.replace("{arg}", arg)) }) { Text("Run") }
                    if (tool.pkg != null) TextButton(onClick = { onRun("pkg install -y ${tool.pkg}") }) { Text("Install") }
                }
            }
        }
    }
}
