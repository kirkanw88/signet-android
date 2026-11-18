package com.signet.core

import org.json.JSONArray
import org.json.JSONObject

/** Only the app-owned virtual DNS endpoint is intercepted; provider rules keep their relative order. */
object AndroidDns {
    const val ADDRESS="172.19.0.2"
    fun adapt(config:JSONObject,fallback:String):JSONObject {
        val out=JSONObject(config.toString())
        if(!out.has("dns")) {
            require(TemplateCompatibility.numericAddress(fallback))
            val address=if(fallback.contains(':'))"[$fallback]"else fallback
            out.put("dns",JSONObject().put("servers",JSONArray().put("tcp://$address:53")))
        }
        val upstream=out.getJSONArray("outbounds")
        val tags=(0 until upstream.length()).map{upstream.getJSONObject(it).optString("tag")}.toSet()
        val existing=(0 until upstream.length()).map{upstream.getJSONObject(it)}.firstOrNull{it.optString("protocol")=="dns"&&it.optString("tag").isNotBlank()}
        var tag=existing?.getString("tag")?:"signet-device-dns";var suffix=0
        if(existing==null){
            while(tag in tags){suffix++;tag="signet-device-dns-$suffix"}
            // Selectors are prefixes, including "" (all). Keep the new DNS handler outside AUTO.
            if(tags.any{it.isNotEmpty()&&tag.startsWith(it)}){
                val first=(33..0x10ff).first{code->tags.none{it.startsWith(code.toChar())}}.toChar()
                tag="$first$tag"
            }
            fun excludeAdapter(owner:JSONObject?,key:String){
                val selectors=owner?.optJSONArray(key)?:return
                val values=(0 until selectors.length()).map{selectors.getString(it)}
                if(values.none{tag.startsWith(it)})return
                val members=tags.filter{it.isNotEmpty()&&values.any(it::startsWith)}
                var absent="$tag-unused";while(tags.any{it.startsWith(absent)})absent+="-"
                owner.put(key,JSONArray(members.ifEmpty{listOf(absent)}))
            }
            val balancers=out.optJSONObject("routing")?.optJSONArray("balancers")
            if(balancers!=null)for(i in 0 until balancers.length())excludeAdapter(balancers.getJSONObject(i),"selector")
            excludeAdapter(out.optJSONObject("observatory"),"subjectSelector")
            excludeAdapter(out.optJSONObject("burstObservatory"),"subjectSelector")
            upstream.put(JSONObject().put("tag",tag).put("protocol","dns").put("settings",JSONObject()))
        }
        val routing=out.optJSONObject("routing")?:JSONObject()
        val rules=JSONArray().put(JSONObject().put("type","field")
            .put("inboundTag",JSONArray().put(out.getJSONArray("inbounds").getJSONObject(0).getString("tag")))
            .put("ip",JSONArray().put("$ADDRESS/32")).put("port","53").put("network","tcp,udp").put("outboundTag",tag))
        routing.optJSONArray("rules")?.let{original->for(i in 0 until original.length())rules.put(original.get(i))}
        routing.put("rules",rules);out.put("routing",routing)
        return out
    }
}
