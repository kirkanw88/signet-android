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
    private val controller=libXray.DialerController { fd->
        val manager=getSystemService(ConnectivityManager::class.java)
        val vpn=manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true
        !vpn||protector.protect(fd.toInt())
    }
    override fun onCreate(){super.onCreate();libXray.LibXray.registerDialerController(controller);libXray.LibXray.registerListenerController(controller)}
    private val incoming=Messenger(Handler(Looper.getMainLooper()){message->
        val fd=message.data.getParcelable<ParcelFileDescriptor>("requestFd")?:return@Handler true
        val reply=message.replyTo
        scope.launch {
            val result=lock.withLock { runCatching { val raw=ParcelFileDescriptor.AutoCloseInputStream(fd).use{String(it.readLimited(16*1024*1024),Charsets.UTF_8)};execute(JSONObject(raw)) }.getOrDefault(JSONObject()) }
            runCatching{reply.send(Message.obtain(null,1).apply{data=Bundle().apply{putString("result",result.toString())}})}
        };true
    })
    override fun onBind(intent:Intent):IBinder=incoming.binder
    private suspend fun execute(request:JSONObject):JSONObject {
        val options=ProbeSettings.parse(request.getJSONObject("options").toString())
        val array=request.getJSONArray("servers");require(array.length() in 1..5)
        val servers=(0 until array.length()).map { i->val s=array.getJSONObject(i);Profiles.parseJson(s.getString("config")).single().copy(id=s.getString("id")) }
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
        return manager.allNetworks.firstOrNull { network->manager.getNetworkCapabilities(network)?.let { it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&!it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }==true }
    }
    private fun direct(server:Server,options:ProbeSettings):JSONObject {
        val endpoint=Endpoints.from(server)?:return JSONObject().put("id",server.id).put("status","UNSUPPORTED")
        val started=SystemClock.elapsedRealtime()
        return runCatching {
            val network=physicalNetwork()
            if(options.mode==ProbeMode.TCP) {
                val address=network?.getAllByName(endpoint.host)?.firstOrNull()?:InetAddress.getByName(endpoint.host)
                (network?.socketFactory?.createSocket()?:Socket()).use {socket->socket.connect(InetSocketAddress(address,endpoint.port),options.timeoutMs)}
            } else {
                val host=endpoint.tlsName?:endpoint.host
                val factory=network?.socketFactory?:javax.net.SocketFactory.getDefault()
                val client=OkHttpClient.Builder().socketFactory(factory).dns(object:Dns {
                    override fun lookup(hostname:String):List<InetAddress> {
                        val target=if(hostname==host)endpoint.host else hostname
                        return (network?.getAllByName(target)?:InetAddress.getAllByName(target)).toList()
                    }
                }).proxy(java.net.Proxy.NO_PROXY).callTimeout(options.timeoutMs.toLong(),TimeUnit.MILLISECONDS).followRedirects(false).build()
                try {client.newCall(Request.Builder().url(HttpUrl.Builder().scheme("https").host(host).port(endpoint.port).encodedPath("/").build()).build()).execute().use {check(it.code in 200..499)}}
                finally{client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
            }
            JSONObject().put("id",server.id).put("delay",SystemClock.elapsedRealtime()-started).put("status","OK")
        }.getOrElse {JSONObject().put("id",server.id).put("status","FAILED")}
    }
    private suspend fun fullProfile(server:Server,options:ProbeSettings,validateOnly:Boolean=false):JSONObject {
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
                .callTimeout(options.timeoutMs.toLong(),TimeUnit.MILLISECONDS).build()
            client.newCall(Request.Builder().url(options.url).build()).awaitResponse().use {check(it.isSuccessful)}
            JSONObject().put("id",server.id).put("delay",SystemClock.elapsedRealtime()-started).put("status","OK")
        } catch(e:CancellationException){throw e}
        catch(e:Exception){JSONObject().put("id",server.id).put("status","FAILED")}
        finally {runCatching{NativeCore.call("stopXray")};client?.connectionPool?.evictAll();client?.dispatcher?.executorService?.shutdown()}
    }
    override fun onDestroy(){scope.cancel();super.onDestroy();android.os.Process.killProcess(android.os.Process.myPid())}
}

