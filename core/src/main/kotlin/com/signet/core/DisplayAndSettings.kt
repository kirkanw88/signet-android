package com.signet.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale

object DisplayText {
    fun clean(text:String,max:Int=240):String = text.codePoints().toArray().filter {
        it>=32 && it !in 0x7f..0x9f && it !in 0x202a..0x202e && it !in 0x2066..0x2069 && it !in 0x1f1e6..0x1f1ff && it!=0xfeff
    }.fold(StringBuilder()) { b,c->b.appendCodePoint(c) }.toString().replace(Regex("\\s+")," ").trim().take(max)
}

data class ServerDisplayModel(val id:String,val title:String,val description:String?,val country:String?,val group:String,val isAuto:Boolean,val transport:String) {
    companion object {
        fun all(servers:List<Server>,hostNames:Boolean=false):Map<String,ServerDisplayModel> {
            val bases=servers.associate{it.id to from(it,hostNames)}
            val titles=servers.groupingBy{(it.countryCode?:"")+":"+bases.getValue(it.id).title}.eachCount()
            val positions=mutableMapOf<String,Int>()
            return servers.associate { server->
                val base=bases.getValue(server.id);val key=(server.countryCode?:"")+":"+base.title
                val n=(positions[key]?:0)+1;positions[key]=n
                server.id to base.copy(title=if((titles[key]?:0)>1)"${base.title} №$n"else base.title)
            }
        }
        fun from(server:Server,hostNames:Boolean=false):ServerDisplayModel {
            val code=server.countryCode
            val white=server.name.contains(Regex("(?i)signet[ _-]*white"))
            val gaming=Regex("(?i)gaming|game|игров").containsMatchIn(server.name+" "+server.description.orEmpty())
            val name=when {
                white->"Signet_White"
                gaming->if(code!=null)"${server.countryName} · Игровой"else"Игровой"
                else->server.countryName
            }
            val transport=runCatching {
                val all=JSONObject(server.config).getJSONArray("outbounds")
                val outbound=(0 until all.length()).map{all.getJSONObject(it)}.firstOrNull{it.optString("tag")==server.outboundTag}?:all.getJSONObject(0)
                val protocol=outbound.optString("protocol").uppercase(Locale.ROOT)
                val stream=outbound.optJSONObject("streamSettings")
                val network=stream?.optString("network")?.uppercase(Locale.ROOT).orEmpty().let{if(it=="RAW")"TCP"else it}
                val security=stream?.optString("security")?.uppercase(Locale.ROOT).orEmpty().takeIf{it.isNotBlank()&&it!="NONE"}
                listOfNotNull(protocol,network.takeIf{it.isNotBlank()},security).joinToString(" / ")
            }.getOrDefault("Xray")
            val autoTitle=if(Regex("(?i)signet[ _-]+auto(?:\\b|$)").containsMatchIn(server.name))"Signet_Auto"else"AUTO"
            val title=if(hostNames)DisplayText.clean(server.name,200).ifBlank{"Профиль"}else if(server.isAuto&&!white)autoTitle else name
            return ServerDisplayModel(server.id,title,
                server.description?.let{DisplayText.clean(it.replace(Regex("[\\r\\n]+")," "),300)}?.takeIf{it.isNotBlank()&&!it.equals(name,true)}?:if(server.isAuto)"Автоматическая маршрутизация"else null,
                if(server.isAuto)null else code,if(server.isAuto)"AUTO"else server.countryName,server.isAuto,transport)
        }
    }
}

enum class ProbeMode { TCP, HTTPS, XRAY, OUTBOUND }
data class ProbeSettings(val mode:ProbeMode=ProbeMode.OUTBOUND,val url:String="https://www.gstatic.com/generate_204",val timeoutMs:Int=8000,val attempts:Int=1,val intervalMs:Int=1000,val parallelism:Int=3,val automatic:Boolean=false,val autoMinutes:Int=15) {
    fun validated():ProbeSettings {val uri=URI(url);require(uri.scheme=="https"&&!uri.host.isNullOrBlank()&&uri.rawUserInfo==null);require(timeoutMs in 500..30000 && attempts in 1..3 && intervalMs in 200..60000 && parallelism in 1..5 && autoMinutes in 15..360);return this}
    fun json():JSONObject=JSONObject().put("mode",mode.name).put("url",url).put("timeoutMs",timeoutMs).put("attempts",attempts).put("intervalMs",intervalMs).put("parallelism",parallelism).put("automatic",automatic).put("autoMinutes",autoMinutes)
    companion object {fun parse(raw:String):ProbeSettings {val j=JSONObject(raw);return ProbeSettings(ProbeMode.valueOf(j.getString("mode")),j.getString("url"),j.getInt("timeoutMs"),j.getInt("attempts"),j.getInt("intervalMs"),j.getInt("parallelism"),j.getBoolean("automatic"),j.getInt("autoMinutes")).validated()} }
}

data class AppRouting(val mode:String,val packages:Set<String>) {
    fun json():String=JSONObject().put("schemaVersion",1).put("type","signet-app-routing").put("mode",mode).put("packages",JSONArray(packages.sorted())).put("options",JSONObject()).toString(2)
    companion object {
        fun parse(raw:String):AppRouting {
            require(raw.toByteArray().size<=256*1024)
            val j=JSONObject(raw);require(j.getInt("schemaVersion")==1&&j.getString("type")=="signet-app-routing")
            require(j.keySet().all { it in setOf("schemaVersion","type","mode","packages","options") })
            val mode=j.getString("mode");require(mode in setOf("all","include","exclude"))
            val list=j.getJSONArray("packages");require(list.length()<=2000)
            val packages=(0 until list.length()).map{list.getString(it)}.toSet()
            require(packages.all{it.length<=255&&it.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))})
            require((j.optJSONObject("options")?.length()?:0)==0) { "Неподдерживаемые параметры" }
            return AppRouting(mode,packages)
        }
    }
}

data class ServerEndpoint(val host:String,val port:Int,val tlsName:String?)
object Endpoints {
    fun from(server:Server):ServerEndpoint? = runCatching {
        if(server.isAuto)return null
        val out=JSONObject(server.config).getJSONArray("outbounds")
        val node=(0 until out.length()).map{out.getJSONObject(it)}.firstOrNull{it.optString("tag")==server.outboundTag}?:out.getJSONObject(0)
        val settings=node.optJSONObject("settings")?:return null
        val address=settings.optJSONArray("vnext")?.optJSONObject(0)?:settings.optJSONArray("servers")?.optJSONObject(0)?:settings
        val host=address.optString("address");val port=address.optInt("port")
        require(host.isNotBlank()&&port in 1..65535)
        val stream=node.optJSONObject("streamSettings");val tls=stream?.optJSONObject("tlsSettings")?:stream?.optJSONObject("realitySettings")
        ServerEndpoint(host,port,tls?.optString("serverName")?.takeIf{it.isNotBlank()})
    }.getOrNull()
}
