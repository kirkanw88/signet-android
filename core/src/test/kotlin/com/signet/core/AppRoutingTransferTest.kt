package com.signet.core

import org.junit.Test
import kotlin.test.*

class AppRoutingTransferTest {
    @Test fun lineListFromOtherClientsNeedsExplicitMode(){
        val result=AppRoutingTransfer.parse("com.example.first\norg.example.second\ncom.example.first\n")
        assertEquals(setOf("com.example.first","org.example.second"),result.packages)
        assertNull(result.mode);assertTrue(result.rejected.isEmpty())
    }
    @Test fun chatMarkdownAndTimestampAreReviewedWithoutGuessing(){
        val result=AppRoutingTransfer.parse("com.example.first\n[org.example.second](https://org.example.second/)\ncom.example.last22:01")
        assertEquals(setOf("com.example.first","org.example.second"),result.packages)
        assertEquals(listOf("com.example.last22:01"),result.rejected)
        assertFails{AppRoutingTransfer.parse("[com.example.app](https://evil.example/)")}
    }
    @Test fun plainExportAndJsonRoundTrip(){
        val routing=AppRouting("exclude",setOf("com.example.first","org.example.second"))
        assertEquals(routing.packages,AppRoutingTransfer.parse(AppRoutingTransfer.plain(routing.packages)).packages)
        assertEquals(ImportedApps(routing.packages,"exclude"),AppRoutingTransfer.parse(routing.json()))
        assertFails{AppRoutingTransfer.parse(routing.json().replace("\"schemaVersion\": 1","\"schemaVersion\": 99"))}
    }
    @Test fun csvJsonAndDocumentedPerAppHeaders(){
        assertEquals(2,AppRoutingTransfer.parse("com.example.first,org.example.second").packages.size)
        assertEquals(2,AppRoutingTransfer.parse("[\"com.example.first\",\"org.example.second\"]").packages.size)
        assertEquals("include",AppRoutingTransfer.parse("#per-app-proxy-mode: on\n#per-app-proxy-list: com.example.first,org.example.second").mode)
        assertEquals("exclude",AppRoutingTransfer.parse("per-app-proxy-mode: bypass\nper-app-proxy-list: base64:Y29tLmV4YW1wbGUuYXBw").mode)
        assertEquals("exclude",AppRoutingTransfer.parse("per-app-proxy-mode: on\nper-app-proxy-list-invert: com.example.app").mode)
    }
    @Test fun rejectPathsUrlsUnexpectedFieldsAndOversize(){
        listOf("https://example.com/apps","../com.example.app","{\"mode\":\"include\",\"packages\":[\"com.example.app\"],\"token\":\"secret\"}","x".repeat(256*1024+1)).forEach{assertFails{AppRoutingTransfer.parse(it)}}
    }
}
