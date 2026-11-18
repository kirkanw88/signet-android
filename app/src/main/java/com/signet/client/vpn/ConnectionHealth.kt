package com.signet.client.vpn

import com.signet.client.data.awaitResponse
import com.signet.client.data.ConnectionLog
import com.signet.core.SubscriptionMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.ConnectException
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** Checks transport, not a website's application status. Never weakens TLS or changes profile routing. */
object ConnectionHealth {
    data class Result(val category:String,val attempts:Int,val httpCode:Int?=null) {
        val healthy get()=category=="HTTPS"
        val description get()=when(category){
            "HTTPS"->"HTTPS установлен · HTTP $httpCode"
            "DNS"->"DNS не разрешил адрес проверки"
            "TLS"->"Не удалось установить защищённое TLS-соединение"
            "TIMEOUT"->"Истекло время ожидания HTTPS"
            "CONNECT"->"Не удалось установить соединение"
            else->"HTTPS-соединение прервано"
        }
    }
    private val current=MutableStateFlow<Result?>(null)
    val latest=current.asStateFlow()
    fun clear(){current.value=null}
    fun category(error:Throwable):String {
        val causes=generateSequence(error){it.cause}.take(12).toList()
        return when {
            causes.any{it is SSLException}->"TLS"
            causes.any{it is UnknownHostException}->"DNS"
            causes.any{it is SocketTimeoutException||it is java.io.InterruptedIOException}->"TIMEOUT"
            causes.any{it is ConnectException}->"CONNECT"
            else->"IO"
        }
    }
    suspend fun check(url:String,attempts:Int=3,record:Boolean=true,client:OkHttpClient?=null):Result {
        require(SubscriptionMetadata.safeLink(url)!=null)
        require(attempts in 1..3)
        val owned=client==null
        val http=client?:OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).connectTimeout(5,TimeUnit.SECONDS).readTimeout(5,TimeUnit.SECONDS).callTimeout(7,TimeUnit.SECONDS).build()
        try {
            var result=Result("IO",0)
            for(attempt in 1..attempts){
                if(record)ConnectionLog.add("HTTPS","ATTEMPT",attempt.toString())
                if(attempt>1)delay((attempt-1)*500L)
                try {
                    http.newCall(Request.Builder().url(url).get().build()).awaitResponse().use { response->
                        // Even 403/451/503 confirms HTTPS traffic; it is not a broken VPN tunnel.
                        kotlin.check(response.request.url.isHttps&&response.handshake!=null)
                        result=Result("HTTPS",attempt,response.code)
                    }
                }catch(e:CancellationException){throw e}
                catch(e:Exception){result=Result(category(e),attempt)}
                if(record)ConnectionLog.add("HTTPS",result.category,result.httpCode?.toString().orEmpty())
                if(result.healthy||result.category=="TLS")break
            }
            if(record)current.value=result
            return result
        }finally {if(owned){http.connectionPool.evictAll();http.dispatcher.executorService.shutdown()}}
    }
}
