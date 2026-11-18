package com.signet.core

import org.json.*
import org.junit.Test
import kotlin.test.*
import java.io.File

class AndroidDnsTest {
    private fun config()=JSONObject("""{"inbounds":[{"tag":"device","protocol":"tun"}],"outbounds":[{"tag":"proxy-1","protocol":"freedom"},{"tag":"direct","protocol":"freedom"}],"dns":{"hosts":{"health.signet.test":"198.18.0.1"},"servers":["https://dns.example/dns-query"]},"routing":{"domainStrategy":"IPIfNonMatch","rules":[{"type":"field","domain":["geosite:private"],"outboundTag":"direct"}],"balancers":[{"tag":"auto","selector":["proxy-"]}]}}""")
    @Test fun interceptOnlyDeviceDnsPreservingProviderConfig(){
        val original=config();val raw=original.toString();val adapted=AndroidDns.adapt(original,"1.1.1.1")
        assertEquals(raw,original.toString())
        assertEquals(original.getJSONObject("dns").toString(),adapted.getJSONObject("dns").toString())
        val rules=adapted.getJSONObject("routing").getJSONArray("rules")
        assertEquals(original.getJSONObject("routing").getJSONArray("rules").get(0).toString(),rules.get(1).toString())
        assertEquals("172.19.0.2/32",rules.getJSONObject(0).getJSONArray("ip").getString(0))
        assertEquals("device",rules.getJSONObject(0).getJSONArray("inboundTag").getString(0))
        assertEquals("53",rules.getJSONObject(0).getString("port"))
        assertEquals("tcp,udp",rules.getJSONObject(0).getString("network"))
        assertEquals(original.getJSONObject("routing").getJSONArray("balancers").toString(),adapted.getJSONObject("routing").getJSONArray("balancers").toString())
        for(i in 0..1)assertEquals(original.getJSONArray("outbounds").get(i).toString(),adapted.getJSONArray("outbounds").get(i).toString())
    }
    @Test fun reuseExistingDnsHandlerAndResolveTagCollision(){
        val original=config();original.getJSONArray("outbounds").put(JSONObject("""{"tag":"signet-device-dns","protocol":"freedom"}"""))
        val adapted=AndroidDns.adapt(original,"1.1.1.1")
        assertEquals(4,adapted.getJSONArray("outbounds").length())
        assertNotEquals("signet-device-dns",adapted.getJSONObject("routing").getJSONArray("rules").getJSONObject(0).getString("outboundTag"))
        val dns=JSONObject("""{"tag":"existing-dns","protocol":"dns","settings":{"nonIPQuery":"skip"}}""")
        original.getJSONArray("outbounds").put(dns)
        val reused=AndroidDns.adapt(original,"1.1.1.1")
        assertEquals(4,reused.getJSONArray("outbounds").length())
        assertEquals(dns.toString(),reused.getJSONArray("outbounds").getJSONObject(3).toString())
        assertEquals("existing-dns",reused.getJSONObject("routing").getJSONArray("rules").getJSONObject(0).getString("outboundTag"))
    }
    @Test fun wildcardAutoAndObservatoryRetainExactlyTheirOriginalMembers(){
        val original=config();original.getJSONArray("outbounds").put(JSONObject("""{"tag":"signet","protocol":"freedom"}"""))
        original.getJSONObject("routing").getJSONArray("balancers").getJSONObject(0).put("selector",JSONArray().put(""))
        original.put("observatory",JSONObject().put("subjectSelector",JSONArray().put("s")))
        val adapted=AndroidDns.adapt(original,"1.1.1.1");val out=adapted.getJSONArray("outbounds")
        val tag=out.getJSONObject(out.length()-1).getString("tag")
        val selector=adapted.getJSONObject("routing").getJSONArray("balancers").getJSONObject(0).getJSONArray("selector")
        val values=(0 until selector.length()).map{selector.getString(it)}
        assertFalse(values.any{tag.startsWith(it)})
        assertEquals(setOf("proxy-1","direct","signet"),values.toSet())
        val subject=adapted.getJSONObject("observatory").getJSONArray("subjectSelector").getString(0)
        assertTrue("signet".startsWith(subject));assertFalse(tag.startsWith(subject))
    }
    @Test fun fallbackUsesRoutedTcpDnsIncludingIpv6(){
        val original=config().apply{remove("dns")}
        assertEquals("tcp://[2606:4700:4700::1111]:53",AndroidDns.adapt(original,"2606:4700:4700::1111").getJSONObject("dns").getJSONArray("servers").getString(0))
        assertFailsWith<IllegalArgumentException>{AndroidDns.adapt(original,"dns.invalid")}
    }
    @Test fun emitNativeDnsFixtureUsingTheRealAndroidAdapter(){
        val source=config().apply{
            getJSONObject("routing").put("rules",JSONArray()).remove("balancers")
            getJSONObject("dns").put("servers",JSONArray().put("tcp://127.0.0.1:1"))
        }
        val adapted=AndroidDns.adapt(source,"1.1.1.1")
        val fixture=File("build/dns-bridge-fixture.json");fixture.parentFile.mkdirs();fixture.writeText(adapted.toString(2))
        assertEquals("dns",adapted.getJSONArray("outbounds").getJSONObject(2).getString("protocol"))
    }
}
