package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.model.EngineStatus
import com.example.ui.theme.LocalPulseColors
import com.example.ui.theme.MonoFontFamily
import java.util.Locale

/**
 * Minimalist, restrained connect/disconnect control with crisp hairline border and zero GPU animation overhead.
 */
@Composable
fun PulseConnectButton(
    status: EngineStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseColors = LocalPulseColors.current
    val isConnected = status == EngineStatus.RUNNING

    val ringColor by animateColorAsState(
        targetValue = when (status) {
            EngineStatus.RUNNING -> pulseColors.success
            EngineStatus.STARTING -> pulseColors.warning
            EngineStatus.ERROR -> MaterialTheme.colorScheme.error
            EngineStatus.STOPPED -> MaterialTheme.colorScheme.primary
        },
        label = "ringColor"
    )

    val fillColor by animateColorAsState(
        targetValue = when (status) {
            EngineStatus.RUNNING -> pulseColors.success.copy(alpha = 0.12f)
            EngineStatus.STARTING -> pulseColors.warning.copy(alpha = 0.10f)
            EngineStatus.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
            EngineStatus.STOPPED -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "fillColor"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = fillColor,
        border = BorderStroke(
            width = if (isConnected) 2.dp else 1.5.dp,
            color = ringColor
        ),
        modifier = modifier
            .size(92.dp)
            .minimumInteractiveComponentSize()
            .testTag("connect_toggle_button")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = if (isConnected) "Stop Tunnel" else "Start Tunnel",
                tint = ringColor,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
fun EndpointChip(
    label: String,
    value: String,
    accentColor: Color,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onCopy)
            .minimumInteractiveComponentSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = "Copy $label",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

fun formatSpeed(bytesPerSec: Long): String = "${formatBytes(bytesPerSec)}/s"
