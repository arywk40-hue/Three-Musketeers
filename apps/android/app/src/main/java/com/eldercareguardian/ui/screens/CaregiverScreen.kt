package com.eldercareguardian.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eldercareguardian.data.AlertEvent
import com.eldercareguardian.data.RiskStatus
import com.eldercareguardian.data.SensorFrame
import com.eldercareguardian.ui.components.AlertTimeline
import com.eldercareguardian.ui.components.BatteryRow
import com.eldercareguardian.ui.components.MetricCard
import com.eldercareguardian.ui.components.PillStatus
import com.eldercareguardian.ui.components.SectionTitle
import com.eldercareguardian.ui.components.SignalRow
import com.eldercareguardian.ui.components.StatusPill
import com.eldercareguardian.ui.theme.AppColors
import com.eldercareguardian.ui.riskProgress

@Composable
fun CaregiverScreen(
    frame: SensorFrame,
    caregiverDisplayName: String,
    caregiverPhoneNumber: String,
    onCallCaregiver: () -> Unit,
    alertHistory: List<AlertEvent>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CaregiverPanel(frame, caregiverDisplayName, caregiverPhoneNumber, onCallCaregiver)
        DailyStatusPanel(frame)
        AlertTimeline(events = alertHistory)
    }
}

@Composable
private fun CaregiverPanel(
    frame: SensorFrame,
    caregiverDisplayName: String,
    caregiverPhoneNumber: String,
    onCallCaregiver: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Caregiver alert")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Status", frame.caregiverAlert.name, "", Modifier.weight(1f))
                MetricCard("Check-in", if (frame.sosActive) "Needed" else "OK", "", Modifier.weight(1f))
            }
            StatusPill(label = frame.caregiverAlert.name, status = PillStatus.CaregiverAlert(frame.caregiverAlert))
            HorizontalDivider(color = AppColors.borderLight)
            Text(
                text = "Caregiver contact",
                color = AppColors.textSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = caregiverDisplayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.textPrimary,
                    )
                    Text(
                        text = caregiverPhoneNumber,
                        color = AppColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = onCallCaregiver,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.primary),
                ) {
                    Text("Call")
                }
            }
        }
    }
}

@Composable
private fun DailyStatusPanel(frame: SensorFrame) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Daily status")
            val hydrationColor = when (frame.dehydration) {
                RiskStatus.High -> AppColors.danger
                RiskStatus.Medium -> AppColors.warning
                RiskStatus.Low -> AppColors.primary
            }
            SignalRow("Hydration risk", frame.dehydration.name, riskProgress(frame.dehydration), barColor = hydrationColor)
            SignalRow("Breathing trend", "${frame.respiratoryRate} breaths/min", 0.55f)
            SignalRow("HR reserve", "${frame.hrReservePercent}%", (frame.hrReservePercent / 100f).coerceIn(0f, 1f))
            BatteryRow(frame.batteryPercent)
        }
    }
}
