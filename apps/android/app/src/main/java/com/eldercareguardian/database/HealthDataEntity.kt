package com.eldercareguardian.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.eldercareguardian.data.HealthSnapshot

@Entity(tableName = "health_data")
data class HealthDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long = 0,
    val timestampMillis: Long,
    val heartRateBpm: Int,
    val spo2Percent: Float,
    val respiratoryRate: Int,
    val skinTempC: Float,
    val posture: String,
    val fatigue: String,
    val fallRisk: String,
    val caregiverAlert: String,
    val batteryPercent: Int?,
)

fun HealthDataEntity.toHealthSnapshot() = HealthSnapshot(
    id = id,
    patientId = patientId,
    timestampMillis = timestampMillis,
    heartRateBpm = heartRateBpm,
    spo2Percent = spo2Percent,
    respiratoryRate = respiratoryRate,
    skinTempC = skinTempC,
    posture = posture,
    fatigue = fatigue,
    fallRisk = fallRisk,
    caregiverAlert = caregiverAlert,
    batteryPercent = batteryPercent,
)

fun HealthSnapshot.toEntity() = HealthDataEntity(
    id = id,
    patientId = patientId,
    timestampMillis = timestampMillis,
    heartRateBpm = heartRateBpm,
    spo2Percent = spo2Percent,
    respiratoryRate = respiratoryRate,
    skinTempC = skinTempC,
    posture = posture,
    fatigue = fatigue,
    fallRisk = fallRisk,
    caregiverAlert = caregiverAlert,
    batteryPercent = batteryPercent,
)
