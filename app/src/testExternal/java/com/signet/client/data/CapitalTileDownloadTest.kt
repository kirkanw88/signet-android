package com.signet.client.data

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.Assert.*
import java.net.UnknownHostException

class CapitalTileDownloadTest {
    private val key="fixture-client-key"
    private fun client(code:Int,bytes:ByteArray)=OkHttpClient.Builder().followRedirects(false).addInterceptor{chain->
        val request=chain.request()
        assertEquals("api.maptiler.com",request.url.host);assertEquals(key,request.url.queryParameter("key"))
        assertEquals("SignetVPN-Capital/1",request.header("User-Agent"));assertNull(request.header("X-Hwid"))
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("fixture").code(code).body(bytes.toResponseBody()).build()
    }.build()
    private suspend fun result(code:Int,body:ByteArray):ByteArray {
        val http=client(code,body)
        try{return CapitalTileDownload.fetch(http,key,17,70000,40000)}finally{http.connectionPool.evictAll();http.dispatcher.executorService.shutdown()}
    }
    @Test fun validBytesUseOnlyImageryIdentityAndRemainUnchanged()=runBlocking {
        val bytes=byteArrayOf(1,2,3,4)
        assertArrayEquals(bytes,result(200,bytes))
    }
    @Test fun providerFailuresAreSafeAndHaveBoundedRetryPolicy()=runBlocking {
        for((status,code) in listOf(403 to CapitalError.AUTH,429 to CapitalError.QUOTA,404 to CapitalError.NO_IMAGERY,503 to CapitalError.SERVICE,302 to CapitalError.HTTP)){
            try{result(status,key.toByteArray());fail("Expected failure")}
            catch(e:CapitalFailure){assertEquals(code,e.code);assertFalse(e.message!!.contains(key));assertEquals(status==503,code.retryable)}
        }
        assertEquals(CapitalError.DNS,CapitalError.from(UnknownHostException("secret URL $key")))
    }
    @Test fun oversizedPayloadIsRejectedBeforeDecoding()=runBlocking {
        try{result(200,ByteArray(CapitalTileDownload.LIMIT+1));fail("Expected size failure")}
        catch(e:CapitalFailure){assertEquals(CapitalError.SIZE,e.code);assertFalse(e.code.retryable)}
    }
}
