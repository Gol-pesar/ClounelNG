package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppLanguage
import com.example.model.EngineSettings
import com.example.proxy.PythonScriptBridge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pulse_prefs")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")
        val LANGUAGE = stringPreferencesKey("language")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val VPN_MODE_ENABLED = booleanPreferencesKey("vpn_mode_enabled")
        val ALLOW_LAN = booleanPreferencesKey("allow_lan")
        val ENABLE_HTTP_BRIDGE = booleanPreferencesKey("enable_http_bridge")
        val AUTO_FALLBACK_CLEAN_IP = booleanPreferencesKey("auto_fallback_clean_ip")
        val TCP_NO_DELAY = booleanPreferencesKey("tcp_no_delay")
        val PYTHON_SCRIPT = stringPreferencesKey("python_script")
    }

    val activeProfileId: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_PROFILE_ID] ?: 1L
    }

    val settings: Flow<EngineSettings> = context.dataStore.data.map { prefs ->
        EngineSettings(
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE] ?: AppLanguage.FA.code),
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            vpnModeEnabled = prefs[Keys.VPN_MODE_ENABLED] ?: true,
            allowLan = prefs[Keys.ALLOW_LAN] ?: false,
            enableHttpBridge = prefs[Keys.ENABLE_HTTP_BRIDGE] ?: true,
            autoFallbackCleanIp = prefs[Keys.AUTO_FALLBACK_CLEAN_IP] ?: true,
            tcpNoDelay = prefs[Keys.TCP_NO_DELAY] ?: true
        )
    }

    val pythonScript: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.PYTHON_SCRIPT] ?: PythonScriptBridge.DEFAULT_CL2_SCRIPT
    }

    suspend fun setActiveProfileId(id: Long) {
        context.dataStore.edit { it[Keys.ACTIVE_PROFILE_ID] = id }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setVpnModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VPN_MODE_ENABLED] = enabled }
    }

    suspend fun setAllowLan(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ALLOW_LAN] = enabled }
    }

    suspend fun setEnableHttpBridge(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ENABLE_HTTP_BRIDGE] = enabled }
    }

    suspend fun setAutoFallbackCleanIp(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_FALLBACK_CLEAN_IP] = enabled }
    }

    suspend fun setTcpNoDelay(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TCP_NO_DELAY] = enabled }
    }

    suspend fun savePythonScript(script: String) {
        context.dataStore.edit { it[Keys.PYTHON_SCRIPT] = script }
    }
}
