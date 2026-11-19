package com.signet.client.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import com.signet.core.ProbeSettings
import com.signet.core.SignetLinks

val Context.settingsStore by preferencesDataStore("signet_settings")
data class Settings(
    val reconnect: Boolean = true, val autoConnect: Boolean = false, val dns: String = "1.1.1.1",
    val graphics: String = "Balanced", val reduceMotion: Boolean = false,
    val appMode: String = "all", val packages: Set<String> = emptySet(), val cabinet: String = if(com.signet.client.BuildConfig.SIGNET_ACCOUNT)SignetLinks.CABINET else "",
    val hwid: Boolean = true, val probes:ProbeSettings=ProbeSettings(),val automaticUpdates:Boolean=true,
    val connectionCheckUrl:String="https://example.com",val logging:Boolean=false
)
class SettingsStore(private val context: Context) {
    suspend fun load(): Settings {
        val p = context.settingsStore.data.first()
        return Settings(p[booleanPreferencesKey("reconnect")] ?: true, p[booleanPreferencesKey("autoConnect")] ?: false,
            p[stringPreferencesKey("dns")] ?: "1.1.1.1", p[stringPreferencesKey("graphics")] ?: "Balanced",
            p[booleanPreferencesKey("reduceMotion")] ?: false, p[stringPreferencesKey("appMode")] ?: "all",
            p[stringSetPreferencesKey("packages")] ?: emptySet(), if(com.signet.client.BuildConfig.SIGNET_ACCOUNT)SignetLinks.CABINET else "", true,
            p[stringPreferencesKey("probes")]?.let { runCatching { ProbeSettings.parse(it) }.getOrNull() }?:ProbeSettings(),p[booleanPreferencesKey("automaticUpdates")]?:true,
            p[stringPreferencesKey("connectionCheckUrl")]?.takeIf{com.signet.core.SubscriptionMetadata.safeLink(it)!=null}?:"https://example.com",p[booleanPreferencesKey("logging")]?:false)
    }
    suspend fun save(s: Settings) = context.settingsStore.edit {
        it[booleanPreferencesKey("reconnect")] = s.reconnect; it[booleanPreferencesKey("autoConnect")] = s.autoConnect
        it[stringPreferencesKey("dns")] = s.dns; it[stringPreferencesKey("graphics")] = s.graphics
        it[booleanPreferencesKey("reduceMotion")] = s.reduceMotion; it[stringPreferencesKey("appMode")] = s.appMode
        it[stringSetPreferencesKey("packages")] = s.packages; it[stringPreferencesKey("cabinet")] = if(com.signet.client.BuildConfig.SIGNET_ACCOUNT)SignetLinks.CABINET else ""
        it[booleanPreferencesKey("hwid")] = true
        require(com.signet.core.SubscriptionMetadata.safeLink(s.connectionCheckUrl)!=null)
        it[stringPreferencesKey("connectionCheckUrl")] = s.connectionCheckUrl
        it[stringPreferencesKey("probes")] = s.probes.validated().json().toString()
        it[booleanPreferencesKey("automaticUpdates")] = s.automaticUpdates
        it[booleanPreferencesKey("logging")] = s.logging
    }
}
