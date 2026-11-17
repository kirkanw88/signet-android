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
    val repository: Repository = get(Repository::class.java)
    val state = repository.state
    val vpn = VpnState.status.asStateFlow()
    private val _probing = MutableStateFlow(false); val probing = _probing.asStateFlow()
    private val _diagnostic = MutableStateFlow(""); val diagnostic = _diagnostic.asStateFlow()
    private var probeJob: Job? = null
    fun import(text: String, ageKey: String = "") { viewModelScope.launch(Dispatchers.IO) { repository.import(text,ageKey=ageKey) } }
    fun select(id: String) { viewModelScope.launch { repository.select(id) } }
    fun favorite(id: String) { viewModelScope.launch { repository.favorite(id) } }
    fun remove(id: String) { viewModelScope.launch { repository.remove(id) } }
    fun refresh() { viewModelScope.launch(Dispatchers.IO) { repository.refreshAll() } }
    fun save(s: Settings) { viewModelScope.launch { repository.settings(s) } }
    fun start() { getApplication<Application>().startForegroundService(Intent(getApplication(), SignetVpnService::class.java)) }
    fun stop() { getApplication<Application>().startService(Intent(getApplication(), SignetVpnService::class.java).setAction("STOP")) }
    fun measure(auto: Boolean = false) {
        if (_probing.value) { probeJob?.cancel(); return }
        probeJob = viewModelScope.launch(Dispatchers.IO) {
            _probing.value = true
            try {
                for(batch in state.value.servers.chunked(5)) {
                    val results = Probes.measure(getApplication(), batch)
                    results.forEach { (id, delay) -> repository.measurement(id, delay) }
                }
                if(auto) {
                    val best = Profiles.best(state.value.servers, System.currentTimeMillis())
                    if(best != null) repository.select(best.id) else repository.notify("Нет доступных серверов с актуальным измерением")
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { repository.notify("Не удалось измерить задержку") }
            finally { _probing.value = false }
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
