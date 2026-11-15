package com.example.proxy

import com.example.model.EngineSettings
import com.example.model.LogLevel
import com.example.model.ProxyProfile
import com.example.model.TunnelMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

interface TelemetrySink {
    fun onLog(level: LogLevel, message: String)
    fun onConnectionOpened(id: Long, host: String, port: Int, protocol: String, handshakeMs: Long)
    fun onBytesTransferred(connectionId: Long, bytesUp: Long, bytesDown: Long)
    fun onConnectionClosed(id: Long)
}

/**
 * High-performance asynchronous SOCKS5 & HTTP-CONNECT proxy server backed by
 * either the Cl2 WSS encrypted tunnel or a direct upstream SOCKS5 server.
 */
class Socks5ProxyServer(
    private val profile: ProxyProfile,
    private val settings: EngineSettings,
    private val sink: TelemetrySink
) {
    private val ioExecutor = Executors.newCachedThreadPool()
    private val ioDispatcher = ioExecutor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var socksServerSocket: ServerSocket? = null
    private var httpServerSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var httpAcceptJob: Job? = null
    private val connIdGen = AtomicLong(1L)

    val bindHost: String
        get() = if (settings.allowLan) "0.0.0.0" else profile.listenHost.ifBlank { "127.0.0.1" }

    val socksPort: Int
        get() = profile.listenPort

    val httpBridgePort: Int?
        get() = if (settings.enableHttpBridge) (profile.listenPort + 1).takeIf { it <= 65535 } else null

    fun start() {
        val host = bindHost
        val port = socksPort

        val socksSrv = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName(host), port), 256)
        }
        socksServerSocket = socksSrv

        val targetDesc = if (profile.mode == TunnelMode.CL2_WSS) {
            "${profile.workerHost} (Clean IP: ${profile.cleanIp.ifBlank { "DNS" }})"
        } else {
            "${profile.upstreamHost}:${profile.upstreamPort}"
        }
        sink.onLog(LogLevel.SUCCESS, "[*] AMIR Mode Engine -> $targetDesc")

        acceptJob = scope.launch {
            while (isActive && !socksSrv.isClosed) {
                try {
                    val client = socksSrv.accept()
                    launch { handleSocks5Client(client) }
                } catch (_: IOException) {
                    if (!socksSrv.isClosed) {
                        sink.onLog(LogLevel.WARN, "SOCKS5 accept interrupted")
                    }
                    break
                }
            }
        }

        val bridgePort = httpBridgePort
        if (bridgePort != null) {
            try {
                val httpSrv = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName(host), bridgePort), 128)
                }
                httpServerSocket = httpSrv
                sink.onLog(LogLevel.INFO, "[*] HTTP CONNECT Bridge listening on $host:$bridgePort")
                httpAcceptJob = scope.launch {
                    while (isActive && !httpSrv.isClosed) {
                        try {
                            val client = httpSrv.accept()
                            launch { handleHttpConnectClient(client) }
                        } catch (_: IOException) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                sink.onLog(LogLevel.WARN, "HTTP bridge port $bridgePort unavailable: ${e.message}")
            }
        }
    }

    fun stop() {
        runCatching { socksServerSocket?.close() }
        runCatching { httpServerSocket?.close() }
        acceptJob?.cancel()
        httpAcceptJob?.cancel()
        scope.cancel()
        runCatching { ioExecutor.shutdownNow() }
        sink.onLog(LogLevel.INFO, "[*] Proxy server stopped")
    }

    private fun tuneSocket(socket: Socket) {
        runCatching {
            socket.tcpNoDelay = settings.tcpNoDelay
            val bufSize = profile.bufferMax.coerceIn(65536, 4194304)
            socket.receiveBufferSize = bufSize
            socket.sendBufferSize = bufSize
        }
    }

    private suspend fun handleSocks5Client(client: Socket) {
        val connId = connIdGen.getAndIncrement()
        try {
            tuneSocket(client)
            client.soTimeout = 10_000
            val input = DataInputStream(BufferedInputStream(client.getInputStream(), 65536))
            val output = client.getOutputStream()

            // 1. Greeting: VER, NMETHODS, METHODS...
            val ver = input.readUnsignedByte()
            if (ver != 5) return
            val nMethods = input.readUnsignedByte()
            val methods = ByteArray(nMethods)
            input.readFully(methods)

            // Reply: No authentication required (0x00)
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            // 2. Request: VER, CMD, RSV, ATYP, DST.ADDR, DST.PORT
            val reqVer = input.readUnsignedByte()
            val cmd = input.readUnsignedByte()
            input.readUnsignedByte() // RSV
            val atyp = input.readUnsignedByte()
            if (reqVer != 5 || cmd != 1) {
                // Only CONNECT (0x01) is supported
                output.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                output.flush()
                return
            }

            val targetHost = when (atyp) {
                1 -> {
                    val addr = ByteArray(4)
                    input.readFully(addr)
                    InetAddress.getByAddress(addr).hostAddress ?: return
                }
                3 -> {
                    val len = input.readUnsignedByte()
                    val domain = ByteArray(len)
                    input.readFully(domain)
                    String(domain, Charsets.UTF_8)
                }
                4 -> {
                    val addr = ByteArray(16)
                    input.readFully(addr)
                    InetAddress.getByAddress(addr).hostAddress ?: return
                }
                else -> {
                    output.write(byteArrayOf(0x05, 0x08, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    output.flush()
                    return
                }
            }
            val targetPort = input.readUnsignedShort()
            client.soTimeout = 0 // Unlimited streaming timeout once connected

            relayTarget(
                connId = connId,
                clientInput = input,
                clientOutput = output,
                targetHost = targetHost,
                targetPort = targetPort,
                protocolLabel = "AMIR",
                sendSocksSuccess = {
                    output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    output.flush()
                },
                sendFailure = {
                    runCatching {
                        output.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                        output.flush()
                    }
                }
            )
        } catch (_: EOFException) {
            // Client closed early
        } catch (_: Exception) {
            // Connection terminated
        } finally {
            sink.onConnectionClosed(connId)
            runCatching { client.close() }
        }
    }

    private suspend fun handleHttpConnectClient(client: Socket) {
        val connId = connIdGen.getAndIncrement()
        try {
            tuneSocket(client)
            client.soTimeout = 10_000
            val input = BufferedInputStream(client.getInputStream(), 65536)
            val output = client.getOutputStream()

            val headerBytes = readHttpHeaderBlock(input) ?: return
            val headerText = String(headerBytes, Charsets.ISO_8859_1)
            val lines = headerText.split("\r\n")
            val requestLine = lines.firstOrNull() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            if (method != "CONNECT") {
                output.write("HTTP/1.1 405 Method Not Allowed\r\nConnection: close\r\n\r\n".toByteArray())
                output.flush()
                return
            }

            val authority = parts[1]
            val lastColon = authority.lastIndexOf(':')
            if (lastColon <= 0) return
            val targetHost = authority.substring(0, lastColon).removePrefix("[").removeSuffix("]")
            val targetPort = authority.substring(lastColon + 1).toIntOrNull() ?: 443
            client.soTimeout = 0

            relayTarget(
                connId = connId,
                clientInput = input,
                clientOutput = output,
                targetHost = targetHost,
                targetPort = targetPort,
                protocolLabel = "HTTPS-BRIDGE",
                sendSocksSuccess = {
                    output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                    output.flush()
                },
                sendFailure = {
                    runCatching {
                        output.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
                        output.flush()
                    }
                }
            )
        } catch (_: Exception) {
            // Closed
        } finally {
            sink.onConnectionClosed(connId)
            runCatching { client.close() }
        }
    }

    private suspend fun relayTarget(
        connId: Long,
        clientInput: InputStream,
        clientOutput: OutputStream,
        targetHost: String,
        targetPort: Int,
        protocolLabel: String,
        sendSocksSuccess: () -> Unit,
        sendFailure: () -> Unit
    ) {
        when (profile.mode) {
            TunnelMode.CL2_WSS -> relayViaCl2Wss(
                connId, clientInput, clientOutput, targetHost, targetPort, protocolLabel,
                sendSocksSuccess, sendFailure
            )
            TunnelMode.DIRECT_SOCKS5 -> relayViaDirectSocks5(
                connId, clientInput, clientOutput, targetHost, targetPort, protocolLabel,
                sendSocksSuccess, sendFailure
            )
        }
    }

    private suspend fun relayViaCl2Wss(
        connId: Long,
        clientInput: InputStream,
        clientOutput: OutputStream,
        targetHost: String,
        targetPort: Int,
        protocolLabel: String,
        sendSocksSuccess: () -> Unit,
        sendFailure: () -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        var ws: Cl2WebSocketTransport? = null
        try {
            ws = Cl2WebSocketTransport.connect(
                workerHost = profile.workerHost,
                workerUrl = profile.workerUrl,
                cleanIp = profile.cleanIp,
                subprotocol = profile.subprotocol,
                bufferMax = profile.bufferMax,
                tcpNoDelay = settings.tcpNoDelay,
                autoFallbackCleanIp = settings.autoFallbackCleanIp,
                onLog = { msg -> sink.onLog(LogLevel.WARN, msg) }
            )

            val (initPacket, key) = Cl2Crypto.buildInit(profile.uuid, targetHost, targetPort)
            if (!ws.sendBinary(initPacket)) {
                throw IOException("Failed sending Cl2 init frame")
            }

            val ack = ws.readMessage(timeoutMs = 10_000)
                ?: throw IOException("Cl2 handshake closed before ACK")
            if (!Cl2Crypto.verifyAck(ack, key)) {
                throw IOException("Invalid Cl2 HMAC handshake ACK from ${profile.workerHost}")
            }

            val handshakeMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
            sendSocksSuccess()
            sink.onConnectionOpened(connId, targetHost, targetPort, protocolLabel, handshakeMs)
            sink.onLog(LogLevel.INFO, "$protocolLabel $targetHost:$targetPort (${handshakeMs}ms)")

            val chunkSize = profile.maxChunk.coerceIn(16384, 262144)

            coroutineScope {
                val upJob = launch {
                    val buffer = ByteArray(chunkSize)
                    try {
                        while (isActive) {
                            val read = clientInput.read(buffer)
                            if (read <= 0) break
                            Cl2Crypto.xorInPlace(buffer, read, key)
                            if (!ws.sendBinary(buffer, 0, read)) break
                            sink.onBytesTransferred(connId, read.toLong(), 0L)
                        }
                    } catch (_: Exception) {
                        // Upstream closed
                    } finally {
                        ws.close()
                    }
                }

                val downJob = launch {
                    try {
                        while (isActive) {
                            val msg = ws.readMessage() ?: break
                            if (msg.isNotEmpty()) {
                                Cl2Crypto.xorInPlace(msg, msg.size, key)
                                clientOutput.write(msg)
                                if (!ws.hasBufferedInput()) {
                                    clientOutput.flush()
                                }
                                sink.onBytesTransferred(connId, 0L, msg.size.toLong())
                            }
                        }
                    } catch (_: Exception) {
                        // Downstream closed
                    } finally {
                        runCatching { clientOutput.flush() }
                        runCatching { clientOutput.close() }
                    }
                }

                upJob.join()
                downJob.join()
            }
        } catch (e: Exception) {
            sink.onLog(LogLevel.ERROR, "Tunnel error ($targetHost:$targetPort): ${e.message}")
            sendFailure()
        } finally {
            ws?.close()
        }
    }

    private suspend fun relayViaDirectSocks5(
        connId: Long,
        clientInput: InputStream,
        clientOutput: OutputStream,
        targetHost: String,
        targetPort: Int,
        protocolLabel: String,
        sendSocksSuccess: () -> Unit,
        sendFailure: () -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        var remoteSocket: Socket? = null
        try {
            if (profile.upstreamHost.isBlank()) {
                throw IOException("Upstream SOCKS5 host is empty")
            }
            val remote = Socket()
            tuneSocket(remote)
            remote.connect(InetSocketAddress(profile.upstreamHost, profile.upstreamPort), 12_000)
            remoteSocket = remote

            val remoteIn = DataInputStream(BufferedInputStream(remote.getInputStream(), 8192))
            val remoteOut = remote.getOutputStream()

            val hasAuth = profile.upstreamUsername.isNotEmpty()
            if (hasAuth) {
                remoteOut.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
            } else {
                remoteOut.write(byteArrayOf(0x05, 0x01, 0x00))
            }
            remoteOut.flush()

            val ver = remoteIn.readUnsignedByte()
            val method = remoteIn.readUnsignedByte()
            if (ver != 5 || method == 0xFF) {
                throw IOException("Upstream SOCKS5 rejected auth methods")
            }

            if (method == 0x02) {
                val u = profile.upstreamUsername.toByteArray(Charsets.UTF_8)
                val p = profile.upstreamPassword.toByteArray(Charsets.UTF_8)
                val authReq = ByteArray(3 + u.size + p.size)
                authReq[0] = 0x01
                authReq[1] = u.size.toByte()
                System.arraycopy(u, 0, authReq, 2, u.size)
                authReq[2 + u.size] = p.size.toByte()
                System.arraycopy(p, 0, authReq, 3 + u.size, p.size)
                remoteOut.write(authReq)
                remoteOut.flush()

                val authVer = remoteIn.readUnsignedByte()
                val authStatus = remoteIn.readUnsignedByte()
                if (authVer != 1 || authStatus != 0) {
                    throw IOException("Upstream SOCKS5 username/password authentication failed")
                }
            }

            // Send CONNECT to upstream SOCKS5
            val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
            val req = ByteBuffer.allocate(4 + 1 + hostBytes.size + 2)
                .order(ByteOrder.BIG_ENDIAN)
                .apply {
                    put(0x05)
                    put(0x01)
                    put(0x00)
                    put(0x03) // Domain
                    put(hostBytes.size.toByte())
                    put(hostBytes)
                    putShort(targetPort.toShort())
                }.array()
            remoteOut.write(req)
            remoteOut.flush()

            val repVer = remoteIn.readUnsignedByte()
            val repCode = remoteIn.readUnsignedByte()
            remoteIn.readUnsignedByte() // RSV
            val repAtyp = remoteIn.readUnsignedByte()
            when (repAtyp) {
                1 -> remoteIn.readFully(ByteArray(4))
                3 -> {
                    val l = remoteIn.readUnsignedByte()
                    remoteIn.readFully(ByteArray(l))
                }
                4 -> remoteIn.readFully(ByteArray(16))
            }
            remoteIn.readUnsignedShort()
            if (repVer != 5 || repCode != 0) {
                throw IOException("Upstream SOCKS5 CONNECT error code: $repCode")
            }

            val handshakeMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
            sendSocksSuccess()
            sink.onConnectionOpened(connId, targetHost, targetPort, protocolLabel, handshakeMs)
            sink.onLog(LogLevel.INFO, "DIRECT $targetHost:$targetPort (${handshakeMs}ms)")

            val chunkSize = profile.maxChunk.coerceIn(4096, 262144)

            coroutineScope {
                val upJob = launch {
                    val buf = ByteArray(chunkSize)
                    try {
                        while (isActive) {
                            val r = clientInput.read(buf)
                            if (r <= 0) break
                            remoteOut.write(buf, 0, r)
                            remoteOut.flush()
                            sink.onBytesTransferred(connId, r.toLong(), 0L)
                        }
                    } catch (_: Exception) {
                    } finally {
                        runCatching { remote.shutdownOutput() }
                    }
                }
                val downJob = launch {
                    val buf = ByteArray(chunkSize)
                    try {
                        while (isActive) {
                            val r = remoteIn.read(buf)
                            if (r <= 0) break
                            clientOutput.write(buf, 0, r)
                            clientOutput.flush()
                            sink.onBytesTransferred(connId, 0L, r.toLong())
                        }
                    } catch (_: Exception) {
                    } finally {
                        runCatching { clientOutput.close() }
                    }
                }
                upJob.join()
                downJob.join()
            }
        } catch (e: Exception) {
            sink.onLog(LogLevel.ERROR, "Direct SOCKS5 error ($targetHost:$targetPort): ${e.message}")
            sendFailure()
        } finally {
            runCatching { remoteSocket?.close() }
        }
    }

    private fun readHttpHeaderBlock(input: InputStream): ByteArray? {
        val buf = ArrayList<Byte>(256)
        var state = 0
        while (buf.size < 16384) {
            val b = input.read()
            if (b == -1) return null
            buf.add(b.toByte())
            state = when {
                state == 0 && b == '\r'.code -> 1
                state == 1 && b == '\n'.code -> 2
                state == 2 && b == '\r'.code -> 3
                state == 3 && b == '\n'.code -> return buf.toByteArray()
                b == '\r'.code -> 1
                else -> 0
            }
        }
        return null
    }
}
