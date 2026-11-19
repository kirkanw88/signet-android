package com.signet.core
import org.junit.Test
import kotlin.test.*

class GlobeSessionTest {
    @Test fun resumedReentryStartsAgainAndThemeUpdateDoesNotPause(){
        val session=GlobeSession()
        assertNull(session.transition(false,true))
        assertEquals(true,session.transition(true,true))
        assertNull(session.transition(true,true))
        assertEquals(false,session.transition(false,true))
        assertNull(session.transition(false,false))
        assertEquals(true,session.transition(true,true))
        assertEquals(false,session.transition(true,false))
        assertEquals(true,session.transition(true,true))
    }
}
