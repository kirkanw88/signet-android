package com.signet.client.vpn

import com.signet.core.TemplateCompatibility
import org.json.JSONArray
import org.json.JSONObject

/**
 * Conservative first-stage runtime adaptation. Original provider JSON is not mutated.
 * Static-only bootstrap deliberately requires numeric carrier/DNS addresses until
 * the SDK's network-aware hostname bootstrap has device acceptance coverage.
 */
internal object RustRuntimeConfig {
    fun prepare(raw: String, fallbackDns: String): JSONObject {
        val config = JSONObject(raw)
        require(TemplateCompatibility.inspect(config).accepted) { "RUST_INVALID_PROFILE" }
        require(!config.has("remnawave")) { "RUST_UNRENDERED_TEMPLATE" }
        val outbounds = config.getJSONArray("outbounds")
        fun requireAddress(address: String) {
            require(TemplateCompatibility.numericAddress(address)) { "RUST_BOOTSTRAP_UNSUPPORTED" }
        }
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.getJSONObject(i)
            val protocol = outbound.optString("protocol")
            require(protocol in setOf("vless", "hysteria", "hysteria2", "freedom", "blackhole", "dns")) {
                "RUST_PROTOCOL_UNSUPPORTED"
            }
            if (protocol in setOf("freedom", "blackhole", "dns")) continue
            val settings = outbound.getJSONObject("settings")
            val vnext = settings.optJSONArray("vnext")
            val servers = settings.optJSONArray("servers")
            val addresses = vnext ?: servers
            if (addresses != null) {
                require(addresses.length() > 0)
                for (j in 0 until addresses.length()) requireAddress(addresses.getJSONObject(j).getString("address"))
            } else requireAddress(settings.getString("address"))
        }
        val dns = config.optJSONObject("dns") ?: JSONObject().also { config.put("dns", it) }
        if (!dns.has("servers")) {
            requireAddress(fallbackDns)
            dns.put("servers", JSONArray().put(fallbackDns))
        }
        val resolvers = dns.getJSONArray("servers")
        require(resolvers.length() > 0) { "RUST_DNS_REQUIRED" }
        for (i in 0 until resolvers.length()) {
            val resolver = resolvers.get(i)
            requireAddress(if (resolver is JSONObject) resolver.getString("address") else resolver.toString())
        }
        val inbound = JSONObject().put("protocol", "tun").put("tag", TemplateCompatibility.inboundTag(config))
        TemplateCompatibility.sourceInbound(config)?.optJSONObject("sniffing")?.let {
            inbound.put("sniffing", JSONObject(it.toString()))
        }
        config.put("inbounds", JSONArray().put(inbound))
        return config
    }
}
