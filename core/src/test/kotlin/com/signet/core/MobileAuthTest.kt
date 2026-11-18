package com.signet.core

import org.junit.Test
import kotlin.test.*

class MobileAuthTest {
    @Test fun rfc7636S256Vector(){assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",MobileAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))}
    @Test fun callbackRequiresExactDestinationStateAndFreshPendingLogin(){
        val code="a".repeat(43);val uri=MobileAuth.REDIRECT+"?code=$code&state=pending"
        assertEquals(code,MobileAuth.code(uri,"pending",100,200))
        listOf(uri.replace("oauth/","evil/"),uri.replace("com.signet.client:","https:"),uri+"&code=$code",uri+"#fragment").forEach{assertFails{MobileAuth.code(it,"pending",100,200)}}
        assertFails{MobileAuth.code(uri,"different",100,200)}
        assertFails{MobileAuth.code(uri,"pending",100,700_000)}
        assertFails{MobileAuth.code(uri,"pending",200,100)}
    }
}
