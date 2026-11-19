package com.signet.client.data

import com.signet.core.*
import kotlinx.coroutines.CancellationException
import okhttp3.*
import java.io.IOException

data class DownloadedSubscription(val text:String,val headers:Headers,val url:HttpUrl)

/** Prefer a provider's complete Xray profile, without replacing working URI-only subscriptions. */
object SubscriptionDownload {
    private const val LIMIT=4*1024*1024
    private fun xray(text:String)=runCatching {
        val decoded=Profiles.decode(text)
        if(!decoded.startsWith("{")&&!decoded.startsWith("["))false
        else Profiles.parseJson(decoded).all{
            val config=org.json.JSONObject(it.config);val out=config.getJSONArray("outbounds")
            (0 until out.length()).all{index->out.getJSONObject(index).optString("protocol").isNotBlank()}&&TemplateCompatibility.inspect(config).accepted
        }
    }.getOrDefault(false)
    suspend fun fetch(client:OkHttpClient,original:String,userAgent:String,device:Map<String,String>,event:(String)->Unit={}):DownloadedSubscription {
        require(SubscriptionMetadata.safeLink(original)!=null)
        require(!client.followRedirects){"Subscription redirects must be checked explicitly"}
        val origin=original.toHttpUrlCompat()
        suspend fun get(url:String,restricted:HttpUrl?=null,extended:Boolean=false):DownloadedSubscription {
            var next=url
            repeat(6){
                require(SubscriptionMetadata.safeLink(next)!=null)
                val target=next.toHttpUrlCompat()
                if(restricted!=null)require(target.scheme==restricted.scheme&&target.host==restricted.host&&target.port==restricted.port)
                // Remnawave hard-codes /^Happ\// as an extended-metadata capability token.
                // Advertise compatibility, never an invented Happ version; retain our actual product identity.
                val agent=if(extended)"Happ/compatible $userAgent"else userAgent
                val request=Request.Builder().url(target).header("User-Agent",agent).header("Accept","application/json, text/plain")
                // Device identity is never forwarded to another origin, even through redirects.
                if(target.scheme==origin.scheme&&target.host==origin.host&&target.port==origin.port)device.forEach{(name,value)->request.header(name,value)}
                val call=client.newCall(request.build())
                if(restricted!=null)call.timeout().timeout(8,java.util.concurrent.TimeUnit.SECONDS)
                call.awaitResponse().use { response->
                    if(response.code in 300..399){
                        next=response.request.url.resolve(response.header("Location")?:error("Нет адреса перенаправления"))?.toString()?:error("Неверный адрес")
                    } else {
                        require(response.isSuccessful){"HTTP ${response.code}"}
                        val body=response.body?:error("Пустой ответ")
                        require(body.contentLength()<=LIMIT)
                        val raw=body.byteStream().use{String(it.readLimited(LIMIT),Charsets.UTF_8)}
                        return DownloadedSubscription(raw,response.headers,response.request.url)
                    }
                }
            }
            error("Слишком много перенаправлений")
        }
        val initial=get(original)
        val originalJson=xray(initial.text)
        val needsDescriptions=originalJson&&initial.headers["profile-title"]!=null&&runCatching{Profiles.parseJson(Profiles.decode(initial.text)).none{!it.description.isNullOrBlank()}}.getOrDefault(false)
        if(originalJson&&!needsDescriptions){event("JSON_ORIGINAL");return initial}
        val decoded=Profiles.decode(initial.text)
        // Probe only after receiving an actual URI subscription, never an auth/error page or encrypted data.
        val links=decoded.lineSequence().map{it.trim()}.filter{it.isNotBlank()}.toList()
        if(!originalJson&&(links.isEmpty()||links.any{!it.matches(Regex("[A-Za-z][A-Za-z0-9+.-]*://.+"))}))return initial
        val candidate=SubscriptionRequest.jsonUrl(initial.url.toString())
        if(candidate==initial.url.toString()&&!needsDescriptions)return initial
        try {
            val json=get(candidate,initial.url,extended=true)
            if(xray(json.text)){
                val headers=initial.headers.newBuilder()
                json.headers.forEach{(name,value)->headers.set(name,value)}
                event("JSON_SELECTED");return json.copy(headers=headers.build())
            }
        }catch(e:CancellationException){throw e}
        catch(e:IOException){/* Optional JSON endpoint unavailable: retain the successful URI response. */}
        catch(e:IllegalArgumentException){/* Unsupported endpoint, invalid JSON or forbidden redirect. */}
        catch(e:IllegalStateException){/* Missing redirect/body or redirect loop on optional endpoint. */}
        event("JSON_UNAVAILABLE_ORIGINAL_RETAINED")
        return initial
    }
    private fun String.toHttpUrlCompat()=Request.Builder().url(this).build().url
}
