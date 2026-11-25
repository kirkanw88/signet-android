package com.signet.client.vpn

import com.signet.core.TemplateCompatibility
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale

/** Bootstrap carrier names on the physical network, never replace TLS/SNI or provider routing. */
internal object RustRuntimeConfig {
    fun prepare(raw: String, fallbackDns: String, resolve: (String) -> List<String> = { throw CoreFailure("RUST_BOOTSTRAP_REQUIRED") }): JSONObject {
        val config = JSONObject(raw)
        if (!TemplateCompatibility.inspect(config).accepted) throw CoreFailure("RUST_INVALID_PROFILE")
        if (config.has("remnawave")) throw CoreFailure("RUST_UNRENDERED_TEMPLATE")
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
        val inbound = JSONObject().put("protocol", "tun").put("tag", TemplateCompatibility.inboundTag(config))
        TemplateCompatibility.sourceInbound(config)?.optJSONObject("sniffing")?.let {
            inbound.put("sniffing", JSONObject(it.toString()))
        }
        config.put("inbounds", JSONArray().put(inbound))
        return config
    }
}
