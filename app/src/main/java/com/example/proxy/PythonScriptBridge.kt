package com.example.proxy

import com.example.model.ParsedPythonConfig
import com.example.model.ProxyProfile
import com.example.model.TunnelMode

/**
 * Parses, generates, and integrates Python Cl2 SOCKS5 scripts (such as Cl2_82497904.py)
 * with the native unlimited Kotlin tunnel engine and Python client tooling.
 */
object PythonScriptBridge {

    val DEFAULT_CL2_SCRIPT = """
#!/usr/bin/env python3
import asyncio, socket, struct, sys, os, hmac, hashlib
import websockets

try:
    import uvloop
    asyncio.set_event_loop_policy(uvloop.EventLoopPolicy())
except ImportError:
    pass

UUID        = ""
WORKER_HOST = ""
WORKER_URL  = f"wss://{WORKER_HOST}/"
CLEAN_IP    = ""
LISTEN      = ("127.0.0.1", 40446)
SUBPROTO    = "chat.v2"
MAX_CHUNK   = 65536
BUFFER_MAX  = 4194304  # 4MB

_WS_MAJOR = int(websockets.__version__.split(".")[0]) if hasattr(websockets, "__version__") else 12
_HEADERS_KWARG = "additional_headers" if _WS_MAJOR >= 14 else "extra_headers"

BO = sys.byteorder
_int_from_bytes = int.from_bytes

def derive_key(nonce):
    tb = UUID.encode()
    return bytes(tb[i % len(tb)] ^ nonce[i] for i in range(16))

def build_init(host, port, payload=b""):
    nonce = os.urandom(16)
    key = derive_key(nonce)
    try:
        socket.inet_pton(socket.AF_INET, host); at, addr = 1, socket.inet_aton(host)
    except OSError:
        try:
            socket.inet_pton(socket.AF_INET6, host); at, addr = 3, socket.inet_pton(socket.AF_INET6, host)
        except OSError:
            hb = host.encode(); at, addr = 2, bytes([len(hb)]) + hb
    body = bytes([1]) + struct.pack("!H", port) + bytes([at]) + addr + payload

    init_key_expanded = key * (len(body) // len(key)) + key[:len(body) % len(key)]
    enc = (_int_from_bytes(body, BO) ^ _int_from_bytes(init_key_expanded, BO)).to_bytes(len(body), BO)
    return nonce + enc + hmac.new(key, enc, hashlib.sha256).digest()[:8], key

async def ws_connect():
    kw = {
        "max_size": None,
        "open_timeout": 15,
        "subprotocols": [SUBPROTO],
        "server_hostname": WORKER_HOST,
        "compression": None,
        "ping_interval": None,
    }
    if CLEAN_IP:
        ip = f"[{CLEAN_IP}]" if ":" in CLEAN_IP else CLEAN_IP
        url = WORKER_URL.replace(WORKER_HOST, ip)
        kw[_HEADERS_KWARG] = {"Host": WORKER_HOST}
        try:
            return await websockets.connect(url, **kw)
        except Exception:
            pass

    kw[_HEADERS_KWARG] = {}
    return await websockets.connect(WORKER_URL, **kw)

async def handler(r, w):
    try:
        try:
            h = await asyncio.wait_for(r.readexactly(2), timeout=5)
            if h[0] != 5: return
            await asyncio.wait_for(r.readexactly(h[1]), timeout=5)
        except Exception: return

        w.transport.write(b"\x05\x00")

        try:
            q = await asyncio.wait_for(r.readexactly(4), timeout=5)
            if q[0] != 5 or q[1] != 1: return
            at = q[3]
            if at == 1: host = socket.inet_ntoa(await r.readexactly(4))
            elif at == 3: host = (await r.readexactly((await r.readexactly(1))[0])).decode()
            elif at == 4: host = socket.inet_ntop(socket.AF_INET6, await r.readexactly(16))
            else: return
            port = struct.unpack("!H", await r.readexactly(2))[0]
        except Exception: return

        sock = w.get_extra_info('socket')
        if sock:
            try:
                sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                if hasattr(socket, 'TCP_QUICKACK'):
                    sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_QUICKACK, 1)
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, BUFFER_MAX)
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, BUFFER_MAX)
            except Exception: pass

        try:
            ws = await ws_connect()
        except Exception: return

        init, key = build_init(host, port)
        await ws.send(init)

        raw_expanded = key * ((MAX_CHUNK // len(key)) + 1)
        mv_key = memoryview(raw_expanded)
        key_cache = {}

        try:
            ack = await asyncio.wait_for(ws.recv(), timeout=10)
            if len(ack) < 16: return
        except Exception: return

        ack_l = len(ack)
        ack_k = _int_from_bytes(mv_key[:ack_l], BO)
        expect = hmac.new(key, b"OK", hashlib.sha256).digest()[:16]
        if (_int_from_bytes(ack, BO) ^ ack_k).to_bytes(ack_l, BO) != expect: return

        w.transport.write(b"\x05\x00\x00\x01\x00\x00\x00\x00\x00\x00")

        async def up():
            r_read = r.read
            ws_send = ws.send
            try:
                while True:
                    d = await r_read(MAX_CHUNK)
                    if not d: break
                    l = len(d)
                    k = key_cache.get(l)
                    if k is None:
                        k = _int_from_bytes(mv_key[:l], BO)
                        if len(key_cache) < 2048: key_cache[l] = k
                    await ws_send((_int_from_bytes(d, BO) ^ k).to_bytes(l, BO))
            except Exception: pass
            finally:
                try: await ws.close()
                except: pass

        async def down():
            transport = w.transport
            t_write = transport.write
            w_drain = w.drain
            get_buf = transport.get_write_buffer_size
            try:
                async for m in ws:
                    if m:
                        l = len(m)
                        k = key_cache.get(l)
                        if k is None:
                            k = _int_from_bytes(mv_key[:l], BO)
                            if len(key_cache) < 2048: key_cache[l] = k
                        t_write((_int_from_bytes(m, BO) ^ k).to_bytes(l, BO))
                        if get_buf() > BUFFER_MAX:
                            await w_drain()
            except Exception: pass
            finally:
                try: w.close()
                except: pass

        await asyncio.gather(up(), down())
    except Exception: pass
    finally:
        try: w.close()
        except: pass

async def main():
    print(f"[*] AMIR Mode Proxy {LISTEN[0]}:{LISTEN[1]} -> {WORKER_HOST}")
    srv = await asyncio.start_server(handler, LISTEN[0], LISTEN[1])
    async with srv: await srv.serve_forever()

if __name__ == "__main__":
    try: asyncio.run(main())
    except KeyboardInterrupt: pass
""".trimIndent()

