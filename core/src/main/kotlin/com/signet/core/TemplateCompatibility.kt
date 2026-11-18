package com.signet.core

import org.json.JSONObject

data class TemplateIssue(val code:String,val message:String,val error:Boolean=false)
data class TemplateReport(val inboundTag:String,val issues:List<TemplateIssue>) {
    val accepted get()=issues.none{it.error}
    fun text():String="Android TUN → $inboundTag\n"+if(issues.isEmpty())"Статическая проверка: PASS. Требуются проверка ядром и тест трафика."else issues.joinToString("\n"){"${if(it.error)"ERROR"else"WARN"} ${it.code}: ${it.message}"}
}

object TemplateCompatibility {
    /** A single Android TUN maps to the template's TUN/SOCKS/HTTP ingress, never rewrites rules. */
    fun inboundTag(config:JSONObject):String {
        config.optJSONObject("signet")?.optString("tunInboundTag")?.takeIf{it.isNotBlank()}?.let{return it}
        val a=config.optJSONArray("inbounds")
        val all=if(a==null)emptyList()else (0 until a.length()).map{a.getJSONObject(it)}
        val chosen=listOf("tun","socks","http").firstNotNullOfOrNull{protocol->all.firstOrNull{it.optString("protocol")==protocol}}
        return chosen?.optString("tag")?.takeIf{it.isNotBlank()}?:"signet-tun"
    }
    fun inspect(config:JSONObject):TemplateReport {
        val issues=mutableListOf<TemplateIssue>();val active=inboundTag(config)
        if(config.has("remnawave"))issues+=TemplateIssue("UNRENDERED_TEMPLATE","Нужен сформированный панелью Xray JSON из подписки, а не исходный injectHosts-шаблон",true)
        val out=config.optJSONArray("outbounds")
        if(out==null||out.length()==0)issues+=TemplateIssue("NO_OUTBOUNDS","Нет исходящих подключений",true)
        val tags=if(out==null)emptyList()else (0 until out.length()).map{out.getJSONObject(it).optString("tag")}.filter{it.isNotBlank()}
        if(tags.distinct().size!=tags.size)issues+=TemplateIssue("DUPLICATE_TAG","Повторяющиеся outbound tags требуют исправления в панели",true)
        val routing=config.optJSONObject("routing");val balancers=routing?.optJSONArray("balancers")
        val balancerTags=if(balancers==null)emptySet()else (0 until balancers.length()).map{balancers.getJSONObject(it).optString("tag")}.toSet()
        if(balancers!=null)for(i in 0 until balancers.length()) {
            val selectors=balancers.getJSONObject(i).optJSONArray("selector")?:continue
            if((0 until selectors.length()).none{j->tags.any{it.startsWith(selectors.getString(j))}})issues+=TemplateIssue("EMPTY_BALANCER","У balancer нет совпадающих outbound tags; проверьте результат injectHosts")
        }
        val rules=routing?.optJSONArray("rules")
        if(rules!=null)for(i in 0 until rules.length()) {
            val rule=rules.getJSONObject(i)
            val balancer=rule.optString("balancerTag")
            if(balancer.isNotBlank()&&balancer !in balancerTags)issues+=TemplateIssue("MISSING_BALANCER","Правило ${i+1} ссылается на отсутствующий balancer",true)
            val inbound=rule.optJSONArray("inboundTag")
            if(inbound!=null && (0 until inbound.length()).none{inbound.getString(it)==active})issues+=TemplateIssue("INBOUND_SCOPE","Правило ${i+1} относится к другому ingress; Android использует tag $active")
            if(rule.toString().contains("ext:"))issues+=TemplateIssue("CUSTOM_GEODATA","Правило ${i+1} требует пользовательский geodata-файл; его наличие проверит ядро")
        }
        if(config.optJSONObject("dns")!=null)issues+=TemplateIssue("DNS_PRESERVED","DNS из шаблона сохранён; системный DNS Android направляется в TUN и зависит от правил шаблона")
        return TemplateReport(active,issues)
    }
    fun numericAddress(value:String):Boolean = if(value.contains(':'))runCatching{java.net.InetAddress.getByName(value) is java.net.Inet6Address}.getOrDefault(false)
        else value.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))&&value.split('.').all{it.toInt() in 0..255}
    fun deviceDns(server:Server,fallback:String):String {
        val a=JSONObject(server.config).optJSONObject("dns")?.optJSONArray("servers")?:return fallback
        for(i in 0 until a.length()){val item=a.get(i);val address=if(item is JSONObject)item.optString("address")else item.toString();if(numericAddress(address))return address}
        return fallback
    }
}
