package com.smartsuit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import kotlin.math.sqrt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartsuit.ble.BleConnectionState
import com.smartsuit.ble.DiscoveredBleDevice
import com.smartsuit.ble.SmartSuitBleTelemetry
import com.smartsuit.data.AlertEvent
import com.smartsuit.data.CaregiverAlertStatus
import com.smartsuit.data.EcgAnomalyStatus
import com.smartsuit.data.FatigueStatus
import com.smartsuit.data.PostureStatus
import com.smartsuit.data.RiskStatus
import com.smartsuit.data.SensorFrame
import com.smartsuit.permissions.SmartSuitPermissions
import com.smartsuit.samsung.SamsungHealthState
import com.smartsuit.ui.components.AlertTimeline
import com.smartsuit.ui.components.PillStatus
import com.smartsuit.ui.components.StatusPill
import com.smartsuit.ui.components.TrendChart
import com.smartsuit.ui.components.UrgentAlertBanner
import com.smartsuit.ui.screens.SettingsScreen
import com.smartsuit.notifications.CaregiverContact

@Composable
fun SmartSuitApp(
    smartSuitViewModel: SmartSuitViewModel = viewModel(),
) {
    val frame by smartSuitViewModel.frames.collectAsState()
    val bleConnectionState by smartSuitViewModel.bleConnectionState.collectAsState()
    val discoveredDevices by smartSuitViewModel.discoveredDevices.collectAsState()
    val bleTelemetry by smartSuitViewModel.bleTelemetry.collectAsState()
    val samsungState by smartSuitViewModel.samsungState.collectAsState()
    val sosOverride by smartSuitViewModel.sosOverride.collectAsState()
    val acknowledgedUrgent by smartSuitViewModel.acknowledgedUrgent.collectAsState()
    val alertHistory by smartSuitViewModel.alertHistory.collectAsState()
    val hrTrend by smartSuitViewModel.hrTrend.collectAsState()
    val spo2Trend by smartSuitViewModel.spo2Trend.collectAsState()
    val caregiverPhoneNumber by smartSuitViewModel.caregiverPhoneNumber.collectAsState()
    val caregiverDisplayName by smartSuitViewModel.caregiverDisplayName.collectAsState()
    val permissionController = rememberPermissionController()
    var selectedTab by remember { mutableStateOf(AppTab.Vitals) }
    var sessionMode by remember { mutableStateOf(SessionMode.Demo) }

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
            frame?.let {
                AppShell(
                    frame = it,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    sessionMode = sessionMode,
                    onSessionModeSelected = { sessionMode = it },
                    missingPermissions = permissionController.missingPermissions,
                    onRequestPermissions = permissionController.requestPermissions,
                    bleConnectionState = bleConnectionState,
                    discoveredDevices = discoveredDevices,
                    bleTelemetry = bleTelemetry,
                    samsungState = samsungState,
                    sosOverride = sosOverride,
                    acknowledgedUrgent = acknowledgedUrgent,
                    alertHistory = alertHistory,
                    hrTrend = hrTrend,
                    spo2Trend = spo2Trend,
                    caregiverDisplayName = caregiverDisplayName,
                    caregiverPhoneNumber = caregiverPhoneNumber,
                    onCallCaregiver = { ctx ->
                        if (com.smartsuit.settings.isValidPhone(caregiverPhoneNumber)) {
                            CaregiverContact.launchDialer(ctx, caregiverPhoneNumber)
                        }
                    },
                    onStartBleScan = smartSuitViewModel::startBleScan,
                    onStopBle = smartSuitViewModel::stopBle,
                    onConnectFirstDevice = smartSuitViewModel::connectToFirstDiscoveredDevice,
                    onStartSamsung = smartSuitViewModel::startSamsungBridge,
                    onTriggerSosDemo = smartSuitViewModel::triggerSosDemo,
                    onClearSosDemo = smartSuitViewModel::clearSosDemo,
                    onAcknowledgeUrgent = smartSuitViewModel::acknowledgeUrgent,
                    onUpdateCaregiverContact = smartSuitViewModel::updateCaregiverContact,
                )
            } ?: LoadingScreen()
        }
    }
}

