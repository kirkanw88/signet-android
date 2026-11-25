package com.signet.client

import android.content.Intent
import android.net.VpnService
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.signet.client.data.Repository
import com.signet.client.vpn.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import okhttp3.*
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ClientIntegrationTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun nativeCoreSupportsRequiredShareFormats() {
        assertTrue(NativeCore.version().contains("26.11.9"))
        listOf(
            "vless://00000000-0000-4000-8000-000000000001@example.com:443?security=reality&type=tcp&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&fp=chrome&sni=example.com&sid=0123456789abcdef#Reality",
            "vless://00000000-0000-4000-8000-000000000001@example.com:443?security=tls&type=xhttp&path=%2Fsignet&mode=packet-up&sni=example.com#XHTTP",
            "hysteria2://password@example.com:443?sni=example.com#HY2"
        ).forEach { link ->
            val result=NativeCore.call("convertShareLinksToXrayJson",JSONObject().put("text",link))
            assertTrue(result.getJSONArray("outbounds").length()>0)
        }
    }
    @Test fun encryptedStorageDoesNotContainSubscriptionSecrets()=runBlocking {
        val repository=get<Repository>(Repository::class.java);repository.awaitReady()
        val secret="signet-secret-storage-test"
        val config=JSONObject().put("remarks","storage-test").put("outbounds",JSONArray().put(JSONObject().put("protocol","socks").put("tag","proxy").put("settings",JSONObject().put("servers",JSONArray().put(JSONObject().put("address","127.0.0.1").put("port",10888).put("users",JSONArray().put(JSONObject().put("user",secret).put("pass",secret))))))))
        repository.import(config.toString())
        val sub=repository.state.value.subscriptions.first{it.title=="storage-test"}
        try {
            context.getDatabasePath("signet.db").parentFile!!.listFiles()!!.filter{it.name.startsWith("signet.db")}.forEach {
                assertFalse("Plaintext secret in ${it.name}",String(it.readBytes(),Charsets.ISO_8859_1).contains(secret))
            }
        } finally {repository.remove(sub.id)}
    }
    @Test fun realTunTrafficThroughLocalVlessServer()=runBlocking {
        verifyLocalTun(null)
    }
    @Test fun realTunTrafficThroughLocalRealityVision()=runBlocking {
        verifyLocalTun("vless://00000000-0000-4000-8000-000000000001@10.0.2.2:10889?security=reality&type=tcp&flow=xtls-rprx-vision&pbk=6fJ8ZrtKPl03dmX4FA6WG61MIGgCm_sJKfZ0G4onWw0&fp=chrome&sni=www.microsoft.com&sid=0123456789abcdef#LOCAL%20TEST%20ONLY")
    }
    @Test fun realTunTrafficThroughLocalXhttpReality()=runBlocking {
        verifyLocalTun("vless://00000000-0000-4000-8000-000000000001@10.0.2.2:10890?security=reality&type=xhttp&path=%2Fsignet-local&mode=packet-up&pbk=6fJ8ZrtKPl03dmX4FA6WG61MIGgCm_sJKfZ0G4onWw0&fp=chrome&sni=www.microsoft.com&sid=0123456789abcdef#LOCAL%20TEST%20ONLY")
    }
    private suspend fun verifyLocalTun(link: String?) {
        val args=InstrumentationRegistry.getArguments()
        Assume.assumeTrue("Start the authorized local fixture and pass -e localVpn true",args.getString("localVpn")=="true")
        assertNull("Grant VPN consent with adb appops before this test",VpnService.prepare(context))
        val repository=get<Repository>(Repository::class.java);repository.awaitReady()
        val config="""{"remarks":"LOCAL TEST ONLY","signet":{"countryCode":"DE","city":"Test fixture","latitude":50.1,"longitude":8.6},"outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"10.0.2.2","port":10888,"users":[{"id":"00000000-0000-4000-8000-000000000001","encryption":"none"}]}]},"streamSettings":{"network":"tcp","security":"none"}}]}"""
        val imported=if(link==null)config else NativeCore.call("convertShareLinksToXrayJson",JSONObject().put("text",link)).put("remarks","LOCAL TEST ONLY").toString()
        // Only the public local test fixture is written here; never user subscriptions.
        context.cacheDir.resolve("local-fixture-config.json").writeText(imported)
        repository.import(imported.replace("10.0.2.2","127.0.0.1"))
        val sub=repository.state.value.subscriptions.first{it.title=="LOCAL TEST ONLY"}
        repository.select(sub.servers.first().id)
        try {
            val tcpOptions=com.signet.core.ProbeSettings(mode=com.signet.core.ProbeMode.TCP,timeoutMs=12000)
            val beforeTun=Probes.measureDetailed(context,sub.servers,tcpOptions)
            assertNotNull("TCP must carry HTTPS before TUN: ${beforeTun[sub.servers.first().id]?.status}",beforeTun[sub.servers.first().id]?.delay)
            delay(700)
            if(link==null){
                // Same listening fixture and transport, but deliberately invalid VLESS credentials.
                // A raw TCP connect would falsely report success.
                java.net.Socket().use{it.connect(java.net.InetSocketAddress("127.0.0.1",10888),3000)}
                val broken=com.signet.core.Profiles.parseJson(sub.servers.first().config.replace("00000000-0000-4000-8000-000000000001","00000000-0000-4000-8000-000000000099")).single()
                val result=Probes.measureDetailed(context,listOf(broken),tcpOptions)[broken.id]
                assertNotNull("Probe must return a result",result)
                assertNull("Listening port with invalid VPN credentials must not show latency",result?.delay)
                assertNotEquals("OK",result?.status)
                delay(700)
            }
            context.startForegroundService(Intent(context,SignetVpnService::class.java))
            val connected=withTimeout(40_000){VpnState.status.first{it.phase==VpnPhase.CONNECTED||it.phase==VpnPhase.ERROR}}
            assertEquals(connected.error,VpnPhase.CONNECTED,connected.phase)
            val client=OkHttpClient.Builder().callTimeout(20,TimeUnit.SECONDS).build()
            try {
                client.newCall(Request.Builder().url("https://example.com").build()).execute().use{response->assertEquals(200,response.code);assertTrue(response.body!!.string().contains("Example Domain"))}
            }finally{client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
            delay(3500)
            assertTrue("Real Xray traffic statistics",VpnState.status.value.uploaded>0||VpnState.status.value.downloaded>0)
            val probe=Probes.measureDetailed(context,sub.servers,tcpOptions)
            assertNotNull("TCP must carry HTTPS while VPN runs: ${probe[sub.servers.first().id]?.status}",probe[sub.servers.first().id]?.delay)
        }finally{
            context.startService(Intent(context,SignetVpnService::class.java).setAction("STOP"))
            withTimeout(15_000){VpnState.status.first{it.phase==VpnPhase.DISCONNECTED}}
            repository.remove(sub.id)
        }
    }
}
