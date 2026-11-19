package com.signet.client.vpn

import org.junit.Assert.*
import org.junit.Test
import java.net.*

class ProbeTcpTest {
    @Test fun fallsBackAfterFirstAddressRefusesAndClosesAllSockets(){
        ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { listener->
            val sockets=mutableListOf<Socket>()
            ProbeTcp.connect(listOf(InetAddress.getByName("127.0.0.2"),InetAddress.getByName("127.0.0.1")),listener.localPort,1000){Socket().also{sockets.add(it)}}
            listener.soTimeout=1000;listener.accept().close()
            assertEquals(2,sockets.size);assertTrue(sockets.all{it.isClosed})
        }
    }
    @Test fun failedProbeClosesItsSocketAndDoesNotInventLatency(){
        val port=ServerSocket(0).use{it.localPort};val socket=Socket()
        try {ProbeTcp.connect(listOf(InetAddress.getLoopbackAddress()),port,500){socket};fail("Expected connection failure")}
        catch(expected:java.io.IOException){assertTrue(socket.isClosed)}
    }
}
