package com.eldercareguardian.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDataDao {
    @Insert
    suspend fun insert(snapshot: HealthDataEntity)

    @Query("SELECT * FROM health_data WHERE patientId = :patientId ORDER BY timestampMillis DESC")
    fun getAllForPatient(patientId: Long): Flow<List<HealthDataEntity>>

    @Query("SELECT * FROM health_data ORDER BY timestampMillis DESC")
    suspend fun getAll(): List<HealthDataEntity>

    @Query("SELECT * FROM health_data WHERE patientId = :patientId ORDER BY timestampMillis DESC LIMIT :limit")
    suspend fun getRecentForPatient(patientId: Long, limit: Int): List<HealthDataEntity>

    @Query("DELETE FROM health_data WHERE patientId = :patientId AND timestampMillis < :beforeMillis")
    suspend fun deleteOlderThanForPatient(patientId: Long, beforeMillis: Long)

    @Query("DELETE FROM health_data")
    suspend fun deleteAll()
}
