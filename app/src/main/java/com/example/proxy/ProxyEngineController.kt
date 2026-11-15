package com.example.proxy

import com.example.model.ActiveConnectionInfo
import com.example.model.EngineSettings
import com.example.model.EngineStatus
import com.example.model.LogEntry
import com.example.model.LogLevel
import com.example.model.ProxyProfile
import com.example.model.TrafficSample
import com.example.model.TunnelMode
import com.example.model.TunnelTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object ProxyEngineController : TelemetrySink {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: Socks5ProxyServer? = null
    private var tickerJob: Job? = null

    private class MutableConnInfo(
        val id: Long,
        val targetHost: String,
        val targetPort: Int,
        val protocolLabel: String,
        val startedAtMs: Long,
        val handshakeMs: Long
    ) {
        val bytesUp = AtomicLong(0L)
        val bytesDown = AtomicLong(0L)

        fun snapshot() = ActiveConnectionInfo(
            id = id,
            targetHost = targetHost,
            targetPort = targetPort,
            protocolLabel = protocolLabel,
            bytesUp = bytesUp.get(),
            bytesDown = bytesDown.get(),
            startedAtMs = startedAtMs,
            handshakeMs = handshakeMs
        )
    }

    private val activeConnMap = ConcurrentHashMap<Long, MutableConnInfo>()
    private val totalUp = AtomicLong(0L)
    private val totalDown = AtomicLong(0L)
    private val totalConnCount = AtomicLong(0L)
    private val logSeq = AtomicLong(1L)

    private val _telemetry = MutableStateFlow(TunnelTelemetry())
    val telemetry: StateFlow<TunnelTelemetry> = _telemetry.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun startEngine(
        profile: ProxyProfile,
        settings: EngineSettings,
        isVpnTunnelActive: Boolean = false
    ) {
        if (server != null) {
            stopEngine()
        }

        _telemetry.update {
            it.copy(
                status = EngineStatus.STARTING,
                isVpnTunnelActive = isVpnTunnelActive,
                errorMessage = null,
                mode = profile.mode
            )
        }

        val srv = Socks5ProxyServer(profile, settings, this)
        try {
            srv.start()
            server = srv
            val listenAddr = "${srv.bindHost}:${srv.socksPort}"
            val httpAddr = srv.httpBridgePort?.let { "${srv.bindHost}:$it" }
            val endpoint = if (profile.mode == TunnelMode.CL2_WSS) {
                profile.workerHost
            } else {
                "${profile.upstreamHost}:${profile.upstreamPort}"
            }

            _telemetry.update {
                it.copy(
                    status = EngineStatus.RUNNING,
                    isVpnTunnelActive = isVpnTunnelActive,
                    errorMessage = null,
                    listenAddress = listenAddr,
                    httpBridgeAddress = httpAddr,
                    targetEndpoint = endpoint,
                    mode = profile.mode,
                    startedAtMs = System.currentTimeMillis()
                )
            }
            startTicker()
        } catch (e: Exception) {
            runCatching { srv.stop() }
            server = null
            val msg = e.message ?: "Failed to bind port ${profile.listenPort}"
            onLog(LogLevel.ERROR, "Start failed: $msg")
            _telemetry.update {
                it.copy(
                    status = EngineStatus.ERROR,
                    errorMessage = msg
                )
            }
        }
    }

    @Synchronized
    fun stopEngine() {
        tickerJob?.cancel()
        tickerJob = null
        server?.stop()
        server = null
        activeConnMap.clear()
        _telemetry.update {
            it.copy(
                status = EngineStatus.STOPPED,
                isVpnTunnelActive = false,
                activeConnectionsCount = 0,
                currentUpBps = 0L,
                currentDownBps = 0L,
                startedAtMs = null,
                activeConnections = emptyList()
            )
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            var lastUp = totalUp.get()
            var lastDown = totalDown.get()
            while (isActive) {
                delay(1000)
                val nowUp = totalUp.get()
                val nowDown = totalDown.get()
                val upBps = (nowUp - lastUp).coerceAtLeast(0L)
                val downBps = (nowDown - lastDown).coerceAtLeast(0L)
                lastUp = nowUp
                lastDown = nowDown

                val sample = TrafficSample(
                    timestampMs = System.currentTimeMillis(),
                    upBytesPerSec = upBps,
                    downBytesPerSec = downBps
                )
                val connsSnapshot = activeConnMap.values
                    .sortedByDescending { it.startedAtMs }
                    .take(25)
                    .map { it.snapshot() }

                _telemetry.update { prev ->
                    val history = (prev.trafficHistory + sample).takeLast(45)
                    prev.copy(
                        activeConnectionsCount = activeConnMap.size,
                        totalConnectionsHandled = totalConnCount.get(),
                        totalBytesUp = nowUp,
                        totalBytesDown = nowDown,
                        currentUpBps = upBps,
                        currentDownBps = downBps,
                        activeConnections = connsSnapshot,
                        trafficHistory = history
                    )
                }
            }
        }
    }

    override fun onLog(level: LogLevel, message: String) {
        val entry = LogEntry(
            id = logSeq.getAndIncrement(),
            timestamp = timeFormat.format(Date()),
            level = level,
            message = message
        )
        _logs.update { current ->
            (current + entry).takeLast(350)
        }
    }

    override fun onConnectionOpened(
        id: Long,
        host: String,
        port: Int,
        protocol: String,
        handshakeMs: Long
    ) {
        totalConnCount.incrementAndGet()
        activeConnMap[id] = MutableConnInfo(
            id = id,
            targetHost = host,
            targetPort = port,
            protocolLabel = protocol,
            startedAtMs = System.currentTimeMillis(),
            handshakeMs = handshakeMs
        )
        _telemetry.update { it.copy(activeConnectionsCount = activeConnMap.size) }
    }

    override fun onBytesTransferred(connectionId: Long, bytesUp: Long, bytesDown: Long) {
        if (bytesUp > 0L) totalUp.addAndGet(bytesUp)
        if (bytesDown > 0L) totalDown.addAndGet(bytesDown)
        val entry = activeConnMap[connectionId] ?: return
        if (bytesUp > 0L) entry.bytesUp.addAndGet(bytesUp)
        if (bytesDown > 0L) entry.bytesDown.addAndGet(bytesDown)
    }

    override fun onConnectionClosed(id: Long) {
        activeConnMap.remove(id)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun resetCounters() {
        totalUp.set(0L)
        totalDown.set(0L)
        totalConnCount.set(0L)
        _telemetry.update {
            it.copy(
                totalBytesUp = 0L,
                totalBytesDown = 0L,
                totalConnectionsHandled = 0L,
                trafficHistory = emptyList()
            )
        }
    }
}