private enum class SessionMode(val label: String) {
    Demo("Demo"),
    Ble("BLE"),
}

private enum class AppTab(
    val label: String,
    val icon: ImageVector,
) {
    Vitals("Vitals", Icons.Filled.Favorite),
    Safety("Safety", Icons.Filled.HealthAndSafety),
    Caregiver("Care", Icons.Filled.ContactPhone),
    Readiness("Ready", Icons.Filled.Checklist),
    Settings("Settings", Icons.Filled.Settings),
}

private data class PermissionController(
    val missingPermissions: List<String>,
    val requestPermissions: () -> Unit,
)

@Composable
private fun rememberPermissionController(): PermissionController {
    val context = LocalContext.current
    var missingPermissions by remember {
        mutableStateOf(SmartSuitPermissions.missingPermissions(context))
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        missingPermissions = SmartSuitPermissions.missingPermissions(context)
    }

    return PermissionController(
        missingPermissions = missingPermissions,
        requestPermissions = {
            launcher.launch(SmartSuitPermissions.requiredRuntimePermissions().toTypedArray())
        },
    )
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
private fun AppShell(
    frame: SensorFrame,
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    sessionMode: SessionMode,
    onSessionModeSelected: (SessionMode) -> Unit,
    missingPermissions: List<String>,
    onRequestPermissions: () -> Unit,
    bleConnectionState: BleConnectionState,
    discoveredDevices: List<DiscoveredBleDevice>,
    bleTelemetry: SmartSuitBleTelemetry,
    samsungState: SamsungHealthState,
    sosOverride: Boolean,
    acknowledgedUrgent: Boolean,
    alertHistory: List<AlertEvent>,
    hrTrend: List<Float>,
    spo2Trend: List<Float>,
    onStartBleScan: () -> Unit,
    onStopBle: () -> Unit,
    onConnectFirstDevice: () -> Unit,
    onStartSamsung: () -> Unit,
    onTriggerSosDemo: () -> Unit,
    onClearSosDemo: () -> Unit,
    onAcknowledgeUrgent: () -> Unit,
    caregiverDisplayName: String,
    caregiverPhoneNumber: String,
    onCallCaregiver: (android.content.Context) -> Unit,
    onUpdateCaregiverContact: suspend (displayName: String, phoneNumber: String) -> Boolean,
) {
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { padding ->
        if (selectedTab == AppTab.Settings) {
            SettingsScreen(
                initialName = caregiverDisplayName,
                initialPhone = caregiverPhoneNumber,
                onSave = onUpdateCaregiverContact,
                onBack = { onTabSelected(AppTab.Vitals) },
                modifier = Modifier.padding(padding),
            )
        } else {
            val context = androidx.compose.ui.platform.LocalContext.current
            LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Header(
                    frame = frame,
                    sessionMode = sessionMode,
                    onSessionModeSelected = onSessionModeSelected,
                )
            }
            item {
                ModeNotice(sessionMode)
            }
            if (frame.caregiverAlert == CaregiverAlertStatus.Urgent && !acknowledgedUrgent) {
                item {
                    UrgentAlertBanner(
                        latestEvent = alertHistory.firstOrNull(),
                        onAcknowledge = onAcknowledgeUrgent,
                    )
                }
            }
            if (missingPermissions.isNotEmpty()) {
                item {
                    PermissionNotice(
                        missingPermissions = missingPermissions,
                        onRequestPermissions = onRequestPermissions,
                    )
                }
            }
            when (selectedTab) {
                AppTab.Vitals -> {
                    item { EcgPanel(frame.ecgSamples) }
                    item { EcgAnomalyCard(frame.ecgAnomaly) }
                    item { MetricGrid(frame) }
                    item {
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
                                label = "SpO₂ trend",
                                values = spo2Trend,
                                unit = "%",
                                lineColor = Color(0xFF2563EB),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    item { HealthPanel(frame) }
                }
                AppTab.Safety -> {
                    item {
                        SafetyPanel(
                            frame = frame,
                            sosOverride = sosOverride,
                            onTriggerSosDemo = onTriggerSosDemo,
                            onClearSosDemo = onClearSosDemo,
                        )
                    }
                    item { MotionPanel(frame) }
                    item { VitalsRiskPanel(frame) }
                }
                AppTab.Caregiver -> {
                    item {
                        CaregiverPanel(
                            frame = frame,
                            caregiverDisplayName = caregiverDisplayName,
                            caregiverPhoneNumber = caregiverPhoneNumber,
                            onCallCaregiver = { onCallCaregiver(context) },
                        )
                    }
                    item { DailyStatusPanel(frame) }
                    item { AlertTimeline(events = alertHistory) }
                }
                AppTab.Readiness -> {
                    item {
                        ReadinessPanel(
                            missingPermissions = missingPermissions,
                            bleConnectionState = bleConnectionState,
                            samsungState = samsungState,
                        )
                    }
                    item {
                        SamsungHealthPanel(
                            samsungState = samsungState,
                            onStartSamsung = onStartSamsung,
                        )
                    }
                    item {
                        BleConnectionPanel(
                            missingPermissions = missingPermissions,
                            bleConnectionState = bleConnectionState,
                            discoveredDevices = discoveredDevices,
                            bleTelemetry = bleTelemetry,
                            onRequestPermissions = onRequestPermissions,
                            onStartBleScan = onStartBleScan,
                            onStopBle = onStopBle,
                            onConnectFirstDevice = onConnectFirstDevice,
                        )
                    }
                    item { DeploymentPanel() }
                }
                AppTab.Settings -> {
                    // Rendered outside the LazyColumn (see Scaffold content block).
                }
            }
            }
        }
    }
}

