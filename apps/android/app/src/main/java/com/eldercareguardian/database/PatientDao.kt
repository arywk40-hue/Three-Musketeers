package com.eldercareguardian.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Query("SELECT * FROM patients ORDER BY createdAt ASC")
    fun getAll(): Flow<List<PatientEntity>>

    @Query("SELECT * FROM patients WHERE id = :patientId")
    suspend fun getById(patientId: Long): PatientEntity?

    @Insert
    suspend fun insert(patient: PatientEntity): Long

    @Update
    suspend fun update(patient: PatientEntity)

    @Delete
    suspend fun delete(patient: PatientEntity)

    @Query("DELETE FROM patients")
    suspend fun deleteAll()

    @Query("SELECT * FROM patients ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<PatientEntity>
}
