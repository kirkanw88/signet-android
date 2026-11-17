package com.signet.client.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

val Context.settingsStore by preferencesDataStore("signet_settings")
data class Settings(
    val reconnect: Boolean = true, val autoConnect: Boolean = false, val dns: String = "1.1.1.1",
    val graphics: String = "Balanced", val reduceMotion: Boolean = false,
    val appMode: String = "all", val packages: Set<String> = emptySet(), val cabinet: String = "",
    val hwid: Boolean = false
)
class SettingsStore(private val context: Context) {
    suspend fun load(): Settings {
        val p = context.settingsStore.data.first()
        return Settings(p[booleanPreferencesKey("reconnect")] ?: true, p[booleanPreferencesKey("autoConnect")] ?: false,
            p[stringPreferencesKey("dns")] ?: "1.1.1.1", p[stringPreferencesKey("graphics")] ?: "Balanced",
            p[booleanPreferencesKey("reduceMotion")] ?: false, p[stringPreferencesKey("appMode")] ?: "all",
            p[stringSetPreferencesKey("packages")] ?: emptySet(), p[stringPreferencesKey("cabinet")] ?: "", p[booleanPreferencesKey("hwid")] ?: false)
    }
    suspend fun save(s: Settings) = context.settingsStore.edit {
        it[booleanPreferencesKey("reconnect")] = s.reconnect; it[booleanPreferencesKey("autoConnect")] = s.autoConnect
        it[stringPreferencesKey("dns")] = s.dns; it[stringPreferencesKey("graphics")] = s.graphics
        it[booleanPreferencesKey("reduceMotion")] = s.reduceMotion; it[stringPreferencesKey("appMode")] = s.appMode
        it[stringSetPreferencesKey("packages")] = s.packages; it[stringPreferencesKey("cabinet")] = s.cabinet
        it[booleanPreferencesKey("hwid")] = s.hwid
    }
}
