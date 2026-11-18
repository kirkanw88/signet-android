package com.signet.core

import org.junit.Test
import kotlin.test.*

class SubscriptionRequestTest {
    @Test fun requestActualJsonPreservingRawPathAndToken(){
        assertEquals("https://sub.example/s/abc%2Bdef/json?token=a%2Bb%26c&x=1",SubscriptionRequest.jsonUrl("https://sub.example/s/abc%2Bdef/?token=a%2Bb%26c&x=1#ignored"))
    }
    @Test fun explicitJsonUrlsAreNotRewritten(){
        listOf("https://sub.example/s/id/json","https://sub.example/s/id/v2ray-json?x=1","https://sub.example/profile.json?x=1").forEach{assertEquals(it,SubscriptionRequest.jsonUrl(it))}
    }
    @Test fun rejectUnsafeEndpoints(){
        listOf("http://sub.example/s/id","https://user:secret@sub.example/id").forEach{assertFailsWith<IllegalArgumentException>{SubscriptionRequest.jsonUrl(it)}}
    }
}
