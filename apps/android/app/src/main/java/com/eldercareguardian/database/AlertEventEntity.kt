package com.eldercareguardian.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.eldercareguardian.data.AlertEvent
import com.eldercareguardian.data.AlertReason
import com.eldercareguardian.data.CaregiverAlertStatus

@Entity(tableName = "alert_events")
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long = 0,
    val timestampMillis: Long,
    val levelName: String,
    val reasonName: String,
)

fun AlertEventEntity.toAlertEvent(): AlertEvent = AlertEvent(
    id = id,
    patientId = patientId,
    timestampMillis = timestampMillis,
    level = CaregiverAlertStatus.valueOf(levelName),
    reason = AlertReason.valueOf(reasonName),
)

fun AlertEvent.toEntity(): AlertEventEntity = AlertEventEntity(
    id = id,
    patientId = patientId,
    timestampMillis = timestampMillis,
    levelName = level.name,
    reasonName = reason.name,
)
