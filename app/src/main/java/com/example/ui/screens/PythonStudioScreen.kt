package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.proxy.PythonScriptBridge
import com.example.ui.MainUiState
import com.example.ui.UiStrings
import com.example.ui.theme.LocalPulseColors
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PythonStudioScreen(
    state: MainUiState,
    strings: UiStrings,
    onScriptChange: (String) -> Unit,
    onSyncFromProfile: () -> Unit,
    onApplyAndRun: (Boolean) -> Unit,
    onCopyText: (String) -> Unit,
    onRunProbe: (String) -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pulseColors = LocalPulseColors.current
    var testUrl by remember { mutableStateOf("https://api.ipify.org?format=json") }

    val parsedPreview = remember(state.pythonScript) {
        PythonScriptBridge.parseScript(state.pythonScript)
    }

    val openPyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.onSuccess { content ->
                if (!content.isNullOrBlank()) {
                    onScriptChange(content)
                    onNotify(strings.pythonParsedSuccess)
                }
            }
        }
    }

    val exportPyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-python")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(state.pythonScript)
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, pulseColors.studioAccent.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Code,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.pythonStudioTitle,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = strings.pythonStudioSubtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Live Parsed Summary Chips
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = pulseColors.terminalBg,
                                border = BorderStroke(1.dp, pulseColors.terminalBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = "LISTEN: ${parsedPreview.listenHost ?: "?"}:${parsedPreview.listenPort ?: "?"}   •   SUBPROTO: ${parsedPreview.subprotocol ?: "?"}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                        color = pulseColors.cyanAccent
                                    )
                                    Text(
                                        text = "WORKER_HOST: ${parsedPreview.workerHost ?: "?"}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                        color = Color(0xFFE2E8F0)
                                    )
                                    Text(
                                        text = "CLEAN_IP: ${parsedPreview.cleanIp ?: "DNS"}   •   CHUNK: ${parsedPreview.maxChunk ?: 65536}   •   BUF: ${parsedPreview.bufferMax ?: 4194304}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                        color = pulseColors.success
                                    )
                                }
                            }
                        }

                        // Primary Action Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    onApplyAndRun(true)
                                    onNotify(strings.pythonParsedSuccess)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = pulseColors.success,
                                    contentColor = Color(0xFF002116)
                                ),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .testTag("python_run_unlimited_button")
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.applyAndRunUnlimited, maxLines = 1)
                            }

                            FilledTonalButton(
                                onClick = {
                                    onApplyAndRun(false)
                                    onNotify(strings.pythonParsedSuccess)
                                },
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Text(strings.parseOnly, maxLines = 1)
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { openPyLauncher.launch(arrayOf("*/*")) }
                            ) {
                                Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.importPy)
                            }
                            OutlinedButton(
                                onClick = { exportPyLauncher.launch("Cl2_82497904.py") }
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.exportPy)
                            }
                            OutlinedButton(onClick = onSyncFromProfile) {
                                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.syncFromProfile)
                            }
                            OutlinedButton(onClick = { onCopyText(state.pythonScript) }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.copy)
                            }
                        }
                    }
                }
            }

            // Code Editor Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = pulseColors.terminalBg),
                    border = BorderStroke(1.dp, pulseColors.terminalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = "Cl2_82497904.py",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = MonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = pulseColors.studioAccent
                                )
                                Text(
                                    text = "${state.pythonScript.lines().size} lines",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(270.dp)
                                    .background(Color(0xFF05080E), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                BasicTextField(
                                    value = state.pythonScript,
                                    onValueChange = onScriptChange,
                                    textStyle = TextStyle(
                                        fontFamily = MonoFontFamily,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = Color(0xFFE2E8F0)
                                    ),
                                    cursorBrush = SolidColor(pulseColors.cyanAccent),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("python_code_editor")
                                )
                            }
                        }
                    }
                }
            }

            // Live Python / SOCKS5 Client Probe Card
            item {
                val activePort = state.activeProfile?.listenPort ?: 40446
                val activeHost = state.activeProfile?.listenHost ?: "127.0.0.1"
                val pySnippet = remember(activeHost, activePort, testUrl) {
                    PythonScriptBridge.buildPythonClientSnippet(activeHost, activePort, testUrl)
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = strings.pythonClientTestTitle,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = strings.pythonClientTestDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(
                                value = testUrl,
                                onValueChange = { testUrl = it },
                                label = { Text("Target URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = pulseColors.terminalBg,
                                border = BorderStroke(1.dp, pulseColors.terminalBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = pySnippet,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = MonoFontFamily,
                                            lineHeight = 17.sp
                                        ),
                                        color = Color(0xFF93C5FD),
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { onCopyText(pySnippet) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = strings.copy,
                                            tint = pulseColors.cyanAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { onRunProbe(testUrl) },
                            enabled = !state.probeResult.isRunning,
                            modifier = Modifier.fillMaxWidth()
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

                        AnimatedVisibility(visible = state.probeResult.success != null) {
                            val probe = state.probeResult
                            val ok = probe.success == true
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = pulseColors.terminalBg,
                                    border = BorderStroke(
                                        1.dp,
                                        if (ok) pulseColors.success.copy(alpha = 0.6f)
                                        else MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = if (ok) {
                                                "HTTP ${probe.httpStatusCode} • ${probe.latencyMs}ms"
                                            } else {
                                                "ERROR (${probe.latencyMs}ms): ${probe.errorMessage}"
                                            },
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = MonoFontFamily,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (ok) pulseColors.success else MaterialTheme.colorScheme.error
                                        )
                                        if (probe.responseBody.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = probe.responseBody.take(400),
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                                color = Color(0xFFE2E8F0)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Termux / Pydroid Integration Snippet
            item {
                val activePort = state.activeProfile?.listenPort ?: 40446
                val activeHost = state.activeProfile?.listenHost ?: "127.0.0.1"
                val termuxSnippet = remember(activeHost, activePort) {
                    PythonScriptBridge.buildTermuxEnvSnippet(activeHost, activePort)
                }
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = null,
                                tint = pulseColors.cyanAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = strings.termuxSnippetTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = pulseColors.terminalBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = termuxSnippet,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = MonoFontFamily,
                                            lineHeight = 17.sp
                                        ),
                                        color = pulseColors.success,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { onCopyText(termuxSnippet) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = strings.copy,
                                            tint = pulseColors.cyanAccent,
                                            modifier = Modifier.size(18.dp)
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
}
