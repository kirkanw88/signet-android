package com.signet.core

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64

object MobileAuth {
    const val REDIRECT="com.signet.client://oauth/callback"
    fun challenge(verifier:String):String {
        require(verifier.matches(Regex("[A-Za-z0-9._~-]{43,128}")))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    }
    fun code(callback:String,state:String,created:Long,now:Long):String {
        val uri=URI(callback)
        require(uri.scheme=="com.signet.client"&&uri.rawAuthority=="oauth"&&uri.path=="/callback"&&uri.fragment==null)
        require(now-created in 0L..600_000L)
        val pairs=uri.rawQuery.orEmpty().split('&').map{part->val p=part.split('=',limit=2);require(p.size==2);p[0] to URLDecoder.decode(p[1],"UTF-8")}
        require(pairs.map{it.first}.toSet()==setOf("state","code")&&pairs.size==2)
        val values=pairs.toMap()
        require(MessageDigest.isEqual(values.getValue("state").toByteArray(),state.toByteArray()))
        return values.getValue("code").also{require(it.matches(Regex("[A-Za-z0-9_-]{32,128}")))}
    }
}
