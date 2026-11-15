package com.example

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.AppLanguage
import com.example.model.EngineStatus
import com.example.proxy.ConfigShareCodec
import com.example.ui.MainViewModel
import com.example.ui.StringsL10n
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LogsSettingsScreen
import com.example.ui.screens.ProfilesScreen
import com.example.ui.screens.PythonStudioScreen
import com.example.ui.theme.PulseProxyTheme
import kotlinx.coroutines.launch

private const val ROUTE_TUNNEL = "tunnel"
private const val ROUTE_PROFILES = "profiles"
private const val ROUTE_PYTHON = "python"
private const val ROUTE_LOGS = "logs"

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PulseProxyAppRoot()
        }
    }
}

private fun checkBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseProxyAppRoot(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var isIgnoringBatteryOpt by remember {
        mutableStateOf(checkBatteryOptimizationIgnored(context))
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isIgnoringBatteryOpt = checkBatteryOptimizationIgnored(context)
    }

    val batteryOptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isIgnoringBatteryOpt = checkBatteryOptimizationIgnored(context)
    }

    val requestDisableBatteryOptimization: () -> Unit = {
        val directIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        runCatching {
            batteryOptLauncher.launch(directIntent)
        }.onFailure {
            runCatching {
                batteryOptLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    val strings = StringsL10n.forLang(state.settings.language)
    val layoutDirection = if (state.settings.language == AppLanguage.FA) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    val showToastMessage: (String) -> Unit = { msg ->
        coroutineScope.launch {
            snackbarHostState.showSnackbar(msg)
        }
    }

    val copyToClipboard: (String) -> Unit = { text ->
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("PulseProxy", text))
        coroutineScope.launch {
            snackbarHostState.showSnackbar(strings.copiedToast)
        }
    }

    val readClipboardText: () -> String? = {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(context)?.toString()
        } else {
            null
        }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startSelectedEngine(context)
        } else {
            showToastMessage(strings.vpnPermissionDenied)
        }
    }

    val launchEngineOrVpnWithPermission: () -> Unit = {
        val status = state.telemetry.status
        if (status == EngineStatus.RUNNING || status == EngineStatus.STARTING) {
            viewModel.stopAllEngines(context)
        } else if (state.settings.vpnModeEnabled) {
            val prepIntent = VpnService.prepare(context)
            if (prepIntent != null) {
                vpnPermissionLauncher.launch(prepIntent)
            } else {
                viewModel.startSelectedEngine(context)
            }
        } else {
            viewModel.startSelectedEngine(context)
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        launchEngineOrVpnWithPermission()
    }

    val onToggleWithPermissionCheck: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchEngineOrVpnWithPermission()
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Python Studio hidden from main tabs (preserved in NavHost & reachable via Advanced Settings)
    val destinations = listOf(
        NavDestinationItem(ROUTE_TUNNEL, strings.tabTunnel, Icons.Filled.Shield, Icons.Outlined.Shield, "nav_tunnel"),
        NavDestinationItem(ROUTE_PROFILES, strings.tabProfiles, Icons.Filled.Dns, Icons.Outlined.Dns, "nav_profiles"),
        NavDestinationItem(ROUTE_LOGS, strings.tabLogs, Icons.Filled.Tune, Icons.Outlined.Tune, "nav_logs")
    )

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    PulseProxyTheme(darkTheme = state.settings.darkTheme) {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_clounel_logo),
                                        contentDescription = "Clounel logo",
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = strings.appTitle,
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                }
                            },
                            actions = {
                                TextButton(
                                    onClick = {
                                        val nextLang = if (state.settings.language == AppLanguage.FA) {
                                            AppLanguage.EN
                                        } else {
                                            AppLanguage.FA
                                        }
                                        viewModel.setLanguage(nextLang)
                                    },
                                    modifier = Modifier.testTag("top_lang_toggle")
                                ) {
                                    Text(if (state.settings.language == AppLanguage.FA) "EN" else "فا")
                                }
                                IconButton(
                                    onClick = { viewModel.setDarkTheme(!state.settings.darkTheme) },
                                    modifier = Modifier.testTag("top_theme_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (state.settings.darkTheme) {
                                            Icons.Rounded.LightMode
                                        } else {
                                            Icons.Rounded.DarkMode
                                        },
                                        contentDescription = strings.settingDarkTheme,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    },
                    bottomBar = {
                        if (!useRail) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                destinations.forEach { dest ->
                                    val selected = currentDestination?.hierarchy?.any {
                                        it.route == dest.route
                                    } == true
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateTo(dest.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label
                                            )
                                        },
                                        label = { Text(dest.label, maxLines = 1) },
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                destinations.forEach { dest ->
                                    val selected = currentDestination?.hierarchy?.any {
                                        it.route == dest.route
                                    } == true
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateTo(dest.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label
                                            )
                                        },
                                        label = { Text(dest.label) },
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_TUNNEL,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(ROUTE_TUNNEL) {
                                DashboardScreen(
                                    state = state,
                                    strings = strings,
                                    isIgnoringBatteryOpt = isIgnoringBatteryOpt,
                                    onRequestDisableBatteryOpt = requestDisableBatteryOptimization,
                                    onToggleEngine = onToggleWithPermissionCheck,
                                    onVpnModeChange = { enabled ->
                                        viewModel.setVpnModeEnabled(context, enabled)
                                    },
                                    onCopyText = copyToClipboard,
                                    onRunProbe = { viewModel.runSocks5Probe("https://api.ipify.org?format=json") },
                                    onTestHandshake = {
                                        state.activeProfile?.let { viewModel.testCl2Handshake(it) }
                                    }
                                )
                            }
                            composable(ROUTE_PROFILES) {
                                ProfilesScreen(
                                    state = state,
                                    strings = strings,
                                    onSelectProfile = { viewModel.selectProfile(context, it) },
                                    onSaveProfile = { viewModel.saveProfile(context, it, true) },
                                    onDeleteProfile = { viewModel.deleteProfile(it) },
                                    onTestHandshake = { viewModel.testCl2Handshake(it) },
                                    onImportFromClipboard = {
                                        val clipText = readClipboardText()
                                        if (clipText.isNullOrBlank()) {
                                            showToastMessage(strings.clipboardEmpty)
                                        } else {
                                            viewModel.importConfigFromText(
                                                context = context,
                                                rawText = clipText,
                                                onSuccess = { showToastMessage(strings.configImported) },
                                                onError = { showToastMessage(strings.invalidConfigFormat) }
                                            )
                                        }
                                    },
                                    onImportDecodedText = { decoded ->
                                        viewModel.importConfigFromText(
                                            context = context,
                                            rawText = decoded,
                                            onSuccess = { showToastMessage(strings.configImported) },
                                            onError = { showToastMessage(strings.invalidConfigFormat) }
                                        )
                                    },
                                    onExportToClipboard = { profile ->
                                        copyToClipboard(ConfigShareCodec.encodeToUri(profile))
                                    },
                                    onShowMessage = showToastMessage
                                )
                            }
                            // Preserved (hidden from bottom nav, reachable from Advanced Settings)
                            composable(ROUTE_PYTHON) {
                                PythonStudioScreen(
                                    state = state,
                                    strings = strings,
                                    onScriptChange = { viewModel.updatePythonScriptDraft(it) },
                                    onSyncFromProfile = { viewModel.syncScriptFromActiveProfile() },
                                    onApplyAndRun = { startNow ->
                                        if (!startNow) {
                                            viewModel.applyPythonScript(context, false)
                                        } else {
                                            viewModel.applyPythonScript(context, false)
                                            if (state.settings.vpnModeEnabled) {
                                                val prepIntent = VpnService.prepare(context)
                                                if (prepIntent != null) {
                                                    vpnPermissionLauncher.launch(prepIntent)
                                                } else {
                                                    viewModel.startSelectedEngine(context)
                                                }
                                            } else {
                                                viewModel.startSelectedEngine(context)
                                            }
                                        }
                                    },
                                    onCopyText = copyToClipboard,
                                    onRunProbe = { url -> viewModel.runSocks5Probe(url) },
                                    onNotify = showToastMessage
                                )
                            }
                            composable(ROUTE_LOGS) {
                                LogsSettingsScreen(
                                    state = state,
                                    strings = strings,
                                    isIgnoringBatteryOpt = isIgnoringBatteryOpt,
                                    onRequestDisableBatteryOpt = requestDisableBatteryOptimization,
                                    onOpenHiddenPythonStudio = {
                                        navController.navigate(ROUTE_PYTHON) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onLanguageChange = { viewModel.setLanguage(it) },
                                    onDarkThemeChange = { viewModel.setDarkTheme(it) },
                                    onVpnModeChange = { viewModel.setVpnModeEnabled(context, it) },
                                    onAllowLanChange = { viewModel.setAllowLan(context, it) },
                                    onHttpBridgeChange = { viewModel.setEnableHttpBridge(context, it) },
                                    onAutoFallbackChange = { viewModel.setAutoFallbackCleanIp(it) },
                                    onTcpNoDelayChange = { viewModel.setTcpNoDelay(it) },
                                    onClearLogs = { viewModel.clearLogs() },
                                    onResetStats = { viewModel.resetStats() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
