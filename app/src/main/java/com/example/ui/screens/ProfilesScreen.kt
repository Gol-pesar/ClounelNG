package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPasteGo
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.model.ProxyProfile
import com.example.model.TunnelMode
import com.example.proxy.ConfigShareCodec
import com.example.ui.MainUiState
import com.example.ui.UiStrings
import com.example.ui.components.CautionNoteBanner
import com.example.ui.components.QrExportDialog
import com.example.ui.components.QrImportDialog
import com.example.ui.theme.LocalPulseColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun ProfilesScreen(
    state: MainUiState,
    strings: UiStrings,
    onSelectProfile: (ProxyProfile) -> Unit,
    onSaveProfile: (ProxyProfile) -> Unit,
    onDeleteProfile: (ProxyProfile) -> Unit,
    onTestHandshake: (ProxyProfile) -> Unit,
    onImportFromClipboard: () -> Unit,
    onImportDecodedText: (String) -> Unit,
    onExportToClipboard: (ProxyProfile) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseColors = LocalPulseColors.current
    var editingProfile by remember { mutableStateOf<ProxyProfile?>(null) }
    var qrExportProfile by remember { mutableStateOf<ProxyProfile?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strings.profilesTitle,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        if (state.handshakeTestMessage != null) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = state.handshakeTestMessage,
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Import Clipboard & Scan QR Bar
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = onImportFromClipboard,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_clipboard_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPasteGo,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(strings.importClipboard, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        OutlinedButton(
                            onClick = { showQrScanner = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_qr_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(strings.importQrTitle.let { if (it.contains("اسکن")) "ورود QR Code" else "Scan QR" }, maxLines = 1)
                        }
                    }

                    CautionNoteBanner(text = strings.cautionNote)
                }
            }

            if (state.profiles.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.2.dp, pulseColors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("empty_configs_card")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(18.dp)
                        ) {
                            Text(
                                text = strings.noConfigTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = strings.noConfigDesc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(state.profiles, key = { it.id }) { profile ->
                val isActive = state.activeProfile?.id == profile.id
                Card(
                    onClick = { onSelectProfile(profile) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(
                        width = if (isActive) 1.8.dp else 1.dp,
                        color = if (isActive) MaterialTheme.colorScheme.primary
                        else pulseColors.cardBorder.copy(alpha = 0.75f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_card_${profile.id}")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isActive) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isActive) pulseColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (profile.mode == TunnelMode.CL2_WSS) {
                                            strings.cl2ModeLabel
                                        } else {
                                            strings.directSocksModeLabel
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row {
                                IconButton(onClick = { qrExportProfile = profile }) {
                                    Icon(
                                        imageVector = Icons.Rounded.QrCode2,
                                        contentDescription = strings.exportQr,
                                        tint = pulseColors.cyanAccent
                                    )
                                }
                                IconButton(onClick = { onExportToClipboard(profile) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = strings.exportClipboard
                                    )
                                }
                                if (profile.mode == TunnelMode.CL2_WSS) {
                                    IconButton(onClick = { onTestHandshake(profile) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.NetworkPing,
                                            contentDescription = strings.testHandshake,
                                            tint = pulseColors.success
                                        )
                                    }
                                }
                                IconButton(onClick = { editingProfile = profile }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = strings.editProfile
                                    )
                                }
                                IconButton(onClick = { onDeleteProfile(profile) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = strings.deleteProfile,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    if (profile.mode == TunnelMode.CL2_WSS) {
                                        Text(
                                            text = "Worker URL: ${profile.workerUrl}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Clean IP: ${profile.cleanIp.ifBlank { "Auto DNS" }}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = MonoFontFamily,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = pulseColors.success
                                        )
                                        Text(
                                            text = "UUID: ${profile.uuid}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else {
                                        Text(
                                            text = "Endpoint: ${profile.upstreamHost}:${profile.upstreamPort}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                            color = pulseColors.success
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingProfile = ProxyProfile(
                    name = "AMIR Config",
                    mode = TunnelMode.CL2_WSS
                )
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_profile_fab")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = strings.addProfile)
        }
    }

    editingProfile?.let { initial ->
        ProfileEditorDialog(
            initial = initial,
            strings = strings,
            onDismiss = { editingProfile = null },
            onConfirm = { updated ->
                onSaveProfile(updated)
                editingProfile = null
            }
        )
    }

    qrExportProfile?.let { profile ->
        val uri = remember(profile) { ConfigShareCodec.encodeToUri(profile) }
        QrExportDialog(
            profileName = profile.name,
            shareUri = uri,
            strings = strings,
            onCopyUri = {
                onExportToClipboard(profile)
                qrExportProfile = null
            },
            onDismiss = { qrExportProfile = null }
        )
    }

    if (showQrScanner) {
        QrImportDialog(
            strings = strings,
            onQrDecoded = { text ->
                showQrScanner = false
                onImportDecodedText(text)
            },
            onError = { errMsg ->
                onShowMessage(errMsg)
            },
            onDismiss = { showQrScanner = false }
        )
    }
}

@Composable
private fun ProfileEditorDialog(
    initial: ProxyProfile,
    strings: UiStrings,
    onDismiss: () -> Unit,
    onConfirm: (ProxyProfile) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var mode by remember { mutableStateOf(initial.mode) }
    var uuid by remember { mutableStateOf(initial.uuid) }
    var workerUrl by remember { mutableStateOf(initial.workerUrl) }
    var cleanIp by remember { mutableStateOf(initial.cleanIp) }
    var showAdvanced by remember { mutableStateOf(false) }

    var subprotocol by remember { mutableStateOf(initial.subprotocol) }
    var listenHost by remember { mutableStateOf(initial.listenHost) }
    var listenPort by remember { mutableStateOf(initial.listenPort.toString()) }
    var maxChunk by remember { mutableStateOf(initial.maxChunk.toString()) }
    var bufferMax by remember { mutableStateOf(initial.bufferMax.toString()) }

    var upstreamHost by remember { mutableStateOf(initial.upstreamHost) }
    var upstreamPort by remember { mutableStateOf(initial.upstreamPort.toString()) }
    var upstreamUser by remember { mutableStateOf(initial.upstreamUsername) }
    var upstreamPass by remember { mutableStateOf(initial.upstreamPassword) }
    var showValidationError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initial.id == 0L) strings.addProfile else strings.editProfile)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = mode == TunnelMode.CL2_WSS,
                        onClick = { mode = TunnelMode.CL2_WSS },
                        label = { Text("AMIR (Cl2)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = mode == TunnelMode.DIRECT_SOCKS5,
                        onClick = { mode = TunnelMode.DIRECT_SOCKS5 },
                        label = { Text("Direct") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.profileName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    if (mode == TunnelMode.CL2_WSS) {
                        OutlinedTextField(
                            value = cleanIp,
                            onValueChange = { cleanIp = it },
                            label = { Text("Clean IP") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = uuid,
                            onValueChange = {
                                uuid = it
                                showValidationError = false
                            },
                            label = { Text("UUID") },
                            isError = showValidationError && uuid.isBlank(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = workerUrl,
                            onValueChange = {
                                workerUrl = it
                                showValidationError = false
                            },
                            label = { Text("Worker URL") },
                            isError = showValidationError && ConfigShareCodec.extractHostFromWorkerUrl(workerUrl).isBlank(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = upstreamHost,
                                onValueChange = {
                                    upstreamHost = it
                                    showValidationError = false
                                },
                                label = { Text(strings.upstreamHost) },
                                isError = showValidationError && upstreamHost.isBlank(),
                                singleLine = true,
                                modifier = Modifier.weight(1.5f)
                            )
                            OutlinedTextField(
                                value = upstreamPort,
                                onValueChange = { upstreamPort = it.filter(Char::isDigit) },
                                label = { Text(strings.upstreamPort) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = upstreamUser,
                            onValueChange = { upstreamUser = it },
                            label = { Text(strings.usernameOptional) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = upstreamPass,
                            onValueChange = { upstreamPass = it },
                            label = { Text(strings.passwordOptional) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    TextButton(
                        onClick = { showAdvanced = !showAdvanced },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.advancedOptions)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null
                        )
                    }

                    AnimatedVisibility(visible = showAdvanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = listenHost,
                                    onValueChange = { listenHost = it },
                                    label = { Text(strings.listenHost) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.3f)
                                )
                                OutlinedTextField(
                                    value = listenPort,
                                    onValueChange = { listenPort = it.filter(Char::isDigit) },
                                    label = { Text(strings.listenPort) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (mode == TunnelMode.CL2_WSS) {
                                OutlinedTextField(
                                    value = subprotocol,
                                    onValueChange = { subprotocol = it },
                                    label = { Text("SUBPROTO") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = maxChunk,
                                        onValueChange = { maxChunk = it.filter(Char::isDigit) },
                                        label = { Text(strings.maxChunk) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = bufferMax,
                                        onValueChange = { bufferMax = it.filter(Char::isDigit) },
                                        label = { Text(strings.bufferMax) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalizedUrl = ConfigShareCodec.normalizeWorkerUrl(workerUrl)
                    val derivedHost = ConfigShareCodec.extractHostFromWorkerUrl(normalizedUrl)
                        .ifBlank { initial.workerHost }
                    val finalUrl = normalizedUrl.ifBlank {
                        if (derivedHost.isNotBlank()) "wss://$derivedHost/" else ""
                    }
                    val trimmedUuid = uuid.trim()
                    val trimmedUpstream = upstreamHost.trim()
                    val isValid = if (mode == TunnelMode.CL2_WSS) {
                        trimmedUuid.isNotBlank() && derivedHost.isNotBlank() && finalUrl.isNotBlank()
                    } else {
                        trimmedUpstream.isNotBlank()
                    }
                    if (!isValid) {
                        showValidationError = true
                        return@Button
                    }

                    val portInt = (listenPort.toIntOrNull() ?: 40446).coerceIn(1024, 65535)
                    val upPortInt = (upstreamPort.toIntOrNull() ?: 1080).coerceIn(1, 65535)
                    val chunkInt = (maxChunk.toIntOrNull() ?: 65536).coerceIn(4096, 1048576)
                    val bufInt = (bufferMax.toIntOrNull() ?: 4194304).coerceIn(65536, 16777216)
                    onConfirm(
                        initial.copy(
                            name = name.trim().ifEmpty { derivedHost.ifBlank { "AMIR Config" } },
                            mode = mode,
                            uuid = trimmedUuid,
                            workerHost = derivedHost,
                            workerUrl = finalUrl,
                            cleanIp = cleanIp.trim(),
                            subprotocol = subprotocol.trim().ifEmpty { "chat.v2" },
                            listenHost = listenHost.trim().ifEmpty { "127.0.0.1" },
                            listenPort = portInt,
                            maxChunk = chunkInt,
                            bufferMax = bufInt,
                            upstreamHost = trimmedUpstream,
                            upstreamPort = upPortInt,
                            upstreamUsername = upstreamUser,
                            upstreamPassword = upstreamPass,
                            isDefault = false
                        )
                    )
                }
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
