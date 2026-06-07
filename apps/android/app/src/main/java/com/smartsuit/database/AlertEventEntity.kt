package com.smartsuit.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.smartsuit.data.AlertEvent
import com.smartsuit.data.AlertReason
import com.smartsuit.data.CaregiverAlertStatus

@Entity(tableName = "alert_events")
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val levelName: String,
    val reasonName: String,
)

fun AlertEventEntity.toAlertEvent(): AlertEvent = AlertEvent(
    id = id,
    timestampMillis = timestampMillis,
    level = CaregiverAlertStatus.valueOf(levelName),
    reason = AlertReason.valueOf(reasonName),
)

fun AlertEvent.toEntity(): AlertEventEntity = AlertEventEntity(
    id = id,
    timestampMillis = timestampMillis,
    levelName = level.name,
    reasonName = reason.name,
)
