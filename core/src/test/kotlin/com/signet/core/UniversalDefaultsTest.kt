package com.signet.core

import org.junit.Test
import kotlin.test.*

class UniversalDefaultsTest {
    @Test fun refreshIsHourlyAndRecoversFromClockChanges(){
        val time=10_000_000L
        assertFalse(UniversalDefaults.subscriptionDue(time,time+3_599_999))
        assertTrue(UniversalDefaults.subscriptionDue(time,time+3_600_000))
        assertTrue(UniversalDefaults.subscriptionDue(time,time-1))
        assertTrue(UniversalDefaults.subscriptionDue(0,time))
    }
    @Test fun hostNamesKeepProviderNamesWithoutChangingRoutingOrGeography(){
        val raw="""{"remarks":"🇱🇻 Signet_Latvia 🛡","outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"example.com","port":443}]}}]}"""
        val server=Profiles.parseJson(raw).single();val before=server.config
        val display=ServerDisplayModel.from(server,hostNames=true)
        assertEquals("Signet_Latvia 🛡",display.title);assertEquals("LV",display.country)
        assertEquals(before,server.config)
    }
}
