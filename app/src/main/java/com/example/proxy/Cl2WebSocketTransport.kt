package com.example.proxy

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

/**
 * High-speed WebSocket transport with Clean IP DNS override, TLS SNI preservation,
 * cached high-concurrency OkHttpClient pool (maxRequestsPerHost = 128), and TCP tuning.
 */
class Cl2WebSocketTransport private constructor(
    private val webSocket: WebSocket,
    val incoming: Channel<ByteArray>,
    val usedCleanIp: Boolean
) {
    fun sendBinary(data: ByteArray, offset: Int = 0, byteCount: Int = data.size): Boolean {
        return webSocket.send(data.toByteString(offset, byteCount))
    }

    fun close(code: Int = 1000, reason: String = "bye") {
        runCatching { webSocket.close(code, reason) }
        incoming.close()
    }

    companion object {
        private val clientCache = ConcurrentHashMap<String, OkHttpClient>()

        private val baseHttpClient: OkHttpClient by lazy {
            val dispatcher = Dispatcher().apply {
                maxRequests = 256
                maxRequestsPerHost = 128
            }
            OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .build()
        }

        suspend fun connect(
            workerHost: String,
            workerUrl: String,
            cleanIp: String,
            subprotocol: String,
            bufferMax: Int,
            tcpNoDelay: Boolean,
            autoFallbackCleanIp: Boolean,
            onLog: (String) -> Unit
        ): Cl2WebSocketTransport {
            val normalizedUrl = normalizeWsUrl(workerUrl, workerHost)
            val trimmedCleanIp = cleanIp.trim().removePrefix("[").removeSuffix("]")

            if (trimmedCleanIp.isNotEmpty()) {
                try {
                    return connectInternal(
                        url = normalizedUrl,
                        workerHost = workerHost,
                        overrideIp = trimmedCleanIp,
                        subprotocol = subprotocol,
                        bufferMax = bufferMax,
                        tcpNoDelay = tcpNoDelay
                    )
                } catch (e: Exception) {
                    if (!autoFallbackCleanIp) throw e
                    onLog("Clean IP ($trimmedCleanIp) failed (${e.message}), falling back to $workerHost")
                }
            }

            return connectInternal(
                url = normalizedUrl,
                workerHost = workerHost,
                overrideIp = null,
                subprotocol = subprotocol,
                bufferMax = bufferMax,
                tcpNoDelay = tcpNoDelay
            )
        }

        private fun getOrCreateClient(
            workerHost: String,
            overrideIp: String?,
            bufferBytes: Int,
            tcpNoDelay: Boolean
        ): OkHttpClient {
            val key = "$workerHost|${overrideIp.orEmpty()}|$bufferBytes|$tcpNoDelay"
            return clientCache.getOrPut(key) {
                val customDns = if (overrideIp != null) {
                    object : Dns {
                        override fun lookup(hostname: String): List<InetAddress> {
                            return if (hostname.equals(workerHost, ignoreCase = true)) {
                                listOf(InetAddress.getByName(overrideIp))
                            } else {
                                Dns.SYSTEM.lookup(hostname)
                            }
                        }
                    }
                } else {
                    Dns.SYSTEM
                }

                val tunedSocketFactory = TunedSocketFactory(
                    tcpNoDelay = tcpNoDelay,
                    bufferBytes = bufferBytes
                )

                baseHttpClient.newBuilder()
                    .dns(customDns)
                    .socketFactory(tunedSocketFactory)
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .writeTimeout(12, TimeUnit.SECONDS)
                    .pingInterval(0, TimeUnit.MILLISECONDS)
                    .build()
            }
        }

        private suspend fun connectInternal(
            url: String,
            workerHost: String,
            overrideIp: String?,
            subprotocol: String,
            bufferMax: Int,
            tcpNoDelay: Boolean
        ): Cl2WebSocketTransport {
            val client = getOrCreateClient(
                workerHost = workerHost,
                overrideIp = overrideIp,
                bufferBytes = bufferMax.coerceIn(65536, 4194304),
                tcpNoDelay = tcpNoDelay
            )

            val requestBuilder = Request.Builder()
                .url(url)
                .header("Host", workerHost)
                .header("User-Agent", "Clounel/1.0")
            if (subprotocol.isNotBlank()) {
                requestBuilder.header("Sec-WebSocket-Protocol", subprotocol)
            }

            val connected = CompletableDeferred<WebSocket>()
            val incoming = Channel<ByteArray>(capacity = 512)

            val ws = client.newWebSocket(
                requestBuilder.build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        connected.complete(webSocket)
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        incoming.trySend(bytes.toByteArray())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        incoming.trySend(text.toByteArray(Charsets.UTF_8))
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                        incoming.close()
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        incoming.close()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (!connected.isCompleted) {
                            connected.completeExceptionally(
                                IOException(t.message ?: "WebSocket failure", t)
                            )
                        }
                        incoming.close(t)
                    }
                }
            )

            connected.await()
            return Cl2WebSocketTransport(
                webSocket = ws,
                incoming = incoming,
                usedCleanIp = overrideIp != null
            )
        }

        private fun normalizeWsUrl(rawUrl: String, workerHost: String): String {
            val trimmed = rawUrl.trim().ifEmpty { "wss://$workerHost/" }
            return when {
                trimmed.startsWith("wss://", ignoreCase = true) -> "https://" + trimmed.substring(6)
                trimmed.startsWith("ws://", ignoreCase = true) -> "http://" + trimmed.substring(5)
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                trimmed.startsWith("http://", ignoreCase = true) -> trimmed
                else -> "https://$trimmed"
            }
        }
    }

    private class TunedSocketFactory(
        private val tcpNoDelay: Boolean,
        private val bufferBytes: Int
    ) : SocketFactory() {
        private val delegate = getDefault()

        private fun tune(socket: Socket): Socket {
            runCatching {
                socket.tcpNoDelay = tcpNoDelay
                socket.receiveBufferSize = bufferBytes
                socket.sendBufferSize = bufferBytes
            }
            return socket
        }

        override fun createSocket(): Socket = tune(delegate.createSocket())
        override fun createSocket(host: String?, port: Int): Socket =
            tune(delegate.createSocket(host, port))

        override fun createSocket(
            host: String?,
            port: Int,
            localHost: InetAddress?,
            localPort: Int
        ): Socket = tune(delegate.createSocket(host, port, localHost, localPort))

        override fun createSocket(host: InetAddress?, port: Int): Socket =
            tune(delegate.createSocket(host, port))

        override fun createSocket(
            address: InetAddress?,
            port: Int,
            localAddress: InetAddress?,
            localPort: Int
        ): Socket = tune(delegate.createSocket(address, port, localAddress, localPort))
    }
}