@Composable
private fun PermissionNotice(
    missingPermissions: List<String>,
    onRequestPermissions: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Permissions needed for BLE mode",
                    color = Color(0xFF92400E),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = missingPermissions.joinToString { SmartSuitPermissions.label(it) },
                    color = Color(0xFF92400E),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(
                onClick = onRequestPermissions,
                shape = RoundedCornerShape(6.dp),
            ) {
                Text("Grant")
            }
        }
    }
}

@Composable
private fun ModeNotice(sessionMode: SessionMode) {
    val message = when (sessionMode) {
        SessionMode.Demo -> "Demo stream active for reliable elder-care pitch rehearsals."
        SessionMode.Ble -> "BLE scanner looks for ElderCare_v1; simulator remains as fallback until firmware streams sensor data."
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (sessionMode == SessionMode.Demo) Color(0xFFEFF6FF) else Color(0xFFFFF7ED))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = message,
            color = if (sessionMode == SessionMode.Demo) Color(0xFF1D4ED8) else Color(0xFF9A3412),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Header(
    frame: SensorFrame,
    sessionMode: SessionMode,
    onSessionModeSelected: (SessionMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "ElderCare Guardian",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Live session",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF64748B),
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeSwitch(sessionMode, onSessionModeSelected)
            StatusPill(label = frame.fatigue.name, status = PillStatus.Fatigue(frame.fatigue))
        }
    }
}

@Composable
private fun ModeSwitch(
    selected: SessionMode,
    onSelected: (SessionMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SessionMode.entries.forEach { mode ->
            OutlinedButton(
                onClick = { onSelected(mode) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (selected == mode) Color(0xFF0F766E) else Color.Transparent,
                    contentColor = if (selected == mode) Color.White else Color(0xFF334155),
                ),
                border = null,
                modifier = Modifier.height(30.dp),
            ) {
                Text(mode.label, style = MaterialTheme.typography.labelMedium)
            }
        }
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
                text = "Demo trigger — simulates an emergency event",
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
            SignalRow("IMU magnitude", "%.2f m/s²".format(frame.imuMagnitude), (frame.imuMagnitude / 25f).coerceIn(0f, 1f))
        }
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
            SectionTitle("Caregiver alert")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Status", frame.caregiverAlert.name, "", Modifier.weight(1f))
                MetricCard("Check-in", if (frame.sosActive) "Needed" else "OK", "", Modifier.weight(1f))
            }
            StatusPill(label = frame.caregiverAlert.name, status = PillStatus.CaregiverAlert(frame.caregiverAlert))
            HorizontalDivider(color = Color(0xFFE2E8F0))
            Text(
                text = "Caregiver contact",
                color = Color(0xFF64748B),
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
                    )
                    Text(
                        text = caregiverPhoneNumber,
                        color = Color(0xFF475569),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = onCallCaregiver,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
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
            SectionTitle("Daily status")
            SignalRow("Hydration risk", frame.dehydration.name, riskProgress(frame.dehydration))
            SignalRow("Breathing trend", "${frame.respiratoryRate} breaths/min", 0.55f)
            SignalRow("HR reserve", "${frame.hrReservePercent}%", (frame.hrReservePercent / 100f).coerceIn(0f, 1f))
            BatteryRow(frame.batteryPercent)
        }
    }
}

