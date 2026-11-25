package com.signet.client.vpn

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RustRuntimeConfigTest {
    private fun profile(address: String = "192.0.2.1") = """
        {"dns":{"servers":["1.1.1.1"]},
         "inbounds":[{"tag":"socks","protocol":"socks","listen":"127.0.0.1","port":10808}],
         "outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"$address","port":443,"users":[{"id":"00000000-0000-4000-8000-000000000000","encryption":"none"}]}]}},
         {"tag":"direct","protocol":"freedom"}],
         "routing":{"rules":[{"type":"field","network":"tcp,udp","outboundTag":"proxy"}]}}
    """.trimIndent()

    @Test fun preservesRoutingAndOutbounds() {
        val raw = profile()
        val original = JSONObject(raw)
        val adapted = RustRuntimeConfig.prepare(raw, "1.0.0.1")
        assertEquals(original.getJSONArray("outbounds").toString(), adapted.getJSONArray("outbounds").toString())
        assertEquals(original.getJSONObject("routing").toString(), adapted.getJSONObject("routing").toString())
        assertEquals("tun", adapted.getJSONArray("inbounds").getJSONObject(0).getString("protocol"))
        assertEquals("socks", adapted.getJSONArray("inbounds").getJSONObject(0).getString("tag"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnverifiedBootstrapDomain() {
        RustRuntimeConfig.prepare(profile("example.invalid"), "1.0.0.1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonNumericResolver() {
        val config = JSONObject(profile())
        config.getJSONObject("dns").put("servers", org.json.JSONArray().put("https://example.invalid/dns-query"))
        RustRuntimeConfig.prepare(config.toString(), "1.0.0.1")
    }
}
