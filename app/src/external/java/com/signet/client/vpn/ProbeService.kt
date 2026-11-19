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
class ProbeService:Service() {
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
            val physical=network?:error("NO_NETWORK")
            ParcelFileDescriptor.fromFd(fd.toInt()).use{physical.bindSocket(it.fileDescriptor)}
            true
        }.getOrDefault(false)
    }
    override fun onCreate(){super.onCreate();libXray.LibXray.registerDialerController(controller);libXray.LibXray.registerListenerController(listener)}
    private fun bootstrap(resolve:Boolean=true){
        val manager=getSystemService(ConnectivityManager::class.java)
        network=physicalNetwork()
        val physical=network?:error("NO_NETWORK")
        if(!resolve)return
        val dns=manager.getLinkProperties(physical)?.dnsServers.orEmpty()
        val address=(dns.firstOrNull{it is Inet4Address}?:dns.firstOrNull())?.hostAddress?:fallbackDns
        libXray.LibXray.setDNS(controller,if(address.contains(':'))"[$address]:53"else"$address:53")
    }
    private val incoming=Messenger(Handler(Looper.getMainLooper()){message->
        val fd=message.data.getParcelable<ParcelFileDescriptor>("requestFd")?:return@Handler true
        val reply=message.replyTo
        scope.launch {
            val result=lock.withLock { runCatching { val raw=ParcelFileDescriptor.AutoCloseInputStream(fd).use{String(it.readLimited(16*1024*1024),Charsets.UTF_8)};execute(JSONObject(raw)) }.getOrElse{JSONObject().put("status",if(network==null)"NO_NETWORK"else"PROBE_ERROR")} }
            runCatching{reply.send(Message.obtain(null,1).apply{data=Bundle().apply{putString("result",result.toString())}})}
        };true
    })
    override fun onBind(intent:Intent):IBinder=incoming.binder
    private suspend fun execute(request:JSONObject):JSONObject {
        fallbackDns=request.optString("bootstrapDns","1.1.1.1").also{require(TemplateCompatibility.numericAddress(it))}
        val options=ProbeSettings.parse(request.getJSONObject("options").toString())
        val array=request.getJSONArray("servers");require(array.length() in 1..5)
        val servers=(0 until array.length()).map { i->val s=array.getJSONObject(i);Profiles.parseJson(s.getString("config")).single().copy(id=s.getString("id")) }
        if(!request.optBoolean("validateOnly"))bootstrap(options.mode!=ProbeMode.TCP&&options.mode!=ProbeMode.HTTPS)
        val out=JSONArray()
        if(request.optBoolean("validateOnly")) {
            for(server in servers)out.put(fullProfile(server,options,validateOnly=true))
        } else if(options.mode==ProbeMode.TCP||options.mode==ProbeMode.HTTPS) {
            val result=coroutineScope {servers.map{s->async { direct(s,options) }}.awaitAll()}
            result.forEach(out::put)
        } else if(options.mode==ProbeMode.OUTBOUND && servers.none{it.isAuto}) {
            val configs=JSONArray();servers.forEach{s->val c=JSONObject(s.config);c.remove("inbounds");c.remove("env");c.remove("metrics");configs.put(JSONObject().put("xrayJson",c.toString()).put("outboundTag",s.outboundTag))}
            val values=NativeCore.call("pingBatch",JSONObject().put("configs",configs).put("timeout",(options.timeoutMs+999)/1000).put("url",options.url)).getJSONArray("results")
            for(i in 0 until values.length()){val r=values.getJSONObject(i);out.put(JSONObject().put("id",servers[i].id).put("delay",if(r.optBoolean("success"))r.optLong("delay")else JSONObject.NULL).put("status",if(r.optBoolean("success"))"OK"else"FAILED"))}
        } else {
            // Full profile, including AUTO DNS/routing/balancers, is never flattened into pingBatch.
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
            if(options.mode==ProbeMode.TCP) {
                ProbeTcp.connect(network.getAllByName(endpoint.host).toList(),endpoint.port,options.timeoutMs){network.socketFactory.createSocket()}
            } else {
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
        if(!validateOnly)bootstrap() // Restore after each managed full-profile shutdown.
        val started=SystemClock.elapsedRealtime()
        val assetsDir=File(filesDir,"geo").apply{mkdirs()}
        listOf("geoip.dat","geosite.dat").forEach{name->val target=File(assetsDir,name);if(!target.exists())assets.open(name).use{input->target.outputStream().use(input::copyTo)}}
        val port=NativeCore.call("getFreePorts",JSONObject().put("count",1)).getJSONArray("ports").getInt(0)
        val secret=UUID.randomUUID().toString()
        val config=Profiles.probeRuntime(server,port,assetsDir.absolutePath,secret)
        var client:OkHttpClient?=null
        return try {
            NativeCore.call("testXray",JSONObject().put("xrayJson",config.toString()))
            if(validateOnly)return JSONObject().put("id",server.id).put("status","VALID")
            NativeCore.call("runXray",JSONObject().put("xrayJson",config.toString()))
            client=OkHttpClient.Builder().proxy(java.net.Proxy(java.net.Proxy.Type.HTTP,InetSocketAddress("127.0.0.1",port)))
                .proxyAuthenticator { _,response->if(response.request.header("Proxy-Authorization")!=null)null else response.request.newBuilder().header("Proxy-Authorization",Credentials.basic("signet",secret)).build() }
                .followRedirects(false).followSslRedirects(false).callTimeout(options.timeoutMs.toLong(),TimeUnit.MILLISECONDS).build()
            client.newCall(Request.Builder().url(options.url).build()).awaitResponse().use {check(it.handshake!=null)}
            JSONObject().put("id",server.id).put("delay",SystemClock.elapsedRealtime()-started).put("status","OK")
        } catch(e:CancellationException){throw e}
        catch(e:Exception){JSONObject().put("id",server.id).put("status",ConnectionHealth.category(e))}
        finally {runCatching{NativeCore.call("stopXray")};client?.connectionPool?.evictAll();client?.dispatcher?.executorService?.shutdown()}
    }
    override fun onDestroy(){scope.cancel();super.onDestroy();android.os.Process.killProcess(android.os.Process.myPid())}
}

