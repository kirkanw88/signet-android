package com.signet.client.vpn

import android.app.Service
import android.content.*
import android.os.*
import com.signet.core.Server
import kotlinx.coroutines.*
import org.json.*
import kotlin.coroutines.resume

/** One independent Go runtime, isolated from the active VPN core. No persistent probe logs. */
class ProbeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val incoming = Messenger(Handler(Looper.getMainLooper()) { message ->
        val raw = message.data.getString("request") ?: return@Handler true
        val reply = message.replyTo
        scope.launch {
            val result = runCatching {
                val networks=getSystemService(android.net.ConnectivityManager::class.java)
                val protector=android.net.VpnService()
                val controller=libXray.DialerController { fd ->
                    val vpn=networks.getNetworkCapabilities(networks.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)==true
                    !vpn || protector.protect(fd.toInt())
                }
                libXray.LibXray.registerDialerController(controller)
                NativeCore.call("pingBatch", JSONObject(raw)).toString()
            }.getOrDefault("{}")
            runCatching { reply.send(Message.obtain(null, 1).apply { data = Bundle().apply { putString("result", result) } }) }
        }
        true
    })
    override fun onBind(intent: Intent): IBinder = incoming.binder
    override fun onDestroy() { scope.cancel(); super.onDestroy(); android.os.Process.killProcess(android.os.Process.myPid()) }
}

object Probes {
    suspend fun measure(context: Context, servers: List<Server>): Map<String, Long?> = withTimeout(45_000) {
        require(servers.size in 1..5)
        val configs = JSONArray()
        servers.forEach { s ->
            val config = JSONObject(s.config)
            config.remove("inbounds"); config.remove("metrics"); config.remove("env")
            configs.put(JSONObject().put("xrayJson", config.toString()).put("outboundTag", s.outboundTag))
        }
        val request = JSONObject().put("configs", configs).put("timeout", 10).put("url", "https://www.gstatic.com/generate_204").toString()
        require(request.toByteArray().size < 600_000) { "Конфигурация слишком велика для измерения" }
        var connection: ServiceConnection? = null
        try {
            suspendCancellableCoroutine { continuation ->
                val reply = Messenger(Handler(Looper.getMainLooper()) { m ->
                    val result = runCatching {
                        val values = JSONObject(m.data.getString("result") ?: "{}").getJSONArray("results")
                        servers.mapIndexed { i, server ->
                            val value = values.getJSONObject(i)
                            server.id to if (value.optBoolean("success")) value.getLong("delay") else null
                        }.toMap()
                    }.getOrElse { servers.associate { it.id to null } }
                    if (continuation.isActive) continuation.resume(result)
                    true
                })
                connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        runCatching { Messenger(binder).send(Message.obtain(null, 1).apply { replyTo = reply; data = Bundle().apply { putString("request", request) } }) }
                            .onFailure { if (continuation.isActive) continuation.resume(servers.associate { it.id to null }) }
                    }
                    override fun onServiceDisconnected(name: ComponentName?) { if (continuation.isActive) continuation.resume(servers.associate { it.id to null }) }
                }
                if (!context.bindService(Intent(context, ProbeService::class.java), connection!!, Context.BIND_AUTO_CREATE)) continuation.resume(servers.associate { it.id to null })
            }
        } finally { connection?.let { runCatching { context.unbindService(it) } } }
    }
}
