package com.signet.core

import org.json.JSONObject
import java.net.URI
import java.util.Base64

data class SubscriptionMetadata(val announcement:String="",val webPage:String?=null,val support:String?=null,val updateHours:Int=6,val refillAt:Long?=null) {
    fun json():JSONObject=JSONObject().put("announcement",announcement).put("webPage",webPage).put("support",support).put("updateHours",updateHours).put("refillAt",refillAt)
    companion object {
        fun safeLink(raw:String?):String?=runCatching{val uri=URI(raw.orEmpty());raw?.takeIf{it.length<=2048&&uri.scheme=="https"&&!uri.host.isNullOrBlank()&&uri.rawUserInfo==null}}.getOrNull()
        fun text(raw:String?,limit:Int=2048):String {
            if(raw==null||raw.length>16384)return ""
            val decoded=if(raw.startsWith("base64:"))runCatching{String(Base64.getDecoder().decode(raw.substring(7)),Charsets.UTF_8)}.getOrDefault("")else raw
            val plain=decoded.replace("\r\n","\n").replace('\r','\n')
                .replace(Regex("(?i)<br\\s*/?>"),"\n").replace(Regex("(?i)</p>"),"\n\n").replace(Regex("<[^>]*>"),"")
                .replace("&#10;","\n").replace("&#13;","\n").replace("&nbsp;"," ").replace("&#32;"," ").replace("&#x20;"," ")
                .replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&amp;","&")
            return plain.codePoints().filter{it==10||it==9||(it>=32&&it !in 0x7f..0x9f&&it !in 0x202a..0x202e&&it !in 0x2066..0x2069&&it!=0xfeff)}
                .limit(limit.toLong()).collect({StringBuilder()},{b,c->b.appendCodePoint(c)},{a,b->a.append(b)}).toString().trim()
        }
        fun headers(get:(String)->String?):SubscriptionMetadata=SubscriptionMetadata(text(get("announce")),safeLink(get("profile-web-page-url")),safeLink(get("support-url")),get("profile-update-interval")?.toIntOrNull()?.coerceIn(6,168)?:6,get("subscription-refill-date")?.toLongOrNull()?.takeIf{it>0})
        fun parse(j:JSONObject):SubscriptionMetadata=SubscriptionMetadata(text(j.optString("announcement")),safeLink(j.optString("webPage")),safeLink(j.optString("support")),j.optInt("updateHours",6).coerceIn(6,168),j.optLong("refillAt").takeIf{it>0})
    }
}
