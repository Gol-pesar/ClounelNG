package com.example

import com.example.model.ProxyProfile
import com.example.model.TunnelMode
import com.example.proxy.Cl2Crypto
import com.example.proxy.ConfigShareCodec
import com.example.proxy.PythonScriptBridge
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class Cl2ProtocolUnitTest {

    @Test
    fun `deriveKey matches Python byte-for-byte`() {
        val uuid = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
        val nonce = ByteArray(16) { it.toByte() }
        val key = Cl2Crypto.deriveKey(uuid, nonce)
        val tb = uuid.toByteArray(Charsets.UTF_8)
        val expected = ByteArray(16) { i -> (tb[i].toInt() xor nonce[i].toInt()).toByte() }
        assertArrayEquals(expected, key)
    }

    @Test
    fun `xorInPlace produces identical output to xorTransform`() {
        val key = ByteArray(16) { (it * 13 + 5).toByte() }
        val original = ByteArray(1027) { (it * 7 + 3).toByte() }
        val expected = Cl2Crypto.xorTransform(original, original.size, key)

        val inPlace = original.copyOf()
        Cl2Crypto.xorInPlace(inPlace, inPlace.size, key)
        assertArrayEquals(expected, inPlace)

        // XOR twice restores original
        Cl2Crypto.xorInPlace(inPlace, inPlace.size, key)
        assertArrayEquals(original, inPlace)
    }

    @Test
    fun `buildInit and verifyAck follow exact Cl2 wire format`() {
        val uuid = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
        val fixedNonce = ByteArray(16) { (it + 7).toByte() }
        val (initPacket, key) = Cl2Crypto.buildInit(
            uuid = uuid,
            host = "1.1.1.1",
            port = 443,
            fixedNonce = fixedNonce
        )

        assertEquals(32, initPacket.size)
        val enc = initPacket.copyOfRange(16, 24)
        val dec = Cl2Crypto.xorTransform(enc, enc.size, key)
        assertArrayEquals(
            byteArrayOf(1, 0x01, 0xBB.toByte(), 1, 1, 1, 1, 1),
            dec
        )

        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(key, "HmacSHA256"))
        }.doFinal(enc).copyOfRange(0, 8)
        assertArrayEquals(mac, initPacket.copyOfRange(24, 32))

        val ackPlain = Cl2Crypto.expectedAck(key)
        val ackEncrypted = Cl2Crypto.xorTransform(ackPlain, ackPlain.size, key)
        assertTrue(Cl2Crypto.verifyAck(ackEncrypted, key))
    }

    @Test
    fun `parseScript extracts all constants from Cl2_82497904`() {
        val parsed = PythonScriptBridge.parseScript(PythonScriptBridge.DEFAULT_CL2_SCRIPT)
        assertEquals("63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd", parsed.uuid)
        assertEquals("twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev", parsed.workerHost)
        assertEquals("wss://twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev/", parsed.workerUrl)
        assertEquals("172.67.70.244", parsed.cleanIp)
        assertEquals("127.0.0.1", parsed.listenHost)
        assertEquals(40446, parsed.listenPort)
        assertEquals("chat.v2", parsed.subprotocol)
        assertEquals(65536, parsed.maxChunk)
        assertEquals(4194304, parsed.bufferMax)
    }

    @Test
    fun `ConfigShareCodec roundtrips amir URI and decodes JSON and Python`() {
        val profile = ProxyProfile(
            name = "AMIR Fast",
            mode = TunnelMode.CL2_WSS,
            uuid = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd",
            workerHost = "twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev",
            workerUrl = "wss://twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev/",
            cleanIp = "172.67.70.244"
        )
        val uri = ConfigShareCodec.encodeToUri(profile)
        assertTrue(uri.startsWith("amir://cl2?"))
        val decoded = ConfigShareCodec.decodeFromText(uri).getOrThrow()
        assertEquals(profile.name, decoded.name)
        assertEquals(profile.uuid, decoded.uuid)
        assertEquals(profile.workerUrl, decoded.workerUrl)
        assertEquals(profile.workerHost, decoded.workerHost)
        assertEquals(profile.cleanIp, decoded.cleanIp)

        val decodedPy = ConfigShareCodec.decodeFromText(PythonScriptBridge.DEFAULT_CL2_SCRIPT).getOrThrow()
        assertEquals("172.67.70.244", decodedPy.cleanIp)
        assertEquals("63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd", decodedPy.uuid)
    }
}
