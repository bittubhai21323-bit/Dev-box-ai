package com.example.devboxai.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    var tab by rememberSaveable { mutableStateOf(0) }
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
    // Saare tasks dikhao — sirf aaj wale dikhane se purane task "gayab" lagte the
    val tasks by app.db.taskDao().all().collectAsState(initial = emptyList())
    var text by rememberSaveable { mutableStateOf("") }

    Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), placeholder = { Text("New task") })
            IconButton(onClick = { if (text.isNotBlank()) scope.launch { app.db.taskDao().insert(DailyTask(title = text, date = today)); text = "" } }) {
                Icon(Icons.Default.Add, "Add")
            }
        }
        if (tasks.isEmpty()) Text("Koi task nahi hai — upar likh kar + dabao", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        LazyColumn {
            items(tasks, key = { it.id }) { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Checkbox(checked = t.done, onCheckedChange = { c -> scope.launch { app.db.taskDao().update(t.copy(done = c)) } })
                    Column(Modifier.weight(1f)) {
                        Text(t.title)
                        if (t.date != today) Text(t.date, style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { scope.launch { app.db.taskDao().delete(t) } }) { Text("✕") }
                }
            }
        }
    }
}

@Composable
private fun JournalTab(app: DevBoxApp, scope: CoroutineScope) {
    val entries by app.db.journalDao().all().collectAsState(initial = emptyList())
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }

    Column(Modifier.padding(12.dp)) {
        OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Title") })
        OutlinedTextField(value = content, onValueChange = { content = it }, modifier = Modifier.fillMaxWidth().height(100.dp), placeholder = { Text("Entry...") })
        Button(onClick = { if (title.isNotBlank() || content.isNotBlank()) scope.launch { app.db.journalDao().insert(JournalEntry(title = title, content = content)); title = ""; content = "" } }) { Text("Save") }
        LazyColumn {
            items(entries, key = { it.id }) { e ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.titleSmall)
                            Text(e.content)
                        }
                        IconButton(onClick = { scope.launch { app.db.journalDao().delete(e) } }) { Text("✕") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemindersTab(app: DevBoxApp, scope: CoroutineScope, context: Context) {
    val reminders by app.db.reminderDao().all().collectAsState(initial = emptyList())
    var title by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("10") }
    var msg by rememberSaveable { mutableStateOf("") }

    Column(Modifier.padding(12.dp)) {
        OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("Reminder") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = minutes, onValueChange = { minutes = it }, placeholder = { Text("In min") }, modifier = Modifier.weight(1f))
            Button(onClick = {
                val m = minutes.toLongOrNull()
                when {
                    title.isBlank() -> msg = "⚠ Reminder ka naam likho"
                    m == null || m <= 0 -> msg = "⚠ Minute sahi number me likho (jaise 10)"
                    else -> {
                        val trigger = System.currentTimeMillis() + m * 60_000
                        scope.launch {
                            val id = app.db.reminderDao().insert(Reminder(title = title, triggerAt = trigger))
                            ReminderScheduler.schedule(context, Reminder(id = id, title = title, triggerAt = trigger))
                            msg = "✓ Reminder set ho gaya"
                            title = ""
                        }
                    }
                }
            }) { Text("Set") }
        }
        if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        LazyColumn {
            items(reminders, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${r.title} — ${SimpleDateFormat("HH:mm dd MMM", Locale.getDefault()).format(Date(r.triggerAt))}", modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            ReminderScheduler.cancel(context, r.id)
                            scope.launch { app.db.reminderDao().delete(r) }
                        }) { Text("✕") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutinesTab(app: DevBoxApp, scope: CoroutineScope, context: Context) {
    val routines by app.db.routineDao().all().collectAsState(initial = emptyList())
    var name by rememberSaveable { mutableStateOf("") }
    var hour by rememberSaveable { mutableStateOf("8") }
    var minute by rememberSaveable { mutableStateOf("0") }
    var msg by rememberSaveable { mutableStateOf("") }

    Column(Modifier.padding(12.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("Routine name") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = hour, onValueChange = { hour = it }, label = { Text("Hour") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = minute, onValueChange = { minute = it }, label = { Text("Min") }, modifier = Modifier.weight(1f))
            Button(onClick = {
                val h = hour.toIntOrNull()
                val m = minute.toIntOrNull()
                when {
                    name.isBlank() -> msg = "⚠ Routine ka naam likho"
                    h == null || h !in 0..23 -> msg = "⚠ Hour 0 se 23 ke beech likho"
                    m == null || m !in 0..59 -> msg = "⚠ Minute 0 se 59 ke beech likho"
                    else -> scope.launch {
                        val newRoutine = Routine(name = name, description = "", hour = h, minute = m, daysOfWeek = "1,2,3,4,5,6,7")
                        val id = app.db.routineDao().insert(newRoutine)
                        RoutineScheduler.schedule(context, newRoutine.copy(id = id))
                        msg = "✓ Routine set ho gaya"
                        name = ""
                    }
                }
            }) { Text("Add") }
        }
        if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        LazyColumn {
            items(routines, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${r.name} — ${"%02d:%02d".format(r.hour, r.minute)}", modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            RoutineScheduler.cancel(context, r.id)
                            scope.launch { app.db.routineDao().delete(r) }
                        }) { Text("✕") }
                    }
                }
            }
        }
    }
}
