package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.EngineStatus
import com.example.model.TunnelMode
import com.example.ui.MainUiState
import com.example.ui.UiStrings
import com.example.ui.components.CautionNoteBanner
import com.example.ui.components.EndpointChip
import com.example.ui.components.PulseConnectButton
import com.example.ui.components.formatBytes
import com.example.ui.components.formatSpeed
import com.example.ui.theme.LocalPulseColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun DashboardScreen(
    state: MainUiState,
    strings: UiStrings,
    isIgnoringBatteryOpt: Boolean,
    onRequestDisableBatteryOpt: () -> Unit,
    onToggleEngine: () -> Unit,
    onVpnModeChange: (Boolean) -> Unit,
    onCopyText: (String) -> Unit,
    onRunProbe: () -> Unit,
    onTestHandshake: () -> Unit,
    onOpenConfigs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseColors = LocalPulseColors.current
    val telemetry = state.telemetry
    val activeProfile = state.activeProfile
    val isRunning = telemetry.status == EngineStatus.RUNNING

    val statusText = when (telemetry.status) {
        EngineStatus.STOPPED -> strings.statusStopped
        EngineStatus.STARTING -> strings.statusStarting
        EngineStatus.RUNNING -> strings.statusRunning
        EngineStatus.ERROR -> telemetry.errorMessage ?: strings.statusError
    }
    val statusColor = when (telemetry.status) {
        EngineStatus.RUNNING -> pulseColors.success
        EngineStatus.STARTING -> pulseColors.warning
        EngineStatus.ERROR -> MaterialTheme.colorScheme.error
        EngineStatus.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CautionNoteBanner(text = strings.cautionNote)
            }

            if (!isIgnoringBatteryOpt) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.2.dp, pulseColors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BatteryChargingFull,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = strings.batteryOptTitle,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onRequestDisableBatteryOpt,
                                modifier = Modifier.testTag("battery_opt_banner_button")
                            ) {
                                Text(if (strings.save == "ذخیره") "غیرفعال‌سازی" else "Fix Now")
                            }
                        }
                    }
                }
            }

            // Minimalist Main Connection Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.2.dp, pulseColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = statusColor.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, pulseColors.cardBorder.copy(alpha = 0.8f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bolt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = strings.amirModeBadge,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = state.settings.vpnModeEnabled,
                                onClick = { onVpnModeChange(true) },
                                label = {
                                    Text(
                                        text = strings.vpnModeFullDevice,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("vpn_mode_chip")
                            )
                            FilterChip(
                                selected = !state.settings.vpnModeEnabled,
                                onClick = { onVpnModeChange(false) },
                                label = {
                                    Text(
                                        text = strings.vpnModeProxyOnly,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("proxy_mode_chip")
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        PulseConnectButton(
                            status = telemetry.status,
                            onClick = onToggleEngine
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_clounel_logo),
                                contentDescription = "Clounel emblem",
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isRunning) strings.tapToDisconnect else strings.tapToConnect,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (activeProfile != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${strings.activeProfileLabel}: ${activeProfile.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(14.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                EndpointChip(
                                    label = strings.amirModeBadge,
                                    value = if (activeProfile.mode == TunnelMode.CL2_WSS) {
                                        activeProfile.cleanIp.ifBlank { activeProfile.workerHost }
                                    } else {
                                        activeProfile.upstreamHost
                                    },
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    onCopy = {
                                        val target = if (activeProfile.mode == TunnelMode.CL2_WSS) {
                                            activeProfile.cleanIp.ifBlank { activeProfile.workerHost }
                                        } else {
                                            activeProfile.upstreamHost
                                        }
                                        onCopyText(target)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (activeProfile.mode == TunnelMode.CL2_WSS) {
                                    EndpointChip(
                                        label = "Worker URL",
                                        value = activeProfile.workerHost,
                                        accentColor = MaterialTheme.colorScheme.onSurface,
                                        onCopy = { onCopyText(activeProfile.workerUrl) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        } else {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = strings.noConfigTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onOpenConfigs,
                                border = BorderStroke(1.dp, pulseColors.cardBorder),
                                modifier = Modifier.testTag("dashboard_add_config_button")
                            ) {
                                Text(strings.addOrImportConfig)
                            }
                        }
                    }
                }
            }

            // Minimalist Stats Row (Zero Canvas overhead)
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TelemetryMiniCard(
                        icon = Icons.Rounded.ArrowDownward,
                        label = strings.downloadLabel,
                        primaryValue = formatBytes(telemetry.totalBytesDown),
                        secondaryValue = formatSpeed(telemetry.currentDownBps),
                        accent = pulseColors.success,
                        borderColor = pulseColors.cardBorder,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMiniCard(
                        icon = Icons.Rounded.ArrowUpward,
                        label = strings.uploadLabel,
                        primaryValue = formatBytes(telemetry.totalBytesUp),
                        secondaryValue = formatSpeed(telemetry.currentUpBps),
                        accent = MaterialTheme.colorScheme.primary,
                        borderColor = pulseColors.cardBorder,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMiniCard(
                        icon = Icons.Rounded.Hub,
                        label = strings.activeStreams,
                        primaryValue = "${telemetry.activeConnectionsCount}",
                        secondaryValue = "${strings.totalConns}: ${telemetry.totalConnectionsHandled}",
                        accent = MaterialTheme.colorScheme.onSurface,
                        borderColor = pulseColors.cardBorder,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Tunnel Probe & Ping Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.2.dp, pulseColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.NetworkCheck,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = strings.testTunnelTitle,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            if (state.handshakeTestMessage != null) {
                                val ok = state.handshakeTestMessage.startsWith("OK")
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (ok) pulseColors.success.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = state.handshakeTestMessage,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                        color = if (ok) pulseColors.success else MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onRunProbe,
                                enabled = !state.probeResult.isRunning,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("run_probe_button")
                            ) {
                                if (state.probeResult.isRunning) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(if (state.probeResult.isRunning) strings.runningProbe else strings.runProbe)
                            }

                            if (activeProfile?.mode == TunnelMode.CL2_WSS) {
                                OutlinedButton(
                                    onClick = onTestHandshake,
                                    border = BorderStroke(1.dp, pulseColors.cardBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("test_handshake_button")
                                ) {
                                    Text(strings.testHandshake)
                                }
                            }
                        }

                        AnimatedVisibility(visible = state.probeResult.success != null) {
                            val probe = state.probeResult
                            val ok = probe.success == true
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = pulseColors.terminalBg,
                                border = BorderStroke(
                                    1.dp,
                                    if (ok) pulseColors.success
                                    else MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = if (ok) {
                                                "HTTP ${probe.httpStatusCode} • ${probe.latencyMs}ms • ${probe.checkedAt}"
                                            } else {
                                                "FAILED (${probe.latencyMs}ms): ${probe.errorMessage}"
                                            },
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = MonoFontFamily,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (ok) Color(0xFF4ADE80) else Color(0xFFF87171)
                                        )
                                        if (probe.responseBody.isNotBlank()) {
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = probe.responseBody.take(320),
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                                color = Color(0xFFE5E7EB)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Active Connections List
            item {
                Text(
                    text = strings.liveConnectionsTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (telemetry.activeConnections.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, pulseColors.cardBorder.copy(alpha = 0.65f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isRunning) strings.noActiveStreams else strings.startEngineFirstHint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(telemetry.activeConnections, key = { it.id }) { conn ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, pulseColors.cardBorder.copy(alpha = 0.65f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${conn.targetHost}:${conn.targetPort}",
                                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "AMIR Mode • RTT ${conn.handshakeMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "↓ ${formatBytes(conn.bytesDown)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                        color = pulseColors.success
                                    )
                                    Text(
                                        text = "↑ ${formatBytes(conn.bytesUp)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryMiniCard(
    icon: ImageVector,
    label: String,
    primaryValue: String,
    secondaryValue: String,
    accent: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, borderColor),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = primaryValue,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = secondaryValue,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
