package com.smartsuit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartsuit.ble.SmartSuitSimulator
import com.smartsuit.data.FatigueStatus
import com.smartsuit.data.PostureStatus
import com.smartsuit.data.RiskStatus
import com.smartsuit.data.SensorFrame

@Composable
fun SmartSuitApp() {
    val source = remember { SmartSuitSimulator() }
    val frame by source.frames.collectAsState(initial = null)

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF0F766E),
            secondary = Color(0xFF2563EB),
            surface = Color(0xFFFFFFFF),
            background = Color(0xFFF8FAFC),
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            frame?.let { DashboardScreen(it) } ?: LoadingScreen()
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Starting session",
            color = Color(0xFF64748B),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun DashboardScreen(frame: SensorFrame) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Header(frame)
        }
        item {
            EcgPanel(frame.ecgSamples)
        }
        item {
            MetricGrid(frame)
        }
        item {
            WorkoutPanel(frame)
        }
        item {
            PowerPanel(frame)
        }
    }
}

@Composable
private fun Header(frame: SensorFrame) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Smart Suit",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Live session",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF64748B),
            )
        }
        StatusPill(label = frame.fatigue.name, status = frame.fatigue)
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
            androidx.compose.foundation.Canvas(
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
private fun MetricGrid(frame: SensorFrame) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("HR", "${frame.heartRateBpm}", "bpm", Modifier.weight(1f))
            MetricCard("SpO2", "%.1f".format(frame.spo2Percent), "%", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("BP", "${frame.systolicMmHg}/${frame.diastolicMmHg}", "mmHg", Modifier.weight(1f))
            MetricCard("Temp", "%.1f".format(frame.skinTempC), "C", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = Color(0xFF64748B), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = " $unit",
                    modifier = Modifier.padding(bottom = 3.dp),
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun WorkoutPanel(frame: SensorFrame) {
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
            Text("Workout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MetricCard("Reps", frame.reps.toString(), "total", Modifier.weight(1f))
                MetricCard("Form", "%.1f".format(frame.formScore), "/10", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(label = frame.posture.name, status = frame.posture)
                StatusPill(label = frame.dehydration.name, status = frame.dehydration)
            }
        }
    }
}

@Composable
private fun PowerPanel(frame: SensorFrame) {
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
            Text("Power", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("TEG", "%.1f".format(frame.tegPowerMw), "mW", Modifier.weight(1f))
                MetricCard("Solar", "%.1f".format(frame.solarPowerMw), "mW", Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFFE2E8F0))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(frame.supercapPercent / 100f)
                            .height(10.dp)
                            .background(Color(0xFF2563EB))
                    )
                }
                Text("${frame.supercapPercent}%", color = Color(0xFF334155))
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, status: Any) {
    val color = when (status) {
        PostureStatus.Bad, FatigueStatus.Stop, RiskStatus.High -> Color(0xFFB91C1C)
        PostureStatus.Warning, FatigueStatus.Caution, RiskStatus.Medium -> Color(0xFFB45309)
        else -> Color(0xFF0F766E)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}