@Composable
private fun ReadinessPanel(
    missingPermissions: List<String>,
    bleConnectionState: BleConnectionState,
    samsungState: SamsungHealthState,
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
            SectionTitle("Showcase readiness")
            ChecklistRow("Demo data stream", true)
            ChecklistRow("Runtime permissions", missingPermissions.isEmpty())
            ChecklistRow("BLE contract defined", true)
            ChecklistRow("ElderCare_v1 connected", bleConnectionState == BleConnectionState.Connected)
            ChecklistRow("Samsung SDK AAR installed", samsungState != SamsungHealthState.NeedsSdkAar)
            ChecklistRow("Partner approval received", samsungState == SamsungHealthState.Ready)
        }
    }
}

@Composable
private fun BleConnectionPanel(
    missingPermissions: List<String>,
    bleConnectionState: BleConnectionState,
    discoveredDevices: List<DiscoveredBleDevice>,
    bleTelemetry: SmartSuitBleTelemetry,
    onRequestPermissions: () -> Unit,
    onStartBleScan: () -> Unit,
    onStopBle: () -> Unit,
    onConnectFirstDevice: () -> Unit,
) {
    val permissionsReady = missingPermissions.isEmpty()

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("BLE connection")
                BleStatePill(bleConnectionState)
            }

            Text(
                text = "Scanner looks for ElderCare_v1. Sensor frames stay on simulator until firmware exposes the custom GATT stream.",
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = if (permissionsReady) onStartBleScan else onRequestPermissions,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(if (permissionsReady) "Scan" else "Grant")
                }
                OutlinedButton(
                    onClick = onStopBle,
                    shape = RoundedCornerShape(6.dp),
                    enabled = bleConnectionState != BleConnectionState.Idle,
                ) {
                    Text("Stop")
                }
                OutlinedButton(
                    onClick = onConnectFirstDevice,
                    shape = RoundedCornerShape(6.dp),
                    enabled = permissionsReady && discoveredDevices.isNotEmpty(),
                ) {
                    Text("Connect")
                }
            }

            if (discoveredDevices.isEmpty()) {
                Text(
                    text = "No ElderCare_v1 advertisements yet.",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                discoveredDevices.take(3).forEach { device ->
                    DeviceRow(device)
                }
            }

            HorizontalDivider(color = Color(0xFFE2E8F0))
            BleTelemetrySummary(bleTelemetry)
        }
    }
}

