package com.signet.client.graphics

import android.content.Context
import android.graphics.*
import com.signet.client.BuildConfig
import com.signet.client.data.awaitResponse
import com.signet.client.data.readLimited
import com.signet.core.CapitalTiles
import kotlinx.coroutines.*
import okhttp3.*
import java.io.File
import java.util.concurrent.TimeUnit

data class CapitalCity(val code:String,val name:String,val latitude:Double,val longitude:Double)

/** One bounded bitmap; HTTP cache is private, temporary, and capped at 8 MiB. */
class CapitalImagery(context:Context) {
    private val cache=Cache(File(context.cacheDir,"capital-imagery"),8L*1024*1024)
    private val client=OkHttpClient.Builder().cache(cache).followRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(6,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS).build()
    suspend fun load(city:CapitalCity):Bitmap=withContext(Dispatchers.IO){withTimeout(25_000){
        check(BuildConfig.MAPTILER_API_KEY.isNotBlank()){"IMAGERY_NOT_CONFIGURED"}
        val plan=CapitalTiles.plan(city.latitude,city.longitude)
        var mosaic:Bitmap?=null
        try {
            var tileSize=0
            for(tile in plan.tiles){
                ensureActive()
                val url=HttpUrl.Builder().scheme("https").host("api.maptiler.com")
                    .addPathSegments("tiles/satellite-v2/${plan.zoom}/${tile.x}/${tile.y}")
                    .addQueryParameter("key",BuildConfig.MAPTILER_API_KEY).build()
                val request=Request.Builder().url(url).header("User-Agent","SignetVPN-Capital/1").build()
                val bytes=client.newCall(request).awaitResponse().use{response->
                    check(response.code==200){"IMAGERY_HTTP_${response.code}"}
                    val body=checkNotNull(response.body)
                    require(body.contentLength()<=512*1024)
                    body.byteStream().use{it.readLimited(512*1024)}
                }
                val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                require(bounds.outWidth==bounds.outHeight&&bounds.outWidth in setOf(256,512))
                if(mosaic==null){tileSize=bounds.outWidth;mosaic=Bitmap.createBitmap(tileSize*3,tileSize*3,Bitmap.Config.RGB_565)}
                require(bounds.outWidth==tileSize)
                val bitmap=checkNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inPreferredConfig=Bitmap.Config.RGB_565}))
                try{Canvas(checkNotNull(mosaic)).drawBitmap(bitmap,tile.column*tileSize.toFloat(),tile.row*tileSize.toFloat(),null)}finally{bitmap.recycle()}
            }
            val size=tileSize*2
            val left=(plan.centerX*tileSize-size/2).toInt().coerceIn(0,tileSize*3-size)
            val top=(plan.centerY*tileSize-size/2).toInt().coerceIn(0,tileSize*3-size)
            Bitmap.createBitmap(checkNotNull(mosaic),left,top,size,size)
        }finally{mosaic?.recycle()}
    }}
    fun close(){client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown();runCatching{cache.close()}}
}
