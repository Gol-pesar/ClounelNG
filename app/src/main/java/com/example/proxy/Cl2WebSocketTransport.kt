package com.example.proxy

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket

/**
 * High-speed direct RFC 6455 WebSocket over TLS (no permessage-deflate overhead,
 * shared TLS session cache, Clean IP direct socket connect, and SNI preservation)
 * matching Cl2_82497904.py wire semantics directly.
 */
class Cl2WebSocketTransport private constructor(
    private val socket: Socket,
    private val binIn: DataInputStream,
    private val bufOut: BufferedOutputStream,
    val usedCleanIp: Boolean
) {
    private val writeLock = Any()
    private val closed = AtomicBoolean(false)
    private var sendScratch = ByteArray(65536 + 16)

    fun sendBinary(data: ByteArray, offset: Int = 0, byteCount: Int = data.size): Boolean {
        if (closed.get() || socket.isClosed) return false
        return try {
            synchronized(writeLock) {
                writeFrameLocked(OPCODE_BINARY, data, offset, byteCount)
            }
            true
        } catch (_: Exception) {
            close()
            false
        }
    }

    fun readMessage(timeoutMs: Int = 0): ByteArray? {
        val prevTimeout = if (timeoutMs > 0) socket.soTimeout else 0
        if (timeoutMs > 0) {
            socket.soTimeout = timeoutMs
        }
        try {
            var fragmentBuf: ByteArrayOutputStream? = null
            while (!closed.get()) {
                val b0 = try {
                    binIn.readUnsignedByte()
                } catch (_: EOFException) {
                    return null
                }
                val b1 = binIn.readUnsignedByte()

                val fin = (b0 and 0x80) != 0
                val opcode = b0 and 0x0F
                val masked = (b1 and 0x80) != 0
                var len = b1 and 0x7F
                if (len == 126) {
                    len = binIn.readUnsignedShort()
                } else if (len == 127) {
                    val longLen = binIn.readLong()
                    if (longLen < 0L || longLen > 32L * 1024L * 1024L) {
                        throw IOException("WebSocket frame too large: $longLen")
                    }
                    len = longLen.toInt()
                }

                val maskKey = if (masked) {
                    ByteArray(4).also { binIn.readFully(it) }
                } else {
                    null
                }

                val payload = ByteArray(len)
                if (len > 0) {
                    binIn.readFully(payload)
                    if (maskKey != null) {
                        for (i in 0 until len) {
                            payload[i] = (payload[i].toInt() xor maskKey[i and 3].toInt()).toByte()
                        }
                    }
                }

                when (opcode) {
                    OPCODE_CLOSE -> {
                        close()
                        return null
                    }
                    OPCODE_PING -> {
                        runCatching {
                            synchronized(writeLock) {
                                writeFrameLocked(OPCODE_PONG, payload, 0, payload.size)
                            }
                        }
                    }
                    OPCODE_PONG -> {
                        // Ignore control PONG
                    }
                    OPCODE_BINARY, OPCODE_TEXT, OPCODE_CONTINUATION -> {
                        if (fin && fragmentBuf == null) {
                            return payload
                        }
                        val acc = fragmentBuf ?: ByteArrayOutputStream(len * 2).also { fragmentBuf = it }
                        if (len > 0) acc.write(payload)
                        if (fin) {
                            return acc.toByteArray()
                        }
                    }
                    else -> {
                        // Ignore unknown non-control opcodes
                    }
                }
            }
            return null
        } finally {
            if (timeoutMs > 0) {
                runCatching { socket.soTimeout = prevTimeout }
            }
        }
    }

    fun hasBufferedInput(): Boolean {
        return runCatching { binIn.available() > 0 }.getOrDefault(false)
    }

    fun close(code: Int = 1000, reason: String = "bye") {
        if (closed.compareAndSet(false, true)) {
            runCatching {
                synchronized(writeLock) {
                    val reasonBytes = reason.toByteArray(Charsets.UTF_8)
                    val payload = ByteArray(2 + reasonBytes.size)
                    payload[0] = ((code ushr 8) and 0xFF).toByte()
                    payload[1] = (code and 0xFF).toByte()
                    if (reasonBytes.isNotEmpty()) {
                        System.arraycopy(reasonBytes, 0, payload, 2, reasonBytes.size)
                    }
                    writeFrameLocked(OPCODE_CLOSE, payload, 0, payload.size)
                }
            }
            runCatching { socket.close() }
        }
    }

    private fun writeFrameLocked(opcode: Int, data: ByteArray, offset: Int, len: Int) {
        val headerSize = when {
            len <= 125 -> 2 + 4
            len <= 65535 -> 4 + 4
            else -> 10 + 4
        }
        val totalLen = headerSize + len
        if (sendScratch.size < totalLen) {
            sendScratch = ByteArray(totalLen + 4096)
        }
        val out = sendScratch
        var p = 0
        out[p++] = (0x80 or opcode).toByte()
        when {
            len <= 125 -> {
                out[p++] = (0x80 or len).toByte()
            }
            len <= 65535 -> {
                out[p++] = (0x80 or 126).toByte()
                out[p++] = ((len ushr 8) and 0xFF).toByte()
                out[p++] = (len and 0xFF).toByte()
            }
            else -> {
                out[p++] = (0x80 or 127).toByte()
                val l = len.toLong()
                out[p++] = ((l ushr 56) and 0xFF).toByte()
                out[p++] = ((l ushr 48) and 0xFF).toByte()
                out[p++] = ((l ushr 40) and 0xFF).toByte()
                out[p++] = ((l ushr 32) and 0xFF).toByte()
                out[p++] = ((l ushr 24) and 0xFF).toByte()
                out[p++] = ((l ushr 16) and 0xFF).toByte()
                out[p++] = ((l ushr 8) and 0xFF).toByte()
                out[p++] = (l and 0xFF).toByte()
            }
        }

        val maskInt = ThreadLocalRandom.current().nextInt()
        val m0 = ((maskInt ushr 24) and 0xFF).toByte()
        val m1 = ((maskInt ushr 16) and 0xFF).toByte()
        val m2 = ((maskInt ushr 8) and 0xFF).toByte()
        val m3 = (maskInt and 0xFF).toByte()
        out[p++] = m0
        out[p++] = m1
        out[p++] = m2
        out[p++] = m3

        var i = 0
        var src = offset
        val limit4 = len and -4
        while (i < limit4) {
            out[p++] = (data[src++].toInt() xor m0.toInt()).toByte()
            out[p++] = (data[src++].toInt() xor m1.toInt()).toByte()
            out[p++] = (data[src++].toInt() xor m2.toInt()).toByte()
            out[p++] = (data[src++].toInt() xor m3.toInt()).toByte()
            i += 4
        }
        while (i < len) {
            val maskByte = when (i and 3) {
                0 -> m0
                1 -> m1
                2 -> m2
                else -> m3
            }
            out[p++] = (data[src++].toInt() xor maskByte.toInt()).toByte()
            i++
        }

        bufOut.write(out, 0, totalLen)
        bufOut.flush()
    }

    companion object {
        private const val OPCODE_CONTINUATION = 0x0
        private const val OPCODE_TEXT = 0x1
        private const val OPCODE_BINARY = 0x2
        private const val OPCODE_CLOSE = 0x8
        private const val OPCODE_PING = 0x9
        private const val OPCODE_PONG = 0xA
        private const val WS_MAGIC_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        private const val DNS_CACHE_TTL_MS = 30_000L

        private val secureRandom = SecureRandom()
        private val dnsCache = ConcurrentHashMap<String, Pair<Long, InetAddress>>()

        private val sharedSslContext: SSLContext by lazy {
            SSLContext.getInstance("TLS").apply {
                init(null, null, null)
                clientSessionContext.sessionCacheSize = 256
                clientSessionContext.sessionTimeout = 300
            }
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
        ): Cl2WebSocketTransport = withContext(Dispatchers.IO) {
            val effectiveHost = workerHost.trim().ifBlank { ConfigShareCodec.extractHostFromWorkerUrl(workerUrl) }
            require(effectiveHost.isNotBlank()) { "Worker Host/URL is empty" }
            val normalizedUrl = ConfigShareCodec.normalizeWorkerUrl(workerUrl.ifBlank { "wss://$effectiveHost/" })
            val trimmedCleanIp = cleanIp.trim().removePrefix("[").removeSuffix("]")

            if (trimmedCleanIp.isNotEmpty()) {
                try {
                    return@withContext connectInternal(
                        url = normalizedUrl,
                        workerHost = effectiveHost,
                        overrideIp = trimmedCleanIp,
                        subprotocol = subprotocol,
                        bufferMax = bufferMax,
                        tcpNoDelay = tcpNoDelay,
                        connectTimeoutMs = if (autoFallbackCleanIp) 7_000 else 10_000
                    )
                } catch (e: Exception) {
                    if (!autoFallbackCleanIp) throw e
                    onLog("Clean IP ($trimmedCleanIp) failed (${e.message}), falling back to $effectiveHost")
                }
            }

            connectInternal(
                url = normalizedUrl,
                workerHost = effectiveHost,
                overrideIp = null,
                subprotocol = subprotocol,
                bufferMax = bufferMax,
                tcpNoDelay = tcpNoDelay,
                connectTimeoutMs = 10_000
            )
        }

        private fun resolveHostCached(host: String): InetAddress {
            val now = System.currentTimeMillis()
            val cached = dnsCache[host]
            if (cached != null && (now - cached.first) < DNS_CACHE_TTL_MS) {
                return cached.second
            }
            val resolved = InetAddress.getByName(host)
            dnsCache[host] = now to resolved
            return resolved
        }

        private fun connectInternal(
            url: String,
            workerHost: String,
            overrideIp: String?,
            subprotocol: String,
            bufferMax: Int,
            tcpNoDelay: Boolean,
            connectTimeoutMs: Int
        ): Cl2WebSocketTransport {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase() ?: "wss"
            val isTls = scheme == "wss" || scheme == "https"
            val port = if (uri.port > 0) uri.port else if (isTls) 443 else 80
            val sniHost = workerHost.ifBlank { uri.host.orEmpty() }
            val path = uri.rawPath?.ifEmpty { "/" } ?: "/"
            val pathAndQuery = if (!uri.rawQuery.isNullOrEmpty()) "$path?${uri.rawQuery}" else path
            val defaultPort = if (isTls) 443 else 80
            val hostHeader = if (port != defaultPort) "$sniHost:$port" else sniHost

            val targetAddr = if (overrideIp != null) {
                InetAddress.getByName(overrideIp)
            } else {
                resolveHostCached(sniHost)
            }

            val rawSocket = Socket()
            val tunedBuf = bufferMax.coerceIn(131072, 4194304)
            runCatching {
                rawSocket.tcpNoDelay = tcpNoDelay
                rawSocket.keepAlive = true
                rawSocket.receiveBufferSize = tunedBuf
                rawSocket.sendBufferSize = tunedBuf
            }
            rawSocket.connect(InetSocketAddress(targetAddr, port), connectTimeoutMs)

            val socket: Socket = if (isTls) {
                val ssl = sharedSslContext.socketFactory.createSocket(
                    rawSocket,
                    sniHost,
                    port,
                    true
                ) as SSLSocket
                runCatching {
                    val params = ssl.sslParameters
                    params.serverNames = listOf(SNIHostName(sniHost))
                    ssl.sslParameters = params
                }
                ssl.soTimeout = 10_000
                ssl.startHandshake()
                if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(sniHost, ssl.session)) {
                    runCatching { ssl.close() }
                    throw SSLPeerUnverifiedException("Hostname verification failed for $sniHost")
                }
                ssl
            } else {
                rawSocket.soTimeout = 10_000
                rawSocket
            }

            val binIn = DataInputStream(BufferedInputStream(socket.getInputStream(), 131072))
            val bufOut = BufferedOutputStream(socket.getOutputStream(), 131072)

            val keyBytes = ByteArray(16).also { secureRandom.nextBytes(it) }
            val secKey = Base64.encodeToString(keyBytes, Base64.NO_WRAP)

            val reqHeaders = buildString {
                append("GET ").append(pathAndQuery).append(" HTTP/1.1\r\n")
                append("Host: ").append(hostHeader).append("\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Key: ").append(secKey).append("\r\n")
                append("Sec-WebSocket-Version: 13\r\n")
                if (subprotocol.isNotBlank()) {
                    append("Sec-WebSocket-Protocol: ").append(subprotocol.trim()).append("\r\n")
                }
                append("User-Agent: Clounel/1.0\r\n")
                append("\r\n")
            }

            bufOut.write(reqHeaders.toByteArray(Charsets.ISO_8859_1))
            bufOut.flush()

            val headerBlock = readHttpResponseHeaderBlock(binIn)
            val lines = headerBlock.split("\r\n")
            val statusLine = lines.firstOrNull().orEmpty()
            val statusCode = statusLine.split(" ").getOrNull(1)?.toIntOrNull() ?: 0
            if (statusCode != 101) {
                runCatching { socket.close() }
                throw IOException("HTTP $statusCode: $statusLine")
            }

            val expectedAccept = computeAcceptKey(secKey)
            val actualAccept = lines.drop(1)
                .firstOrNull { it.startsWith("sec-websocket-accept:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
            if (actualAccept != expectedAccept) {
                runCatching { socket.close() }
                throw IOException("Invalid Sec-WebSocket-Accept header")
            }

            socket.soTimeout = 0

            return Cl2WebSocketTransport(
                socket = socket,
                binIn = binIn,
                bufOut = bufOut,
                usedCleanIp = overrideIp != null
            )
        }

        private fun readHttpResponseHeaderBlock(input: DataInputStream): String {
            val buf = ByteArrayOutputStream(512)
            var state = 0
            while (buf.size() < 16384) {
                val b = input.read()
                if (b == -1) throw EOFException("Unexpected EOF during WebSocket upgrade")
                buf.write(b)
                state = when {
                    state == 0 && b == '\r'.code -> 1
                    state == 1 && b == '\n'.code -> 2
                    state == 2 && b == '\r'.code -> 3
                    state == 3 && b == '\n'.code -> return buf.toString("ISO-8859-1")
                    b == '\r'.code -> 1
                    else -> 0
                }
            }
            throw IOException("HTTP WebSocket upgrade response headers too large")
        }

        private fun computeAcceptKey(secKey: String): String {
            val sha1 = MessageDigest.getInstance("SHA-1")
                .digest((secKey + WS_MAGIC_GUID).toByteArray(Charsets.US_ASCII))
            return Base64.encodeToString(sha1, Base64.NO_WRAP)
        }
    }
}
