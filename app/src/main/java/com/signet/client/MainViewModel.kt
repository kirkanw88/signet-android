package com.signet.client

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.signet.client.data.*
import com.signet.client.vpn.*
import com.signet.core.Profiles
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.java.KoinJavaComponent.get
import okhttp3.*
import java.util.concurrent.TimeUnit

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val updates=AppUpdates(app)
    init { viewModelScope.launch(Dispatchers.IO){runCatching{updates.restore()}} }
    fun checkUpdates(automatic:Boolean=false){viewModelScope.launch(Dispatchers.IO){try{val result=updates.check(automatic);if(!automatic&&result.isNotBlank())repository.notify(result)}catch(e:CancellationException){throw e}catch(e:Exception){if(!automatic)repository.notify("Не удалось проверить обновления")}}}
    fun dismissUpdate(){viewModelScope.launch(Dispatchers.IO){updates.dismiss()}}
    override fun onCleared(){updates.close();super.onCleared()}
    val repository: Repository = get(Repository::class.java)
    val state = repository.state
    val vpn = VpnState.status.asStateFlow()
    private val _probing = MutableStateFlow(false); val probing = _probing.asStateFlow()
    private val _diagnostic = MutableStateFlow(""); val diagnostic = _diagnostic.asStateFlow()
    private var probeJob: Job? = null
    fun import(text: String, ageKey: String = "",allowXDriveOAuth:Boolean=false) { viewModelScope.launch(Dispatchers.IO) { repository.import(text,ageKey=ageKey,allowXDriveOAuth=allowXDriveOAuth) } }
    fun select(id: String) { viewModelScope.launch { repository.select(id)
        if(vpn.value.phase in listOf(VpnPhase.CONNECTED,VpnPhase.CONNECTING,VpnPhase.RECONNECTING))start()
    } }
    fun selectAuto() { state.value.servers.firstOrNull { it.isAuto && it.enabled }?.let { select(it.id) }
        ?: repository.notify("В подписке нет серверного профиля AUTO") }
    fun favorite(id: String) { viewModelScope.launch { repository.favorite(id) } }
    fun remove(id: String) { viewModelScope.launch { repository.remove(id) } }
    fun refresh() { viewModelScope.launch(Dispatchers.IO) { repository.refreshAll() } }
    fun save(s: Settings) { viewModelScope.launch { runCatching { s.probes.validated();repository.settings(s) }.onFailure { repository.notify("Проверьте настройки: HTTPS URL, таймаут и допустимые ограничения") } } }
    fun start() { getApplication<Application>().startForegroundService(Intent(getApplication(), SignetVpnService::class.java)) }
    fun stop() { getApplication<Application>().startService(Intent(getApplication(), SignetVpnService::class.java).setAction("STOP")) }
    private var automaticProbe=false
    fun cancelAutoProbes(){if(automaticProbe)probeJob?.cancel()}
    fun measure(onlySelected: Boolean = false,automatic:Boolean=false) {
        if (_probing.value) { probeJob?.cancel(); return }
        _probing.value=true
        automaticProbe=automatic
        probeJob = viewModelScope.launch(Dispatchers.IO) {
            _probing.value = true
            try {
                val options=state.value.settings.probes.validated()
                val targets=if(onlySelected)listOfNotNull(state.value.server)else state.value.servers.filter{it.enabled}
                val allowed=targets.filter { !com.signet.core.TransportChecks.requiresOAuthConsent(org.json.JSONObject(it.config))||repository.allowsOAuth(it.id) }
                targets.filterNot{it in allowed}.forEach{repository.measurement(it.id,null,options.mode.name,"CONSENT_REQUIRED")}
                for(batch in allowed.chunked(options.parallelism)) {
                    val samples=batch.associate{it.id to mutableListOf<Long>()};val statuses=mutableMapOf<String,String>()
                    repeat(options.attempts) { attempt->
                        Probes.measureDetailed(getApplication(),batch,options).forEach { (id,result)->
                            result.delay?.let { samples.getValue(id).add(it) };statuses[id]=result.status
                        }
                        if(attempt<options.attempts-1)delay(options.intervalMs.toLong())
                    }
                    batch.forEach { server->val values=samples.getValue(server.id).sorted();repository.measurement(server.id,values.getOrNull(values.size/2),options.mode.name,if(values.isEmpty())statuses[server.id]else"OK") }
                    delay(200)
                }
                // A probe NEVER changes the selection or the server's AUTO configuration.
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { repository.notify("Не удалось измерить задержку") }
            finally { _probing.value = false;automaticProbe=false }
        }
    }
    fun diagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            _diagnostic.value = "Проверка…"
            val client=OkHttpClient.Builder().callTimeout(15,TimeUnit.SECONDS).build()
            val check=runCatching { client.newCall(Request.Builder().url("https://www.gstatic.com/generate_204").build()).execute().use { if(it.isSuccessful) "PASS" else "FAIL" } }.getOrDefault("FAIL")
            val dns=runCatching { java.net.InetAddress.getAllByName("example.com").isNotEmpty() }.getOrDefault(false)
            _diagnostic.value = "Signet ${BuildConfig.VERSION_NAME}\nXray ${NativeCore.version()}\nVPN: ${vpn.value.phase}\nHTTPS: $check\nСистемный DNS: ${if(dns) "PASS" else "FAIL"}\nПрофилей: ${state.value.subscriptions.size}\nСерверов: ${state.value.servers.size}\nАдреса узлов и ключи исключены из отчёта."
            client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
        }
    }
}