    private val strVarRegex = { name: String ->
        Regex("""^[ \t]*$name\s*=\s*f?["']([^"']*)["']""", RegexOption.MULTILINE)
    }
    private val intVarRegex = { name: String ->
        Regex("""^[ \t]*$name\s*=\s*(\d+)""", RegexOption.MULTILINE)
    }
    private val listenRegex = Regex(
        """^[ \t]*LISTEN\s*=\s*\(\s*["']([^"']+)["']\s*,\s*(\d+)\s*\)""",
        RegexOption.MULTILINE
    )

    fun parseScript(script: String): ParsedPythonConfig {
        val uuid = strVarRegex("UUID").find(script)?.groupValues?.get(1)?.trim()
        val workerHost = strVarRegex("WORKER_HOST").find(script)?.groupValues?.get(1)?.trim()
        var workerUrl = strVarRegex("WORKER_URL").find(script)?.groupValues?.get(1)?.trim()
        if (workerUrl != null && workerHost != null) {
            workerUrl = workerUrl.replace("{WORKER_HOST}", workerHost)
        }
        val cleanIp = strVarRegex("CLEAN_IP").find(script)?.groupValues?.get(1)?.trim()
        val subproto = strVarRegex("SUBPROTO").find(script)?.groupValues?.get(1)?.trim()
        val listenMatch = listenRegex.find(script)
        val listenHost = listenMatch?.groupValues?.get(1)?.trim()
        val listenPort = listenMatch?.groupValues?.get(2)?.toIntOrNull()
        val maxChunk = intVarRegex("MAX_CHUNK").find(script)?.groupValues?.get(1)?.toIntOrNull()
        val bufferMax = intVarRegex("BUFFER_MAX").find(script)?.groupValues?.get(1)?.toIntOrNull()

        return ParsedPythonConfig(
            uuid = uuid,
            workerHost = workerHost,
            workerUrl = workerUrl,
            cleanIp = cleanIp,
            listenHost = listenHost,
            listenPort = listenPort,
            subprotocol = subproto,
            maxChunk = maxChunk,
            bufferMax = bufferMax
        )
    }

