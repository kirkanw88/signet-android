package com.signet.client.vpn

import com.signet.core.TemplateCompatibility
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale

/** Bootstrap carrier names on the physical network, never replace TLS/SNI or provider routing. */
internal object RustRuntimeConfig {
    fun prepare(raw: String, fallbackDns: String, burstSupported: Boolean = com.signet.client.BuildConfig.RUST_BURST_SUPPORTED, resolve: (String) -> List<String> = { throw CoreFailure("RUST_BOOTSTRAP_REQUIRED") }): JSONObject {
        val config = JSONObject(raw)
        if (!TemplateCompatibility.inspect(config).accepted) throw CoreFailure("RUST_INVALID_PROFILE")
        if (config.has("remnawave")) throw CoreFailure("RUST_UNRENDERED_TEMPLATE")
        val inboundTag = TemplateCompatibility.inboundTag(config)
        val sourceSniffing = TemplateCompatibility.sourceInbound(config)?.optJSONObject("sniffing")
        // Host metadata and Go-only instrumentation are not Rust runtime fields.
        // Do not remove routing, DNS, security or unsupported balancing features.
        for (key in listOf("signet", "stats", "metrics", "env")) config.remove(key)
        val supported = setOf("inbounds", "outbounds", "routing", "observatory", "dns", "policy", "log", "reverse", "remarks", "meta")
        val allowed = if (burstSupported) supported + "burstObservatory" else supported
        val unsupported = config.keys().asSequence().filter { it !in allowed }.toList()
        if (unsupported.isNotEmpty()) {
            val code = if ("burstObservatory" in unsupported) "RUST_BURST_OBSERVATORY_UNSUPPORTED"
                else if ("api" in unsupported) "RUST_API_UNSUPPORTED" else "RUST_TOP_LEVEL_UNSUPPORTED"
            throw CoreFailure(code)
        }
        normalizeReality(config.getJSONArray("outbounds"))
        val dns = config.optJSONObject("dns") ?: JSONObject().also { config.put("dns", it) }
        if (!dns.has("servers")) {
            if (!TemplateCompatibility.numericAddress(fallbackDns)) throw CoreFailure("RUST_DNS_REQUIRED")
            dns.put("servers", JSONArray().put(fallbackDns))
        }
        val hosts = dns.optJSONObject("hosts") ?: JSONObject().also { dns.put("hosts", it) }
        val visiting = mutableSetOf<String>()
        val done = mutableSetOf<String>()
        fun bootstrap(rawHost: String) {
            val host = rawHost.removeSurrounding("[", "]").trim().trimEnd('.').lowercase(Locale.ROOT)
            if (TemplateCompatibility.numericAddress(host)) return
            if (!host.matches(Regex("[a-z0-9_.-]{1,253}"))) throw CoreFailure("RUST_BOOTSTRAP_HOST")
            if (host in done) return
            if (done.size + visiting.size >= 64 || !visiting.add(host)) throw CoreFailure("RUST_BOOTSTRAP_LIMIT")
            try {
                val existing = hosts.keys().asSequence().firstOrNull {
                    it.equals(host, true) || it.equals("full:$host", true)
                }
                if (existing != null) {
                    val target = hosts.get(existing)
                    if (target is String) bootstrap(target)
                    else if (target !is JSONArray || target.length() == 0 ||
                        (0 until target.length()).any { !TemplateCompatibility.numericAddress(target.getString(it)) })
                        throw CoreFailure("RUST_DNS_HOSTS")
                } else {
                    val addresses = try { resolve(host).distinct().filter(TemplateCompatibility::numericAddress).take(8) }
                    catch (e: CoreFailure) { throw e }
                    catch (e: Exception) { throw CoreFailure("RUST_BOOTSTRAP_DNS") }
                    if (addresses.isEmpty()) throw CoreFailure("RUST_BOOTSTRAP_DNS")
                    hosts.put(host, JSONArray(addresses))
                }
                done.add(host)
            } finally { visiting.remove(host) }
        }
        val outbounds = config.getJSONArray("outbounds")
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.getJSONObject(i)
            val protocol = outbound.optString("protocol")
            if (protocol in setOf("freedom", "blackhole", "dns", "loopback")) continue
            if (protocol !in setOf("vless", "vmess", "hysteria", "trojan", "shadowsocks", "socks", "http")) throw CoreFailure("RUST_PROTOCOL_UNSUPPORTED")
            val settings = outbound.getJSONObject("settings")
            if (!settings.isNull("address")) bootstrap(settings.getString("address"))
            else {
                val addresses = settings.optJSONArray("vnext") ?: settings.optJSONArray("servers") ?: throw CoreFailure("RUST_ADDRESS_REQUIRED")
                if (addresses.length() == 0) throw CoreFailure("RUST_ADDRESS_REQUIRED")
                for (j in 0 until addresses.length()) bootstrap(addresses.getJSONObject(j).getString("address"))
            }
        }
        val resolvers = dns.getJSONArray("servers")
        if (resolvers.length() == 0) throw CoreFailure("RUST_DNS_REQUIRED")
        for (i in 0 until resolvers.length()) {
            val resolver = resolvers.get(i)
            val address = if (resolver is JSONObject) resolver.getString("address") else resolver.toString()
            if (address.contains("://")) {
                val uri = try { URI(address) } catch (e: Exception) { throw CoreFailure("RUST_DNS_URL") }
                if (uri.scheme !in setOf("tcp","tcp+local","tls","https","https+local","quic+local")) throw CoreFailure("RUST_DNS_SCHEME")
                bootstrap(uri.host ?: throw CoreFailure("RUST_DNS_URL"))
            } else if (TemplateCompatibility.numericAddress(address)) continue
            else if (address == "localhost" || address == "fakedns") throw CoreFailure("RUST_DNS_SPECIAL")
            else if (address.startsWith("[") && address.contains("]:")) bootstrap(address.substringBefore("]:").removePrefix("["))
            else bootstrap(address.substringBefore(':'))
        }
        val inbound = JSONObject().put("protocol", "tun").put("tag", inboundTag)
        sourceSniffing?.let {
            inbound.put("sniffing", JSONObject(it.toString()))
        }
        config.put("inbounds", JSONArray().put(inbound))
        return config
    }
    /** Translate the Go alias in the runtime copy only; never modify saved profiles. */
    private fun normalizeReality(outbounds: JSONArray) {
        fun normalize(stream: JSONObject) {
            if (stream.optString("security") == "reality") {
                val reality = stream.optJSONObject("realitySettings")
                    ?: throw CoreFailure("RUST_REALITY_SETTINGS_INVALID_CONFIG")
                val password = reality.opt("password") as? String
                val publicKey = reality.opt("publicKey") as? String
                if (!password.isNullOrEmpty() && !publicKey.isNullOrEmpty() && password != publicKey)
                    throw CoreFailure("RUST_REALITY_KEY_CONFLICT_INVALID_CONFIG")
                val key = password?.takeIf { it.isNotEmpty() } ?: publicKey
                if (key == null || !key.matches(Regex("[A-Za-z0-9_-]{43}")) ||
                    runCatching { java.util.Base64.getUrlDecoder().decode(key).size }.getOrDefault(0) != 32)
                    throw CoreFailure("RUST_REALITY_PUBLIC_KEY_INVALID_CONFIG")
                reality.put("publicKey", key)
                reality.remove("password")
                val sid = reality.opt("shortId") as? String
                if (sid == null || !sid.matches(Regex("(?:[0-9a-fA-F]{2}){0,8}")))
                    throw CoreFailure("RUST_REALITY_SHORT_ID_INVALID_CONFIG")
                if ((reality.opt("serverName") as? String).isNullOrEmpty())
                    throw CoreFailure("RUST_REALITY_SERVER_NAME_INVALID_CONFIG")
                val spider = reality.opt("spiderX") as? String
                if (!spider.isNullOrEmpty() && !spider.startsWith("/"))
                    throw CoreFailure("RUST_REALITY_SPIDER_INVALID_CONFIG")
            }
            // XHTTP can have an independent download transport.
            for (name in listOf("xhttpSettings", "splithttpSettings")) {
                val settings = stream.optJSONObject(name) ?: continue
                settings.optJSONObject("downloadSettings")?.let(::normalize)
                settings.optJSONObject("extra")?.optJSONObject("downloadSettings")?.let(::normalize)
            }
        }
        for (i in 0 until outbounds.length())
            outbounds.getJSONObject(i).optJSONObject("streamSettings")?.let(::normalize)
    }
}
