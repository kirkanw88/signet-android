package com.signet.client.data

import kotlinx.coroutines.TimeoutCancellationException
import okhttp3.*
import java.io.*
import java.net.*
import javax.net.ssl.SSLException

enum class CapitalError(val label:String,val retryable:Boolean=false){
    DNS("Не удалось разрешить адрес снимков",true),NETWORK("Нет соединения с сервисом снимков",true),
    TIMEOUT("Сервис снимков не ответил вовремя",true),SERVICE("Сервис снимков временно недоступен",true),
    AUTH("Сервис отклонил ключ снимков"),QUOTA("Достигнут лимит запросов снимков"),
    NO_IMAGERY("Для этого участка нет снимка"),TLS("Не удалось проверить защищённое соединение"),
    SIZE("Снимок превысил допустимый размер"),IMAGE("Сервис вернул некорректный снимок"),
    CONFIG("Источник снимков не настроен"),HTTP("Сервис отклонил запрос снимка"),UNKNOWN("Не удалось загрузить снимок");
    companion object {
        fun from(error:Exception):CapitalError=when(error){
            is CapitalFailure->error.code
            is UnknownHostException->DNS
            is TimeoutCancellationException,is InterruptedIOException->TIMEOUT
            is SSLException->TLS
            is IOException->NETWORK
            is IllegalArgumentException->IMAGE
            else->UNKNOWN
        }
    }
}
class CapitalFailure(val code:CapitalError):IOException(code.name)

object CapitalTileDownload {
    const val LIMIT=512*1024
    suspend fun fetch(client:OkHttpClient,key:String,z:Int,x:Int,y:Int):ByteArray {
        if(key.isBlank())throw CapitalFailure(CapitalError.CONFIG)
        require(z in 0..22&&x in 0 until (1 shl z)&&y in 0 until (1 shl z))
        val url=HttpUrl.Builder().scheme("https").host("api.maptiler.com")
            .addPathSegments("tiles/satellite-v2/$z/$x/$y").addQueryParameter("key",key).build()
        val request=Request.Builder().url(url).header("User-Agent","SignetVPN-Capital/1").build()
        return client.newCall(request).awaitResponse().use{response->
            if(response.code!=200)throw CapitalFailure(when(response.code){
                401,403->CapitalError.AUTH;429->CapitalError.QUOTA;204,404->CapitalError.NO_IMAGERY
                in 500..599->CapitalError.SERVICE;else->CapitalError.HTTP
            })
            val body=response.body?:throw CapitalFailure(CapitalError.IMAGE)
            if(body.contentLength()>LIMIT)throw CapitalFailure(CapitalError.SIZE)
            try{body.byteStream().use{it.readLimited(LIMIT)}}catch(e:IllegalArgumentException){throw CapitalFailure(CapitalError.SIZE)}
        }
    }
}
