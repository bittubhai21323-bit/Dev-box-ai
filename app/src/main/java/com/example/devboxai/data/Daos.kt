package com.example.devboxai.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM ChatMessage WHERE conversationId = :cid ORDER BY timestamp ASC")
    fun messages(cid: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM ChatMessage WHERE conversationId = :cid ORDER BY timestamp DESC LIMIT :limit")
    suspend fun lastMessages(cid: String, limit: Int): List<ChatMessage>

    @Insert suspend fun insert(m: ChatMessage): Long
    @Query("DELETE FROM ChatMessage WHERE conversationId = :cid") suspend fun clear(cid: String)
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM JournalEntry ORDER BY timestamp DESC") fun all(): Flow<List<JournalEntry>>
    @Insert suspend fun insert(e: JournalEntry): Long
    @Delete suspend fun delete(e: JournalEntry)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM Reminder ORDER BY triggerAt ASC") fun all(): Flow<List<Reminder>>
    @Insert suspend fun insert(r: Reminder): Long
    @Update suspend fun update(r: Reminder)
    @Delete suspend fun delete(r: Reminder)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM Routine") fun all(): Flow<List<Routine>>
    @Insert suspend fun insert(r: Routine): Long
    @Update suspend fun update(r: Routine)
    @Delete suspend fun delete(r: Routine)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM DailyTask WHERE date = :d") fun forDate(d: String): Flow<List<DailyTask>>
    @Insert suspend fun insert(t: DailyTask): Long
    @Update suspend fun update(t: DailyTask)
    @Delete suspend fun delete(t: DailyTask)
}
