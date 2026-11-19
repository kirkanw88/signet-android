package com.signet.client.graphics

import android.content.Context
import android.graphics.*
import com.signet.client.data.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

data class CapitalCity(val code:String,val name:String,val latitude:Double,val longitude:Double)
data class LandmarkPhoto(val bitmap:Bitmap,val city:String,val landmark:String,val author:String,val license:String,val source:String,val licenseUrl:String,val cityView:Boolean)

/** Offline catalogue: one decoded photograph, no service, credentials or growing photo cache. */
class CapitalImagery(context:Context) {
    private val application=context.applicationContext
    private val cleaned=AtomicBoolean(false)
    private val catalogue by lazy{application.assets.open("landmarks.json").bufferedReader().use{JSONObject(it.readText())}}
    suspend fun load(city:CapitalCity):LandmarkPhoto=withContext(Dispatchers.IO){
        if(cleaned.compareAndSet(false,true)){
            val old=java.io.File(application.cacheDir,"capital-imagery")
            runCatching{if(old.canonicalFile.parentFile==application.cacheDir.canonicalFile){
                old.listFiles()?.filter{it.isFile&&it.canonicalFile.parentFile==old.canonicalFile}?.forEach{it.delete()}
                old.delete()
            }}
        }
        val item=catalogue.optJSONObject(city.code)?:throw CapitalFailure(CapitalError.NO_IMAGERY)
        val asset=item.getString("asset")
        require(asset.matches(Regex("landmarks/[a-z]{2}\\.(webp|jpg|png)")))
        val bytes=application.assets.open(asset).use{it.readLimited(1024*1024)}
        ensureActive()
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth in 1..1280&&bounds.outHeight in 1..1280)
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inPreferredConfig=Bitmap.Config.RGB_565})?:throw CapitalFailure(CapitalError.IMAGE)
        LandmarkPhoto(bitmap,item.getString("city"),item.getString("landmark"),item.getString("author"),item.getString("license"),item.getString("source"),item.optString("licenseUrl"),item.optBoolean("cityView"))
    }
}
