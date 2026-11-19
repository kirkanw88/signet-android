package com.signet.client.vpn

import com.signet.core.AndroidDns
import com.signet.core.DnsWire
import okhttp3.Dns
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.*
import java.util.concurrent.ThreadLocalRandom

/** Only used for through-TUN checks: recover from Android's stale negative DNS cache via the same DNS policy. */
class TunnelDns(private val system:Dns=Dns.SYSTEM,private val address:String=AndroidDns.ADDRESS,private val port:Int=53):Dns {
    override fun lookup(hostname:String):List<InetAddress> {
        try{return system.lookup(hostname).also{if(it.isEmpty())throw UnknownHostException()}}
        catch(original:UnknownHostException){
            com.signet.client.data.ConnectionLog.add("DNS","TUNNEL_FALLBACK")
            val answers=mutableListOf<InetAddress>()
            for(type in listOf(1,28)){
                try {
                    val id=ThreadLocalRandom.current().nextInt(65536);val query=DnsWire.query(hostname,id,type)
                    // Unprotected normal socket: it MUST enter the active TUN, never the physical network.
                    Socket(Proxy.NO_PROXY).use{socket->
                        socket.soTimeout=2000;socket.connect(InetSocketAddress(address,port),2000)
                        val output=DataOutputStream(socket.getOutputStream());output.writeShort(query.size);output.write(query);output.flush()
                        val input=DataInputStream(socket.getInputStream());val length=input.readUnsignedShort();require(length in 12..65535)
                        val packet=ByteArray(length);input.readFully(packet);answers.addAll(DnsWire.addresses(packet,hostname,id,type))
                    }
                }catch(e:Exception){/* Retain genuine DNS failure if neither query obtains an address. */}
            }
            if(answers.isEmpty()){
                com.signet.client.data.ConnectionLog.add("DNS","TUNNEL_FALLBACK_FAILED")
                throw UnknownHostException("Tunnel DNS lookup failed").apply{initCause(original)}
            }
            com.signet.client.data.ConnectionLog.add("DNS","TUNNEL_FALLBACK_OK")
            return answers.distinctBy{it.hostAddress}
        }
    }
}
