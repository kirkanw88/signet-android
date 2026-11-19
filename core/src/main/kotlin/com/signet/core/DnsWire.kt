package com.signet.core

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.IDN
import java.net.InetAddress

/** Bounded DNS wire codec for the app-owned DNS bridge. No external resolver or pinned IPs. */
object DnsWire {
    private fun normalized(host:String)=IDN.toASCII(host.trimEnd('.')).lowercase(java.util.Locale.ROOT).also{require(it.length in 1..253)}
    fun query(host:String,id:Int,type:Int):ByteArray {
        require(type==1||type==28)
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use{out->
            out.writeShort(id);out.writeShort(0x100);out.writeShort(1);repeat(3){out.writeShort(0)}
            normalized(host).split('.').forEach{label->val raw=label.toByteArray(Charsets.US_ASCII);require(raw.size in 1..63);out.writeByte(raw.size);out.write(raw)}
            out.writeByte(0);out.writeShort(type);out.writeShort(1)
        }
        return bytes.toByteArray()
    }
    fun addresses(packet:ByteArray,host:String,id:Int,type:Int):List<InetAddress> {
        require(type==1||type==28)
        require(packet.size in 12..65535)
        fun word(at:Int):Int {require(at>=0&&at+2<=packet.size);return ((packet[at].toInt() and 255) shl 8) or (packet[at+1].toInt() and 255)}
        fun name(start:Int):Pair<String,Int> {
            var at=start;var end=-1;val labels=mutableListOf<String>();val visited=mutableSetOf<Int>()
            repeat(128){
                require(at in packet.indices&&visited.add(at));val length=packet[at].toInt() and 255
                if(length==0)return labels.joinToString(".") to if(end<0)at+1 else end
                if(length and 0xC0==0xC0){if(end<0)end=at+2;at=word(at) and 0x3FFF}
                else {require(length in 1..63&&at+1+length<=packet.size);labels.add(String(packet,at+1,length,Charsets.US_ASCII));at+=length+1}
            }
            error("Invalid DNS compression")
        }
        val flags=word(2)
        require(word(0)==(id and 0xFFFF)&&flags and 0x8000!=0&&flags and 0x7A0F==0&&word(4)==1)
        val question=name(12);var at=question.second
        require(question.first.equals(normalized(host),true)&&word(at)==type&&word(at+2)==1);at+=4
        val count=word(6);require(count<=256);val result=mutableListOf<InetAddress>()
        repeat(count){
            at=name(at).second
            val recordType=word(at);val recordClass=word(at+2);val length=word(at+8);at+=10
            require(at+length<=packet.size)
            if(recordClass==1&&recordType==type&&length==(if(type==1)4 else 16))result.add(InetAddress.getByAddress(packet.copyOfRange(at,at+length)))
            at+=length
        }
        return result.distinctBy{it.hostAddress}
    }
}
