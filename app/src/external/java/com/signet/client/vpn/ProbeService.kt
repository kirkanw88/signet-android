package com.signet.client.vpn

import android.app.Service
import android.content.*
import android.net.*
import android.os.*
import com.signet.core.*
import com.signet.client.data.awaitResponse
import com.signet.client.data.readLimited
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import org.json.*
import java.io.File
import java.net.*
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Isolated process; unbinding kills its native runtime and cancels sockets. */
open class ProbeService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val lock=Mutex()
    private val protector=android.net.VpnService()
    @Volatile private var network:Network?=null
    private var fallbackDns="1.1.1.1"
    private val listener=libXray.DialerController { fd->
        val manager=getSystemService(ConnectivityManager::class.java)
        val vpn=manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true
        !vpn||protector.protect(fd.toInt())
    }
    private val controller=libXray.DialerController { fd->
        listener.protectFd(fd)&&runCatching {
            val physical=network?:throw CoreFailure("NO_NETWORK")
            ParcelFileDescriptor.fromFd(fd.toInt()).use{physical.bindSocket(it.fileDescriptor)}
            true
        }.getOrDefault(false)
    }
    override fun onCreate(){super.onCreate();libXray.LibXray.registerDialerController(controller);libXray.LibXray.registerListenerController(listener)}
    private fun bootstrap(resolve:Boolean=true){
        val manager=getSystemService(ConnectivityManager::class.java)
        network=physicalNetwork()
        val physical=network?:throw CoreFailure("NO_NETWORK")
        if(!resolve)return
        val dns=manager.getLinkProperties(physical)?.dnsServers.orEmpty()
        val addresses=(dns.filterIsInstance<Inet4Address>()+dns.filterNot{it is Inet4Address}).mapNotNull{it.hostAddress}.plus(fallbackDns).distinct()
        for(address in addresses){
            try{libXray.LibXray.setDNS(controller,if(address.contains(':'))"[$address]:53"else"$address:53");return}catch(e:Exception){/* Try another advertised resolver. */}
        }
        throw CoreFailure("DNS_SETUP_ERROR")
    }

    private val incoming=Messenger(Handler(Looper.getMainLooper()){message->
        val fd=message.data.getParcelable<ParcelFileDescriptor>("requestFd")?:return@Handler true
        val reply=message.replyTo
        scope.launch {
            val result=lock.withLock { runCatching { val raw=ParcelFileDescriptor.AutoCloseInputStream(fd).use{String(it.readLimited(16*1024*1024),Charsets.UTF_8)};execute(JSONObject(raw)) }.getOrElse{if(it is CancellationException)throw it;JSONObject().put("status",if(it is CoreFailure)it.code else"PROBE_ERROR")} }
            runCatching{reply.send(Message.obtain(null,1).apply{data=Bundle().apply{putString("result",result.toString())}})}
        };true
    })
    override fun onBind(intent:Intent):IBinder=incoming.binder
    private suspend fun execute(request:JSONObject):JSONObject {
        fallbackDns=request.optString("bootstrapDns","1.1.1.1").also{require(TemplateCompatibility.numericAddress(it))}
        val options=ProbeSettings.parse(request.getJSONObject("options").toString())
        val array=request.getJSONArray("servers");require(array.length() in 1..10)
        val servers=(0 until array.length()).map { i->val s=array.getJSONObject(i);Profiles.parseJson(s.getString("config")).single().copy(id=s.getString("id")) }
        if(!request.optBoolean("validateOnly"))bootstrap(options.mode!=ProbeMode.HTTPS)
        val out=JSONArray()
        if(request.optBoolean("validateOnly")) {
            for(server in servers)out.put(fullProfile(server,options,validateOnly=true))
        } else if(options.mode==ProbeMode.HTTPS) {
            val result=coroutineScope {servers.map{s->async { direct(s,options) }}.awaitAll()}
            result.forEach(out::put)
        } else {
            // TCP displays carrier handshake latency only after the full HTTPS profile check.
            // XRAY and OUTBOUND retain end-to-end timing.
            for(server in servers)out.put(fullProfile(server,options))
        }
        return JSONObject().put("results",out)
    }
    private fun physicalNetwork():Network? {
        val manager=getSystemService(ConnectivityManager::class.java)
        fun eligible(n:Network)=manager.getNetworkCapabilities(n)?.let{it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&!it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)}==true
        manager.activeNetwork?.takeIf(::eligible)?.let{return it}
        return manager.allNetworks.filter(::eligible).maxByOrNull{n->
            val caps=manager.getNetworkCapabilities(n)
            (if(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true)2 else 0)+(if(caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true)1 else 0)
        }
    }

    private fun direct(server:Server,options:ProbeSettings):JSONObject {
        val endpoint=Endpoints.from(server)?:return JSONObject().put("id",server.id).put("status","UNSUPPORTED")
        val started=SystemClock.elapsedRealtime()
        return runCatching {
            val network=network?:error("NO_NETWORK")
            // Direct HTTPS remains a node/TLS diagnostic, never used for TCP availability.
            run {
                val host=endpoint.tlsName?:endpoint.host
                val factory=network.socketFactory
                val client=OkHttpClient.Builder().socketFactory(factory).dns(object:Dns {
                    override fun lookup(hostname:String):List<InetAddress> {
                        val target=if(hostname==host)endpoint.host else hostname
                        return network.getAllByName(target).toList()
                    }
                }).proxy(java.net.Proxy.NO_PROXY).callTimeout(options.timeoutMs.toLong(),TimeUnit.MILLISECONDS).followRedirects(false).build()
                try {client.newCall(Request.Builder().url(HttpUrl.Builder().scheme("https").host(host).port(endpoint.port).encodedPath("/").build()).build()).execute().use {check(it.handshake!=null)}}
                finally{client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
            }
            JSONObject().put("id",server.id).put("delay",SystemClock.elapsedRealtime()-started).put("status","OK")
        }.getOrElse {JSONObject().put("id",server.id).put("status",ConnectionHealth.category(it))}
    }
    private suspend fun fullProfile(server:Server,options:ProbeSettings,validateOnly:Boolean=false):JSONObject {
        // Do not include cold process or geo-asset initialization in proxy latency.
        var requestStarted=SystemClock.elapsedRealtime()
        var client:OkHttpClient?=null;var attemptedRun=false;var stage="ASSET_ERROR"
        return try {
            val assetsDir=GeoAssets.prepare(this)
            // The worker already bootstrapped its physical network and DNS once per batch.
            stage="CONFIG_ERROR"
            val port=NativeCore.call("getFreePorts",JSONObject().put("count",1)).getJSONArray("ports").getInt(0)
            val secret=UUID.randomUUID().toString()
            val config=Profiles.probeRuntime(server,port,assetsDir.absolutePath,secret,strict=!validateOnly)
            if(!config.has("dns")){
                val dnsAddress=if(fallbackDns.contains(':'))"[$fallbackDns]"else fallbackDns
                config.put("dns",JSONObject().put("servers",JSONArray().put("tcp://$dnsAddress:53")))
            }
            if(validateOnly){
                NativeCore.call("testXray",JSONObject().put("xrayJson",config.toString()))
                return JSONObject().put("id",server.id).put("status","VALID")
            }
            // A composite profile or UDP carrier has no unambiguous TCP endpoint.
            // Keep measured HTTPS latency for those profiles instead of probing an unrelated port.
            val endpoint=if(options.mode==ProbeMode.TCP&&!server.isAuto) Endpoints.from(server) else null
            val source=JSONObject(server.config).optJSONArray("outbounds")
            val carriers=if(source==null) emptyList() else (0 until source.length()).map{source.getJSONObject(it)}.filter{it.optString("protocol") !in setOf("freedom","blackhole","dns","loopback")}
            val carrier=carriers.singleOrNull()
            val protocol=carrier?.optString("protocol").orEmpty()
            val stream=carrier?.optJSONObject("streamSettings")
            val transport=stream?.optString("network","tcp")?:"tcp"
            val alpn=stream?.optJSONObject("tlsSettings")?.optJSONArray("alpn")
            val quic=alpn!=null&&alpn.length()==1&&alpn.optString(0)=="h3"
            val tcpCarrier=protocol in setOf("vless","vmess","trojan","shadowsocks","socks","http")&&transport in setOf("tcp","raw","ws","grpc","httpupgrade","xhttp","splithttp")&&!quic
            val tcpDelay=if(endpoint!=null&&tcpCarrier){
                stage="TCP_HANDSHAKE"
                val physical=network?:throw CoreFailure("NO_NETWORK")
                ProbeTcp.connect(physical.getAllByName(endpoint.host).toList(),endpoint.port,options.timeoutMs){physical.socketFactory.createSocket()}
            }else null
            stage="CONFIG_ERROR"
            // runXray parses the configuration itself; avoid a second cold core initialization for each ping.
            stage="CORE_ERROR";attemptedRun=true
            NativeCore.call("runXray",JSONObject().put("xrayJson",config.toString()))
            stage="REQUEST"
            client=OkHttpClient.Builder().proxy(java.net.Proxy(java.net.Proxy.Type.HTTP,InetSocketAddress("127.0.0.1",port)))
                .proxyAuthenticator { _,response->if(response.request.header("Proxy-Authorization")!=null)null else response.request.newBuilder().header("Proxy-Authorization",Credentials.basic("signet",secret)).build() }
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).callTimeout(options.timeoutMs.toLong(),TimeUnit.MILLISECONDS).build()
            // Via Proxy GET measures the request through the selected full Xray profile.
            requestStarted=SystemClock.elapsedRealtime()
            val request=Request.Builder().url(options.url).header("Connection","close")
            if(options.mode==ProbeMode.OUTBOUND)request.head()else request.get()
            client.newCall(request.build()).awaitResponse().use {
                if(it.handshake==null)return JSONObject().put("id",server.id).put("status","TLS")
                val expected=if(options.url.substringBefore('?').endsWith("/generate_204"))it.code==204 else it.code in 200..299
                if(!expected)return JSONObject().put("id",server.id).put("status","HTTP_ERROR")
                if(options.mode!=ProbeMode.OUTBOUND)it.body?.byteStream()?.read()
            }
            val proxyDelay=SystemClock.elapsedRealtime()-requestStarted
            val delay=tcpDelay?:proxyDelay
            JSONObject().put("id",server.id).put("delay",delay).put("status","OK").put("proxyMs",proxyDelay).put("tcpMs",tcpDelay?:-1L)
        } catch(e:CancellationException){throw e}
        catch(e:Exception){JSONObject().put("id",server.id).put("status",if(e is CoreFailure)e.code else if(stage=="REQUEST"||stage=="TCP_HANDSHAKE")ConnectionHealth.category(e)else stage)}
        finally {if(attemptedRun)runCatching{NativeCore.call("stopXray")};client?.connectionPool?.evictAll();client?.dispatcher?.executorService?.shutdown()}
    }
    override fun onDestroy(){scope.cancel();super.onDestroy();android.os.Process.killProcess(android.os.Process.myPid())}
}

// Each worker has its own native singleton and Android process.
class ProbeWorker1 : ProbeService()
class ProbeWorker2 : ProbeService()
class ProbeWorker3 : ProbeService()
class ProbeWorker4 : ProbeService()

class ProbeWorker5 : ProbeService()
class ProbeWorker6 : ProbeService()
class ProbeWorker7 : ProbeService()
class ProbeWorker8 : ProbeService()
