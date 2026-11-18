package com.signet.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

data class ImportedApps(val packages:Set<String>,val mode:String?,val rejected:List<String> = emptyList())

/** Clipboard formats contain package identifiers, never routes, executable paths or fetched URLs. */
object AppRoutingTransfer {
    private val packageName=Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
    fun parse(raw:String):ImportedApps {
        require(raw.toByteArray().size<=256*1024)
        val text=raw.trim().removePrefix("\uFEFF")
        if(text.startsWith("{")) {
            val j=JSONObject(text)
            if(j.has("schemaVersion")||j.has("type"))return AppRouting.parse(text).let{ImportedApps(it.packages,it.mode)}
            require(j.keySet().all{it in setOf("packages","apps","mode")})
            require(j.has("packages") xor j.has("apps"))
            val list=j.getJSONArray(if(j.has("packages"))"packages"else"apps")
            return parseList((0 until list.length()).map{list.getString(it)},j.optString("mode").takeIf{it.isNotBlank()}?.let(::mode))
        }
        if(Regex("^\\[\\s*(?:\"|])").containsMatchIn(text)) {
            val array=runCatching{JSONArray(text)}.getOrNull()
            if(array!=null)return parseList((0 until array.length()).map{array.getString(it)},null)
        }
        val headers=text.lines().map{it.trim().removePrefix("#").trim()}
        if(headers.any{it.startsWith("per-app-proxy-")}) {
            val fields=headers.associate{val pair=it.split(':',limit=2);require(pair.size==2);pair[0].trim() to pair[1].trim()}
            require(fields.keys.all{it in setOf("per-app-proxy-list","per-app-proxy-list-set","per-app-proxy-list-invert","per-app-proxy-mode","per-app-proxy-enable")})
            val lists=fields.keys.filter{it.startsWith("per-app-proxy-list")};require(lists.size==1)
            val key=lists.single();val value=fields.getValue(key)
            val supplied=fields["per-app-proxy-mode"]?.let(::mode)
            val chosen=if(fields["per-app-proxy-enable"]=="0")"all"else if(key.endsWith("-invert"))"exclude"else supplied
            return parseList(decode(value).split(Regex("[,;\\s]+")),chosen)
        }
        val decoded=decode(text)
        // Chat clients may format package names as Markdown links; accept the label only when URL agrees.
        val unlinked=Regex("\\[([^]\\s]+)]\\(https://([^/\\s)]+)/?\\)").replace(decoded){m->
            if(m.groupValues[1]==m.groupValues[2])m.groupValues[1]else"INVALID_LINK"
        }
        return parseList(unlinked.split(Regex("[,;\\s]+")),null)
    }
    private fun decode(text:String)=if(text.startsWith("base64:",true)){
        val encoded=text.substringAfter(':');require(encoded.length<=350000)
        String(Base64.getDecoder().decode(encoded),Charsets.UTF_8).also{require(it.toByteArray().size<=256*1024)}
    }else text
    private fun mode(value:String)=when(value.lowercase()){
        "on","proxy","include"->"include";"bypass","exclude"->"exclude";"off","all"->"all";else->error("Неизвестный режим")
    }
    private fun parseList(values:List<String>,mode:String?):ImportedApps {
        val entries=values.filter{it.isNotBlank()};require(entries.size<=2000)
        val valid=entries.filter{it.length<=255&&packageName.matches(it)}.toSet()
        val rejected=entries.filterNot{it in valid}.distinct()
        require(valid.isNotEmpty()||mode=="all"&&entries.isEmpty()){"Нет имён пакетов Android"}
        return ImportedApps(valid,mode,rejected)
    }
    fun plain(packages:Set<String>)=packages.sorted().joinToString("\n")
}
