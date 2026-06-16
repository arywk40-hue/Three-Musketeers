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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eldercareguardian.data.EcgAnomalyStatus
import com.eldercareguardian.data.FatigueStatus
import com.eldercareguardian.data.RiskStatus
import com.eldercareguardian.data.SensorFrame
import com.eldercareguardian.ui.theme.AppColors

// ── Zone model ───────────────────────────────────────────────────────────────

private data class BodyZone(
    val name: String,
    val detail: String,
    val score: Float,          // 0.0 (critical) … 1.0 (good)
    val scoreLabel: String,
)

/** Derive a 0–1 health score for each body zone from the current SensorFrame. */
private fun SensorFrame.toZones(): List<BodyZone> {
    // Heart (HR + ECG rhythm)
    val heartScore = run {
        val hrOk = heartRateBpm in 50..100
        val ecgOk = ecgAnomaly == EcgAnomalyStatus.Normal || ecgAnomaly == EcgAnomalyStatus.Unknown
        when {
            !hrOk && !ecgOk -> 0.25f
            !hrOk || !ecgOk -> 0.55f
            else -> 0.90f
        }
    }
    val heartDetail = "HR ${heartRateBpm} bpm · ${ecgAnomaly.displayLabel}"

    // Lungs (SpO2 + respiratory rate)
    val lungsScore = run {
        val spo2Score = when {
            spo2Percent < 90f -> 0.10f
            spo2Percent < 94f -> 0.50f
            spo2Percent < 96f -> 0.75f
            else -> 1.00f
        }
        val rrScore = when (respiratoryRate) {
            in 12..20 -> 1.00f
            in 9..24  -> 0.65f
            else      -> 0.25f
        }
        (spo2Score * 0.65f + rrScore * 0.35f)
    }
    val lungsDetail = "SpO2 ${"%.0f".format(spo2Percent)}% · RR $respiratoryRate /min"

    // Wrist (IMU fall risk + inactivity)
    val wristScore = run {
        val fallScore = when (fallRisk) {
            RiskStatus.Low    -> 1.00f
            RiskStatus.Medium -> 0.50f
            RiskStatus.High   -> 0.10f
        }
        val inactivityPenalty = (inactivityMinutes / 30f).coerceIn(0f, 0.40f)
        (fallScore - inactivityPenalty).coerceIn(0.05f, 1.00f)
    }
    val wristDetail = "Fall ${fallRisk.name} · Inactivity ${inactivityMinutes} min"

    // Skin (temperature + hydration)
    val skinScore = run {
        val tempScore = when {
            skinTempC >= 38.1f || skinTempC <= 34.0f -> 0.20f
            skinTempC >= 37.1f || skinTempC <= 35.1f -> 0.60f
            else -> 1.00f
        }
        val sweatScore = when {
            sweatRatePercentPerMin >= 1.5f -> 0.45f
            sweatRatePercentPerMin >= 0.8f -> 0.70f
            else -> 1.00f
        }
        (tempScore * 0.6f + sweatScore * 0.4f)
    }
    val skinDetail = "Temp ${"%.1f".format(skinTempC)}°C · Sweat ${"%.2f".format(sweatRatePercentPerMin)} %/min"

    // Muscles (fatigue + HR reserve)
    val muscleScore = run {
        val fatigueScore = when (fatigue) {
            FatigueStatus.Safe    -> 1.00f
            FatigueStatus.Caution -> 0.55f
            FatigueStatus.Stop    -> 0.20f
        }
        val reservePenalty = (hrReservePercent / 100f).coerceIn(0f, 1f) * 0.3f
        (fatigueScore - reservePenalty).coerceIn(0.10f, 1.00f)
    }
    val muscleDetail = "Fatigue ${fatigue.name} · HR reserve ${hrReservePercent}%"

    return listOf(
        BodyZone("Heart", heartDetail, heartScore, scoreLabel(heartScore)),
        BodyZone("Lungs", lungsDetail, lungsScore, scoreLabel(lungsScore)),
        BodyZone("Wrist", wristDetail, wristScore, scoreLabel(wristScore)),
        BodyZone("Skin", skinDetail, skinScore, scoreLabel(skinScore)),
        BodyZone("Muscles", muscleDetail, muscleScore, scoreLabel(muscleScore)),
    )
}

