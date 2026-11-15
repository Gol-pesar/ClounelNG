package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppLanguage
import com.example.model.LogLevel
import com.example.ui.MainUiState
import com.example.ui.UiStrings
import com.example.ui.theme.LocalPulseColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun LogsSettingsScreen(
    state: MainUiState,
    strings: UiStrings,
    isIgnoringBatteryOpt: Boolean,
    onRequestDisableBatteryOpt: () -> Unit,
    onOpenHiddenPythonStudio: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onVpnModeChange: (Boolean) -> Unit,
    onAllowLanChange: (Boolean) -> Unit,
    onHttpBridgeChange: (Boolean) -> Unit,
    onAutoFallbackChange: (Boolean) -> Unit,
    onTcpNoDelayChange: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    onResetStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseColors = LocalPulseColors.current
    val settings = state.settings

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Battery Optimization Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isIgnoringBatteryOpt) {
                            pulseColors.success.copy(alpha = 0.08f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(
                        1.2.dp,
                        if (isIgnoringBatteryOpt) pulseColors.success.copy(alpha = 0.65f)
                        else pulseColors.cardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isIgnoringBatteryOpt) {
                                    Icons.Rounded.CheckCircle
                                } else {
                                    Icons.Rounded.BatteryChargingFull
                                },
                                contentDescription = null,
                                tint = if (isIgnoringBatteryOpt) pulseColors.success else pulseColors.warning,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = strings.batteryOptTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = if (isIgnoringBatteryOpt) {
                                strings.batteryOptDisabledDone
                            } else {
                                strings.batteryOptDesc
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isIgnoringBatteryOpt) {
                            Button(
                                onClick = onRequestDisableBatteryOpt,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = pulseColors.warning,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("battery_opt_settings_button")
                            ) {
                                Text(strings.batteryOptButton)
                            }
                        }
                    }
                }
            }

            // Settings Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.2.dp, pulseColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = strings.settingsTitle,
                            style = MaterialTheme.typography.titleLarge
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = strings.settingLanguage,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = settings.language == AppLanguage.FA,
                                    onClick = { onLanguageChange(AppLanguage.FA) },
                                    label = { Text("فارسی") }
                                )
                                FilterChip(
                                    selected = settings.language == AppLanguage.EN,
                                    onClick = { onLanguageChange(AppLanguage.EN) },
                                    label = { Text("English") }
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        SettingToggleRow(
                            title = strings.settingDarkTheme,
                            subtitle = null,
                            checked = settings.darkTheme,
                            onCheckedChange = onDarkThemeChange
                        )
                        SettingToggleRow(
                            title = strings.settingVpnMode,
                            subtitle = strings.settingVpnModeDesc,
                            checked = settings.vpnModeEnabled,
                            onCheckedChange = onVpnModeChange
                        )
                        SettingToggleRow(
                            title = strings.settingAllowLan,
                            subtitle = strings.settingAllowLanDesc,
                            checked = settings.allowLan,
                            onCheckedChange = onAllowLanChange
                        )
                        SettingToggleRow(
                            title = strings.settingHttpBridge,
                            subtitle = strings.settingHttpBridgeDesc,
                            checked = settings.enableHttpBridge,
                            onCheckedChange = onHttpBridgeChange
                        )
                        SettingToggleRow(
                            title = strings.settingAutoFallback,
                            subtitle = strings.settingAutoFallbackDesc,
                            checked = settings.autoFallbackCleanIp,
                            onCheckedChange = onAutoFallbackChange
                        )
                        SettingToggleRow(
                            title = strings.settingTcpNoDelay,
                            subtitle = strings.settingTcpNoDelayDesc,
                            checked = settings.tcpNoDelay,
                            onCheckedChange = onTcpNoDelayChange
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Preserved (hidden from main tabs) Python Studio entry
                        OutlinedButton(
                            onClick = onOpenHiddenPythonStudio,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.hiddenPythonTitle)
                        }
                    }
                }
            }

            // Console Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = strings.logsTitle,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onResetStats) {
                            Text(strings.resetStats)
                        }
                        OutlinedButton(onClick = onClearLogs) {
                            Text(strings.clearLogs)
                        }
                    }
                }
            }

            // Console Body
            if (state.logs.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = pulseColors.terminalBg,
                        border = BorderStroke(1.dp, pulseColors.terminalBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strings.noLogsYet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                val reversedLogs = state.logs.asReversed()
                items(reversedLogs, key = { it.id }) { entry ->
                    val logColor = when (entry.level) {
                        LogLevel.SUCCESS -> pulseColors.success
                        LogLevel.INFO -> pulseColors.cyanAccent
                        LogLevel.WARN -> pulseColors.warning
                        LogLevel.ERROR -> MaterialTheme.colorScheme.error
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(pulseColors.terminalBg, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = entry.timestamp,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = MonoFontFamily,
                                    fontSize = 10.sp
                                ),
                                color = Color(0xFF64748B)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = entry.message,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = MonoFontFamily,
                                    fontSize = 11.sp
                                ),
                                color = logColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
