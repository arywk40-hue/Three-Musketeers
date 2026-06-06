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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartsuit.ble.BleConnectionState
import com.smartsuit.ble.DiscoveredBleDevice
import com.smartsuit.data.FatigueStatus
import com.smartsuit.data.PostureStatus
import com.smartsuit.data.RiskStatus
import com.smartsuit.data.SensorFrame
import com.smartsuit.permissions.SmartSuitPermissions

@Composable
fun SmartSuitApp(
    smartSuitViewModel: SmartSuitViewModel = viewModel(),
) {
    val frame by smartSuitViewModel.frames.collectAsState()
    val bleConnectionState by smartSuitViewModel.bleConnectionState.collectAsState()
    val discoveredDevices by smartSuitViewModel.discoveredDevices.collectAsState()
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
                    onStartBleScan = smartSuitViewModel::startBleScan,
                    onStopBle = smartSuitViewModel::stopBle,
                    onConnectFirstDevice = smartSuitViewModel::connectToFirstDiscoveredDevice,
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
    Workout("Workout", Icons.Filled.FitnessCenter),
    Power("Power", Icons.Filled.Bolt),
    Readiness("Ready", Icons.Filled.Checklist),
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
    onStartBleScan: () -> Unit,
    onStopBle: () -> Unit,
    onConnectFirstDevice: () -> Unit,
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
                    item { MetricGrid(frame) }
                    item { HealthPanel(frame) }
                }
                AppTab.Workout -> {
                    item { WorkoutPanel(frame) }
                    item { MotionPanel(frame) }
                }
                AppTab.Power -> {
                    item { PowerPanel(frame) }
                    item { PowerStrategyPanel(frame) }
                }
                AppTab.Readiness -> {
                    item {
                        ReadinessPanel(
                            missingPermissions = missingPermissions,
                            bleConnectionState = bleConnectionState,
                        )
                    }
                    item {
                        BleConnectionPanel(
                            missingPermissions = missingPermissions,
                            bleConnectionState = bleConnectionState,
                            discoveredDevices = discoveredDevices,
                            onRequestPermissions = onRequestPermissions,
                            onStartBleScan = onStartBleScan,
                            onStopBle = onStopBle,
                            onConnectFirstDevice = onConnectFirstDevice,
                        )
                    }
                    item { DeploymentPanel() }
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
        SessionMode.Demo -> "Demo stream active for reliable pitch rehearsals."
        SessionMode.Ble -> "BLE scanner is the next build step; simulator remains as fallback until SmartSuit_v1 firmware is connected."
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
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeSwitch(sessionMode, onSessionModeSelected)
            StatusPill(label = frame.fatigue.name, status = frame.fatigue)
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
            MetricCard("BP", "${frame.systolicMmHg}/${frame.diastolicMmHg}", "mmHg", Modifier.weight(1f))
            MetricCard("Temp", "%.1f".format(frame.skinTempC), "C", Modifier.weight(1f))
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
            SectionTitle("Workout")
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
            SectionTitle("Motion intelligence")
            SignalRow("Form confidence", "%.0f%%".format(frame.formScore * 10f), frame.formScore / 10f)
            SignalRow("Posture stability", frame.posture.name, postureProgress(frame.posture))
            SignalRow("Fatigue status", frame.fatigue.name, fatigueProgress(frame.fatigue))
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
            SectionTitle("Power")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("TEG", "%.1f".format(frame.tegPowerMw), "mW", Modifier.weight(1f))
                MetricCard("Solar", "%.1f".format(frame.solarPowerMw), "mW", Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Supercap", color = Color(0xFF475569), style = MaterialTheme.typography.bodyMedium)
                Text("${frame.supercapPercent}%", color = Color(0xFF334155), style = MaterialTheme.typography.labelLarge)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
        }
    }
}

@Composable
private fun PowerStrategyPanel(frame: SensorFrame) {
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
            SectionTitle("Energy strategy")
            SignalRow("Supercap reserve", "${frame.supercapPercent}%", frame.supercapPercent / 100f)
            SignalRow("Burst cycle readiness", if (frame.supercapPercent >= 65) "Ready" else "Charging", frame.supercapPercent / 100f)
            SignalRow("TEG contribution", "%.1f mW".format(frame.tegPowerMw), (frame.tegPowerMw / 8f).coerceIn(0f, 1f))
        }
    }
}

@Composable
private fun ReadinessPanel(
    missingPermissions: List<String>,
    bleConnectionState: BleConnectionState,
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
            ChecklistRow("SmartSuit_v1 connected", bleConnectionState == BleConnectionState.Connected)
            ChecklistRow("Samsung SDK AAR installed", false)
            ChecklistRow("Partner approval received", false)
        }
    }
}

@Composable
private fun BleConnectionPanel(
    missingPermissions: List<String>,
    bleConnectionState: BleConnectionState,
    discoveredDevices: List<DiscoveredBleDevice>,
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
                text = "Scanner looks for SmartSuit_v1. Sensor frames stay on simulator until firmware exposes the custom GATT stream.",
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
                    text = "No SmartSuit_v1 advertisements yet.",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                discoveredDevices.take(3).forEach { device ->
                    DeviceRow(device)
                }
            }
        }
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
