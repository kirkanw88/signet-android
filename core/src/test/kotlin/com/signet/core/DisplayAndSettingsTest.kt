package com.signet.core
import org.junit.Test
import kotlin.test.*

class DisplayAndSettingsTest {
    @Test fun duplicateCountryServersKeepCitiesAndReceiveOrdinalsOnlyWhenNeeded(){
        val first=server("DE Frankfurt").copy(id="first")
        val second=first.copy(id="second")
        val gaming=server("DE Gaming").copy(id="game")
        val labels=ServerDisplayModel.all(listOf(first,second,gaming))
        assertTrue(labels.getValue("first").title.endsWith("№1"))
        assertTrue(labels.getValue("second").title.endsWith("№2"))
        assertEquals("Германия · Игровой",labels.getValue("game").title)
        assertFalse(labels.getValue("game").title.contains("№"))
        assertEquals(listOf("first","second","game"),labels.keys.toList())
    }
    @Test fun remnawaveAnnouncementPreservesParagraphsAndIndentation(){
        val text="Signet\r\n\r\n  Gaming\n    Fast &amp; stable"
        assertEquals("Signet\n\n  Gaming\n    Fast & stable",SubscriptionMetadata.text(text))
        val encoded="base64:"+java.util.Base64.getEncoder().encodeToString(text.toByteArray())
        assertEquals(SubscriptionMetadata.text(text),SubscriptionMetadata.text(encoded))
        assertEquals("Title\n  Details\n\nEnd",SubscriptionMetadata.text("Title<br>  Details</p>End"))
        assertEquals("ab",SubscriptionMetadata.text("a\u202eb\u0000"))
    }
    private fun server(name:String="🇩🇪 DE Frankfurt")=Profiles.parseJson("""{"remarks":"$name","outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"example.com","port":443}]}}]}""").single()
    @Test fun displayDoesNotChangeConfigurationOrIdentity(){val s=server();val display=ServerDisplayModel.from(s);assertEquals("Германия",display.title);assertEquals(s.id,display.id);assertEquals("DE",display.country);assertTrue(s.name.contains("🇩🇪"))}
    @Test fun countryLabelsAndDuplicatePrefixesDoNotDependOnDeviceLanguage(){
        val previous=java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.ENGLISH)
            val s=server("DE Germany Signet Германия Frankfurt");val display=ServerDisplayModel.from(s)
            assertEquals("Германия",display.group);assertEquals("Германия",display.title);assertEquals(s.id,display.id)
        }finally{java.util.Locale.setDefault(previous)}
    }
    @Test fun stripsControlAndDirectionOverrides(){assertEquals("safe title",DisplayText.clean("safe\u202e title\u0000"))}
    @Test fun signetAutoAndWhiteRemainVisibleWithoutHostLabels(){
        val auto=server("Signet_Auto");assertTrue(auto.isAuto);assertEquals("Signet_Auto",ServerDisplayModel.from(auto).title)
        val white=server("Signet_White");assertEquals("Signet_White",ServerDisplayModel.from(white).title)
        assertFalse(ServerDisplayModel.from(server("🇱🇻 Signet_Latvia")).title.contains("Signet"))
        assertEquals("Латвия",ServerDisplayModel.from(server("🇱🇻 Signet_Latvia")).title)
        assertEquals("Латвия",ServerDisplayModel.from(server("Signet_Latvia")).title)
    }
    @Test fun panelDescriptionsAreReadForNormalAndAutoProfiles(){
        for(name in listOf("Signet_Latvia","Signet_Auto")){
            val json=org.json.JSONObject(server(name).config).put("meta",org.json.JSONObject().put("serverDescription","Enhanced traffic\ncamouflage"))
            val parsed=Profiles.parseJson(json.toString()).single()
            assertEquals("Enhanced traffic\ncamouflage",parsed.description)
            assertEquals("Enhanced traffic camouflage",ServerDisplayModel.from(parsed).description)
        }
    }
    @Test fun routingExportHasNoProfileSecrets(){val r=AppRouting("include",setOf("ru.yandex.yandexmaps","org.example.app"));assertEquals(r,AppRouting.parse(r.json()));assertFalse(r.json().contains("token"));assertFails{AppRouting.parse(r.json().replace("\"schemaVersion\": 1","\"schemaVersion\": 9"))};assertFails{AppRouting.parse(r.json().replace("org.example.app","../secret"))}}
    @Test fun probesRejectUnsafeSchemesAndResourceAbuse(){assertFails{ProbeSettings(url="http://example.com").validated()};assertFails{ProbeSettings(url="https://u:p@example.com").validated()};assertFails{ProbeSettings(parallelism=100).validated()};assertEquals(ProbeSettings(),ProbeSettings.parse(ProbeSettings().json().toString()))}
    @Test fun happStylePingDefaultsAndLegacySettingsRemainCompatible(){
        val defaults=ProbeSettings()
        assertEquals(ProbeMode.XRAY,defaults.mode)
        assertFalse(defaults.pingOnLaunch)
        val enabled=defaults.copy(pingOnLaunch=true)
        assertEquals(enabled,ProbeSettings.parse(enabled.json().toString()))
        val legacy=defaults.json().apply{remove("pingOnLaunch");put("mode","OUTBOUND")}
        val restored=ProbeSettings.parse(legacy.toString())
        assertEquals(ProbeMode.OUTBOUND,restored.mode)
        assertFalse(restored.pingOnLaunch)
    }
    @Test fun autoHasNoInventedTcpEndpoint(){assertNull(Endpoints.from(server().copy(isAuto=true)));assertEquals("example.com",Endpoints.from(server())?.host)}
    @Test fun fullProfileProbePreservesRoutingAndOutbounds(){val original=org.json.JSONObject(server("AUTO").config).put("routing",org.json.JSONObject().put("rules",org.json.JSONArray().put(org.json.JSONObject().put("balancerTag","server"))).put("balancers",org.json.JSONArray().put(org.json.JSONObject().put("tag","server").put("selector",org.json.JSONArray().put("proxy"))))).put("dns",org.json.JSONObject().put("servers",org.json.JSONArray().put("9.9.9.9")));val s=Profiles.parseJson(original.toString()).single();val probe=Profiles.probeRuntime(s,12345,"/private/geo","ephemeral");listOf("outbounds","dns","routing").forEach{assertEquals(original.get(it).toString(),probe.get(it).toString())};assertFalse(probe.getJSONObject("env").has("xray.tun.fd"));assertFalse(s.config.contains("ephemeral"))}
}
