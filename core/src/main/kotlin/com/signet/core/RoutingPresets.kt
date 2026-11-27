package com.signet.core

import org.json.JSONArray
import org.json.JSONObject

object RoutingPresets {
    val choices = linkedMapOf(
        "subscription" to "Из подписки",
        "custom" to "Пустая база (только мои правила)",
        "needed" to "VPN для нужных сайтов",
        "except_ru" to "VPN для всего кроме России",
        "all" to "VPN для всего"
    )

    fun apply(server: Server, preset: String): Server {
        if (preset == "subscription") return server
        require(preset in choices)
        val config = JSONObject(server.config)
        val out = config.getJSONArray("outbounds")
        val routing = config.optJSONObject("routing") ?: JSONObject()
        val rules = routing.optJSONArray("rules") ?: JSONArray()
        val tags = (0 until out.length()).map { out.getJSONObject(it) }
        fun unique(base: String): String {
            var tag = base
            var i = 0
            while (tags.any { it.optString("tag") == tag }) tag = base + (++i)
            return tag
        }
        val direct = tags.firstOrNull { it.optString("protocol") == "freedom" && it.optString("tag").isNotEmpty() }?.optString("tag")
            ?: unique("signet-preset-direct").also { out.put(JSONObject().put("tag",it).put("protocol","freedom")) }
        val proxy = tags.firstOrNull { it.optString("tag")==server.outboundTag && it.optString("protocol") !in setOf("freedom","blackhole","dns","loopback") }
            ?: tags.firstOrNull { it.optString("protocol") !in setOf("freedom","blackhole","dns","loopback") && it.optString("tag").isNotEmpty() }
            ?: error("No proxy outbound")
        val balancers = routing.optJSONArray("balancers")
        val auto = if (server.isAuto && balancers != null && balancers.length()==1) balancers.getJSONObject(0).optString("tag").takeIf{it.isNotEmpty()} else null
        require(!server.isAuto || auto != null) { "Ambiguous automatic profile" }
        fun rule(vpn:Boolean):JSONObject = JSONObject().put("type","field").apply {
            if(vpn && auto!=null) put("balancerTag",auto) else put("outboundTag",if(vpn)proxy.getString("tag")else direct)
        }
        val result = JSONArray()
        // Preserve explicit profile rules, but replace unconditional network fallbacks.
        for(i in 0 until rules.length()){
            val old=rules.getJSONObject(i)
            val selectors=old.keys().asSequence().filter{it !in setOf("type","outboundTag","balancerTag","ruleTag")}.toList()
            if(selectors.isEmpty() || (selectors==listOf("network") && old.optString("network").split(",").map{it.trim()}.toSet()==setOf("tcp","udp"))) continue
            result.put(old)
        }
        when(preset){
            "needed" -> {
                result.put(rule(true).put("domain",JSONArray().put("geosite:ru-blocked")))
                result.put(rule(true).put("ip",JSONArray().put("geoip:ru-blocked")))
            }
            "except_ru" -> {
                result.put(rule(false).put("domain",JSONArray().put("geosite:ru").put("domain:ru").put("domain:рф").put("geosite:private")))
                result.put(rule(false).put("ip",JSONArray().put("geoip:ru").put("geoip:private")))
            }
        }
        result.put(rule(preset=="all" || preset=="except_ru").put("network","tcp,udp"))
        routing.put("rules",result)
        config.put("routing",routing)
        return server.copy(config=config.toString())
    }
}
