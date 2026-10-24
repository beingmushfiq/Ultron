package com.ultron.companion.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultron.companion.core.designsystem.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onDisconnected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTts by viewModel.isTtsEnabled.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val feedback by viewModel.uiFeedback.collectAsState()

    var showDisconnectDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(deviceName) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SETTINGS & SECURITY", style = MaterialTheme.typography.titleMedium.copy(color = CrimsonPrimary))
                        Text("DEVICE & TRANSPORT PRIVACY", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBlack)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ObsidianBlack
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Info Card
            UltronCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "DESKTOP CONNECTION DETAILS",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary)
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingInfoRow("Target Host", "${viewModel.storage.host}:${viewModel.storage.port}")
                SettingInfoRow("Protocol", if (viewModel.storage.useTls) "TLS/WSS (Encrypted)" else "Plain HTTP/WS (LAN)")
                SettingInfoRow(
                    "SHA-256 Fingerprint",
                    viewModel.storage.pinnedFingerprint ?: "No Certificate Pinned"
                )
            }

            // Device Name Card
            UltronCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "DEVICE IDENTIFIER",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Companion Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (editName != deviceName) {
                            IconButton(onClick = { viewModel.updateDeviceName(editName) }) {
                                Icon(Icons.Default.Check, contentDescription = "Save", tint = CrimsonBright)
                            }
                        }
                    }
                )
            }

            // Audio & Voice Feedback
            UltronCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "VOICE & SPEECH FEEDBACK",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Speech Output (TTS)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Ultron vocalizes responses through mobile speaker.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, color = TextSecondary)
                        )
                    }
                    Switch(
                        checked = isTts,
                        onCheckedChange = { viewModel.toggleTts(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CrimsonPrimary)
                    )
                }
            }

            // Diagnostics & Privacy
            UltronCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "DIAGNOSTICS & AUDIT",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Export redacted diagnostics log for connection troubleshooting. API tokens and private keys are never included.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val report = viewModel.getRedactedDiagnostics()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Ultron Diagnostics", report))
                        Toast.makeText(context, "Redacted report copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GraphiteElevated),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("COPY REDACTED LOGS", color = TextPrimary)
                }
            }

            // Danger Zone: Disconnect & Revoke
            UltronCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = CrimsonDark.copy(alpha = 0.25f),
                borderColor = SignalRed
            ) {
                Text(
                    text = "REVOCATION & CACHE",
                    style = MaterialTheme.typography.labelSmall.copy(color = SignalRed)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Revoking the session invalidates the device token on desktop and clears mobile credentials.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { showDisconnectDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SignalRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LinkOff, contentDescription = "Disconnect")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("REVOKE & DISCONNECT")
                }
            }
        }
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect Ultron Companion?", color = SignalRed) },
            text = {
                Text(
                    "This will disconnect the phone, revoke its device token on the desktop, and require re-pairing with a new PIN.",
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectDialog = false
                        viewModel.disconnectSession(onDisconnected)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SignalRed)
                ) {
                    Text("DISCONNECT")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = GraphitePanel
        )
    }
}

@Composable
private fun SettingInfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = TextTertiary))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = TextPrimary,
                fontSize = 12.sp
            )
        )
    }
}
