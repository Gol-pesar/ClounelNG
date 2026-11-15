package com.example.model

enum class TunnelMode(val id: String) {
    CL2_WSS("cl2_wss"),
    DIRECT_SOCKS5("direct_socks5");

    companion object {
        fun fromId(id: String): TunnelMode = entries.firstOrNull { it.id == id } ?: CL2_WSS
    }
}

enum class AppLanguage(val code: String) {
    FA("fa"),
    EN("en");

    companion object {
        fun fromCode(code: String): AppLanguage = entries.firstOrNull { it.code == code } ?: FA
    }
}

data class ProxyProfile(
    val id: Long = 0L,
    val name: String,
    val mode: TunnelMode = TunnelMode.CL2_WSS,
    // Cl2 WSS Worker settings
    val uuid: String = "",
    val workerHost: String = "",
    val workerUrl: String = "",
    val cleanIp: String = "",
    val subprotocol: String = "chat.v2",
    val listenHost: String = "127.0.0.1",
    val listenPort: Int = 40446,
    val maxChunk: Int = 65536,
    val bufferMax: Int = 4194304,
    // Direct upstream SOCKS5 settings
    val upstreamHost: String = "",
    val upstreamPort: Int = 1080,
    val upstreamUsername: String = "",
    val upstreamPassword: String = "",
    val isDefault: Boolean = false
)

enum class EngineStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

data class ActiveConnectionInfo(
    val id: Long,
    val targetHost: String,
    val targetPort: Int,
    val protocolLabel: String,
    val bytesUp: Long,
    val bytesDown: Long,
    val startedAtMs: Long,
    val handshakeMs: Long
)

data class TrafficSample(
    val timestampMs: Long,
    val upBytesPerSec: Long,
    val downBytesPerSec: Long
)

enum class LogLevel {
    INFO,
    SUCCESS,
    WARN,
    ERROR
}

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val level: LogLevel,
    val message: String
)

data class TunnelTelemetry(
    val status: EngineStatus = EngineStatus.STOPPED,
    val isVpnTunnelActive: Boolean = false,
    val errorMessage: String? = null,
    val listenAddress: String = "127.0.0.1:40446",
    val httpBridgeAddress: String? = null,
    val targetEndpoint: String = "",
    val mode: TunnelMode = TunnelMode.CL2_WSS,
    val activeConnectionsCount: Int = 0,
    val totalConnectionsHandled: Long = 0L,
    val totalBytesUp: Long = 0L,
    val totalBytesDown: Long = 0L,
    val currentUpBps: Long = 0L,
    val currentDownBps: Long = 0L,
    val startedAtMs: Long? = null,
    val activeConnections: List<ActiveConnectionInfo> = emptyList(),
    val trafficHistory: List<TrafficSample> = emptyList()
)

data class ProbeResult(
    val isRunning: Boolean = false,
    val success: Boolean? = null,
    val targetUrl: String = "https://api.ipify.org?format=json",
    val latencyMs: Long? = null,
    val httpStatusCode: Int? = null,
    val responseBody: String = "",
    val errorMessage: String? = null,
    val checkedAt: String = ""
)

data class ParsedPythonConfig(
    val uuid: String?,
    val workerHost: String?,
    val workerUrl: String?,
    val cleanIp: String?,
    val listenHost: String?,
    val listenPort: Int?,
    val subprotocol: String?,
    val maxChunk: Int?,
    val bufferMax: Int?
)

data class EngineSettings(
    val language: AppLanguage = AppLanguage.FA,
    val darkTheme: Boolean = true,
    val vpnModeEnabled: Boolean = true,
    val allowLan: Boolean = false,
    val enableHttpBridge: Boolean = true,
    val autoFallbackCleanIp: Boolean = true,
    val tcpNoDelay: Boolean = true
)
