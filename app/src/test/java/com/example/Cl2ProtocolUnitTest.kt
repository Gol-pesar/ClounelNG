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

        // Zero-copy FrameBuffer style oversized array check
        val oversized = ByteArray(1024)
        System.arraycopy(ackEncrypted, 0, oversized, 0, ackEncrypted.size)
        assertTrue(Cl2Crypto.verifyAck(oversized, ackEncrypted.size, key))
    }

    @Test
    fun `default template has no pre-seeded config and parseScript extracts constants`() {
        val defaultParsed = PythonScriptBridge.parseScript(PythonScriptBridge.DEFAULT_CL2_SCRIPT)
        assertEquals("", defaultParsed.uuid)
        assertEquals("", defaultParsed.workerHost)
        assertEquals("", defaultParsed.cleanIp)
        assertEquals("127.0.0.1", defaultParsed.listenHost)
        assertEquals(40446, defaultParsed.listenPort)
        assertEquals("chat.v2", defaultParsed.subprotocol)

        val sampleScript = """
            UUID        = "11111111-2222-3333-4444-555555555555"
            WORKER_HOST = "example-worker.workers.dev"
            WORKER_URL  = f"wss://{WORKER_HOST}/"
            CLEAN_IP    = "104.16.1.1"
            LISTEN      = ("127.0.0.1", 40446)
            SUBPROTO    = "chat.v2"
            MAX_CHUNK   = 65536
            BUFFER_MAX  = 4194304
        """.trimIndent()
        val parsed = PythonScriptBridge.parseScript(sampleScript)
        assertEquals("11111111-2222-3333-4444-555555555555", parsed.uuid)
        assertEquals("example-worker.workers.dev", parsed.workerHost)
        assertEquals("wss://example-worker.workers.dev/", parsed.workerUrl)
        assertEquals("104.16.1.1", parsed.cleanIp)
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
            uuid = "11111111-2222-3333-4444-555555555555",
            workerHost = "example-worker.workers.dev",
            workerUrl = "wss://example-worker.workers.dev/",
            cleanIp = "104.16.1.1"
        )
        val uri = ConfigShareCodec.encodeToUri(profile)
        assertTrue(uri.startsWith("amir://cl2?"))
        val decoded = ConfigShareCodec.decodeFromText(uri).getOrThrow()
        assertEquals(profile.name, decoded.name)
        assertEquals(profile.uuid, decoded.uuid)
        assertEquals(profile.workerUrl, decoded.workerUrl)
        assertEquals(profile.workerHost, decoded.workerHost)
        assertEquals(profile.cleanIp, decoded.cleanIp)

        val samplePy = """
            UUID        = "11111111-2222-3333-4444-555555555555"
            WORKER_HOST = "example-worker.workers.dev"
            CLEAN_IP    = "104.16.1.1"
        """.trimIndent()
        val decodedPy = ConfigShareCodec.decodeFromText(samplePy).getOrThrow()
        assertEquals("104.16.1.1", decodedPy.cleanIp)
        assertEquals("11111111-2222-3333-4444-555555555555", decodedPy.uuid)
    }

    @Test
    fun `updateScriptWithProfile injects active config values into Python core`() {
        val profile = ProxyProfile(
            name = "Custom Node",
            mode = TunnelMode.CL2_WSS,
            uuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            workerHost = "my-node.workers.dev",
            workerUrl = "wss://my-node.workers.dev/ws",
            cleanIp = "172.67.70.10",
            subprotocol = "chat.v2",
            listenHost = "127.0.0.1",
            listenPort = 40446
        )
        val updated = PythonScriptBridge.updateScriptWithProfile(
            PythonScriptBridge.DEFAULT_CL2_SCRIPT,
            profile
        )
        val reparsed = PythonScriptBridge.parseScript(updated)
        assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", reparsed.uuid)
        assertEquals("my-node.workers.dev", reparsed.workerHost)
        assertEquals("wss://my-node.workers.dev/ws", reparsed.workerUrl)
        assertEquals("172.67.70.10", reparsed.cleanIp)
        assertEquals("127.0.0.1", reparsed.listenHost)
        assertEquals(40446, reparsed.listenPort)
        assertTrue(updated.contains("_WS_POOL"))
        assertTrue(updated.contains("_get_ssl_ctx"))
        assertTrue(updated.contains("_parse_clean_ip"))
    }
}
