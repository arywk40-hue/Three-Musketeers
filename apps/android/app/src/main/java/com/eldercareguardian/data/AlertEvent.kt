package com.eldercareguardian.data

data class AlertEvent(
    val id: Long = System.currentTimeMillis(),
    val timestampMillis: Long = System.currentTimeMillis(),
    val level: CaregiverAlertStatus,
    val reason: AlertReason,
)

enum class AlertReason(val displayLabel: String) {
    SosButton("SOS button pressed"),
    FallDetected("Fall detected"),
    HeartRateHigh("Heart rate elevated"),
    HeartRateLow("Heart rate low"),
    LowSpO2("SpO₂ dropped"),
    Inactivity("Prolonged inactivity"),
    DeviceAlert("Device alert"),
    LowBattery("Battery low"),
    Resolved("Alert resolved"),
}
