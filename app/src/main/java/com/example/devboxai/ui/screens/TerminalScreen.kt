package com.example.devboxai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.devboxai.termux.TermuxHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val TermGreen = Color(0xFF33FF66)
private val TermCyan = Color(0xFF00E5FF)
private val TermBg = Color(0xFF050505)

private data class TermLine(val text: String, val isCommand: Boolean = false)

// Savers so terminal output + command history survive tab switches
private val termLineSaver = androidx.compose.runtime.saveable.listSaver<List<TermLine>, String>(
    save = { list -> list.map { (if (it.isCommand) "1" else "0") + it.text } },
    restore = { saved -> saved.map { TermLine(it.drop(1), it.startsWith("1")) } }
)
private val stringListSaver = androidx.compose.runtime.saveable.listSaver<List<String>, String>(
    save = { it },
    restore = { it }
)

/**
 * Kali-style in-app terminal.
 * Local mode runs Android shell commands directly; Termux mode runs full
 * Linux commands through the Termux app when it is installed.
 */
@Composable
fun TerminalScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val termuxInstalled = remember { TermuxHelper.isTermuxInstalled(context) }

    var useTermux by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var workDirPath by rememberSaveable { mutableStateOf(context.filesDir.absolutePath) }
    val workDir = File(workDirPath)
    var lines by rememberSaveable(stateSaver = termLineSaver) {
        mutableStateOf(
            listOf(
                TermLine("DevBox Kali Terminal v1.0"),
                TermLine("Type 'help' for commands. Mode: ${if (termuxInstalled) "Local shell (Termux available)" else "Local shell"}"),
                TermLine("")
            )
        )
    }
    var history by rememberSaveable(stateSaver = stringListSaver) { mutableStateOf(listOf<String>()) }
    var historyIndex by rememberSaveable { mutableStateOf(-1) }
    val listState = rememberLazyListState()

    fun prompt(): String = if (useTermux) "devbox@termux:~$ " else "devbox@kali:${workDir.name}$ "

    fun append(text: String, isCommand: Boolean = false) {
        lines = lines + TermLine(text, isCommand)
    }

    fun runCommand(raw: String) {
        val cmd = raw.trim()
        if (cmd.isBlank() || busy) return
        history = listOf(cmd) + history
        historyIndex = -1
        append(prompt() + cmd, isCommand = true)

        when {
            cmd == "clear" -> { lines = emptyList(); return }
            cmd == "help" -> {
                append(
                    """
                    Commands:
                      ls, pwd, cd <folder>, cat <file>, echo <text>
                      whoami, uname -a, df -h, free -m, ps, top -n1
                      ping -c3 8.8.8.8, ip addr, getprop <name>
                      clear — screen saaf karo
                      exit — (sirf screen par)
                    Upar wale switch se Termux mode on karo to
                    python, git, nmap jaise poore Linux tools chalenge.
                    """.trimIndent()
                )
                return
            }
            cmd == "exit" -> { append("logout (terminal band karne ke liye back dabao)"); return }
            cmd.startsWith("cd") && !useTermux -> {
                val target = cmd.removePrefix("cd").trim()
                val newDir = when {
                    target.isBlank() || target == "~" -> context.filesDir
                    target == ".." -> workDir.parentFile ?: workDir
                    target.startsWith("/") -> File(target)
                    else -> File(workDir, target)
                }
                if (newDir.isDirectory) workDirPath = newDir.absolutePath
                else append("cd: no such directory: $target")
                return
            }
        }

        busy = true
        scope.launch {
            val output = if (useTermux) {
                val result = TermuxHelper.runAndWait(context, cmd, timeoutMs = 60_000)
                when {
                    result == null -> "Termux se jawab nahi aaya (Termux me allow-external-apps on karo)"
                    result.stdout.isNotBlank() -> result.stdout.trimEnd() +
                        if (result.stderr.isNotBlank()) "\n${result.stderr.trimEnd()}" else ""
                    result.stderr.isNotBlank() -> result.stderr.trimEnd()
                    else -> "(no output) [exit=${result.exit}]"
                }
            } else {
                runLocalShell(cmd, workDir)
            }
            append(output)
            busy = false
        }
    }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Column(Modifier.fillMaxSize().background(TermBg).padding(10.dp)) {
        // Mode switch
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Local shell", color = if (!useTermux) TermGreen else Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Switch(
                checked = useTermux,
                onCheckedChange = { v ->
                    if (v && !termuxInstalled) {
                        append("Termux installed nahi hai — F-Droid se lagao, phir ye mode chalega.")
                    } else {
                        useTermux = v
                        append(if (v) "── Termux mode ON ──" else "── Local shell mode ──")
                    }
                },
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            Text("Termux", color = if (useTermux) TermCyan else Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = TermGreen, strokeWidth = 2.dp)
        }

        // Quick commands
        val quick = listOf("ls", "pwd", "whoami", "uname -a", "df -h", "free -m", "ps", "ip addr", "ping -c3 8.8.8.8", "clear")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            items(quick) { q ->
                FilterChip(
                    selected = false,
                    onClick = { runCommand(q) },
                    label = { Text(q, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                )
            }
        }

        // Output
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(lines) { line ->
                Text(
                    line.text,
                    color = if (line.isCommand) TermCyan else TermGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }
        }

        // Input row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(prompt(), color = TermCyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = TermGreen, fontFamily = FontFamily.Monospace, fontSize = 13.sp
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    runCommand(input); input = ""
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TermGreen,
                    unfocusedBorderColor = Color.DarkGray,
                    cursorColor = TermGreen
                )
            )
            IconButton(onClick = { runCommand(input); input = "" }) {
                Icon(Icons.Default.PlayArrow, "Run", tint = TermGreen)
            }
        }

        // History navigation
        if (history.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    if (historyIndex < history.size - 1) {
                        historyIndex++
                        input = history[historyIndex]
                    }
                }) { Text("▲ purana", color = TermCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                TextButton(onClick = {
                    if (historyIndex > 0) {
                        historyIndex--
                        input = history[historyIndex]
                    } else {
                        historyIndex = -1
                        input = ""
                    }
                }) { Text("▼ naya", color = TermCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
            }
        }
    }
}

private suspend fun runLocalShell(command: String, workDir: File): String = withContext(Dispatchers.IO) {
    try {
        val process = ProcessBuilder("sh", "-c", command)
            .directory(workDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        val trimmed = output.trimEnd()
        if (trimmed.isBlank()) "(no output)" else trimmed
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
