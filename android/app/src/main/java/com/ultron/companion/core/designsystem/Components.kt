package com.ultron.companion.core.designsystem

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultron.companion.core.model.ConnectionState

@Composable
fun UltronNeuralCore(
    modifier: Modifier = Modifier,
    size: Dp = 90.dp,
    isActive: Boolean = true,
    isListening: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "corePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = if (isListening) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 600 else 1800, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val coreColor = if (isListening) CrimsonBright else if (isActive) CrimsonPrimary else TextTertiary

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing field
        Box(
            modifier = Modifier
                .size(size)
                .scale(if (isActive) pulseScale else 1f)
                .clip(CircleShape)
                .background(coreColor.copy(alpha = 0.12f))
                .border(1.dp, coreColor.copy(alpha = 0.35f), CircleShape)
        )

        // Middle ring
        Box(
            modifier = Modifier
                .size(size * 0.65f)
                .clip(CircleShape)
                .background(ObsidianBlack)
                .border(1.5.dp, coreColor.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Inner core pupil
            Box(
                modifier = Modifier
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(coreColor)
            )
        }
    }
}

@Composable
fun StatusBadge(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    val (label, dotColor) = when (connectionState) {
        is ConnectionState.Connected -> Pair("CONNECTED", SignalGreen)
        is ConnectionState.Connecting -> Pair("LINKING…", SignalAmber)
        is ConnectionState.Pairing -> Pair("PAIRING", SignalAmber)
        is ConnectionState.AuthExpired -> Pair("AUTH EXPIRED", SignalRed)
        is ConnectionState.Disconnected -> Pair("DISCONNECTED", SignalRed)
        ConnectionState.NotConfigured -> Pair("UNPAIRED", TextTertiary)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(GraphitePanel)
            .border(1.dp, SteelBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        )
    }
}

@Composable
fun UltronCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = GraphitePanel,
    borderColor: Color = SteelBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(borderColor))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun UltronButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(10.dp),
        colors = if (isPrimary) {
            ButtonDefaults.buttonColors(
                containerColor = CrimsonPrimary,
                contentColor = TextPrimary,
                disabledContainerColor = CrimsonDark.copy(alpha = 0.5f),
                disabledContentColor = TextTertiary
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = GraphiteElevated,
                contentColor = TextPrimary,
                disabledContainerColor = GraphitePanel,
                disabledContentColor = TextTertiary
            )
        }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
