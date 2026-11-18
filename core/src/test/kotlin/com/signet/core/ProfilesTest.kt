package com.signet.core

import org.json.JSONObject
import org.junit.Test
import kotlin.test.*

class ProfilesTest {
    private val json="""{"remarks":"🇩🇪 DE Frankfurt","outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"example.com","port":443,"users":[{"id":"00000000-0000-4000-8000-000000000001"}]}]},"streamSettings":{"network":"xhttp","xhttpSettings":{"path":"/secret","extra":{"opaque":true}}}}],"routing":{"domainStrategy":"IPIfNonMatch","rules":[{"type":"field","domain":["example.org"],"outboundTag":"proxy"}]},"dns":{"servers":["9.9.9.9"]},"futureField":{"keep":true}}"""
    @Test fun fullJsonPreservesOpaqueTransportAndRouting(){
        val s=Profiles.parseJson(json).single();val r=Profiles.runtime(s,42,12345,"/data/geo","1.1.1.1")
        assertTrue(r.getJSONObject("futureField").getBoolean("keep"))
        assertEquals("9.9.9.9",r.getJSONObject("dns").getJSONArray("servers").getString(0))
        assertEquals("IPIfNonMatch",r.getJSONObject("routing").getString("domainStrategy"))
        assertTrue(r.getJSONArray("outbounds").getJSONObject(0).getJSONObject("streamSettings").getJSONObject("xhttpSettings").getJSONObject("extra").getBoolean("opaque"))
        assertEquals("42",r.getJSONObject("env").getString("xray.tun.fd"))
        assertEquals(1,r.getJSONArray("inbounds").length())
    }
    @Test fun geographyDoesNotInventCity(){val s=Profiles.parseJson(json).single();assertEquals("DE",s.countryCode);assertNull(s.city);assertNull(s.latitude)}
    @Test fun explicitMetadataOverridesFlag(){val o=JSONObject(json).put("signet",JSONObject().put("countryCode","FI").put("city","Helsinki").put("latitude",60.17).put("longitude",24.94));val s=Profiles.parseJson(o.toString()).single();assertEquals("FI",s.countryCode);assertEquals("Helsinki",s.city)}
    @Test fun malformedConfigRejected(){assertFails{Profiles.parseJson("{}")} ;assertFails{Profiles.parseJson("[]")}}
    @Test fun subscriptionBase64AndBom(){val b=java.util.Base64.getEncoder().encodeToString("vless://example".toByteArray());assertEquals("vless://example",Profiles.decode(b));assertEquals("{}",Profiles.decode("\uFEFF{}"))}
    @Test fun headerParsingIgnoresInvalidValues(){assertEquals(mapOf("upload" to 12L,"total" to 100L),Profiles.userInfo("upload=12; broken; total=100; expire=oops"))}
    @Test fun autoNeverSelectsFastestOrFirstServer(){val s=Profiles.parseJson(json).single();val supplied=s.copy(id="auto",isAuto=true,latencyMs=900,available=false);assertEquals("auto",Profiles.auto(listOf(s.copy(latencyMs=1,available=true),supplied))?.id);assertNull(Profiles.auto(listOf(s)))}
    @Test fun autoPreservesOutboundsDnsRoutingAndBalancers(){val o=JSONObject(json).put("remarks","AUTO").put("routing",JSONObject().put("rules",org.json.JSONArray().put(JSONObject().put("balancerTag","server-balancer"))).put("balancers",org.json.JSONArray().put(JSONObject().put("tag","server-balancer").put("selector",org.json.JSONArray().put("proxy")))));o.getJSONArray("outbounds").getJSONObject(0).remove("tag");val s=Profiles.parseJson(o.toString()).single();assertTrue(s.isAuto);val r=Profiles.runtime(s,1,12345,"/tmp","1.1.1.1");listOf("dns","routing","outbounds").forEach{assertEquals(o.get(it).toString(),r.get(it).toString())};assertNull(s.countryCode)}
    @Test fun autoRequiresExplicitDeliveredElement(){assertFalse(Profiles.parseJson(JSONObject(json).put("remarks","Automatically fast").toString()).single().isAuto);assertTrue(Profiles.parseJson(JSONObject(json).put("signet",JSONObject().put("selectionMode","auto").put("profileId","auto-v1")).toString()).single().isAuto)}
    @Test fun sphereCoordinatesAndAntimeridian(){assertContentEquals(floatArrayOf(0f,0f,1f),Geography.point(0.0,0.0));assertTrue(kotlin.math.abs(Geography.point(90.0,0.0)[1]-1)<.0001);assertContentEquals(Geography.point(0.0,-180.0).map{if(kotlin.math.abs(it)<.0001)0f else it}.toFloatArray(),Geography.point(0.0,180.0).map{if(kotlin.math.abs(it)<.0001)0f else it}.toFloatArray());assertEquals(181f,Geography.approach(179f,-179f,1f));assertFails{Geography.point(91.0,0.0)}}
    @Test fun runtimeNeverMutatesOriginal(){val s=Profiles.parseJson(json).single();val before=s.config;Profiles.runtime(s,42,12345,"/tmp","1.1.1.1");assertEquals(before,s.config)}
}
