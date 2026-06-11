package com.eldercareguardian.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
                lineColor = Color(0xFFDC2626),
                modifier = Modifier.weight(1f),
            )
            TrendChart(
                label = "SpO\u2082 trend",
                values = spo2Trend,
                unit = "%",
                lineColor = Color(0xFF2563EB),
                modifier = Modifier.weight(1f),
            )
        }
        HealthPanel(frame)
    }
}

@Composable
private fun EcgPanel(samples: List<Float>) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("ECG", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFF1F5F9))
            ) {
                if (samples.size > 1) {
                    val step = size.width / (samples.lastIndex).coerceAtLeast(1)
                    val middle = size.height * 0.54f
                    val scale = size.height * 0.38f
                    val path = Path()
                    samples.forEachIndexed { index, value ->
                        val point = Offset(index * step, middle - value * scale)
                        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    drawPath(path, color = Color(0xFF0F766E), style = Stroke(width = 4f))
                }
            }
        }
    }
}

@Composable
private fun SpO2QualityChip(quality: Spo2Quality) {
    val (color, text) = when (quality) {
        Spo2Quality.Reliable -> Color(0xFF0F766E) to "Signal reliable"
        Spo2Quality.Unreliable -> Color(0xFFB45309) to "Signal unreliable"
        Spo2Quality.NoSignal -> Color(0xFFB91C1C) to "No signal"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EcgAnomalyCard(status: EcgAnomalyStatus) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("ECG rhythm")
                StatusPill(label = status.displayLabel, status = PillStatus.Risk(rhythmSeverity(status)))
            }
            Text(
                text = rhythmDescription(status),
                color = Color(0xFF475569),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MetricGrid(frame: SensorFrame) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("HR", "${frame.heartRateBpm}", "bpm", Modifier.weight(1f))
            MetricCard("SpO2", "%.1f".format(frame.spo2Percent), "%", Modifier.weight(1f))
        }
        SpO2QualityChip(frame.spo2Quality)
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

@Composable
private fun HealthPanel(frame: SensorFrame) {
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
            SectionTitle("Health")
            SignalRow("Respiratory rate", "${frame.respiratoryRate} breaths/min", 0.62f)
            SignalRow("Sweat humidity", "%.1f%%".format(frame.humidityPercent), frame.humidityPercent / 100f)
            SignalRow("Sweat rate", "%.2f %%/min".format(frame.sweatRatePercentPerMin), (frame.sweatRatePercentPerMin / 2f).coerceIn(0f, 1f))
            SignalRow("Dehydration risk", frame.dehydration.name, riskProgress(frame.dehydration))
            HorizontalDivider(color = Color(0xFFE2E8F0))
            Text(
                text = "Vitals are stored locally first; Samsung Health write support activates after SDK approval.",
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
