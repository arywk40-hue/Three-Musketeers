package com.eldercareguardian.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eldercareguardian.data.CaregiverAlertStatus
import com.eldercareguardian.data.RiskStatus
import com.eldercareguardian.data.SensorFrame
import com.eldercareguardian.ui.components.MetricCard
import com.eldercareguardian.ui.components.PillStatus
import com.eldercareguardian.ui.components.SectionTitle
import com.eldercareguardian.ui.components.SignalRow
import com.eldercareguardian.ui.components.StatusPill
import com.eldercareguardian.ui.theme.AppColors
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
            SectionTitle("Safety")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val fallSeverity = when (frame.fallRisk) {
                    RiskStatus.High -> AppColors.danger
                    RiskStatus.Medium -> AppColors.warning
                    RiskStatus.Low -> AppColors.primary
                }
                MetricCard("Fall risk", frame.fallRisk.name, "", Modifier.weight(1f), accentColor = fallSeverity)
                MetricCard("SOS", if (frame.sosActive) "Active" else "Off", "", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(label = frame.posture.name, status = PillStatus.Posture(frame.posture))
                StatusPill(label = frame.caregiverAlert.name, status = PillStatus.CaregiverAlert(frame.caregiverAlert))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(label = "Fatigue ${frame.fatigue.name}", status = PillStatus.Fatigue(frame.fatigue))
            }
            HorizontalDivider(color = AppColors.borderLight)
            val infiniteTransition = rememberInfiniteTransition(label = "sosPulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.55f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "pulseAlpha",
            )
            val sosButtonColor = if (sosOverride) AppColors.danger else AppColors.primary
            val sosAlpha = if (sosOverride) pulseAlpha else 1f
            Button(
                onClick = if (sosOverride) onClearSosDemo else onTriggerSosDemo,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = sosButtonColor.copy(alpha = sosAlpha)),
            ) {
                Text(
                    text = if (sosOverride) "Clear SOS Demo" else "Trigger SOS (Demo)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "Demo trigger \u2014 simulates an emergency event",
                color = AppColors.textTertiary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun MotionPanel(frame: SensorFrame) {
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
            SectionTitle("Motion safety")
            val fallColor = when (frame.fallRisk) {
                RiskStatus.High -> AppColors.danger
                RiskStatus.Medium -> AppColors.warning
                RiskStatus.Low -> AppColors.primary
            }
            SignalRow("Fall risk", frame.fallRisk.name, riskProgress(frame.fallRisk), barColor = fallColor)
            val postureColor = when (frame.posture) {
                com.eldercareguardian.data.PostureStatus.Bad -> AppColors.danger
                com.eldercareguardian.data.PostureStatus.Warning -> AppColors.warning
                com.eldercareguardian.data.PostureStatus.Good -> AppColors.success
            }
            SignalRow("Posture stability", frame.posture.name, postureProgress(frame.posture), barColor = postureColor)
            SignalRow("Inactivity", "${frame.inactivityMinutes} min", (frame.inactivityMinutes / 30f).coerceIn(0f, 1f))
            SignalRow("IMU magnitude", "%.2f m/s\u00b2".format(frame.imuMagnitude), (frame.imuMagnitude / 25f).coerceIn(0f, 1f))
        }
    }
}

@Composable
private fun VitalsRiskPanel(frame: SensorFrame) {
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
            SectionTitle("Vitals risk")
            val riskColor = when (frame.vitalsRisk) {
                RiskStatus.High -> AppColors.danger
                RiskStatus.Medium -> AppColors.warning
                RiskStatus.Low -> AppColors.primary
            }
            SignalRow("Composite risk", frame.vitalsRisk.name, riskProgress(frame.vitalsRisk), barColor = riskColor)
            val fatigueColor = when (frame.fatigue) {
                com.eldercareguardian.data.FatigueStatus.Stop -> AppColors.danger
                com.eldercareguardian.data.FatigueStatus.Caution -> AppColors.warning
                com.eldercareguardian.data.FatigueStatus.Safe -> AppColors.primary
            }
            SignalRow("Fatigue", frame.fatigue.name, fatigueProgress(frame.fatigue), barColor = fatigueColor)
            SignalRow("HR reserve", "${frame.hrReservePercent}%", (frame.hrReservePercent / 100f).coerceIn(0f, 1f))
        }
    }
}
