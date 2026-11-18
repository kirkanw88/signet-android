package com.signet.core

import org.json.JSONObject

object TransportChecks {
    private fun nodes(config:JSONObject):List<JSONObject> {val a=config.optJSONArray("outbounds")?:return emptyList();return (0 until a.length()).map{a.getJSONObject(it)}}
    fun requiresOAuthConsent(config:JSONObject):Boolean=nodes(config).any { node->
        val x=node.optJSONObject("streamSettings")?.optJSONObject("xdriveSettings")?:return@any false
        x.optString("service") in setOf("google-drive","Google Drive") || x.optJSONObject("template")?.optJSONObject("auth")?.optString("type")=="oauth2"
    }
    fun issues(config:JSONObject):List<TemplateIssue> = buildList {
        nodes(config).forEach { node->
            val stream=node.optJSONObject("streamSettings")?:return@forEach
            val network=stream.optString("network");val security=stream.optString("security")
            if(stream.optJSONObject("tlsSettings")?.optBoolean("allowInsecure")==true)add(TemplateIssue("TLS_INSECURE","Профиль отключает обычную проверку TLS; проверьте доверие и pin/certificate settings у поставщика"))
            if(network=="xdrive") {
                val mux=node.optJSONObject("mux")
                if(mux?.optBoolean("enabled")!=true || mux.optInt("concurrency")<0 || mux.optInt("xudpConcurrency")<0 || mux.optString("xudpProxyUDP443")=="skip")add(TemplateIssue("XDRIVE_MUX","XDrive требует включённый Mux, неотрицательные concurrency и xudpConcurrency, без xudpProxyUDP443=skip",true))
                val x=stream.optJSONObject("xdriveSettings")
                if(x==null||x.optString("service") !in setOf("google-drive","Google Drive","template","s3","local"))add(TemplateIssue("XDRIVE_BACKEND","Неизвестный или отсутствующий XDrive backend в закреплённом ядре",true))
                if(node.optString("protocol")=="vless") {
                    val servers=node.optJSONObject("settings")?.optJSONArray("vnext")
                    if(servers!=null)for(i in 0 until servers.length()) {
                        val users=servers.getJSONObject(i).optJSONArray("users")?:continue
                        if((0 until users.length()).any{users.getJSONObject(it).optString("encryption") in setOf("","none")})add(TemplateIssue("XDRIVE_ENCRYPTION","XDrive требует VLESS Encryption; обычного encryption=none недостаточно",true))
                    }
                }
                if(x?.optString("service") in setOf("google-drive","Google Drive") && x?.optJSONArray("secrets")?.length()!=3)add(TemplateIssue("XDRIVE_OAUTH_KEYS","Google Drive backend требует три согласованных OAuth-параметра",true))
                if(x?.optString("service")=="local")add(TemplateIssue("XDRIVE_LOCAL","Local backend требует общего файлового хранилища с сервером; адрес хоста не создаёт такое хранилище"))
            }
            if(node.optString("protocol")=="masque") {
                if(network!="masque"||security!="tls")add(TemplateIssue("MASQUE_TLS","MASQUE outbound требует network=masque и security=tls",true))
                if(node.optJSONObject("mux")?.optBoolean("enabled")==true)add(TemplateIssue("MASQUE_MUX","MASQUE outbound не поддерживает Xray Mux",true))
            }
        }
    }
}
