package com.example.devboxai.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM BlockedApp ORDER BY appLabel ASC")
    fun all(): Flow<List<BlockedApp>>

    @Query("SELECT * FROM BlockedApp WHERE packageName = :pkg LIMIT 1")
    suspend fun get(pkg: String): BlockedApp?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: BlockedApp)

    @Query("DELETE FROM BlockedApp WHERE packageName = :pkg")
    suspend fun deleteByPkg(pkg: String)
}

@Dao
interface SecurityLogDao {
    @Query("SELECT * FROM SecurityLog ORDER BY timestamp DESC LIMIT 500")
    fun all(): Flow<List<SecurityLog>>

    @Insert
    suspend fun insert(log: SecurityLog): Long

    @Query("DELETE FROM SecurityLog")
    suspend fun clear()
}

@Dao
interface ProcessSnapshotDao {
    @Query("SELECT * FROM ProcessSnapshot ORDER BY timestamp DESC LIMIT 200")
    fun all(): Flow<List<ProcessSnapshot>>

    @Insert
    suspend fun insert(s: ProcessSnapshot)

    @Query("DELETE FROM ProcessSnapshot WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
}
