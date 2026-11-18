package com.signet.core

object ImportReview {
    /** Unwrap known client launchers without decoding or changing a plain HTTPS token. */
    fun normalize(raw:String):String {
        val text=raw.trim()
        val prefix=listOf("happ://add/","v2raytun://import/","streisand://import/").firstOrNull{text.startsWith(it,true)}
        val nested=if(prefix!=null)text.substring(prefix.length) else if(text.startsWith("signet://import?",true)||text.startsWith("v2rayng://install-config?",true)) {
            java.net.URI(text).rawQuery?.split('&')?.firstOrNull{it.startsWith("url=")}?.substringAfter('=')?.let{java.net.URLDecoder.decode(it,"UTF-8")}?:text
        } else return text
        val url=if(nested.startsWith("https://"))nested else java.net.URLDecoder.decode(nested,"UTF-8")
        return SubscriptionMetadata.safeLink(url)?:text
    }
    fun supported(raw: String): Boolean = runCatching {
        require(raw.toByteArray().size in 1..4*1024*1024)
        val text=Profiles.decode(normalize(raw))
        when {
            text.startsWith("https://") -> java.net.URI(text).let { it.host != null && it.rawUserInfo == null }
            text.startsWith("{") || text.startsWith("[") -> Profiles.parseJson(text).isNotEmpty()
            text.startsWith("-----BEGIN AGE ENCRYPTED FILE-----") -> true
            else -> text.isNotBlank() && text.lineSequence().filter { it.isNotBlank() }.all { line ->
                listOf("vless://","hysteria2://","hy2://","vmess://","trojan://","ss://","socks://").any { line.startsWith(it) }
            }
        }
    }.getOrDefault(false)
}
