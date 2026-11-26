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
    val graphics: String = if(com.signet.client.BuildConfig.SIGNET_ACCOUNT)"Balanced"else com.signet.core.UniversalDefaults.GRAPHICS, val reduceMotion: Boolean = false,
    val appMode: String = "all", val packages: Set<String> = emptySet(), val cabinet: String = if(com.signet.client.BuildConfig.SIGNET_ACCOUNT)SignetLinks.CABINET else "",
    val hwid: Boolean = true, val probes:ProbeSettings=ProbeSettings(),val automaticUpdates:Boolean=true,
    val connectionCheckUrl:String=if(com.signet.client.BuildConfig.SIGNET_ACCOUNT)"https://example.com"else com.signet.core.UniversalDefaults.CONNECTION_URL,val logging:Boolean=true,val theme:String="dark",val collapsedSubscriptions:Set<String> = emptySet(),val pinnedSubscriptions:Set<String> = emptySet(),val showTrafficRates:Boolean=true,val preferRustCore:Boolean=false
)
class SettingsStore(private val context: Context) {
    suspend fun load(): Settings {
        if(!com.signet.client.BuildConfig.SIGNET_ACCOUNT)context.settingsStore.edit{p->
            val migration=booleanPreferencesKey("health_default_0210")
            if(p[migration]!=true){
                val key=stringPreferencesKey("connectionCheckUrl")
                if(p[key]==null||p[key]?.trimEnd('/')=="https://example.com")p[key]=com.signet.core.UniversalDefaults.CONNECTION_URL
                p[migration]=true
            }
        }
        val p = context.settingsStore.data.first()
        return Settings(p[booleanPreferencesKey("reconnect")] ?: true, p[booleanPreferencesKey("autoConnect")] ?: false,
            p[stringPreferencesKey("dns")] ?: "1.1.1.1", if(com.signet.client.BuildConfig.SIGNET_ACCOUNT)p[stringPreferencesKey("graphics")] ?: "Balanced"else com.signet.core.UniversalDefaults.GRAPHICS,
            if(com.signet.client.BuildConfig.SIGNET_ACCOUNT)p[booleanPreferencesKey("reduceMotion")] ?: false else false, p[stringPreferencesKey("appMode")] ?: "all",
            p[stringSetPreferencesKey("packages")] ?: emptySet(), if(com.signet.client.BuildConfig.SIGNET_ACCOUNT)SignetLinks.CABINET else "", true,
            p[stringPreferencesKey("probes")]?.let { runCatching { ProbeSettings.parse(it) }.getOrNull() }?:ProbeSettings(),if(com.signet.client.BuildConfig.SIGNET_ACCOUNT)p[booleanPreferencesKey("automaticUpdates")]?:true else true,
            p[stringPreferencesKey("connectionCheckUrl")]?.takeIf{com.signet.core.SubscriptionMetadata.safeLink(it)!=null}?:(if(com.signet.client.BuildConfig.SIGNET_ACCOUNT)"https://example.com"else com.signet.core.UniversalDefaults.CONNECTION_URL),true,p[stringPreferencesKey("theme")]?.takeIf{it in setOf("dark","light","system")}?:"dark",p[stringSetPreferencesKey("collapsedSubscriptions")]?:emptySet(),p[stringSetPreferencesKey("pinnedSubscriptions")]?:emptySet(),p[booleanPreferencesKey("showTrafficRates")]?:true,p[booleanPreferencesKey("preferRustCore")]?:false)
    }
    suspend fun save(s: Settings) = context.settingsStore.edit {
        it[booleanPreferencesKey("reconnect")] = s.reconnect; it[booleanPreferencesKey("autoConnect")] = s.autoConnect
        it[stringPreferencesKey("dns")] = s.dns; it[stringPreferencesKey("graphics")] = if(com.signet.client.BuildConfig.SIGNET_ACCOUNT)s.graphics else com.signet.core.UniversalDefaults.GRAPHICS
        it[booleanPreferencesKey("reduceMotion")] = com.signet.client.BuildConfig.SIGNET_ACCOUNT&&s.reduceMotion; it[stringPreferencesKey("appMode")] = s.appMode
        it[stringSetPreferencesKey("packages")] = s.packages; it[stringPreferencesKey("cabinet")] = if(com.signet.client.BuildConfig.SIGNET_ACCOUNT)SignetLinks.CABINET else ""
        it[booleanPreferencesKey("hwid")] = true
        require(com.signet.core.SubscriptionMetadata.safeLink(s.connectionCheckUrl)!=null)
        it[stringPreferencesKey("connectionCheckUrl")] = s.connectionCheckUrl
        it[stringPreferencesKey("probes")] = s.probes.validated().json().toString()
        it[booleanPreferencesKey("automaticUpdates")] = if(com.signet.client.BuildConfig.SIGNET_ACCOUNT)s.automaticUpdates else true
        it.remove(booleanPreferencesKey("logging"))
        require(s.theme in setOf("dark","light","system"))
        it[stringPreferencesKey("theme")] = s.theme
        it[stringSetPreferencesKey("collapsedSubscriptions")] = s.collapsedSubscriptions
        it[stringSetPreferencesKey("pinnedSubscriptions")] = s.pinnedSubscriptions
        it[booleanPreferencesKey("showTrafficRates")] = s.showTrafficRates
        it[booleanPreferencesKey("preferRustCore")] = s.preferRustCore
    }
}
