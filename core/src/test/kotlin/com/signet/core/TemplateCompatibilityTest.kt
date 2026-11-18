package com.signet.core
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Test
import kotlin.test.*

class TemplateCompatibilityTest {
    private fun config()=JSONObject("""{"remarks":"AUTO","inbounds":[{"tag":"dns-in","protocol":"dokodemo-door"},{"tag":"socks-in","protocol":"socks"}],"outbounds":[{"tag":"proxy-a","protocol":"freedom"},{"tag":"proxy-b","protocol":"blackhole"}],"dns":{"servers":[{"address":"9.9.9.9"}]},"routing":{"domainStrategy":"IPIfNonMatch","balancers":[{"tag":"auto","selector":["proxy-"]}],"rules":[{"type":"field","domain":["example.com"],"balancerTag":"auto"},{"type":"field","network":"tcp,udp","outboundTag":"proxy-a"}]}}""")
    @Test fun remnawaveRenderedBalancerTemplateRemainsIntact(){val c=config();val s=Profiles.parseJson(c.toString()).single();val before=c.toString();val r=Profiles.runtime(s,42,12345,"/geo","1.1.1.1");val p=Profiles.probeRuntime(s,12347,"/geo","ephemeral");listOf("routing","dns","outbounds").forEach{assertEquals(c.get(it).toString(),r.get(it).toString());assertEquals(c.get(it).toString(),p.get(it).toString())};assertEquals(before,c.toString());assertEquals("socks-in",r.getJSONArray("inbounds").getJSONObject(0).getString("tag"));assertTrue(TemplateCompatibility.inspect(c).accepted)}
    @Test fun unrenderedPanelTemplateMustNotBecomeDirectVpn(){val c=config().put("remnawave",JSONObject().put("injectHosts",JSONArray()));assertFalse(TemplateCompatibility.inspect(c).accepted)}
    @Test fun missingBalancerFailsAndInboundScopeWarns(){val c=config();c.getJSONObject("routing").getJSONArray("rules").getJSONObject(0).put("balancerTag","missing").put("inboundTag",JSONArray().put("http-in"));val r=TemplateCompatibility.inspect(c);assertFalse(r.accepted);assertTrue(r.issues.any{it.code=="INBOUND_SCOPE"})}
    @Test fun numericDnsDoesNotResolveHostnames(){assertFalse(TemplateCompatibility.numericAddress("deadbeef"));assertFalse(TemplateCompatibility.numericAddress("999.1.1.1"));assertTrue(TemplateCompatibility.numericAddress("2606:4700:4700::1111"));assertEquals("9.9.9.9",TemplateCompatibility.deviceDns(Profiles.parseJson(config().toString()).single(),"1.1.1.1"))}
    @Test fun metadataOnlyAcceptsSafeLinksAndBoundedText(){val m=SubscriptionMetadata.headers { mapOf("announce" to "base64:SGVsbG8=","support-url" to "javascript:alert(1)","profile-web-page-url" to "https://user:secret@example.com","profile-update-interval" to "1")[it] };assertEquals("Hello",m.announcement);assertNull(m.support);assertNull(m.webPage);assertEquals(6,m.updateHours)}
    @Test fun inboundSniffingSurvivesBothAdapters(){val c=config();val sniffing=JSONObject().put("enabled",true).put("routeOnly",true).put("destOverride",JSONArray().put("http").put("tls"));c.getJSONArray("inbounds").getJSONObject(1).put("sniffing",sniffing);val s=Profiles.parseJson(c.toString()).single();assertEquals(sniffing.toString(),Profiles.runtime(s,1,1234,"/geo","1.1.1.1").getJSONArray("inbounds").getJSONObject(0).getJSONObject("sniffing").toString());assertEquals(sniffing.toString(),Profiles.probeRuntime(s,1234,"/geo","test").getJSONArray("inbounds").getJSONObject(0).getJSONObject("sniffing").toString())}
    @Test fun catchAllWarningDoesNotRewriteThePolicy(){val c=config();val before=c.toString();assertTrue(TemplateCompatibility.inspect(c).issues.any{it.code=="IPIFNONMATCH_CATCHALL"});assertEquals(before,c.toString())}
    @Test fun danglingOutboundReferenceFailsBeforeImport(){val c=config();c.getJSONObject("routing").getJSONArray("rules").getJSONObject(1).put("outboundTag","removed-host");assertFalse(TemplateCompatibility.inspect(c).accepted)}
    @Test fun upstreamDefaultRenderedTemplatePreservesSniffingAndBittorrentPolicy(){
        val c=JSONObject(javaClass.getResource("/remnawave-default-rendered.json")!!.readText())
        assertTrue(TemplateCompatibility.inspect(c).accepted)
        val s=Profiles.parseJson(c.toString()).single();val runtime=Profiles.runtime(s,42,12345,"/geo","9.9.9.9")
        listOf("routing","outbounds","dns").forEach{assertEquals(c.get(it).toString(),runtime.get(it).toString())}
        val inbound=runtime.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("socks",inbound.getString("tag"));assertTrue(inbound.getJSONObject("sniffing").getBoolean("enabled"))
        assertFalse(inbound.getJSONObject("sniffing").getBoolean("routeOnly"));assertEquals(1,runtime.getJSONArray("inbounds").length())
    }
}
