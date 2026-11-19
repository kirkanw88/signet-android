package com.signet.core

/** Only a bounded machine code leaves this classifier; never log native error text. */
object ProbeErrors {
    val codes=setOf("DNS","TIMEOUT","TLS","CONNECT","IO","CORE_BUSY","CORE_ERROR","CONFIG_ERROR","DNS_SETUP_ERROR","ASSET_ERROR","NO_NETWORK","PROBE_ERROR","SERVICE_ERROR","HTTP_ERROR")
    fun native(message:String):String {
        val text=message.take(4096).lowercase(java.util.Locale.ROOT)
        return when {
            listOf("no such host","lookup ","resolve host","dns").any(text::contains)->"DNS"
            listOf("timeout","deadline exceeded","timed out").any(text::contains)->"TIMEOUT"
            listOf("certificate","tls handshake","x509").any(text::contains)->"TLS"
            listOf("connection refused","network unreachable","no route to host","connection reset").any(text::contains)->"CONNECT"
            listOf("status code","http status").any(text::contains)->"HTTP_ERROR"
            text.contains("isolated process")->"CORE_BUSY"
            listOf("failed to build","failed to parse","invalid","not found","geosite","geoip").any(text::contains)->"CONFIG_ERROR"
            else->"CORE_ERROR"
        }
    }
}
