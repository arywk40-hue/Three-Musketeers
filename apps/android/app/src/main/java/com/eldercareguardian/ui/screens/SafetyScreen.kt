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
import com.eldercareguardian.data.SensorFrame
import com.eldercareguardian.ui.components.MetricCard
import com.eldercareguardian.ui.components.PillStatus
import com.eldercareguardian.ui.components.SectionTitle
import com.eldercareguardian.ui.components.SignalRow
import com.eldercareguardian.ui.components.StatusPill
import com.eldercareguardian.ui.fatigueProgress
import com.eldercareguardian.ui.postureProgress
import com.eldercareguardian.ui.riskProgress

@Composable
fun SafetyScreen(
    frame: SensorFrame,
    sosOverride: Boolean,
    onTriggerSosDemo: () -> Unit,
    onClearSosDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SafetyPanel(frame, sosOverride, onTriggerSosDemo, onClearSosDemo)
        MotionPanel(frame)
        VitalsRiskPanel(frame)
    }
}

@Composable
private fun SafetyPanel(
    frame: SensorFrame,
    sosOverride: Boolean,
    onTriggerSosDemo: () -> Unit,
    onClearSosDemo: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Safety")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MetricCard("Fall risk", frame.fallRisk.name, "", Modifier.weight(1f))
                MetricCard("SOS", if (frame.sosActive) "Active" else "Off", "", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(label = frame.posture.name, status = PillStatus.Posture(frame.posture))
                StatusPill(label = frame.caregiverAlert.name, status = PillStatus.CaregiverAlert(frame.caregiverAlert))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(label = "Fatigue ${frame.fatigue.name}", status = PillStatus.Fatigue(frame.fatigue))
            }
            HorizontalDivider(color = Color(0xFFE2E8F0))
            val sosButtonColor = if (sosOverride) Color(0xFFB91C1C) else Color(0xFF0F766E)
            Button(
                onClick = if (sosOverride) onClearSosDemo else onTriggerSosDemo,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = sosButtonColor),
            ) {
                Text(
                    text = if (sosOverride) "Clear SOS Demo" else "Trigger SOS (Demo)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "Demo trigger \u2014 simulates an emergency event",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun MotionPanel(frame: SensorFrame) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Motion safety")
            SignalRow("Fall risk", frame.fallRisk.name, riskProgress(frame.fallRisk))
            SignalRow("Posture stability", frame.posture.name, postureProgress(frame.posture))
            SignalRow("Inactivity", "${frame.inactivityMinutes} min", (frame.inactivityMinutes / 30f).coerceIn(0f, 1f))
            SignalRow("IMU magnitude", "%.2f m/s\u00b2".format(frame.imuMagnitude), (frame.imuMagnitude / 25f).coerceIn(0f, 1f))
        }
    }
}

@Composable
private fun VitalsRiskPanel(frame: SensorFrame) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Vitals risk")
            SignalRow("Composite risk", frame.vitalsRisk.name, riskProgress(frame.vitalsRisk))
            SignalRow("Fatigue", frame.fatigue.name, fatigueProgress(frame.fatigue))
            SignalRow("HR reserve", "${frame.hrReservePercent}%", (frame.hrReservePercent / 100f).coerceIn(0f, 1f))
        }
    }
}
