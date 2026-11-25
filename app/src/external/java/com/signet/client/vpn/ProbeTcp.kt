package com.signet.client.vpn

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** Try all DNS answers within one budget instead of declaring a host down on its first address. */
internal object ProbeTcp {
    fun connect(addresses:List<InetAddress>,port:Int,timeoutMs:Int,socket:()->Socket={Socket()}):Long {
        require(addresses.isNotEmpty())
        val deadline=System.nanoTime()+timeoutMs*1_000_000L
        var failure:Exception=SocketTimeoutException()
        for((index,address) in addresses.withIndex()) {
            if(Thread.currentThread().isInterrupted)throw java.io.InterruptedIOException()
            val remaining=((deadline-System.nanoTime())/1_000_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if(remaining<=0)break
            // Reserve time for the other IPv4/IPv6 answers if one route silently drops SYNs.
            val budget=(remaining/(addresses.size-index)).coerceAtLeast(1)
            try {socket().use{ val start=System.nanoTime(); it.connect(InetSocketAddress(address,port),budget); return ((System.nanoTime()-start)/1_000_000L).coerceAtLeast(1) }}
            catch(e:Exception){failure=e}
        }
        throw failure
    }
}
