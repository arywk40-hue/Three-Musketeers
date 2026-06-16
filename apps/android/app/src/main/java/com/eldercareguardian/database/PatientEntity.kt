package com.eldercareguardian.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.eldercareguardian.data.Patient

@Entity(tableName = "patients")
data class PatientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val caregiverName: String = "",
    val caregiverPhone: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val ageYears: Int = 70,
)

fun PatientEntity.toPatient(): Patient = Patient(
    id = id,
    name = name,
    caregiverName = caregiverName,
    caregiverPhone = caregiverPhone,
    notes = notes,
    createdAt = createdAt,
    ageYears = ageYears,
)

fun Patient.toEntity(): PatientEntity = PatientEntity(
    id = id,
    name = name,
    caregiverName = caregiverName,
    caregiverPhone = caregiverPhone,
    notes = notes,
    createdAt = createdAt,
    ageYears = ageYears,
)
