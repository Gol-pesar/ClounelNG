package com.example.proxy

import com.example.model.ProxyProfile
import com.example.model.TunnelMode
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Encodes and decodes proxy configurations for Clipboard and QR Code import/export.
 * Supports amir:// URIs, JSON objects, socks5:// URIs, and Cl2 Python scripts.
 */
object ConfigShareCodec {

    fun encodeToUri(profile: ProxyProfile): String {
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        return if (profile.mode == TunnelMode.CL2_WSS) {
            buildString {
                append("amir://cl2?")
                append("uuid=").append(enc(profile.uuid))
                append("&url=").append(enc(profile.workerUrl))
                append("&cleanIp=").append(enc(profile.cleanIp))
                append("&subproto=").append(enc(profile.subprotocol))
                append("&name=").append(enc(profile.name))
            }
        } else {
            buildString {
                append("socks5://")
                if (profile.upstreamUsername.isNotBlank()) {
                    append(enc(profile.upstreamUsername))
                    if (profile.upstreamPassword.isNotBlank()) {
                        append(":").append(enc(profile.upstreamPassword))
                    }
                    append("@")
                }
                append(profile.upstreamHost).append(":").append(profile.upstreamPort)
                if (profile.name.isNotBlank()) {
                    append("#").append(enc(profile.name))
                }
            }
        }
    }

    fun decodeFromText(raw: String): Result<ProxyProfile> = runCatching {
        val text = raw.trim()
        require(text.isNotEmpty()) { "Empty config text" }

        when {
            text.startsWith("amir://", ignoreCase = true) ||
                text.startsWith("cl2://", ignoreCase = true) -> decodeAmirUri(text)
            text.startsWith("socks5://", ignoreCase = true) ||
                text.startsWith("socks://", ignoreCase = true) -> decodeSocks5Uri(text)
            text.startsWith("{") -> decodeJson(text)
            text.contains("WORKER_HOST") || text.contains("UUID") -> {
                val parsed = PythonScriptBridge.parseScript(text)
                require(!parsed.uuid.isNullOrBlank() || !parsed.workerHost.isNullOrBlank()) {
                    "No valid Cl2 fields found in Python snippet"
                }
                PythonScriptBridge.applyConfigToProfile(
                    ProxyProfile(name = "Imported Cl2 Config"),
                    parsed
                )
            }
            else -> throw IllegalArgumentException("Unrecognized config format")
        }
    }

    fun extractHostFromWorkerUrl(workerUrl: String): String {
        val trimmed = workerUrl.trim()
        if (trimmed.isEmpty()) return ""
        val normalized = if ("://" in trimmed) trimmed else "wss://$trimmed"
        return runCatching {
            URI(normalized).host.orEmpty()
        }.getOrDefault("").ifBlank {
            trimmed.substringAfter("://").substringBefore("/").substringBefore(":")
        }
    }

    private fun decodeAmirUri(rawUri: String): ProxyProfile {
        val uri = URI(rawUri)
        val params = parseQuery(uri.rawQuery.orEmpty())
        val workerUrl = params["url"]?.takeIf { it.isNotBlank() }
            ?: params["workerUrl"]?.takeIf { it.isNotBlank() }
            ?: params["worker"]?.let { if ("://" in it) it else "wss://$it/" }
            ?: ""
        val workerHost = params["host"]?.takeIf { it.isNotBlank() }
            ?: extractHostFromWorkerUrl(workerUrl)
        val uuid = params["uuid"].orEmpty()
        val cleanIp = params["cleanIp"] ?: params["clean_ip"] ?: ""
        val subproto = params["subproto"]?.takeIf { it.isNotBlank() } ?: "chat.v2"
        val name = params["name"]?.takeIf { it.isNotBlank() }
            ?: uri.fragment?.let { URLDecoder.decode(it, "UTF-8") }?.takeIf { it.isNotBlank() }
            ?: workerHost.ifBlank { "AMIR Config" }

        require(uuid.isNotBlank() && (workerUrl.isNotBlank() || workerHost.isNotBlank())) {
            "Missing UUID or Worker URL"
        }

        val finalUrl = workerUrl.ifBlank { "wss://$workerHost/" }
        return ProxyProfile(
            name = name,
            mode = TunnelMode.CL2_WSS,
            uuid = uuid.trim(),
            workerHost = workerHost.trim(),
            workerUrl = finalUrl.trim(),
            cleanIp = cleanIp.trim(),
            subprotocol = subproto.trim()
        )
    }

    private fun decodeSocks5Uri(rawUri: String): ProxyProfile {
        val uri = URI(rawUri)
        val host = uri.host ?: throw IllegalArgumentException("Missing SOCKS5 host")
        val port = if (uri.port > 0) uri.port else 1080
        val userInfo = uri.userInfo.orEmpty()
        val user = userInfo.substringBefore(":", "")
        val pass = if (":" in userInfo) userInfo.substringAfter(":") else ""
        val name = uri.fragment?.let { URLDecoder.decode(it, "UTF-8") }?.takeIf { it.isNotBlank() }
            ?: "$host:$port"
        return ProxyProfile(
            name = name,
            mode = TunnelMode.DIRECT_SOCKS5,
            upstreamHost = host,
            upstreamPort = port,
            upstreamUsername = user,
            upstreamPassword = pass
        )
    }

    private fun decodeJson(rawJson: String): ProxyProfile {
        val obj = JSONObject(rawJson)
        val uuid = obj.optString("uuid", "").trim()
        val workerUrl = obj.optString("workerUrl", obj.optString("worker_url", "")).trim()
        val workerHost = obj.optString("workerHost", obj.optString("worker_host", ""))
            .trim()
            .ifBlank { extractHostFromWorkerUrl(workerUrl) }
        val cleanIp = obj.optString("cleanIp", obj.optString("clean_ip", "")).trim()
        val name = obj.optString("name", "").trim().ifBlank { workerHost.ifBlank { "AMIR Config" } }
        require(uuid.isNotBlank() && (workerUrl.isNotBlank() || workerHost.isNotBlank())) {
            "Invalid JSON config: missing uuid or workerUrl"
        }
        return ProxyProfile(
            name = name,
            mode = TunnelMode.CL2_WSS,
            uuid = uuid,
            workerHost = workerHost,
            workerUrl = workerUrl.ifBlank { "wss://$workerHost/" },
            cleanIp = cleanIp,
            subprotocol = obj.optString("subprotocol", "chat.v2").ifBlank { "chat.v2" }
        )
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else {
                val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                k to v
            }
        }.toMap()
    }
}
