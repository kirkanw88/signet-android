package com.signet.client.vpn

import android.app.*
import android.content.Intent
import android.net.*
import android.os.*
import androidx.core.app.NotificationCompat
import com.signet.client.MainActivity
import com.signet.client.R
import com.signet.client.data.*
import com.signet.core.Profiles
import com.signet.core.Server
import com.signet.core.TemplateCompatibility
import com.signet.core.AndroidDns
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libXray.*
import okhttp3.*
import org.json.JSONObject
import org.koin.android.ext.android.inject
import java.io.File
import java.net.*
import java.util.concurrent.TimeUnit

enum class VpnPhase { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, DISCONNECTING, ERROR }
data class VpnStatus(val phase: VpnPhase = VpnPhase.DISCONNECTED, val serverId: String? = null, val since: Long = 0, val uploaded: Long = 0, val downloaded: Long = 0, val error: String? = null,val tunActive:Boolean=false,val uploadRate:Long=0,val downloadRate:Long=0)
object VpnState { val status = MutableStateFlow(VpnStatus()) }

class SignetVpnService : VpnService() {
    private val repository: Repository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var tun: ParcelFileDescriptor? = null
    private var monitor: Job? = null
    private var connectJob: Job? = null
    private var requested = false
    private var retryCount = 0
    private var metricsPort = 0
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { changedNetwork() }
        override fun onLost(network: Network) { changedNetwork() }
    }
    private var networkJob: Job? = null
    private fun changedNetwork() {
        if (requested && repository.state.value.settings.reconnect && VpnState.status.value.phase in listOf(VpnPhase.CONNECTED, VpnPhase.ERROR)) {
            networkJob?.cancel()
            networkJob = scope.launch { delay(1200); if (requested) reconnect() }
        }
    }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("vpn", "VPN-соединение", NotificationManager.IMPORTANCE_LOW))
        connectivity.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), networkCallback)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            ConnectionLog.add("VPN","STOP_REQUESTED")
            requested = false; connectJob?.cancel(); networkJob?.cancel()
            VpnState.status.update { it.copy(phase=VpnPhase.DISCONNECTING) }
            scope.launch { mutex.withLock {
                stopCore()
                if(!requested) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId) }
            } }
            return START_NOT_STICKY
        }
        notification("Подключение…")
        requested = true; retryCount = 0
        connectJob?.cancel()
        connectJob = scope.launch { repository.awaitReady(); reconnect() }
        return START_STICKY
    }
    private suspend fun reconnect() = mutex.withLock {
        if (!requested) return@withLock
        val current = VpnState.status.value
        VpnState.status.value = VpnStatus(if (current.phase == VpnPhase.CONNECTED) VpnPhase.RECONNECTING else VpnPhase.CONNECTING)
        ConnectionHealth.clear()
        stopCore(closeTun=false)
        val selected = repository.state.value.server
        ConnectionLog.add("VPN","CONNECT_REQUESTED")
        if (selected == null) { fail("Добавьте подписку и выберите сервер"); return@withLock }
        if(com.signet.core.TransportChecks.requiresOAuthConsent(JSONObject(selected.config))&&!repository.allowsOAuth(selected.id)){fail("Для XDrive OAuth требуется согласие при повторном импорте подписки");return@withLock}
        if (!selected.enabled) { fail("Сервер отключён в подписке"); return@withLock }
        var startupStage="настройка TUN"
        try {
            val settings = repository.state.value.settings
            require(TemplateCompatibility.numericAddress(settings.dns)) { "Укажите IP DNS-сервера" }
            val physical=physicalNetwork()?:error("Нет доступной сети")
            val linkDns=connectivity.getLinkProperties(physical)?.dnsServers.orEmpty()
            val bootstrap=linkDns.firstOrNull{it is Inet4Address}?:linkDns.firstOrNull()
            val dns=bootstrap?.hostAddress?:settings.dns
            ConnectionLog.add("DNS","BOOTSTRAP",if(bootstrap!=null)"NETWORK"else"FALLBACK")
            val assetDir=if(!com.signet.client.BuildConfig.SIGNET_ACCOUNT)GeoAssets.prepare(this)else {
            val assetDir = File(filesDir, "geo").apply { mkdirs() }
            for (asset in listOf("geoip.dat", "geosite.dat")) {
                val target = File(assetDir, asset)
                if (!target.exists()) runCatching { assets.open(asset).use { input -> target.outputStream().use(input::copyTo) } }
            }
                assetDir
            }
            NativeCore.attach(this,if(dns.contains(':'))"[$dns]:53"else"$dns:53",physical)
            val builder = Builder().setSession("Signet VPN").setMtu(1500).addAddress("172.19.0.1", 30).addRoute("0.0.0.0", 0)
                .addAddress("fdfe:dcba:9876::1", 126).addRoute("::", 0).addDnsServer(AndroidDns.ADDRESS)
                .setConfigureIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false)
            builder.setUnderlyingNetworks(arrayOf(physical))
            when (settings.appMode) {
                "include" -> {
                    require(settings.packages.isNotEmpty()) { "Выберите приложения для VPN" }
                    val visible=settings.packages.filter { runCatching { packageManager.getApplicationInfo(it,0) }.isSuccess }
                    require(visible.isNotEmpty()) { "Выбранные приложения удалены или недоступны в этом профиле Android" }
                    visible.forEach(builder::addAllowedApplication)
                    // Needed for the through-TUN connectivity check and diagnostics.
                    builder.addAllowedApplication(packageName)
                }
                "exclude" -> settings.packages.filter { it != packageName && runCatching { packageManager.getApplicationInfo(it,0) }.isSuccess }.forEach(builder::addDisallowedApplication)
            }
            val ports = NativeCore.call("getFreePorts", JSONObject().put("count", 1)).getJSONArray("ports")
            metricsPort = ports.getInt(0)
            val previousTun=tun
            tun = builder.establish() ?: error("Разрешение VPN отозвано")
            ConnectionLog.add("VPN","TUN_ESTABLISHED")
            runCatching { previousTun?.close() }
            val config = Profiles.androidRuntime(selected, tun!!.fd, metricsPort, assetDir.absolutePath, settings.dns)
            ConnectionLog.add("DNS","XRAY_BRIDGE_READY")
            startupStage="запуск ядра"
            NativeCore.call("runXray", JSONObject().put("xrayJson", config.toString()))
            ConnectionLog.add("CORE","STARTED")
            // A running Go instance alone is insufficient: require real HTTPS over the selected outbound.
            startupStage="проверка HTTPS"
            // Android updates VPN routes/DNS asynchronously after establish(). Allow bounded warm-up retries.
            delay(300)
            check(ConnectionHealth.check(settings.connectionCheckUrl,throughTunnel=true).healthy)
            currentCoroutineContext().ensureActive()
            if (!requested) { stopCore(); return@withLock }
            VpnState.status.value = VpnStatus(VpnPhase.CONNECTED, selected.id, SystemClock.elapsedRealtime(),tunActive=true)
            repository.success(selected.id)
            notification("Подключено · ${if(com.signet.client.BuildConfig.SIGNET_ACCOUNT)selected.countryName else com.signet.core.DisplayText.clean(selected.name)}")
            ConnectionLog.add("VPN","CONNECTED")
            monitor = scope.launch { watch(selected) }
        } catch (e: CancellationException) { stopCore(closeTun=false); throw e }
        catch (e: Exception) {
            ConnectionLog.add("VPN","CONNECT_FAILED",ConnectionHealth.category(e))
            stopCore(closeTun=false)
            fail(if(startupStage=="проверка HTTPS")"${ConnectionHealth.latest.value?.description?:"HTTPS недоступен"}. Откройте диагностику в настройках."
                else "Не удалось подключиться: $startupStage (${e.javaClass.simpleName}). Проверьте подписку и доступность сервера.")
        }
    }
    private suspend fun watch(server: Server) {
        val client = OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).callTimeout(2, TimeUnit.SECONDS).build()
        val traffic=com.signet.core.TrafficMeter()
        var nextHealth=SystemClock.elapsedRealtime()+30_000;var failures=0
        try {
            while (currentCoroutineContext().isActive && requested) {
                delay(3000)
                if (!NativeCore.call("getXrayState").optBoolean("running")) {
                    stopCore(closeTun=false)
                    fail("Ядро VPN остановилось")
                    break
                }
                if(SystemClock.elapsedRealtime()>=nextHealth) {
                    val reachable=ConnectionHealth.check(repository.state.value.settings.connectionCheckUrl,attempts=1,throughTunnel=true).healthy
                    failures=if(reachable)0 else failures+1;nextHealth=SystemClock.elapsedRealtime()+30_000
                    if(reachable)retryCount=0
                    if(failures>=3){stopCore(closeTun=false);fail("VPN перестал передавать HTTPS-трафик");break}
                }
                runCatching {
                    client.newCall(Request.Builder().url("http://127.0.0.1:$metricsPort/debug/vars").build()).execute().use { response ->
                        val raw = response.body?.byteStream()?.use { String(it.readLimited(512*1024),Charsets.UTF_8) } ?: return@use
                        val all=JSONObject(raw).optJSONObject("stats")?:return@use
                        val inbound=all.optJSONObject("inbound")?.optJSONObject(TemplateCompatibility.inboundTag(JSONObject(server.config)))
                        val stats=all.optJSONObject("outbound")
                        val tags=if(stats==null)emptyList()else if(server.isAuto||server.outboundTag.isEmpty())stats.keys().asSequence().toList()else listOf(server.outboundTag)
                        val uploaded=(inbound?.optLong("uplink")?:tags.sumOf{stats?.optJSONObject(it)?.optLong("uplink")?:0L}).coerceAtLeast(0)
                        val downloaded=(inbound?.optLong("downlink")?:tags.sumOf{stats?.optJSONObject(it)?.optLong("downlink")?:0L}).coerceAtLeast(0)
                        val rates=traffic.sample(uploaded,downloaded,SystemClock.elapsedRealtime())
                        VpnState.status.update{it.copy(uploaded=uploaded,downloaded=downloaded,uploadRate=rates.upload,downloadRate=rates.download)}
                    }
                }
            }
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }
    private fun physicalNetwork():Network? {
        fun eligible(network:Network)=connectivity.getNetworkCapabilities(network)?.let{it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&!it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)}==true
        connectivity.activeNetwork?.takeIf(::eligible)?.let{return it}
        return connectivity.allNetworks.filter(::eligible).sortedByDescending{network->
            val caps=connectivity.getNetworkCapabilities(network)
            (if(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true)2 else 0)+(if(caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true)1 else 0)
        }.firstOrNull()
    }
    private fun fail(message: String) {
        VpnState.status.value = VpnStatus(VpnPhase.ERROR, error = message,tunActive=tun!=null)
        notification(message)
        if(requested && repository.state.value.settings.reconnect && retryCount<3) {
            retryCount++;networkJob?.cancel()
            networkJob=scope.launch { delay(2_000L shl (retryCount-1));if(requested)reconnect() }
        }
        if (!repository.state.value.settings.reconnect) { requested = false;if(tun==null){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()} }
    }
    private fun notification(text: String) {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, SignetVpnService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, "vpn").setSmallIcon(R.drawable.ic_notification).setContentTitle("Signet VPN").setContentText(text)
            .setContentIntent(open).setOngoing(true).setSilent(true).addAction(0, "Отключить", stop).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED) else startForeground(1, n)
    }
    private fun stopCore(closeTun:Boolean=true) {
        monitor?.cancel(); monitor = null
        runCatching { NativeCore.call("stopXray") }; runCatching { NativeCore.detach(this) }
        if(closeTun){runCatching { tun?.close() }; tun = null}
    }
    override fun onRevoke() { requested = false; connectJob?.cancel(); scope.launch { mutex.withLock { stopCore(); VpnState.status.value = VpnStatus() }; stopSelf() } }
    override fun onDestroy() {
        requested = false; scope.cancel(); runCatching { connectivity.unregisterNetworkCallback(networkCallback) }; stopCore()
        VpnState.status.value = VpnStatus(); super.onDestroy()
    }
}
