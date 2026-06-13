package com.eldercareguardian.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eldercareguardian.data.EcgAnomalyStatus
import com.eldercareguardian.data.SensorFrame
import com.eldercareguardian.data.Spo2Quality
import com.eldercareguardian.ui.components.MetricCard
import com.eldercareguardian.ui.components.PillStatus
import com.eldercareguardian.ui.components.SectionTitle
import com.eldercareguardian.ui.components.SignalRow
import com.eldercareguardian.ui.components.StatusPill
import com.eldercareguardian.ui.components.TrendChart
import com.eldercareguardian.ui.theme.AppColors
import com.eldercareguardian.ui.riskProgress
import com.eldercareguardian.ui.rhythmDescription
import com.eldercareguardian.ui.rhythmSeverity

@Composable
fun VitalsScreen(
    frame: SensorFrame,
    hrTrend: List<Float>,
    spo2Trend: List<Float>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EcgPanel(frame.ecgSamples)
        EcgAnomalyCard(frame.ecgAnomaly)
        MetricGrid(frame)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TrendChart(
                label = "HR trend",
                values = hrTrend,
                unit = "bpm",
                lineColor = AppColors.danger,
                modifier = Modifier.weight(1f),
            )
            TrendChart(
                label = "SpO2 trend",
                values = spo2Trend,
                unit = "%",
                lineColor = AppColors.primary,
                modifier = Modifier.weight(1f),
            )
        }
        HealthPanel(frame)
    }
}

@Composable
private fun EcgPanel(samples: List<Float>) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ECG",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E)),
                )
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F0F23)),
            ) {
                if (samples.size > 1) {
                    val step = size.width / (samples.lastIndex).coerceAtLeast(1)
                    val middle = size.height * 0.5f
                    val scale = size.height * 0.4f
                    val path = Path()
                    samples.forEachIndexed { index, value ->
                        val point = Offset(index * step, middle - value * scale)
                        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    drawPath(path, color = Color(0xFF22C55E), style = Stroke(width = 3f))
                }
            }
        }
    }
}

@Composable
private fun SpO2Card(
    value: String,
    quality: Spo2Quality,
    modifier: Modifier = Modifier,
) {
    val (tint, label) = when (quality) {
        Spo2Quality.Reliable -> AppColors.success to "Reliable"
        Spo2Quality.Unreliable -> AppColors.warning to "Unreliable"
        Spo2Quality.NoSignal -> AppColors.danger to "No signal"
    }
    Card(
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("SpO2", color = AppColors.textSecondary, style = MaterialTheme.typography.labelLarge)
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
                    Text(
                        text = " %",
                        modifier = Modifier.padding(bottom = 3.dp),
                        color = AppColors.textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(tint),
                    )
                }
                Text(
                    text = label,
                    color = tint,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EcgAnomalyCard(status: EcgAnomalyStatus) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("ECG rhythm", accentColor = rhythmSeverity(status).let { severity ->
                when (severity) {
                    com.eldercareguardian.data.RiskStatus.High -> AppColors.danger
                    com.eldercareguardian.data.RiskStatus.Medium -> AppColors.warning
                    com.eldercareguardian.data.RiskStatus.Low -> AppColors.primary
                }
            })
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusPill(label = status.displayLabel, status = PillStatus.Risk(rhythmSeverity(status)))
            }
            Text(
                text = rhythmDescription(status),
                color = AppColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MetricGrid(frame: SensorFrame) {
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
            SectionTitle("Vitals")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("HR", "${frame.heartRateBpm}", "bpm", Modifier.weight(1f))
                SpO2Card("%.1f".format(frame.spo2Percent), frame.spo2Quality, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    label = "BP",
                    value = "${frame.systolicMmHg}/${frame.diastolicMmHg}",
                    unit = if (frame.bpEstimated) "est mmHg" else "mmHg",
                    modifier = Modifier.weight(1f),
                )
                MetricCard("Temp", "%.1f".format(frame.skinTempC), "C", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Resp", "${frame.respiratoryRate}", "/min", Modifier.weight(1f))
                MetricCard("HR reserve", "${frame.hrReservePercent}", "%", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HealthPanel(frame: SensorFrame) {
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
            SectionTitle("Health")
            SignalRow("Respiratory rate", "${frame.respiratoryRate} breaths/min", 0.62f)
            SignalRow("Sweat humidity", "%.1f%%".format(frame.humidityPercent), frame.humidityPercent / 100f)
            SignalRow("Sweat rate", "%.2f %%/min".format(frame.sweatRatePercentPerMin), (frame.sweatRatePercentPerMin / 2f).coerceIn(0f, 1f))
            val dehydrationColor = when {
                frame.dehydration == com.eldercareguardian.data.RiskStatus.High -> AppColors.danger
                frame.dehydration == com.eldercareguardian.data.RiskStatus.Medium -> AppColors.warning
                else -> AppColors.primary
            }
            SignalRow("Dehydration risk", frame.dehydration.name, riskProgress(frame.dehydration), barColor = dehydrationColor)
            HorizontalDivider(color = AppColors.borderLight)
            Text(
                text = "Vitals are stored locally first; Samsung Health write support activates after SDK approval.",
                color = AppColors.textTertiary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
