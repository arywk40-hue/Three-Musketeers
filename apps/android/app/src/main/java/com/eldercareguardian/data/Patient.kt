package com.eldercareguardian.data

data class Patient(
    val id: Long = 0,
    val name: String,
    val caregiverName: String = "",
    val caregiverPhone: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Patient age in years. Used to compute age-adjusted HRmax (208 − 0.7 × age). */
    val ageYears: Int = 70,
)
