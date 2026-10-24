package com.ultron.companion.feature.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultron.companion.core.designsystem.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceInteractionSheet(
    voiceManager: VoiceManager,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening by voiceManager.isListening.collectAsState()
    val recognizedText by voiceManager.recognizedText.collectAsState()
    val statusText by voiceManager.statusText.collectAsState()

    ModalBottomSheet(
        onDismissRequest = {
            voiceManager.stopListening()
            onDismiss()
        },
        containerColor = ObsidianSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = SteelBorder) }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VOICE TACTICAL MATRIX",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = CrimsonPrimary,
                        letterSpacing = 1.sp
                    )
                )
                IconButton(onClick = {
                    voiceManager.stopListening()
                    onDismiss()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            UltronNeuralCore(
                size = 120.dp,
                isActive = true,
                isListening = isListening
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    color = if (isListening) CrimsonBright else TextSecondary
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (recognizedText.isNotBlank()) {
                Text(
                    text = "\"$recognizedText\"",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
            } else {
                Spacer(modifier = Modifier.height(30.dp))
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isListening) {
                    Button(
                        onClick = { voiceManager.stopListening() },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TRANSMIT NOW", color = TextPrimary)
                    }
                } else {
                    Button(
                        onClick = { voiceManager.startListening() },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Start", tint = TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PUSH TO TALK", color = TextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
