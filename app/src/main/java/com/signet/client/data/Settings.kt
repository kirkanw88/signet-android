package com.signet.client.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import com.signet.core.ProbeSettings

val Context.settingsStore by preferencesDataStore("signet_settings")
data class Settings(
    val reconnect: Boolean = true, val autoConnect: Boolean = false, val dns: String = "1.1.1.1",
    val graphics: String = "Balanced", val reduceMotion: Boolean = false,
    val appMode: String = "all", val packages: Set<String> = emptySet(), val cabinet: String = "",
    val hwid: Boolean = false, val probes:ProbeSettings=ProbeSettings(),val automaticUpdates:Boolean=true
)
class SettingsStore(private val context: Context) {
    suspend fun load(): Settings {
        val p = context.settingsStore.data.first()
        return Settings(p[booleanPreferencesKey("reconnect")] ?: true, p[booleanPreferencesKey("autoConnect")] ?: false,
            p[stringPreferencesKey("dns")] ?: "1.1.1.1", p[stringPreferencesKey("graphics")] ?: "Balanced",
            p[booleanPreferencesKey("reduceMotion")] ?: false, p[stringPreferencesKey("appMode")] ?: "all",
            p[stringSetPreferencesKey("packages")] ?: emptySet(), p[stringPreferencesKey("cabinet")] ?: "https://signet-cab.com", p[booleanPreferencesKey("hwid")] ?: false,
            p[stringPreferencesKey("probes")]?.let { runCatching { ProbeSettings.parse(it) }.getOrNull() }?:ProbeSettings(),p[booleanPreferencesKey("automaticUpdates")]?:true)
    }
    suspend fun save(s: Settings) = context.settingsStore.edit {
        it[booleanPreferencesKey("reconnect")] = s.reconnect; it[booleanPreferencesKey("autoConnect")] = s.autoConnect
        it[stringPreferencesKey("dns")] = s.dns; it[stringPreferencesKey("graphics")] = s.graphics
        it[booleanPreferencesKey("reduceMotion")] = s.reduceMotion; it[stringPreferencesKey("appMode")] = s.appMode
        it[stringSetPreferencesKey("packages")] = s.packages; it[stringPreferencesKey("cabinet")] = s.cabinet
        it[booleanPreferencesKey("hwid")] = s.hwid
        it[stringPreferencesKey("probes")] = s.probes.validated().json().toString()
        it[booleanPreferencesKey("automaticUpdates")] = s.automaticUpdates
    }
}
