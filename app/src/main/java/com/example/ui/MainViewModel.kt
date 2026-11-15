package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PulseProxyApplication
import com.example.model.AppLanguage
import com.example.model.EngineSettings
import com.example.model.EngineStatus
import com.example.model.LogEntry
import com.example.model.LogLevel
import com.example.model.ProbeResult
import com.example.model.ProxyProfile
import com.example.model.TunnelTelemetry
import com.example.proxy.ConfigShareCodec
import com.example.proxy.ProxyEngineController
import com.example.proxy.ProxyProbeRunner
import com.example.proxy.PythonScriptBridge
import com.example.service.ProxyForegroundService
import com.example.service.PulseVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val profiles: List<ProxyProfile> = emptyList(),
    val activeProfile: ProxyProfile? = null,
    val settings: EngineSettings = EngineSettings(),
    val telemetry: TunnelTelemetry = TunnelTelemetry(),
    val logs: List<LogEntry> = emptyList(),
    val pythonScript: String = PythonScriptBridge.DEFAULT_CL2_SCRIPT,
    val probeResult: ProbeResult = ProbeResult(),
    val handshakeTestMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PulseProxyApplication
    private val profileRepo = app.profileRepository
    private val prefsRepo = app.preferencesRepository

    private val _probeResult = MutableStateFlow(ProbeResult())
    private val _handshakeMsg = MutableStateFlow<String?>(null)
    private val _editorScript = MutableStateFlow(PythonScriptBridge.DEFAULT_CL2_SCRIPT)

    init {
        viewModelScope.launch {
            profileRepo.purgeLegacyDefaultConfigs()
            val savedScript = prefsRepo.pythonScript.first()
            val profiles = profileRepo.profiles.first()
            val activeId = prefsRepo.activeProfileId.first()
            val active = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
            val synced = if (active != null) {
                PythonScriptBridge.updateScriptWithProfile(savedScript, active)
            } else {
                PythonScriptBridge.DEFAULT_CL2_SCRIPT
            }
            _editorScript.value = synced
            if (synced != savedScript) {
                prefsRepo.savePythonScript(synced)
            }
        }
    }

    val pythonScriptDraft: StateFlow<String> = _editorScript.asStateFlow()

    val uiState: StateFlow<MainUiState> = combine(
        profileRepo.profiles,
        prefsRepo.activeProfileId,
        prefsRepo.settings,
        ProxyEngineController.telemetry,
        ProxyEngineController.logs,
        _probeResult,
        _handshakeMsg,
        _editorScript
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val profiles = values[0] as List<ProxyProfile>
        val activeId = values[1] as Long
        val settings = values[2] as EngineSettings
        val telemetry = values[3] as TunnelTelemetry
        @Suppress("UNCHECKED_CAST")
        val logs = values[4] as List<LogEntry>
        val probe = values[5] as ProbeResult
        val handshake = values[6] as String?
        val script = values[7] as String

        val active = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
        MainUiState(
            profiles = profiles,
            activeProfile = active,
            settings = settings,
            telemetry = telemetry,
            logs = logs,
            pythonScript = script,
            probeResult = probe,
            handshakeTestMessage = handshake
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    fun startSelectedEngine(context: Context) {
        val useVpn = uiState.value.settings.vpnModeEnabled
        if (useVpn) {
            PulseVpnService.start(context)
        } else {
            ProxyForegroundService.start(context)
        }
    }

    fun stopAllEngines(context: Context) {
        PulseVpnService.stop(context)
        ProxyForegroundService.stop(context)
    }

    fun toggleEngine(context: Context) {
        val status = ProxyEngineController.telemetry.value.status
        if (status == EngineStatus.RUNNING || status == EngineStatus.STARTING) {
            stopAllEngines(context)
        } else {
            startSelectedEngine(context)
        }
    }

    fun selectProfile(context: Context, profile: ProxyProfile) {
        viewModelScope.launch {
            prefsRepo.setActiveProfileId(profile.id)
            val synced = PythonScriptBridge.updateScriptWithProfile(_editorScript.value, profile)
            _editorScript.value = synced
            prefsRepo.savePythonScript(synced)
            if (ProxyEngineController.telemetry.value.status == EngineStatus.RUNNING) {
                startSelectedEngine(context)
            }
        }
    }

    fun saveProfile(context: Context, profile: ProxyProfile, activateImmediately: Boolean = true) {
        viewModelScope.launch {
            val hadNoActive = uiState.value.activeProfile == null
            val savedId = profileRepo.saveProfile(profile)
            if (activateImmediately || hadNoActive) {
                prefsRepo.setActiveProfileId(savedId)
                val updated = profile.copy(id = savedId)
                val synced = PythonScriptBridge.updateScriptWithProfile(_editorScript.value, updated)
                _editorScript.value = synced
                prefsRepo.savePythonScript(synced)
                if (ProxyEngineController.telemetry.value.status == EngineStatus.RUNNING) {
                    startSelectedEngine(context)
                }
            }
        }
    }

    fun deleteProfile(context: Context, profile: ProxyProfile) {
        viewModelScope.launch {
            val wasActive = uiState.value.activeProfile?.id == profile.id
            profileRepo.deleteProfile(profile)
            val remaining = profileRepo.profiles.first()
            if (remaining.isEmpty()) {
                prefsRepo.setActiveProfileId(0L)
                _editorScript.value = PythonScriptBridge.DEFAULT_CL2_SCRIPT
                prefsRepo.savePythonScript(PythonScriptBridge.DEFAULT_CL2_SCRIPT)
                if (wasActive && ProxyEngineController.telemetry.value.status != EngineStatus.STOPPED) {
                    stopAllEngines(context)
                }
            } else if (wasActive) {
                val nextActive = remaining.first()
                prefsRepo.setActiveProfileId(nextActive.id)
                val synced = PythonScriptBridge.updateScriptWithProfile(_editorScript.value, nextActive)
                _editorScript.value = synced
                prefsRepo.savePythonScript(synced)
                if (ProxyEngineController.telemetry.value.status == EngineStatus.RUNNING) {
                    startSelectedEngine(context)
                }
            }
        }
    }

    fun updatePythonScriptDraft(newScript: String) {
        _editorScript.value = newScript
        viewModelScope.launch {
            prefsRepo.savePythonScript(newScript)
        }
    }

    fun syncScriptFromActiveProfile() {
        val active = uiState.value.activeProfile ?: return
        val synced = PythonScriptBridge.updateScriptWithProfile(_editorScript.value, active)
        updatePythonScriptDraft(synced)
    }

    fun applyPythonScript(
        context: Context,
        startImmediately: Boolean,
        onApplied: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val script = _editorScript.value
            prefsRepo.savePythonScript(script)
            val parsed = PythonScriptBridge.parseScript(script)
            val existing = uiState.value.activeProfile
            val hasServerValues = !parsed.uuid.isNullOrBlank() ||
                !parsed.workerHost.isNullOrBlank() ||
                !parsed.workerUrl.isNullOrBlank()
            if (existing == null && !hasServerValues) {
                return@launch
            }
            val current = existing ?: ProxyProfile(
                name = parsed.workerHost?.takeIf { it.isNotBlank() } ?: "AMIR Config"
            )
            val updated = PythonScriptBridge.applyConfigToProfile(current, parsed)
            val savedId = profileRepo.saveProfile(updated)
            val savedProfile = updated.copy(id = savedId)
            prefsRepo.setActiveProfileId(savedId)
            val synced = PythonScriptBridge.updateScriptWithProfile(script, savedProfile)
            _editorScript.value = synced
            prefsRepo.savePythonScript(synced)

            ProxyEngineController.onLog(
                LogLevel.SUCCESS,
                "[PY] Parsed Cl2 script -> ${updated.listenHost}:${updated.listenPort} | ${updated.workerHost} (Clean IP: ${updated.cleanIp})"
            )

            if (startImmediately) {
                startSelectedEngine(context)
            }
            onApplied?.invoke()
        }
    }

    fun importConfigFromText(
        context: Context,
        rawText: String,
        onSuccess: (ProxyProfile) -> Unit,
        onError: (String) -> Unit
    ) {
        val result = ConfigShareCodec.decodeFromText(rawText)
        result.fold(
            onSuccess = { imported ->
                viewModelScope.launch {
                    val savedId = profileRepo.saveProfile(imported)
                    val saved = imported.copy(id = savedId)
                    prefsRepo.setActiveProfileId(savedId)
                    val synced = PythonScriptBridge.updateScriptWithProfile(_editorScript.value, saved)
                    _editorScript.value = synced
                    prefsRepo.savePythonScript(synced)
                    if (ProxyEngineController.telemetry.value.status == EngineStatus.RUNNING) {
                        startSelectedEngine(context)
                    }
                    onSuccess(saved)
                }
            },
            onFailure = { err ->
                onError(err.message ?: "Invalid config")
            }
        )
    }

    fun runSocks5Probe(targetUrl: String) {
        val telemetry = uiState.value.telemetry
        val profile = uiState.value.activeProfile ?: return
        val port = profile.listenPort
        val host = if (telemetry.status == EngineStatus.RUNNING) {
            telemetry.listenAddress.substringBefore(":")
        } else {
            profile.listenHost
        }
        viewModelScope.launch {
            _probeResult.value = _probeResult.value.copy(
                isRunning = true,
                targetUrl = targetUrl,
                errorMessage = null
            )
            val res = ProxyProbeRunner.testThroughSocks5(host, port, targetUrl)
            _probeResult.value = res
        }
    }

    fun testCl2Handshake(profile: ProxyProfile) {
        viewModelScope.launch {
            _handshakeMsg.value = "..."
            val res = ProxyProbeRunner.testCl2EndpointHandshake(profile, uiState.value.settings)
            _handshakeMsg.value = res.fold(
                onSuccess = { ms -> "OK (${ms}ms)" },
                onFailure = { e -> "ERR: ${e.message ?: "Failed"}" }
            )
        }
    }

    fun clearHandshakeResult() {
        _handshakeMsg.value = null
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { prefsRepo.setLanguage(language) }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setDarkTheme(enabled) }
    }

    fun setVpnModeEnabled(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            val wasRunning = ProxyEngineController.telemetry.value.status == EngineStatus.RUNNING
            if (wasRunning) {
                stopAllEngines(context)
            }
            prefsRepo.setVpnModeEnabled(enabled)
        }
    }

    fun setAllowLan(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAllowLan(enabled)
            if (ProxyEngineController.telemetry.value.status == EngineStatus.RUNNING) {
                startSelectedEngine(context)
            }
        }
    }

    fun setEnableHttpBridge(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setEnableHttpBridge(enabled)
            if (ProxyEngineController.telemetry.value.status == EngineStatus.RUNNING) {
                startSelectedEngine(context)
            }
        }
    }

    fun setAutoFallbackCleanIp(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoFallbackCleanIp(enabled) }
    }

    fun setTcpNoDelay(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setTcpNoDelay(enabled) }
    }

    fun clearLogs() = ProxyEngineController.clearLogs()
    fun resetStats() = ProxyEngineController.resetCounters()
}