@Composable
private fun BleTelemetrySummary(telemetry: SmartSuitBleTelemetry) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("GATT telemetry")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TelemetryChip("HR", telemetry.heartRateBpm?.toString() ?: "--", Modifier.weight(1f))
            TelemetryChip("Battery", telemetry.batteryPercent?.let { "$it%" } ?: "--", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TelemetryChip("ECG", telemetry.ecgSamples.size.toString(), Modifier.weight(1f))
            TelemetryChip("SOS", if (telemetry.sosState) "ACTIVE" else "Off", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TelemetryChip("Humidity", telemetry.humidityPercent?.let { "%.1f".format(it) } ?: "--", Modifier.weight(1f))
            TelemetryChip("Resp", telemetry.respiratoryRate?.let { "%.1f".format(it) } ?: "--", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TelemetryChip(
                "IMU mag",
                if (telemetry.wristImu.size >= 3) {
                    val a = telemetry.wristImu
                    "%.2f m/s²".format(sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]))
                } else "--",
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TelemetryChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF8FAFC))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = Color(0xFF64748B), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color(0xFF0F172A), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BleStatePill(state: BleConnectionState) {
    val color = when (state) {
        BleConnectionState.Connected -> Color(0xFF0F766E)
        BleConnectionState.Scanning, BleConnectionState.Connecting -> Color(0xFF2563EB)
        BleConnectionState.Error, BleConnectionState.Unsupported, BleConnectionState.PermissionMissing -> Color(0xFFB91C1C)
        else -> Color(0xFF64748B)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(state.name, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DeviceRow(device: DiscoveredBleDevice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF8FAFC))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(device.name, color = Color(0xFF0F172A), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(device.address, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
        }
        Text("${device.rssi} dBm", color = Color(0xFF475569), style = MaterialTheme.typography.labelMedium)
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
                StatusPill(label = status.name, status = PillStatus.Risk(rhythmSeverity(status)))
            }
            Text(
                text = rhythmDescription(status),
                color = Color(0xFF475569),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun rhythmSeverity(status: EcgAnomalyStatus): RiskStatus = when (status) {
    EcgAnomalyStatus.Normal, EcgAnomalyStatus.Unknown -> RiskStatus.Low
    EcgAnomalyStatus.AFib -> RiskStatus.High
    EcgAnomalyStatus.Tachycardia, EcgAnomalyStatus.Bradycardia -> RiskStatus.Medium
}

private fun rhythmDescription(status: EcgAnomalyStatus): String = when (status) {
    EcgAnomalyStatus.Unknown -> "Not enough RR intervals yet — algorithm needs at least 4 beats."
    EcgAnomalyStatus.Normal -> "Rhythm looks regular, RMSSD in expected range."
    EcgAnomalyStatus.AFib -> "Irregular RR intervals — possible AFib, alert caregiver."
    EcgAnomalyStatus.Tachycardia -> "Sustained HR ≥ 100 bpm — flag for review."
    EcgAnomalyStatus.Bradycardia -> "Sustained HR ≤ 50 bpm — flag for review."
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

@Composable
private fun SamsungHealthPanel(
    samsungState: SamsungHealthState,
    onStartSamsung: () -> Unit,
) {
    val stateLabel = when (samsungState) {
        SamsungHealthState.Ready -> "Ready to write"
        SamsungHealthState.NeedsPartnerApproval -> "Partner approval required"
        SamsungHealthState.NeedsSdkAar -> "AAR missing"
        SamsungHealthState.NeedsPermission -> "Permission needed"
        SamsungHealthState.Disabled -> "Disabled"
        SamsungHealthState.Error -> "Error"
    }
    val stateColor = when (samsungState) {
        SamsungHealthState.Ready -> Color(0xFF0F766E)
        SamsungHealthState.NeedsPermission, SamsungHealthState.Disabled -> Color(0xFFB45309)
        SamsungHealthState.NeedsPartnerApproval -> Color(0xFFB45309)
        else -> Color(0xFFB91C1C)
    }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("Samsung Health")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(stateColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        stateLabel,
                        color = stateColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(
                text = "Data SDK v1.1.0 is a local AAR — drop it into app/libs/ to activate. Writes happen on a 5 s cadence once permissions are granted in Samsung Health.",
                color = Color(0xFF475569),
                style = MaterialTheme.typography.bodySmall,
            )
            // Only surface the connect/permission button when the AAR is present
            // and the bridge is actually usable. Clicking "Request permission"
            // against a NoOp bridge was misleading the user into thinking the
            // app was about to do something it can't.
            if (samsungState == SamsungHealthState.NeedsPermission ||
                samsungState == SamsungHealthState.Disabled
            ) {
                OutlinedButton(
                    onClick = onStartSamsung,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text("Request permission")
                }
            } else if (samsungState == SamsungHealthState.NeedsPartnerApproval) {
                Text(
                    text = "Register at developer.samsung.com/health/data to obtain partner approval, then download the AAR.",
                    color = Color(0xFFB45309),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (samsungState == SamsungHealthState.NeedsSdkAar) {
                Text(
                    text = "Add health-data-api-1.1.0.aar to apps/android/app/libs/ to enable this section.",
                    color = Color(0xFFB45309),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DeploymentPanel() {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("Samsung deployment")
            Text(
                text = "Phase 1 is local dashboard and BLE. Phase 2 enables Samsung Health Data SDK on a real Samsung phone after AAR install and consent setup.",
                color = Color(0xFF475569),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun BatteryRow(percent: Int?) {
    val display = percent?.let { "$it%" } ?: "—"
    val progress = ((percent ?: 0).coerceIn(0, 100)) / 100f
    val isLow = percent != null && percent < 15
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Device battery", color = Color(0xFF475569), style = MaterialTheme.typography.bodyMedium)
            Text(display, color = Color(0xFF0F172A), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (isLow) Color(0xFFB91C1C) else Color(0xFF0F766E),
            trackColor = Color(0xFFE2E8F0),
        )
        if (isLow) {
            Text(
                "Battery low — please charge the device",
                color = Color(0xFFB91C1C),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SignalRow(label: String, value: String, progress: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Color(0xFF475569), style = MaterialTheme.typography.bodyMedium)
            Text(value, color = Color(0xFF0F172A), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = Color(0xFF0F766E),
            trackColor = Color(0xFFE2E8F0),
        )
    }
}

@Composable
private fun ChecklistRow(label: String, checked: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color(0xFF475569), style = MaterialTheme.typography.bodyMedium)
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (checked) "OK" else "--",
                color = if (checked) Color(0xFF15803D) else Color(0xFFB91C1C),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun riskProgress(status: RiskStatus): Float = when (status) {
    RiskStatus.Low -> 0.25f
    RiskStatus.Medium -> 0.6f
    RiskStatus.High -> 0.92f
}

private fun postureProgress(status: PostureStatus): Float = when (status) {
    PostureStatus.Good -> 0.9f
    PostureStatus.Warning -> 0.58f
    PostureStatus.Bad -> 0.25f
}

private fun fatigueProgress(status: FatigueStatus): Float = when (status) {
    FatigueStatus.Safe -> 0.25f
    FatigueStatus.Caution -> 0.65f
    FatigueStatus.Stop -> 0.95f
}

@Preview(showBackground = true, name = "Vitals Dashboard")
@Composable
private fun AppShellVitalsPreview() {
    val mockFrame = SensorFrame(
        timestampMillis = System.currentTimeMillis(),
        heartRateBpm = 72,
        spo2Percent = 98.5f,
        systolicMmHg = 120,
        diastolicMmHg = 80,
        skinTempC = 36.6f,
        humidityPercent = 45f,
        respiratoryRate = 14,
        posture = PostureStatus.Good,
        fatigue = FatigueStatus.Safe,
        dehydration = RiskStatus.Low,
        fallRisk = RiskStatus.Low,
        caregiverAlert = CaregiverAlertStatus.Normal,
        sosActive = false,
        inactivityMinutes = 0,
        supercapPercent = 90,
        ecgSamples = List(50) { (kotlin.math.sin(it.toFloat() * 0.5f)) },
        batteryPercent = 85
    )

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF0F766E),
            secondary = Color(0xFF2563EB),
            surface = Color(0xFFFFFFFF),
            background = Color(0xFFF8FAFC),
        )
    ) {
        AppShell(
            frame = mockFrame,
            selectedTab = AppTab.Vitals,
            onTabSelected = {},
            sessionMode = SessionMode.Demo,
            onSessionModeSelected = {},
            missingPermissions = emptyList(),
            onRequestPermissions = {},
            bleConnectionState = BleConnectionState.Connected,
            discoveredDevices = emptyList(),
            bleTelemetry = SmartSuitBleTelemetry(),
            samsungState = SamsungHealthState.Ready,
            sosOverride = false,
            acknowledgedUrgent = false,
            alertHistory = emptyList(),
            hrTrend = List(20) { 70f + it % 5 },
            spo2Trend = List(20) { 98f + it % 2 },
            caregiverDisplayName = "Jane Doe",
            caregiverPhoneNumber = "+1234567890",
            onCallCaregiver = {},
            onStartBleScan = {},
            onStopBle = {},
            onConnectFirstDevice = {},
            onStartSamsung = {},
            onTriggerSosDemo = {},
            onClearSosDemo = {},
            onAcknowledgeUrgent = {},
            onUpdateCaregiverContact = { _, _ -> true }
        )
    }
}