private fun scoreLabel(score: Float) = when {
    score >= 0.80f -> "Good"
    score >= 0.55f -> "Monitor"
    else           -> "Alert"
}

private fun scoreColor(score: Float) = when {
    score >= 0.80f -> AppColors.success
    score >= 0.55f -> AppColors.warning
    else           -> AppColors.danger
}

// ── Overall health score (0–100) ─────────────────────────────────────────────

private fun List<BodyZone>.overallScore(): Int =
    (map { it.score }.average() * 100).toInt().coerceIn(0, 100)

// ── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun BodyScreen(
    frame: SensorFrame,
    modifier: Modifier = Modifier,
) {
    val zones = frame.toZones()
    val overall = zones.overallScore()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OverallScoreCard(overall)
        BodyDiagramCard(zones)
        ZoneDetailList(zones)
    }
}

// ── Overall score ─────────────────────────────────────────────────────────────

@Composable
private fun OverallScoreCard(score: Int) {
    val color = scoreColor(score / 100f)
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Body Health",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textPrimary,
                )
                Text(
                    text = "Average across all sensor zones",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.textSecondary,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$score",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = color,
                )
                Text(
                    text = " / 100",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.textTertiary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
    }
}

// ── Body diagram ──────────────────────────────────────────────────────────────

/**
 * Draws a simplified front-view body silhouette and places coloured dot
 * indicators at 5 zones: head/heart (chest), lungs (upper chest), wrist
 * (right arm), skin (torso centre), muscles (legs).
 */
@Composable
private fun BodyDiagramCard(zones: List<BodyZone>) {
    // zones order: Heart, Lungs, Wrist, Skin, Muscles
    val heart   = zones.getOrNull(0)
    val lungs   = zones.getOrNull(1)
    val wrist   = zones.getOrNull(2)
    val skin    = zones.getOrNull(3)
    val muscles = zones.getOrNull(4)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Sensor Zones",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.textSecondary,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    drawBody(cx, size.height)
                    // Zone dot positions (fractions of canvas size)
                    // Heart — chest centre
                    drawZoneDot(cx, size.height * 0.285f, heart)
                    // Lungs — slightly above heart, offset left
                    drawZoneDot(cx - size.width * 0.08f, size.height * 0.255f, lungs)
                    // Wrist — right arm
                    drawZoneDot(cx + size.width * 0.28f, size.height * 0.44f, wrist)
                    // Skin — torso centre
                    drawZoneDot(cx, size.height * 0.42f, skin)
                    // Muscles — upper leg
                    drawZoneDot(cx + size.width * 0.07f, size.height * 0.72f, muscles)
                }
            }
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                LegendDot(AppColors.success, "Good")
                LegendDot(AppColors.warning, "Monitor")
                LegendDot(AppColors.danger, "Alert")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.textSecondary)
    }
}

