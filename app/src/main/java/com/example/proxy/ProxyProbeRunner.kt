package com.example.proxy

import com.example.model.EngineSettings
import com.example.model.ProbeResult
import com.example.model.ProxyProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object ProxyProbeRunner {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    /**
     * Executes a real HTTP/HTTPS request routed through the local SOCKS5 proxy.
     */
    suspend fun testThroughSocks5(
        listenHost: String,
        listenPort: Int,
        targetUrl: String
    ): ProbeResult = withContext(Dispatchers.IO) {
        val effectiveHost = if (listenHost == "0.0.0.0") "127.0.0.1" else listenHost
        val started = System.currentTimeMillis()
        try {
            val socksProxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(effectiveHost, listenPort))
            val client = OkHttpClient.Builder()
                .proxy(socksProxy)
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(targetUrl.trim())
                .header("User-Agent", "PulseProxy-Probe/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()?.take(2000).orEmpty()
                val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
                ProbeResult(
                    isRunning = false,
                    success = response.isSuccessful,
                    targetUrl = targetUrl,
                    latencyMs = elapsed,
                    httpStatusCode = response.code,
                    responseBody = body,
                    errorMessage = if (response.isSuccessful) null else "HTTP ${response.code}",
                    checkedAt = timeFormat.format(Date())
                )
            }
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
            ProbeResult(
                isRunning = false,
                success = false,
                targetUrl = targetUrl,
                latencyMs = elapsed,
                httpStatusCode = null,
                responseBody = "",
                errorMessage = e.message ?: "SOCKS5 probe failed",
                checkedAt = timeFormat.format(Date())
            )
        }
    }

    /**
     * Tests direct Cl2 WebSocket + HMAC handshake RTT against Worker/Clean IP.
     */
    suspend fun testCl2EndpointHandshake(
        profile: ProxyProfile,
        settings: EngineSettings
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val started = System.currentTimeMillis()
            val ws = Cl2WebSocketTransport.connect(
                workerHost = profile.workerHost,
                workerUrl = profile.workerUrl,
                cleanIp = profile.cleanIp,
                subprotocol = profile.subprotocol,
                bufferMax = profile.bufferMax,
                tcpNoDelay = settings.tcpNoDelay,
                autoFallbackCleanIp = settings.autoFallbackCleanIp,
                useStandbyPool = false,
                onLog = {}
            )
            try {
                val (initPacket, key) = Cl2Crypto.buildInit(profile.uuid, "1.1.1.1", 443)
                check(ws.sendBinary(initPacket)) { "Failed sending Cl2 init frame" }
                val ack = ws.readMessage(timeoutMs = 10_000)
                    ?: throw IllegalStateException("Connection closed before HMAC ACK")
                check(Cl2Crypto.verifyAck(ack, key)) { "HMAC ACK verification failed" }
                (System.currentTimeMillis() - started).coerceAtLeast(1L)
            } finally {
                ws.close()
            }
        }
    }
}
