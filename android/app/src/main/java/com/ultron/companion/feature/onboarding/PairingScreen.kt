package com.ultron.companion.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultron.companion.core.designsystem.*

@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
    onPairedSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val host by viewModel.hostInput.collectAsState()
    val port by viewModel.portInput.collectAsState()
    val useTls by viewModel.useTls.collectAsState()
    val pin by viewModel.pinInput.collectAsState()
    val isPairing by viewModel.isPairing.collectAsState()
    val error by viewModel.uiError.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        UltronNeuralCore(size = 80.dp, isActive = true)

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "ULTRON UPLINK",
            style = MaterialTheme.typography.headlineLarge.copy(
                letterSpacing = 2.sp,
                color = CrimsonPrimary
            )
        )
        Text(
            text = "PAIR MOBILE COMPANION TO DESKTOP",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.sp,
                color = TextSecondary
            )
        )

        Spacer(modifier = Modifier.height(28.dp))

        UltronCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "1. Connection Settings",
                style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = host,
                onValueChange = { viewModel.hostInput.value = it },
                label = { Text("Desktop LAN IP Address") },
                placeholder = { Text("192.168.1.50") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CrimsonPrimary,
                    unfocusedBorderColor = SteelBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { viewModel.portInput.value = it },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonPrimary,
                        unfocusedBorderColor = SteelBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useTls,
                        onCheckedChange = { viewModel.useTls.value = it },
                        colors = CheckboxDefaults.colors(checkedColor = CrimsonPrimary)
                    )
                    Text("Use TLS", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "2. Enter 6-Character PIN",
                style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary)
            )
            Text(
                text = "Click 'Remote Control' in the Ultron Desktop UI to generate a PIN",
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 6) viewModel.pinInput.value = it.uppercase() },
                label = { Text("Pairing PIN (e.g. 7K2M9P)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = CrimsonBright
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CrimsonPrimary,
                    unfocusedBorderColor = SteelBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodyMedium.copy(color = SignalRed)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            UltronButton(
                text = if (isPairing) "AUTHENTICATING UPLINK…" else "CONNECT & PAIR",
                onClick = { viewModel.startPairing(onPairedSuccess) },
                enabled = !isPairing && pin.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
