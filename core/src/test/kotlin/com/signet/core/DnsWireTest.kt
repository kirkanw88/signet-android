package com.signet.core

import org.junit.Test
import kotlin.test.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class DnsWireTest {
    @Test fun compressedDnsReplyIsValidatedAndDecoded(){
        val query=DnsWire.query("probe.example",123,1)
        val reply=query.copyOf().apply{this[2]=0x81.toByte();this[3]=0x80.toByte();this[7]=1}
        val bytes=ByteArrayOutputStream();bytes.write(reply)
        DataOutputStream(bytes).use{out->out.writeShort(0xC00C);out.writeShort(1);out.writeShort(1);out.writeInt(60);out.writeShort(4);out.write(byteArrayOf(127,0,0,1))}
        assertEquals("127.0.0.1",DnsWire.addresses(bytes.toByteArray(),"probe.example",123,1).single().hostAddress)
        assertFails{DnsWire.addresses(bytes.toByteArray(),"other.example",123,1)}
        assertFails{DnsWire.addresses(bytes.toByteArray(),"probe.example",124,1)}
    }
    @Test fun loopsTruncationAndNegativeAnswersDoNotBecomeFakeAddresses(){
        val packet=DnsWire.query("probe.example",123,1).apply{this[2]=0x81.toByte();this[3]=0x83.toByte()}
        assertFails{DnsWire.addresses(packet,"probe.example",123,1)}
        packet[3]=0x80.toByte();packet[12]=0xC0.toByte();packet[13]=12
        assertFails{DnsWire.addresses(packet,"probe.example",123,1)}
        assertFails{DnsWire.addresses(ByteArray(11),"probe.example",123,1)}
    }
}
