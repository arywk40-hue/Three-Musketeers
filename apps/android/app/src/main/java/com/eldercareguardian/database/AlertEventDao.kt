package com.eldercareguardian.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AlertEventEntity)

    @Query("SELECT * FROM alert_events ORDER BY timestampMillis DESC LIMIT 50")
    fun getRecent(): Flow<List<AlertEventEntity>>

    @Query("SELECT * FROM alert_events WHERE patientId = :patientId ORDER BY timestampMillis DESC LIMIT 50")
    fun getRecentForPatient(patientId: Long): Flow<List<AlertEventEntity>>

    @Query("SELECT * FROM alert_events ORDER BY timestampMillis DESC")
    suspend fun getAll(): List<AlertEventEntity>

    @Query("DELETE FROM alert_events WHERE timestampMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)

    @Query("DELETE FROM alert_events WHERE patientId = :patientId AND timestampMillis < :beforeMillis")
    suspend fun deleteOlderThanForPatient(patientId: Long, beforeMillis: Long)

    @Query("DELETE FROM alert_events")
    suspend fun deleteAll()
}