private fun DrawScope.drawBody(cx: Float, height: Float) {
    val bodyColor = Color(0xFFE2E8F0)
    val strokeColor = Color(0xFFCBD5E1)
    val stroke = Stroke(width = 2.5f, cap = StrokeCap.Round)

    // Head
    val headR = height * 0.072f
    val headCy = height * 0.085f
    drawCircle(color = bodyColor, radius = headR, center = Offset(cx, headCy))
    drawCircle(color = strokeColor, radius = headR, center = Offset(cx, headCy), style = stroke)

    // Neck
    val neckW = height * 0.038f
    val neckTop = headCy + headR
    val neckBot = headCy + headR * 2.0f
    drawRect(color = bodyColor, topLeft = Offset(cx - neckW / 2, neckTop), size = Size(neckW, neckBot - neckTop))

    // Torso
    val torsoW = height * 0.22f
    val torsoTop = neckBot
    val torsoBot = height * 0.60f
    val torsoPath = Path().apply {
        moveTo(cx - torsoW / 2, torsoTop)
        lineTo(cx - torsoW / 2 * 1.1f, torsoBot)
        lineTo(cx + torsoW / 2 * 1.1f, torsoBot)
        lineTo(cx + torsoW / 2, torsoTop)
        close()
    }
    drawPath(torsoPath, color = bodyColor)
    drawPath(torsoPath, color = strokeColor, style = stroke)

    // Left arm
    val armW = height * 0.055f
    val armTopOffset = torsoW / 2 * 0.9f
    val leftArmPath = Path().apply {
        moveTo(cx - armTopOffset, torsoTop + height * 0.01f)
        lineTo(cx - armTopOffset - armW, torsoTop + height * 0.015f)
        lineTo(cx - armTopOffset - armW * 1.3f, torsoBot * 0.80f)
        lineTo(cx - armTopOffset - armW * 0.5f, torsoBot * 0.80f)
        close()
    }
    drawPath(leftArmPath, color = bodyColor)
    drawPath(leftArmPath, color = strokeColor, style = stroke)

    // Right arm
    val rightArmPath = Path().apply {
        moveTo(cx + armTopOffset, torsoTop + height * 0.01f)
        lineTo(cx + armTopOffset + armW, torsoTop + height * 0.015f)
        lineTo(cx + armTopOffset + armW * 1.3f, torsoBot * 0.80f)
        lineTo(cx + armTopOffset + armW * 0.5f, torsoBot * 0.80f)
        close()
    }
    drawPath(rightArmPath, color = bodyColor)
    drawPath(rightArmPath, color = strokeColor, style = stroke)

    // Left leg
    val legW = height * 0.085f
    val legBot = height * 0.975f
    val leftLegPath = Path().apply {
        moveTo(cx - legW * 0.1f, torsoBot)
        lineTo(cx - legW * 1.1f, torsoBot)
        lineTo(cx - legW * 1.2f, legBot)
        lineTo(cx - legW * 0.2f, legBot)
        close()
    }
    drawPath(leftLegPath, color = bodyColor)
    drawPath(leftLegPath, color = strokeColor, style = stroke)

    // Right leg
    val rightLegPath = Path().apply {
        moveTo(cx + legW * 0.1f, torsoBot)
        lineTo(cx + legW * 1.1f, torsoBot)
        lineTo(cx + legW * 1.2f, legBot)
        lineTo(cx + legW * 0.2f, legBot)
        close()
    }
    drawPath(rightLegPath, color = bodyColor)
    drawPath(rightLegPath, color = strokeColor, style = stroke)
}

private fun DrawScope.drawZoneDot(x: Float, y: Float, zone: BodyZone?) {
    if (zone == null) return
    val color = scoreColor(zone.score)
    // Outer glow ring
    drawCircle(color = color.copy(alpha = 0.20f), radius = 22f, center = Offset(x, y))
    // Filled dot
    drawCircle(color = color, radius = 13f, center = Offset(x, y))
    // White centre
    drawCircle(color = Color.White, radius = 5f, center = Offset(x, y))
}

// ── Zone list ─────────────────────────────────────────────────────────────────

@Composable
private fun ZoneDetailList(zones: List<BodyZone>) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Zone breakdown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.textSecondary,
            )
            zones.forEach { zone -> ZoneRow(zone) }
        }
    }
}

@Composable
private fun ZoneRow(zone: BodyZone) {
    val color = scoreColor(zone.score)
    val barFraction = zone.score.coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                Text(zone.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary)
            }
            Text(
                text = zone.scoreLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AppColors.border),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barFraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
        Text(zone.detail, style = MaterialTheme.typography.bodySmall, color = AppColors.textSecondary)
    }
}
