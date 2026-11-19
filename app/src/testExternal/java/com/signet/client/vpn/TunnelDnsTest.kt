package com.signet.client.vpn

import okhttp3.Dns
import org.junit.Test
import org.junit.Assert.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TunnelDnsTest {
    @Test fun staleSystemFailureRecoversThroughTcpDnsWithoutInventedAddresses(){
        ServerSocket(0,2,InetAddress.getByName("127.0.0.1")).use{server->
            val executor=Executors.newSingleThreadExecutor()
            try{
                val worker=executor.submit{repeat(2){server.accept().use{socket->
                    socket.soTimeout=2000
                    val input=DataInputStream(socket.getInputStream());val query=ByteArray(input.readUnsignedShort());input.readFully(query)
                    val a=query[query.size-3].toInt()==1
                    query[2]=0x81.toByte();query[3]=0x80.toByte();query[7]=if(a)1 else 0
                    val output=DataOutputStream(socket.getOutputStream());output.writeShort(query.size+if(a)16 else 0);output.write(query)
                    if(a){output.writeShort(0xC00C);output.writeShort(1);output.writeShort(1);output.writeInt(60);output.writeShort(4);output.write(byteArrayOf(127,0,0,7))};output.flush()
                }}}
                val failed=object:Dns{override fun lookup(hostname:String):List<InetAddress>{throw UnknownHostException("cached NXDOMAIN fixture")}}
                val answers=TunnelDns(failed,"127.0.0.1",server.localPort).lookup("probe.example")
                assertEquals(listOf("127.0.0.7"),answers.map{it.hostAddress});worker.get(5,TimeUnit.SECONDS)
            }finally{executor.shutdownNow()}
        }
    }
    @Test fun failedTunnelDnsRemainsAFailure(){
        val port=ServerSocket(0).use{it.localPort}
        val failed=object:Dns{override fun lookup(hostname:String):List<InetAddress>{throw UnknownHostException()}}
        try{TunnelDns(failed,"127.0.0.1",port).lookup("probe.example");fail("Expected DNS failure")}
        catch(expected:UnknownHostException){assertNotNull(expected.cause)}
    }
}
