package com.signet.client.data

import android.content.Context
import android.os.Build
import com.signet.client.vpn.NativeCore
import com.signet.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.*
import java.util.UUID
import java.util.concurrent.TimeUnit

data class Subscription(val id: String, val title: String, val url: String?, val servers: List<Server>, val info: Map<String, Long> = emptyMap(), val updated: Long = System.currentTimeMillis(), val ageKey: String = "")
data class AppState(val subscriptions: List<Subscription> = emptyList(), val selected: String? = null, val settings: Settings = Settings(), val busy: Boolean = false, val message: String? = null, val ready: Boolean = false) {
    val servers get() = subscriptions.flatMap { it.servers }
    val server get() = servers.firstOrNull { it.id == selected }
}
class Repository(private val context: Context, private val vault: Vault, private val settingsStore: SettingsStore) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS).followRedirects(false).build()
    private var installId = UUID.randomUUID().toString()
    init { scope.launch {
        runCatching {
            val settings = settingsStore.load()
            val saved = vault.read()?.let(::JSONObject)
            installId = saved?.optString("installation")?.takeIf { it.isNotBlank() } ?: installId
            val subs = saved?.optJSONArray("subscriptions") ?: JSONArray()
            val loaded = (0 until subs.length()).map { i ->
                val o = subs.getJSONObject(i); val a = o.getJSONArray("servers")
                Subscription(o.getString("id"), o.getString("title"), o.optString("url").takeIf { it.isNotBlank() },
                    (0 until a.length()).map { j ->
                        val s = a.getJSONObject(j); Profiles.parseJson(s.getString("config")).first().copy(
                            id = s.getString("id"), favorite = s.optBoolean("favorite"), successes = s.optInt("successes"), originalConfig = s.optString("original").takeIf { it.isNotEmpty() } ?: s.getString("config"))
                    }, Profiles.userInfo(o.optString("info")), o.optLong("updated"),o.optString("ageKey"))
            }
            _state.value = AppState(loaded, saved?.optString("selected"), settings)
            if(saved==null) persist()
            _state.update { it.copy(ready=true) }
        }.onFailure { _state.update { it.copy(ready = true, message = "Не удалось прочитать защищённое хранилище. Данные не перезаписаны.") } }
    } }
    suspend fun awaitReady() = state.first { it.ready }
    private suspend fun persist() {
        val s = _state.value
        val subs = JSONArray()
        s.subscriptions.forEach { sub ->
            val servers = JSONArray(); sub.servers.forEach { servers.put(JSONObject().put("id", it.id).put("config", it.config).put("original", if(it.config!=it.originalConfig)it.originalConfig else "").put("favorite", it.favorite).put("successes", it.successes)) }
            subs.put(JSONObject().put("id", sub.id).put("title", sub.title).put("url", sub.url ?: "").put("updated", sub.updated)
                .put("info", sub.info.entries.joinToString(";") { "${it.key}=${it.value}" }).put("servers", servers).put("ageKey",sub.ageKey))
        }
        vault.write(JSONObject().put("installation", installId).put("selected", s.selected).put("subscriptions", subs).toString())
    }
    fun clearMessage() = _state.update { it.copy(message = null) }
    fun notify(text: String) = _state.update { it.copy(message = text) }
    suspend fun select(id: String) = mutex.withLock {
        require(state.value.servers.any { it.id == id && it.enabled }) { "Профиль недоступен" }
        _state.update { it.copy(selected = id) }; persist()
    }
    suspend fun settings(s: Settings) { settingsStore.save(s); _state.update { it.copy(settings = s) } }
    suspend fun favorite(id: String) = updateServers { if (it.id == id) it.copy(favorite = !it.favorite) else it }
    suspend fun success(id: String) = updateServers { if (it.id == id) it.copy(successes = it.successes + 1) else it }
    suspend fun edit(id: String, text: String) {
        require(text.length <= 512 * 1024) { "Профиль слишком большой для редактора" }
        val parsed = Profiles.parseJson(text).single()
        require(state.value.servers.firstOrNull { it.id==id }?.isAuto != true) { "AUTO управляется сервером" }
        updateServers { if(it.id==id) parsed.copy(id=id,favorite=it.favorite,successes=it.successes,originalConfig=it.originalConfig) else it }
    }
    suspend fun restore(id: String) = updateServers { if(it.id==id) it.copy(config=it.originalConfig) else it }
    private suspend fun updateServers(persistent: Boolean = true, f: (Server) -> Server) = mutex.withLock {
        _state.update { it.copy(subscriptions = it.subscriptions.map { sub -> sub.copy(servers = sub.servers.map(f)) }) }; if(persistent) persist()
    }
    suspend fun remove(id: String) = mutex.withLock {
        _state.update { s -> val subs = s.subscriptions.filterNot { it.id == id }; s.copy(subscriptions = subs, selected = s.selected?.takeIf { selected -> subs.any { sub -> sub.servers.any { it.id == selected } } }) }; persist()
    }
    suspend fun import(input: String, existingId: String? = null, ageKey: String = "") = withContext(Dispatchers.IO) {
        awaitReady(); _state.update { it.copy(busy = true) }
        try {
            require(input.toByteArray().size <= 4*1024*1024) { "Слишком большой импорт" }
            var text = input.trim(); var url: String? = null; var title = "Подписка"; var info = emptyMap<String, Long>()
            if (text.startsWith("https://")) {
                url = text
                var next = text
                var fetched = false
                repeat(6) {
                    if (fetched) return@repeat
                    require(next.startsWith("https://")) { "Разрешены только HTTPS подписки" }
                    val request = Request.Builder().url(next).header("User-Agent", "SignetVPN/${com.signet.client.BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})").header("Accept", "application/json, text/plain")
                    if (_state.value.settings.hwid) {
                        request.header("X-Hwid", installId).header("X-Device-Os", "Android").header("X-Ver-Os", Build.VERSION.RELEASE).header("X-Device-Model", Build.MODEL)
                    }
                    client.newCall(request.build()).execute().use { response ->
                        if (response.code in 300..399) {
                            next = response.request.url.resolve(response.header("Location") ?: error("Нет адреса перенаправления"))?.toString() ?: error("Неверный адрес")
                        } else {
                            require(response.isSuccessful) { "HTTP ${response.code}" }
                            val body = response.body ?: error("Пустой ответ")
                            require(body.contentLength() <= 4 * 1024 * 1024) { "Подписка слишком большая" }
                            val bytes = body.byteStream().readLimited(4 * 1024 * 1024)
                            require(bytes.size <= 4 * 1024 * 1024) { "Подписка слишком большая" }
                            text = String(bytes, Charsets.UTF_8)
                            title = response.header("profile-title")?.let { h -> if (h.startsWith("base64:")) runCatching { String(java.util.Base64.getDecoder().decode(h.substring(7))) }.getOrDefault("Подписка") else h } ?: "Подписка"
                            info = Profiles.userInfo(response.header("subscription-userinfo")); fetched = true
                        }
                    }
                }
                check(fetched) { "Слишком много перенаправлений" }
            } else require(!text.startsWith("http://")) { "Нужна защищённая HTTPS ссылка" }
            text = Profiles.decode(text)
            val secret=ageKey.ifEmpty { state.value.subscriptions.firstOrNull { it.id==existingId }?.ageKey.orEmpty() }
            if(text.startsWith("-----BEGIN AGE ENCRYPTED FILE-----")) {
                require(secret.isNotBlank()) { "Требуется ключ AGE" }
                text=Profiles.decode(NativeCore.call("decodeSubscriptionText",JSONObject().put("text",text).put("age",JSONObject().put("secretKey",secret))).getString("text"))
            }
            val parsed = if (text.startsWith("{") || text.startsWith("[")) Profiles.parseJson(text) else {
                val nodes = NativeCore.call("convertShareLinksToXrayJson", JSONObject().put("text", text)).getJSONArray("outbounds")
                (0 until nodes.length()).flatMap { Profiles.parseJson(JSONObject().put("outbounds", JSONArray().put(nodes.getJSONObject(it))).toString()) }
            }
            require(parsed.isNotEmpty()) { "В подписке нет серверов" }
            val subId = existingId ?: Profiles.id(url ?: text)
            val previous = state.value.subscriptions.firstOrNull { it.id == subId }
            val servers = ProfileRefresh.reconcile(subId,previous?.servers.orEmpty(),parsed)
            mutex.withLock {
                val others = state.value.subscriptions.filterNot { it.id == subId }
                require(others.size < 8 && others.sumOf { it.servers.size } + servers.size <= 500) { "Достигнут лимит профилей" }
                require(others.flatMap { it.servers }.sumOf { it.config.length } + servers.sumOf { it.config.length } <= 8 * 1024 * 1024) { "Достигнут лимит хранилища" }
                val sub = Subscription(subId, if (url == null) servers.first().name else title, url, servers, info,ageKey=secret)
                _state.update { it.copy(subscriptions = others + sub, selected = ProfileRefresh.selection(it.selected,others.flatMap { s->s.servers }+servers,existingId==null), message = "Добавлено серверов: ${servers.size}") }
                persist()
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { notify(if (e.message?.startsWith("HTTP ") == true) e.message!! else "Не удалось импортировать профиль. Проверьте ссылку и формат конфигурации.") }
        finally { _state.update { it.copy(busy = false) } }
    }
    suspend fun refreshAll() { awaitReady(); state.value.subscriptions.filter { it.url != null }.forEach { import(it.url!!, it.id) } }
    suspend fun measurement(id: String, delay: Long?,mode:String="OUTBOUND",status:String?=null) = updateServers(persistent = false) { if (it.id == id) it.copy(latencyMs = delay, available = if(status=="UNSUPPORTED")null else delay != null, checkedAt = System.currentTimeMillis(),probeMode=mode,probeMessage=status) else it }
}
