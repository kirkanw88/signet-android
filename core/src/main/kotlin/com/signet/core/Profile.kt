package com.signet.core

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

data class Server(
    val id: String, val name: String, val config: String, val outboundTag: String,
    val countryCode: String? = null, val city: String? = null,
    val latitude: Double? = null, val longitude: Double? = null,
    val latencyMs: Long? = null, val checkedAt: Long = 0, val available: Boolean? = null,
    val favorite: Boolean = false, val successes: Int = 0, val originalConfig: String = config
) {
    val countryName: String get() = countryCode?.let { Locale("ru", it).displayCountry } ?: "Локация не указана"
    val flag: String get() = countryCode?.takeIf { it.length == 2 }?.uppercase()?.map { String(Character.toChars(it.code - 65 + 0x1F1E6)) }?.joinToString("") ?: "◎"
}

object Profiles {
    fun id(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(12).joinToString("") { "%02x".format(it) }

    /** Full configurations remain opaque: do not project them through the share-link converter. */
    fun parseJson(text: String): List<Server> {
        val trimmed = text.trim()
        val items = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONArray().put(JSONObject(trimmed))
        require(items.length() in 1..500) { "Некорректное количество конфигураций" }
        return (0 until items.length()).map { index ->
            val config = items.getJSONObject(index)
            val outbounds = config.optJSONArray("outbounds") ?: error("Нет outbounds")
            require(outbounds.length() > 0) { "Нет исходящих подключений" }
            val first = (0 until outbounds.length()).map { outbounds.getJSONObject(it) }.firstOrNull {
                it.optString("protocol") !in listOf("freedom", "blackhole", "dns", "loopback")
            } ?: outbounds.getJSONObject(0)
            val tag = first.optString("tag", "")
            val name = config.optString("remarks").ifBlank { first.optString("tag").ifBlank { "Профиль ${index + 1}" } }
            val meta = config.optJSONObject("signet") ?: first.optJSONObject("signet")
            val country = meta?.optString("countryCode")?.takeIf { it.matches(Regex("[A-Za-z]{2}")) }?.uppercase()
                ?: flagCountry(name)
            Server(id(config.toString()), name, config.toString(), tag, country,
                meta?.optString("city")?.takeIf { it.isNotBlank() },
                meta?.let { if (it.has("latitude")) it.getDouble("latitude") else null },
                meta?.let { if (it.has("longitude")) it.getDouble("longitude") else null })
        }
    }

    fun flagCountry(name: String): String? {
        val codes = name.codePoints().toArray()
        for (i in 0 until codes.size - 1) if (codes[i] in 0x1F1E6..0x1F1FF && codes[i + 1] in 0x1F1E6..0x1F1FF)
            return "${(codes[i] - 0x1F1E6 + 65).toChar()}${(codes[i + 1] - 0x1F1E6 + 65).toChar()}"
        return Regex("(?:^|[ |_/\\-])([A-Z]{2})(?:$|[ |_/\\-])").find(name)?.groupValues?.get(1)?.takeIf { it in Locale.getISOCountries() }
    }

    fun decode(text: String): String {
        val raw = text.trim().removePrefix("\uFEFF")
        if (raw.startsWith("{") || raw.startsWith("[") || raw.contains("://")) return raw
        return runCatching { String(Base64.getMimeDecoder().decode(raw), Charsets.UTF_8).trim() }.getOrDefault(raw)
    }

    fun userInfo(header: String?): Map<String, Long> = header.orEmpty().split(';').mapNotNull {
        val p = it.trim().split('=', limit = 2)
        if (p.size == 2) p[1].toLongOrNull()?.let { value -> p[0] to value } else null
    }.toMap()

    fun best(servers: List<Server>, now: Long): Server? = servers.filter { it.available == true && it.latencyMs != null && now - it.checkedAt in 0..300_000 }
        .sortedWith(compareBy<Server> { it.latencyMs!! + (100 / (it.successes + 1)) }.thenBy { it.id }).firstOrNull()

    fun runtime(server: Server, fd: Int, metricsPort: Int, socksPort: Int, assets: String, dns: String): JSONObject {
        val config = JSONObject(server.config)
        val env = config.optJSONObject("env") ?: JSONObject()
        if(server.outboundTag.isEmpty()) config.getJSONArray("outbounds").getJSONObject(0).put("tag","signet-default")
        env.put("xray.tun.fd", fd.toString()).put("xray.location.asset", assets)
        config.put("env", env)
        // Imported external listeners are not exposed. Routing, DNS, transport and opaque fields remain untouched.
        val inbounds = JSONArray()
        val originalTag = config.optJSONArray("inbounds")?.optJSONObject(0)?.optString("tag")?.takeIf { it.isNotBlank() } ?: "signet-tun"
        inbounds.put(JSONObject().put("tag", originalTag).put("protocol", "tun").put("settings", JSONObject().put("name", "signet").put("MTU", 1500)))
        inbounds.put(JSONObject().put("tag", "signet-check").put("listen", "127.0.0.1").put("port", socksPort).put("protocol", "socks").put("settings", JSONObject().put("udp", true)))
        config.put("inbounds", inbounds)
        if (!config.has("dns")) config.put("dns", JSONObject().put("servers", JSONArray().put(dns)))
        config.put("log", JSONObject().put("loglevel", "none"))
        config.put("metrics", JSONObject().put("tag", "signet-metrics").put("listen", "127.0.0.1:$metricsPort"))
        val stats = config.optJSONObject("stats") ?: JSONObject(); config.put("stats", stats)
        val policy = config.optJSONObject("policy") ?: JSONObject()
        val system = policy.optJSONObject("system") ?: JSONObject()
        system.put("statsOutboundUplink", true).put("statsOutboundDownlink", true)
        policy.put("system", system); config.put("policy", policy)
        return config
    }
}
