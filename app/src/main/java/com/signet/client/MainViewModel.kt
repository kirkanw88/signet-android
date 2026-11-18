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
    private val _probingIds=MutableStateFlow<Set<String>>(emptySet());val probingIds=_probingIds.asStateFlow()
    private val _refreshing=MutableStateFlow(false);val refreshing=_refreshing.asStateFlow()
    private val _diagnostic = MutableStateFlow(""); val diagnostic = _diagnostic.asStateFlow()
    private var probeJob: Job? = null
    fun import(text: String, ageKey: String = "",allowXDriveOAuth:Boolean=false) { viewModelScope.launch(Dispatchers.IO) { repository.import(com.signet.core.ImportReview.normalize(text),ageKey=ageKey,allowXDriveOAuth=allowXDriveOAuth) } }
    fun select(id: String) { viewModelScope.launch { repository.select(id)
        if(vpn.value.phase in listOf(VpnPhase.CONNECTED,VpnPhase.CONNECTING,VpnPhase.RECONNECTING))start()
    } }
    fun selectAuto() { state.value.servers.firstOrNull { it.isAuto && it.enabled }?.let { select(it.id) }
        ?: repository.notify("В подписке нет серверного профиля AUTO") }
    fun favorite(id: String) { viewModelScope.launch { repository.favorite(id) } }
    fun remove(id: String) { viewModelScope.launch { repository.remove(id) } }
    fun refresh() { if(_refreshing.value)return;_refreshing.value=true;viewModelScope.launch(Dispatchers.IO) {try{repository.refreshAll()}finally{_refreshing.value=false}} }
    fun save(s: Settings) { viewModelScope.launch { runCatching { s.probes.validated();repository.settings(s) }.onFailure { repository.notify("Проверьте настройки: HTTPS URL, таймаут и допустимые ограничения") } } }
    fun start() { viewModelScope.launch {probeJob?.cancelAndJoin();getApplication<Application>().startForegroundService(Intent(getApplication(), SignetVpnService::class.java))} }
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
                    _probingIds.value=batch.map{it.id}.toSet()
                    val samples=batch.associate{it.id to mutableListOf<Long>()};val statuses=mutableMapOf<String,String>()
                    repeat(options.attempts) { attempt->
                        Probes.measureDetailed(getApplication(),batch,options).forEach { (id,result)->
                            ConnectionLog.add("PROBE",options.mode.name,result.status)
                            result.delay?.let { samples.getValue(id).add(it) };statuses[id]=result.status
                        }
                        if(attempt<options.attempts-1)delay(options.intervalMs.toLong())
                    }
                    batch.forEach { server->val values=samples.getValue(server.id).sorted();repository.measurement(server.id,values.getOrNull(values.size/2),options.mode.name,if(values.isEmpty())statuses[server.id]else"OK") }
                    _probingIds.value=emptySet()
                    delay(200)
                }
                // A probe NEVER changes the selection or the server's AUTO configuration.
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { repository.notify("Не удалось измерить задержку") }
            finally { _probingIds.value=emptySet();_probing.value = false;automaticProbe=false }
        }
    }
    fun diagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            val previous=ConnectionHealth.latest.value
            _diagnostic.value="Проверяем HTTPS-соединение…"
            try {
                val result=ConnectionHealth.check(state.value.settings.connectionCheckUrl,attempts=1,record=false)
                val transport=state.value.server?.let{com.signet.core.ServerDisplayModel.from(it).transport}?:"Не выбран"
                _diagnostic.value="Signet ${BuildConfig.VERSION_NAME}\nXray ${NativeCore.version()}\n\nVPN: ${vpn.value.phase}\nПрофиль: $transport\nРежим измерения: ${state.value.settings.probes.mode}\n\nПри подключении:\n${previous?.description?:"Проверка ещё не выполнялась"}\nПопыток: ${previous?.attempts?:0}\n\nСейчас:\n${result.description}\n\nПрофилей: ${state.value.subscriptions.size}\nСерверов: ${state.value.servers.size}\nHWID: включён\n\nАдреса серверов, ссылка подписки и ключи исключены."
            }catch(e:CancellationException){throw e}
            catch(e:Exception){_diagnostic.value="Не удалось выполнить диагностику. Код: ${ConnectionHealth.category(e)}"}
        }
    }
}
