package com.example.devboxai.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.devboxai.DevBoxApp
import com.example.devboxai.data.*
import com.example.devboxai.productivity.ReminderScheduler
import com.example.devboxai.productivity.RoutineScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProductivityScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as DevBoxApp
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Tasks", "Journal", "Reminders", "Routines")

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> TasksTab(app, scope)
            1 -> JournalTab(app, scope)
            2 -> RemindersTab(app, scope, context)
            3 -> RoutinesTab(app, scope, context)
        }
    }
}

@Composable
private fun TasksTab(app: DevBoxApp, scope: CoroutineScope) {
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val tasks by app.db.taskDao().forDate(today).collectAsState(initial = emptyList())
    var text by remember { mutableStateOf("") }

    Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), placeholder = { Text("New task") })
            IconButton(onClick = { if (text.isNotBlank()) scope.launch { app.db.taskDao().insert(DailyTask(title = text, date = today)); text = "" } }) {
                Icon(Icons.Default.Add, "Add")
            }
        }
        LazyColumn {
            items(tasks) { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Checkbox(checked = t.done, onCheckedChange = { c -> scope.launch { app.db.taskDao().update(t.copy(done = c)) } })
                    Text(t.title, modifier = Modifier.weight(1f))
                    IconButton(onClick = { scope.launch { app.db.taskDao().delete(t) } }) { Text("✕") }
                }
            }
        }
    }
}

@Composable
private fun JournalTab(app: DevBoxApp, scope: CoroutineScope) {
    val entries by app.db.journalDao().all().collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Column(Modifier.padding(12.dp)) {
        OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Title") })
        OutlinedTextField(value = content, onValueChange = { content = it }, modifier = Modifier.fillMaxWidth().height(100.dp), placeholder = { Text("Entry...") })
        Button(onClick = { if (title.isNotBlank() || content.isNotBlank()) scope.launch { app.db.journalDao().insert(JournalEntry(title = title, content = content)); title = ""; content = "" } }) { Text("Save") }
        LazyColumn {
            items(entries) { e ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text(e.title, style = MaterialTheme.typography.titleSmall)
                        Text(e.content)
                    }
                }
            }
        }
    }
}

@Composable
private fun RemindersTab(app: DevBoxApp, scope: CoroutineScope, context: Context) {
    val reminders by app.db.reminderDao().all().collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("10") }

    Column(Modifier.padding(12.dp)) {
        OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("Reminder") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = minutes, onValueChange = { minutes = it }, placeholder = { Text("In min") }, modifier = Modifier.weight(1f))
            Button(onClick = {
                val m = minutes.toLongOrNull() ?: return@Button
                if (title.isBlank()) return@Button
                val trigger = System.currentTimeMillis() + m * 60_000
                scope.launch {
                    val id = app.db.reminderDao().insert(Reminder(title = title, triggerAt = trigger))
                    ReminderScheduler.schedule(context, Reminder(id = id, title = title, triggerAt = trigger))
                    title = ""
                }
            }) { Text("Set") }
        }
        LazyColumn {
            items(reminders) { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("${r.title} — ${SimpleDateFormat("HH:mm dd MMM", Locale.getDefault()).format(Date(r.triggerAt))}", modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun RoutinesTab(app: DevBoxApp, scope: CoroutineScope, context: Context) {
    val routines by app.db.routineDao().all().collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf("8") }
    var minute by remember { mutableStateOf("0") }

    Column(Modifier.padding(12.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("Routine name") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = hour, onValueChange = { hour = it }, label = { Text("Hour") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = minute, onValueChange = { minute = it }, label = { Text("Min") }, modifier = Modifier.weight(1f))
            Button(onClick = {
                val h = hour.toIntOrNull() ?: return@Button
                val m = minute.toIntOrNull() ?: return@Button
                if (name.isBlank()) return@Button
                scope.launch {
                    val newRoutine = Routine(name = name, description = "", hour = h, minute = m, daysOfWeek = "1,2,3,4,5,6,7")
                    val id = app.db.routineDao().insert(newRoutine)
                    RoutineScheduler.schedule(context, newRoutine.copy(id = id))
                    name = ""
                }
            }) { Text("Add") }
        }
        LazyColumn {
            items(routines) { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("${r.name} — ${"%02d:%02d".format(r.hour, r.minute)}", modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}
