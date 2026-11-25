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

    @Test(expected = CoreFailure::class)
    fun rejectsUnverifiedBootstrapDomain() {
        RustRuntimeConfig.prepare(profile("example.invalid"), "1.0.0.1")
    }

    @Test(expected = CoreFailure::class)
    fun rejectsUnresolvedResolver() {
        val config = JSONObject(profile())
        config.getJSONObject("dns").put("servers", org.json.JSONArray().put("https://example.invalid/dns-query"))
        RustRuntimeConfig.prepare(config.toString(), "1.0.0.1")
    }
    @Test fun bootstrapsDomainsWithoutChangingCarrierOrTls() {
        val original = JSONObject(profile("node.example"))
        original.getJSONObject("dns").put("servers", org.json.JSONArray().put("https://dns.example/dns-query"))
        val outbound = original.getJSONArray("outbounds").getJSONObject(0)
        outbound.put("streamSettings", JSONObject().put("security","tls").put("tlsSettings",JSONObject().put("serverName","node.example")))
        val requested = mutableListOf<String>()
        val adapted = RustRuntimeConfig.prepare(original.toString(), "1.1.1.1") { host ->
            requested.add(host); listOf("192.0.2.10")
        }
        assertEquals(setOf("node.example","dns.example"), requested.toSet())
        assertEquals(original.getJSONArray("outbounds").toString(), adapted.getJSONArray("outbounds").toString())
        assertEquals(original.getJSONObject("dns").getJSONArray("servers").toString(),adapted.getJSONObject("dns").getJSONArray("servers").toString())
        assertEquals("192.0.2.10",adapted.getJSONObject("dns").getJSONObject("hosts").getJSONArray("node.example").getString(0))
    }
    @Test fun removesOnlyGoMetadataAndKeepsExplicitIngress() {
        val original=JSONObject(profile())
        original.put("signet",JSONObject().put("tunInboundTag","socks"))
        original.put("stats",JSONObject()).put("metrics",JSONObject()).put("env",JSONObject())
        val adapted=RustRuntimeConfig.prepare(original.toString(),"1.1.1.1")
        for(key in listOf("signet","stats","metrics","env"))assertFalse(adapted.has(key))
        assertEquals("socks",adapted.getJSONArray("inbounds").getJSONObject(0).getString("tag"))
        assertEquals(original.getJSONObject("routing").toString(),adapted.getJSONObject("routing").toString())
    }

    @Test fun rejectsUnsupportedBalancerInsteadOfRemovingIt() {
        val original=JSONObject(profile()).put("burstObservatory",JSONObject())
        try {
            RustRuntimeConfig.prepare(original.toString(),"1.1.1.1",burstSupported=false)
            fail("Expected incompatible configuration")
        }catch(e:CoreFailure){assertEquals("RUST_BURST_OBSERVATORY_UNSUPPORTED",e.code)}
    }
    @Test fun preservesBurstAndBalancerForCandidate() {
        val original = JSONObject(profile())
        val burst = JSONObject("""{"subjectSelector":["proxy"],"pingConfig":{"destination":"https://example.invalid/generate_204","interval":"10s","sampling":2}}""")
        original.put("burstObservatory",burst)
        for (strategy in listOf("leastPing","leastLoad","random","roundRobin")) {
            val routing=original.getJSONObject("routing")
            routing.put("balancers",org.json.JSONArray().put(JSONObject().put("tag","auto").put("selector",org.json.JSONArray().put("proxy")).put("fallbackTag","direct").put("strategy",JSONObject().put("type",strategy))))
            routing.put("rules",org.json.JSONArray().put(JSONObject().put("type","field").put("network","tcp,udp").put("balancerTag","auto")))
            val adapted=RustRuntimeConfig.prepare(original.toString(),"1.1.1.1",burstSupported=true)
            assertEquals(burst.toString(),adapted.getJSONObject("burstObservatory").toString())
            assertEquals(routing.toString(),adapted.getJSONObject("routing").toString())
        }
    }
}
