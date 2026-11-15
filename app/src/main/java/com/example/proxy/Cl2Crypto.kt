package com.example.proxy

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Exact Kotlin port of Cl2_82497904.py cryptographic framing and XOR stream cipher.
 */
object Cl2Crypto {
    private val secureRandom = SecureRandom()
    private val IPv4_REGEX = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    fun deriveKey(uuid: String, nonce: ByteArray): ByteArray {
        require(nonce.size == 16) { "Nonce must be 16 bytes" }
        val tb = uuid.toByteArray(Charsets.UTF_8)
        require(tb.isNotEmpty()) { "UUID cannot be empty" }
        return ByteArray(16) { i ->
            (tb[i % tb.size].toInt() xor nonce[i].toInt()).toByte()
        }
    }

    fun buildInit(
        uuid: String,
        host: String,
        port: Int,
        payload: ByteArray = ByteArray(0),
        fixedNonce: ByteArray? = null
    ): Pair<ByteArray, ByteArray> {
        val nonce = fixedNonce ?: ByteArray(16).also { secureRandom.nextBytes(it) }
        val key = deriveKey(uuid, nonce)

        val (at, addrBytes) = encodeTargetAddress(host)

        val bodyLen = 1 + 2 + 1 + addrBytes.size + payload.size
        val body = ByteBuffer.allocate(bodyLen).order(ByteOrder.BIG_ENDIAN).apply {
            put(1.toByte())
            putShort(port.toShort())
            put(at.toByte())
            put(addrBytes)
            if (payload.isNotEmpty()) put(payload)
        }.array()

        val enc = xorTransform(body, body.size, key)
        val mac8 = hmacSha256(key, enc).copyOfRange(0, 8)

        val initPacket = ByteArray(nonce.size + enc.size + mac8.size)
        System.arraycopy(nonce, 0, initPacket, 0, nonce.size)
        System.arraycopy(enc, 0, initPacket, nonce.size, enc.size)
        System.arraycopy(mac8, 0, initPacket, nonce.size + enc.size, mac8.size)

        return initPacket to key
    }

    fun expectedAck(key: ByteArray): ByteArray {
        return hmacSha256(key, "OK".toByteArray(Charsets.US_ASCII)).copyOfRange(0, 16)
    }

    fun verifyAck(ackPacket: ByteArray, key: ByteArray): Boolean {
        return verifyAck(ackPacket, ackPacket.size, key)
    }

    fun verifyAck(ackPacket: ByteArray, length: Int, key: ByteArray): Boolean {
        if (length < 16 || ackPacket.size < length) return false
        val decrypted = xorTransform(ackPacket, length, key)
        val expected = expectedAck(key)
        return decrypted.contentEquals(expected)
    }

    /**
     * Exact equivalent of Python's per-chunk XOR with mv_key[:l]:
     * `(int.from_bytes(d, BO) ^ int.from_bytes(mv_key[:l], BO)).to_bytes(l, BO)`
     * Because XOR is bitwise independent of endianness, byte-by-byte XOR with key[i % 16]
     * produces identical bytes on all architectures.
     */
    fun xorTransform(data: ByteArray, length: Int, key: ByteArray): ByteArray {
        val out = ByteArray(length)
        val kLen = key.size
        for (i in 0 until length) {
            out[i] = (data[i].toInt() xor key[i % kLen].toInt()).toByte()
        }
        return out
    }

    /**
     * In-place XOR transformation to avoid per-chunk ByteArray allocations during high-throughput streaming.
     */
    fun xorInPlace(data: ByteArray, length: Int, key: ByteArray) {
        val kLen = key.size
        if (kLen == 16) {
            var i = 0
            while (i + 16 <= length) {
                for (j in 0 until 16) {
                    data[i + j] = (data[i + j].toInt() xor key[j].toInt()).toByte()
                }
                i += 16
            }
            while (i < length) {
                data[i] = (data[i].toInt() xor key[i and 15].toInt()).toByte()
                i++
            }
        } else {
            for (i in 0 until length) {
                data[i] = (data[i].toInt() xor key[i % kLen].toInt()).toByte()
            }
        }
    }

    private fun encodeTargetAddress(host: String): Pair<Int, ByteArray> {
        val trimmed = host.trim().removePrefix("[").removeSuffix("]")
        // Check literal IPv4 first without DNS lookup
        if (IPv4_REGEX.matches(trimmed)) {
            val parts = trimmed.split(".")
            if (parts.all { it.toIntOrNull() in 0..255 }) {
                val addr = ByteArray(4) { idx -> parts[idx].toInt().toByte() }
                return 1 to addr
            }
        }
        // Check literal IPv6 without triggering DNS
        if (trimmed.contains(":")) {
            val parsed = runCatching { InetAddress.getByName(trimmed) }.getOrNull()
            if (parsed is Inet6Address) {
                return 3 to parsed.address
            }
            if (parsed is Inet4Address) {
                return 1 to parsed.address
            }
        }
        // Domain name: Cl2 protocol uses at = 2, [len] + domain bytes
        val hb = trimmed.toByteArray(Charsets.UTF_8)
        require(hb.size <= 255) { "Hostname too long: ${hb.size}" }
        val addr = ByteArray(1 + hb.size)
        addr[0] = hb.size.toByte()
        System.arraycopy(hb, 0, addr, 1, hb.size)
        return 2 to addr
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
