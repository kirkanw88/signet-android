package com.signet.core
import org.junit.Test
import kotlin.test.*

class DisplayAndSettingsTest {
    private fun server(name:String="🇩🇪 DE Frankfurt")=Profiles.parseJson("""{"remarks":"$name","outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"example.com","port":443}]}}]}""").single()
    @Test fun displayDoesNotChangeConfigurationOrIdentity(){val s=server();val display=ServerDisplayModel.from(s);assertEquals("Frankfurt",display.title);assertEquals(s.id,display.id);assertEquals("DE",display.country);assertTrue(s.name.contains("🇩🇪"))}
    @Test fun stripsControlAndDirectionOverrides(){assertEquals("safe title",DisplayText.clean("safe\u202e title\u0000"))}
    @Test fun routingExportHasNoProfileSecrets(){val r=AppRouting("include",setOf("ru.yandex.yandexmaps","org.example.app"));assertEquals(r,AppRouting.parse(r.json()));assertFalse(r.json().contains("token"));assertFails{AppRouting.parse(r.json().replace("\"schemaVersion\": 1","\"schemaVersion\": 9"))};assertFails{AppRouting.parse(r.json().replace("org.example.app","../secret"))}}
    @Test fun probesRejectUnsafeSchemesAndResourceAbuse(){assertFails{ProbeSettings(url="http://example.com").validated()};assertFails{ProbeSettings(url="https://u:p@example.com").validated()};assertFails{ProbeSettings(parallelism=100).validated()};assertEquals(ProbeSettings(),ProbeSettings.parse(ProbeSettings().json().toString()))}
    @Test fun autoHasNoInventedTcpEndpoint(){assertNull(Endpoints.from(server().copy(isAuto=true)));assertEquals("example.com",Endpoints.from(server())?.host)}
    @Test fun fullProfileProbePreservesRoutingAndOutbounds(){val original=org.json.JSONObject(server("AUTO").config).put("routing",org.json.JSONObject().put("rules",org.json.JSONArray().put(org.json.JSONObject().put("balancerTag","server"))).put("balancers",org.json.JSONArray().put(org.json.JSONObject().put("tag","server").put("selector",org.json.JSONArray().put("proxy"))))).put("dns",org.json.JSONObject().put("servers",org.json.JSONArray().put("9.9.9.9")));val s=Profiles.parseJson(original.toString()).single();val probe=Profiles.probeRuntime(s,12345,"/private/geo","ephemeral");listOf("outbounds","dns","routing").forEach{assertEquals(original.get(it).toString(),probe.get(it).toString())};assertFalse(probe.getJSONObject("env").has("xray.tun.fd"));assertFalse(s.config.contains("ephemeral"))}
}
