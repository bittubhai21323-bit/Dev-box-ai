package com.example.devboxai.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val mood: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerAt: Long,
    val recurringMinutes: Int = 0,
    val active: Boolean = true
)

@Entity
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: String,
    val active: Boolean = true
)

@Entity
data class DailyTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val done: Boolean = false,
    val date: String
)

@Entity
data class BlockedApp(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val reason: String = "",
    val blockedAt: Long = System.currentTimeMillis()
)

@Entity
data class SecurityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val event: String,
    val packageName: String? = null,
    val details: String = "",
    val severity: String = "info",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity
data class ProcessSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val processName: String,
    val pid: Int,
    val cpuPercent: Float,
    val memoryMb: Float,
    val timestamp: Long = System.currentTimeMillis()
)
