package com.signet.client

import android.content.Intent
import android.net.VpnService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.signet.client.data.Repository
import com.signet.client.vpn.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import java.net.*
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.*

/** Generated public loopback fixtures only; private CA/server keys never enter the APK. */
@RunWith(AndroidJUnit4::class)
class TemplateTransportTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    @Test fun xdriveTemplate()=verify("xdrive")
    @Test fun masqueHttp2ConnectIp()=verify("masque-h2")
    @Test fun masqueHttp3ConnectIp()=verify("masque-h3")
    @Test fun remnawaveAutoBalancer()=verify("remnawave-auto")
    @Test fun remnawaveSplitRules()=verify("remnawave-split")

    private fun verify(name:String)=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("localVpn")=="true")
        assertNull("VPN consent must be granted by the emulator fixture",VpnService.prepare(context))
        val fixtures=JSONObject(instrumentation.context.assets.open("local-profiles.json").bufferedReader().use{it.readText()})
        val config=fixtures.getJSONObject("profiles").getJSONObject(name)
        if(name=="masque-h3")config.getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings").put("address","10.0.2.2")
        val repository=get<Repository>(Repository::class.java);repository.awaitReady()
        repository.import(config.toString())
        val sub=repository.state.value.subscriptions.last()
        val server=sub.servers.single();repository.select(server.id)
        val original=server.config
        try {
            assertTrue("Native validation $name",Probes.validate(context,server));delay(700)
            context.startForegroundService(Intent(context,SignetVpnService::class.java))
            val status=withTimeout(55_000){VpnState.status.first{it.phase==VpnPhase.CONNECTED||it.phase==VpnPhase.ERROR}}
            assertEquals("$name: ${status.error}",VpnPhase.CONNECTED,status.phase)
            assertTrue(http("allowed.signet.test").contains("signet-local-lab"))
            // HTTPS through an address reachable only via the fixture's VPN routing.
            val certificates=fixtures.getJSONArray("ca");val pem=(0 until certificates.length()).joinToString("\n"){certificates.getString(it)}
            val ca=CertificateFactory.getInstance("X.509").generateCertificate(pem.byteInputStream())
            val keys=KeyStore.getInstance(KeyStore.getDefaultType()).apply{load(null);setCertificateEntry("lab",ca)}
            val trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply{init(keys)}
            val tls=SSLContext.getInstance("TLS").apply{init(null,trust.trustManagers,null)}
            val connection=URL("https://198.18.0.2:18443/").openConnection(Proxy.NO_PROXY) as HttpsURLConnection
            connection.sslSocketFactory=tls.socketFactory;connection.connectTimeout=12_000;connection.readTimeout=12_000
            try{assertEquals(200,connection.responseCode);assertTrue(connection.inputStream.bufferedReader().readText().contains("signet-local-lab"))}finally{connection.disconnect()}
            DatagramSocket().use{socket->
                socket.soTimeout=12_000
                val query=byteArrayOf(0x12,0x34,1,0,0,1,0,0,0,0,0,0,4)+"echo".toByteArray()+byteArrayOf(6)+"signet".toByteArray()+byteArrayOf(4)+"test".toByteArray()+byteArrayOf(0,0,1,0,1)
                socket.send(DatagramPacket(query,query.size,InetAddress.getByName("198.18.0.3"),53))
                val response=DatagramPacket(ByteArray(4096),4096);socket.receive(response)
                assertEquals(0x12,response.data[0].toInt());assertEquals(0x34,response.data[1].toInt())
            }
            if(name.startsWith("remnawave-"))assertFalse("Sniffed domain must be blocked",runCatching{http("blocked.signet.test").contains("signet-local-lab")}.getOrDefault(false))
            assertTrue("Validation while TUN is active",Probes.validate(context,server));delay(700)
            assertEquals("Probes must preserve the policy",original,repository.state.value.server!!.config)
            assertTrue("TUN still works after isolated probe",http("allowed.signet.test").contains("signet-local-lab"))
        } finally {
            context.startService(Intent(context,SignetVpnService::class.java).setAction("STOP"))
            withTimeout(15_000){VpnState.status.first{it.phase==VpnPhase.DISCONNECTED}}
            repository.remove(sub.id)
        }
    }
    private fun http(host:String):String=Socket().use{socket->
        socket.connect(InetSocketAddress("198.18.0.1",18480),12_000);socket.soTimeout=5_000
        socket.getOutputStream().write("GET / HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray())
        socket.getInputStream().bufferedReader().readText().take(65536)
    }
}