    fun applyConfigToProfile(base: ProxyProfile, parsed: ParsedPythonConfig): ProxyProfile {
        val rawUrl = parsed.workerUrl?.takeIf { it.isNotBlank() && !it.contains("{WORKER_HOST}") }
        val host = parsed.workerHost?.takeIf { it.isNotBlank() }
            ?: rawUrl?.let { ConfigShareCodec.extractHostFromWorkerUrl(it) }?.takeIf { it.isNotBlank() }
            ?: base.workerHost
        val url = ConfigShareCodec.normalizeWorkerUrl(
            parsed.workerUrl?.takeIf { it.isNotBlank() && !it.contains("{WORKER_HOST}") }
                ?: if (host.isNotBlank()) "wss://$host/" else base.workerUrl
        )
        return base.copy(
            mode = TunnelMode.CL2_WSS,
            uuid = parsed.uuid?.takeIf { it.isNotBlank() } ?: base.uuid,
            workerHost = host,
            workerUrl = url,
            cleanIp = parsed.cleanIp ?: base.cleanIp,
            listenHost = parsed.listenHost?.takeIf { it.isNotBlank() } ?: base.listenHost,
            listenPort = parsed.listenPort?.takeIf { it in 1..65535 } ?: base.listenPort,
            subprotocol = parsed.subprotocol?.takeIf { it.isNotBlank() } ?: base.subprotocol,
            maxChunk = parsed.maxChunk?.coerceIn(4096, 1048576) ?: base.maxChunk,
            bufferMax = parsed.bufferMax?.coerceIn(65536, 16777216) ?: base.bufferMax
        )
    }

    fun updateScriptWithProfile(script: String, profile: ProxyProfile): String {
        val safeUuid = sanitizePyLiteral(profile.uuid)
        val effectiveHost = sanitizePyLiteral(
            profile.workerHost.trim().ifBlank { ConfigShareCodec.extractHostFromWorkerUrl(profile.workerUrl) }
        )
        val normalizedUrl = sanitizePyLiteral(ConfigShareCodec.normalizeWorkerUrl(profile.workerUrl))
        val effectiveCleanIp = sanitizePyLiteral(profile.cleanIp.trim())
        val effectiveSubproto = sanitizePyLiteral(profile.subprotocol.trim().ifBlank { "chat.v2" })
        val effectiveListenHost = sanitizePyLiteral(profile.listenHost.trim().ifBlank { "127.0.0.1" })

        val workerUrlLine = if (
            normalizedUrl.isBlank() ||
            (effectiveHost.isNotBlank() && (normalizedUrl == "wss://$effectiveHost/" || normalizedUrl == "wss://$effectiveHost"))
        ) {
            "WORKER_URL  = f\"wss://{WORKER_HOST}/\""
        } else {
            "WORKER_URL  = \"$normalizedUrl\""
        }

        var updated = script.ifBlank { DEFAULT_CL2_SCRIPT }
        updated = replaceOrKeep(updated, strVarRegex("UUID"), "UUID        = \"$safeUuid\"")
        updated = replaceOrKeep(updated, strVarRegex("WORKER_HOST"), "WORKER_HOST = \"$effectiveHost\"")
        updated = replaceOrKeep(updated, strVarRegex("WORKER_URL"), workerUrlLine)
        updated = replaceOrKeep(updated, strVarRegex("CLEAN_IP"), "CLEAN_IP    = \"$effectiveCleanIp\"")
        updated = replaceOrKeep(updated, strVarRegex("SUBPROTO"), "SUBPROTO    = \"$effectiveSubproto\"")
        updated = replaceOrKeep(
            updated,
            listenRegex,
            "LISTEN      = (\"$effectiveListenHost\", ${profile.listenPort})"
        )
        updated = replaceOrKeep(updated, intVarRegex("MAX_CHUNK"), "MAX_CHUNK   = ${profile.maxChunk}")
        updated = replaceOrKeep(updated, intVarRegex("BUFFER_MAX"), "BUFFER_MAX  = ${profile.bufferMax}  # ${profile.bufferMax / (1024 * 1024)}MB")
        return updated
    }

    private fun sanitizePyLiteral(value: String): String =
        value.replace("\\", "").replace("\"", "").replace("\n", "").replace("\r", "")

    private fun replaceOrKeep(source: String, regex: Regex, replacement: String): String {
        return if (regex.containsMatchIn(source)) {
            regex.replaceFirst(source, Regex.escapeReplacement(replacement))
        } else {
            source
        }
    }

    fun buildPythonClientSnippet(listenHost: String, listenPort: Int, targetUrl: String): String = """
        import requests

        proxies = {
            "http": "socks5h://$listenHost:$listenPort",
            "https": "socks5h://$listenHost:$listenPort",
        }
        r = requests.get("$targetUrl", proxies=proxies, timeout=15)
        print(r.status_code, r.text)
    """.trimIndent()

    fun buildTermuxEnvSnippet(listenHost: String, listenPort: Int): String = """
        export ALL_PROXY="socks5h://$listenHost:$listenPort"
        export HTTPS_PROXY="http://$listenHost:${listenPort + 1}"
        python3 Cl2_82497904.py
    """.trimIndent()
}
