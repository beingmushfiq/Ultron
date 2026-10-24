package com.ultron.companion.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultron.companion.core.designsystem.*
import com.ultron.companion.core.model.ConnectionState

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToChat: () -> Unit,
    onNavigateToActions: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onTriggerVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connState by viewModel.connectionState.collectAsState()
    val status by viewModel.systemStatus.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val approvals by viewModel.pendingApprovalsCount.collectAsState()
    val uiMsg by viewModel.uiMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiMsg) {
        uiMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUiMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ObsidianBlack
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ULTRON PRIME",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = CrimsonPrimary
                        )
                    )
                    Text(
                        text = (connState as? ConnectionState.Connected)?.serverName ?: "AUTONOMOUS COMPANION",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                }

                StatusBadge(connectionState = connState)
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Central Animated Neural Core
            UltronNeuralCore(
                size = 110.dp,
                isActive = connState is ConnectionState.Connected,
                modifier = Modifier.clickable { onTriggerVoice() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (connState is ConnectionState.Connected) "NEURAL UPLINK SYNCHRONIZED" else "UPLINK STANDBY",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.5.sp,
                    color = if (connState is ConnectionState.Connected) CrimsonBright else TextTertiary
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Pending Approvals Banner
            if (approvals.isNotEmpty()) {
                UltronCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToActions() },
                    backgroundColor = CrimsonDark.copy(alpha = 0.4f),
                    borderColor = CrimsonPrimary
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Alert", tint = CrimsonBright)
                            Column {
                                Text(
                                    text = "${approvals.size} Action Approval Required",
                                    style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary)
                                )
                                Text(
                                    text = approvals.first().title,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Open", tint = TextSecondary)
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Real System Telemetry Card
            if (status != null && status!!.telemetry.cpu_percent != null) {
                UltronCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "WORKSTATION TELEMETRY",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, color = TextTertiary)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TelemetryItem(
                            label = "CPU LOAD",
                            value = "${status!!.telemetry.cpu_percent}%",
                            color = CrimsonBright
                        )
                        TelemetryItem(
                            label = "RAM USAGE",
                            value = "${status!!.telemetry.memory_percent ?: 0}%",
                            color = SignalAmber
                        )
                        TelemetryItem(
                            label = "PLATFORM",
                            value = status!!.os,
                            color = SignalGreen
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Workspace Presets Strip
            if (presets.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "WORKSPACE PRESETS",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, color = TextTertiary)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(presets) { preset ->
                            PresetChip(preset = preset, onClick = { viewModel.applyPreset(preset) })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Quick Access Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(
                    title = "Assistant Chat",
                    icon = Icons.Default.Chat,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToChat
                )
                QuickActionCard(
                    title = "Action Center",
                    icon = Icons.Default.Bolt,
                    badge = if (approvals.isNotEmpty()) "${approvals.size}" else null,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToActions
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(
                    title = "Neural Memory",
                    icon = Icons.Default.Psychology,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToMemory
                )
                QuickActionCard(
                    title = "Settings",
                    icon = Icons.Default.Settings,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToSettings
                )
            }
        }
    }
}

@Composable
private fun TelemetryItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = TextTertiary))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
    }
}

@Composable
private fun PresetChip(
    preset: com.ultron.companion.core.model.WorkspacePreset,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(GraphitePanel)
            .border(1.dp, SteelBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column {
            Text(
                text = preset.title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp, color = TextPrimary)
            )
            Text(
                text = preset.name.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = CrimsonBright)
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onClick: () -> Unit
) {
    UltronCard(
        modifier = modifier.clickable { onClick() },
        backgroundColor = GraphitePanel
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, tint = CrimsonPrimary)
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(CrimsonPrimary)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = badge, style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary))
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
    }
}
