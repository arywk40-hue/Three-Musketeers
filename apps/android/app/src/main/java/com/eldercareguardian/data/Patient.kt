package com.eldercareguardian.data

data class Patient(
    val id: Long = 0,
    val name: String,
    val caregiverName: String = "",
    val caregiverPhone: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
