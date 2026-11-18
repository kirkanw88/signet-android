package com.signet.core

object SignetLinks {
    const val CABINET="https://signet-cab.com"
    const val SUPPORT="$CABINET/cabinet#help"
}

/** Remnawave's documented client-type override is /{shortUuid}/json, not an Accept-only negotiation. */
object SubscriptionRequest {
    fun jsonUrl(original:String):String {
        require(SubscriptionMetadata.safeLink(original)!=null)
        val raw=original.substringBefore('#');val query=raw.substringAfter('?',"")
        val base=raw.substringBefore('?').trimEnd('/')
        val uri=java.net.URI(raw)
        if(uri.rawPath.orEmpty().endsWith(".json",true))return raw
        if(base.endsWith("/json")||base.endsWith("/v2ray-json"))return raw
        val result=base+"/json"+if(query.isNotEmpty())"?$query"else""
        require(java.net.URI(result).host==uri.host)
        return result
    }
}
