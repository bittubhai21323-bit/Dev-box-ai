package com.example.devboxai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatMessage::class, JournalEntry::class, Reminder::class,
        Routine::class, DailyTask::class,
        BlockedApp::class, SecurityLog::class, ProcessSnapshot::class
    ],
    version = 2,
    exportSchema = false
)
abstract class DevBoxDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun journalDao(): JournalDao
    abstract fun reminderDao(): ReminderDao
    abstract fun routineDao(): RoutineDao
    abstract fun taskDao(): TaskDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun securityLogDao(): SecurityLogDao
    abstract fun processSnapshotDao(): ProcessSnapshotDao

    companion object {
        @Volatile private var INSTANCE: DevBoxDatabase? = null
        fun get(context: Context): DevBoxDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, DevBoxDatabase::class.java, "devbox.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
