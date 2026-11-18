package com.signet.core

import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.security.MessageDigest

object UpdateTransfer {
    const val MAX_BYTES=250L*1024*1024
    fun trustedHop(url:String):Boolean=runCatching{
        val uri=URI(url)
        uri.scheme=="https"&&uri.rawUserInfo==null&&uri.rawFragment==null&&uri.port in setOf(-1,443)&&uri.host in setOf("github.com","release-assets.githubusercontent.com","objects.githubusercontent.com")
    }.getOrDefault(false)
    fun copy(input:InputStream,output:OutputStream,size:Long,sha256:String,progress:(Long)->Unit){
        require(size in 1..MAX_BYTES&&sha256.matches(Regex("[a-fA-F0-9]{64}")))
        val hash=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(64*1024);var count=0L
        while(true){val n=input.read(buffer);if(n<0)break;if(n==0)continue
            count+=n;require(count<=size&&count<=MAX_BYTES){"Размер обновления не совпал"}
            hash.update(buffer,0,n);output.write(buffer,0,n);progress(count)
        }
        require(count==size){"Обновление загружено не полностью"}
        val digest=hash.digest().joinToString(""){"%02x".format(it)}
        require(MessageDigest.isEqual(digest.toByteArray(),sha256.lowercase().toByteArray())){"Контрольная сумма обновления не совпала"}
    }
}
