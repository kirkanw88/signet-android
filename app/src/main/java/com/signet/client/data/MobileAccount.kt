package com.signet.client.data

import android.content.Context
import android.net.Uri
import com.signet.core.SignetLinks
import com.signet.core.MobileAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

data class MobileAccountState(val ready:Boolean=false,val loggedIn:Boolean=false,val busy:Boolean=false,val account:JSONObject?=null,val error:String?=null)

/** Customer bearer credentials only, separate AES-GCM/Keystore vault, serialized refresh rotation. */
class MobileAccount(context:Context){
    private val vault=Vault(context,"signet-account.db","signet.account.v1")
    private val lock=Mutex()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val client=OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).followRedirects(false).retryOnConnectionFailure(false).build()
    private val json="application/json; charset=utf-8".toMediaType()
    private var saved=JSONObject()
    private val mutable=MutableStateFlow(MobileAccountState());val state=mutable.asStateFlow()
    private val base=SignetLinks.CABINET+"/api/mobile/v1"
    init{scope.launch{lock.withLock{runCatching{saved=vault.read()?.let(::JSONObject)?:JSONObject()}.onFailure{mutable.update{it.copy(error="Не удалось открыть защищённую сессию")}};mutable.update{it.copy(ready=true,loggedIn=saved.has("refresh_token"))}}}}
    private fun random()=ByteArray(48).also{SecureRandom().nextBytes(it)}.let{Base64.getUrlEncoder().withoutPadding().encodeToString(it)}
    private suspend fun persist(){vault.write(saved.toString())}
    private fun publicError(code:Int)=when(code){404,503->"API кабинета пока недоступно. Требуется обновление серверной части.";401->"Войдите в аккаунт Signet";403->"Действие недоступно этому аккаунту";409->"Данные изменились. Обновите экран";429->"Слишком много запросов. Повторите позже";else->"Не удалось выполнить запрос ($code)"}
    private class ApiError(val status:Int):Exception()
    private suspend fun execute(path:String,method:String="GET",payload:JSONObject?=null,token:String?=null):ByteArray{
        check(com.signet.client.BuildConfig.SIGNET_ACCOUNT){"Аккаунт Signet недоступен в этой редакции"}
        require(path.startsWith("/")&&!path.contains("://")&&!path.startsWith("//"))
        val request=Request.Builder().url(base+path).header("Accept","application/json").header("User-Agent","SignetAndroid/${com.signet.client.BuildConfig.VERSION_NAME}")
        token?.let{request.header("Authorization","Bearer $it")}
        request.method(method,if(method in setOf("POST","PUT"))(payload?:JSONObject()).toString().toRequestBody(json)else null)
        return client.newCall(request.build()).awaitResponse().use{response->
            if(!response.isSuccessful)throw ApiError(response.code)
            response.body?.byteStream()?.use{it.readLimited(5*1024*1024)}?:ByteArray(0)
        }
    }
    private suspend fun tokens(value:JSONObject){
        require(value.getString("access_token").length in 32..128&&value.getString("refresh_token").length in 32..128)
        saved.remove("pending");saved.put("access_token",value.getString("access_token")).put("refresh_token",value.getString("refresh_token")).put("expires_at",System.currentTimeMillis()+value.getLong("expires_in").coerceIn(1,900)*1000)
        persist();mutable.update{it.copy(loggedIn=true,error=null)}
    }
    private suspend fun refreshToken(){
        val refresh=saved.optString("refresh_token");check(refresh.isNotEmpty()){"Войдите в аккаунт Signet"}
        try{tokens(JSONObject(String(execute("/token","POST",JSONObject().put("client_id","signet-android").put("grant_type","refresh_token").put("refresh_token",refresh)),Charsets.UTF_8)))}
        catch(e:ApiError){if(e.status in setOf(400,401)){saved=JSONObject();persist();mutable.update{it.copy(loggedIn=false,account=null)}};throw e}
    }
    private suspend fun authorized(path:String,method:String="GET",payload:JSONObject?=null):ByteArray{
        check(saved.has("refresh_token")){"Войдите в аккаунт Signet"}
        if(System.currentTimeMillis()>=saved.optLong("expires_at")-30_000)refreshToken()
        return try{execute(path,method,payload,saved.getString("access_token"))}catch(e:ApiError){
            if(e.status!=401)throw e
            refreshToken();execute(path,method,payload,saved.getString("access_token"))
        }
    }
    private suspend fun <T> action(block:suspend ()->T):T?=withContext(Dispatchers.IO){
        state.first{it.ready};lock.withLock{
            mutable.update{it.copy(busy=true,error=null)}
            try{block()}catch(e:CancellationException){throw e}catch(e:Exception){mutable.update{it.copy(error=if(e is ApiError)publicError(e.status)else if(e is IllegalStateException)e.message?:"Войдите в аккаунт Signet"else"Не удалось связаться с кабинетом")};null}
            finally{mutable.update{it.copy(busy=false)}}
        }
    }
    suspend fun loginUrl(provider:String):String?=action{
        require(provider in setOf("google","yandex","telegram"))
        execute("/capabilities") // No browser redirect to a missing/unpublished API.
        val verifier=random();val state=random()
        val challenge=MobileAuth.challenge(verifier)
        saved.put("pending",JSONObject().put("verifier",verifier).put("state",state).put("created",System.currentTimeMillis()));persist()
        Uri.parse(base+"/authorize").buildUpon().appendQueryParameter("client_id","signet-android").appendQueryParameter("code_challenge",challenge).appendQueryParameter("state",state).appendQueryParameter("login_hint",provider).build().toString()
    }
    suspend fun callback(uri:Uri)=action{
        val pending=saved.optJSONObject("pending")?:error("Вход устарел. Повторите вход")
        val code=MobileAuth.code(uri.toString(),pending.getString("state"),pending.getLong("created"),System.currentTimeMillis())
        val session=JSONObject(String(execute("/token","POST",JSONObject().put("client_id","signet-android").put("grant_type","authorization_code").put("redirect_uri","com.signet.client://oauth/callback").put("code",code).put("code_verifier",pending.getString("verifier"))),Charsets.UTF_8))
        mutable.update{it.copy(account=null)};tokens(session)
        val account=JSONObject(String(authorized("/account"),Charsets.UTF_8));mutable.update{it.copy(account=account)}
    }
    suspend fun refresh()=action{JSONObject(String(authorized("/account"),Charsets.UTF_8)).also{value->mutable.update{it.copy(account=value)}}}
    suspend fun request(path:String,method:String="GET",payload:JSONObject?=null):JSONObject?=action{JSONObject(String(authorized(path,method,payload),Charsets.UTF_8))}
    suspend fun image(id:Long):ByteArray?=action{authorized("/support/images/$id")}
    suspend fun logout()=action{
        var revoked=true
        try{authorized("/logout","POST")}catch(e:Exception){if(e is CancellationException)throw e;revoked=false}
        saved=JSONObject();persist();mutable.update{it.copy(loggedIn=false,account=null,error=if(revoked)null else"Вы вышли на устройстве. Отзыв серверной сессии не подтверждён: нет связи.")}
    }
    fun close(){scope.cancel();client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown();vault.close()}
}
