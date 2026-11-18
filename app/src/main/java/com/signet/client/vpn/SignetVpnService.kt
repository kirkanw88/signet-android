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
data class VpnStatus(val phase: VpnPhase = VpnPhase.DISCONNECTED, val serverId: String? = null, val since: Long = 0, val uploaded: Long = 0, val downloaded: Long = 0, val error: String? = null)
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
    private var socksPort = 0
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
        stopCore(closeTun=false)
        val selected = repository.state.value.server
        if (selected == null) { fail("Добавьте подписку и выберите сервер"); return@withLock }
        if (!selected.enabled) { fail("Сервер отключён в подписке"); return@withLock }
        var startupStage="настройка TUN"
        try {
            val settings = repository.state.value.settings
            val dnsSetting=TemplateCompatibility.deviceDns(selected,settings.dns)
            require(TemplateCompatibility.numericAddress(dnsSetting)) { "Укажите IP DNS-сервера" }
            val dns = InetAddress.getByName(dnsSetting).hostAddress ?: error("DNS")
            val assetDir = File(filesDir, "geo").apply { mkdirs() }
            for (asset in listOf("geoip.dat", "geosite.dat")) {
                val target = File(assetDir, asset)
                if (!target.exists()) runCatching { assets.open(asset).use { input -> target.outputStream().use(input::copyTo) } }
            }
            NativeCore.attach(this,if(dns.contains(':'))"[$dns]:53"else"$dns:53")
            val builder = Builder().setSession("Signet VPN").setMtu(1500).addAddress("172.19.0.1", 30).addRoute("0.0.0.0", 0)
                .addAddress("fdfe:dcba:9876::1", 126).addRoute("::", 0).addDnsServer(dns)
                .setConfigureIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false)
            when (settings.appMode) {
                "include" -> {
                    require(settings.packages.isNotEmpty()) { "Выберите приложения для VPN" }
                    settings.packages.forEach(builder::addAllowedApplication)
                    // Needed for the through-TUN connectivity check and diagnostics.
                    builder.addAllowedApplication(packageName)
                }
                "exclude" -> settings.packages.filter { it != packageName }.forEach(builder::addDisallowedApplication)
            }
            val ports = NativeCore.call("getFreePorts", JSONObject().put("count", 2)).getJSONArray("ports")
            metricsPort = ports.getInt(0); socksPort = ports.getInt(1)
            val previousTun=tun
            tun = builder.establish() ?: error("Разрешение VPN отозвано")
            runCatching { previousTun?.close() }
            val config = Profiles.runtime(selected, tun!!.fd, metricsPort, socksPort, assetDir.absolutePath, dns)
            if(com.signet.client.BuildConfig.DEBUG && selected.name=="LOCAL TEST ONLY") {
                val localLog=File(cacheDir,"native-local-test.log").apply{writeText("")}
                config.put("log",JSONObject().put("loglevel","debug").put("error",localLog.absolutePath))
            }
            startupStage="запуск ядра"
            NativeCore.call("runXray", JSONObject().put("xrayJson", config.toString()))
            // A running Go instance alone is insufficient: require real HTTPS over the selected outbound.
            startupStage="проверка HTTPS"
            val proxyClient = OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY)
                .connectTimeout(12, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()
            try {
                proxyClient.newCall(Request.Builder().url(settings.probes.url).build()).awaitResponse().use {
                    check(it.isSuccessful) { "Сервер не подтвердил передачу данных" }
                }
            } finally { proxyClient.connectionPool.evictAll(); proxyClient.dispatcher.executorService.shutdown() }
            currentCoroutineContext().ensureActive()
            if (!requested) { stopCore(); return@withLock }
            VpnState.status.value = VpnStatus(VpnPhase.CONNECTED, selected.id, SystemClock.elapsedRealtime())
            repository.success(selected.id)
            notification("Подключено · ${selected.countryName}")
            monitor = scope.launch { watch(selected) }
        } catch (e: CancellationException) { stopCore(closeTun=false); throw e }
        catch (e: Exception) { stopCore(closeTun=false); fail("Не удалось подключиться: $startupStage (${e.javaClass.simpleName}). Проверьте подписку и доступность сервера.") }
    }
    private suspend fun watch(server: Server) {
        val client = OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).callTimeout(2, TimeUnit.SECONDS).build()
        val health = OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).callTimeout(10,TimeUnit.SECONDS).build()
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
                    val reachable=runCatching { health.newCall(Request.Builder().url(repository.state.value.settings.probes.url).build()).awaitResponse().use { it.isSuccessful } }.getOrDefault(false)
                    failures=if(reachable)0 else failures+1;nextHealth=SystemClock.elapsedRealtime()+30_000
                    if(reachable)retryCount=0
                    if(failures>=3){stopCore(closeTun=false);fail("VPN перестал передавать HTTPS-трафик");break}
                }
                runCatching {
                    client.newCall(Request.Builder().url("http://127.0.0.1:$metricsPort/debug/vars").build()).execute().use { response ->
                        val raw = response.body?.string() ?: return@use
                        val stats = JSONObject(raw).optJSONObject("stats")?.optJSONObject("outbound") ?: return@use
                        val tags=if(server.isAuto || server.outboundTag.isEmpty())stats.keys().asSequence().toList()else listOf(server.outboundTag)
                        VpnState.status.update { it.copy(uploaded = tags.sumOf { tag->stats.optJSONObject(tag)?.optLong("uplink")?:0L }, downloaded = tags.sumOf { tag->stats.optJSONObject(tag)?.optLong("downlink")?:0L }) }
                    }
                }
            }
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown();health.connectionPool.evictAll();health.dispatcher.executorService.shutdown() }
    }
    private fun fail(message: String) {
        VpnState.status.value = VpnStatus(VpnPhase.ERROR, error = message)
        repository.notify(message)
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
