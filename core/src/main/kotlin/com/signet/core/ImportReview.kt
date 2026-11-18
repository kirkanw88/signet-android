package com.signet.core

object ImportReview {
    fun supported(raw: String): Boolean = runCatching {
        require(raw.toByteArray().size in 1..4*1024*1024)
        val text=Profiles.decode(raw)
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
