package com.signet.client.data

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.*
import com.signet.client.BuildConfig
import com.signet.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import java.util.concurrent.TimeUnit

data class UpdateState(val checking:Boolean=false,val available:AppRelease?=null,val dismissed:String="",val message:String="")
class AppUpdates(private val context:Context) {
    private val mutex=Mutex();private val _state=MutableStateFlow(UpdateState());val state=_state.asStateFlow()
    private val last=longPreferencesKey("update_last_attempt");private val cache=stringPreferencesKey("update_release");private val etag=stringPreferencesKey("update_etag");private val dismissed=stringPreferencesKey("update_dismissed")
    private val client=OkHttpClient.Builder().followRedirects(false).callTimeout(20,TimeUnit.SECONDS).build()
    private fun newer(release:AppRelease?):AppRelease?=release?.takeIf{it.version>ReleaseVersion.parse(BuildConfig.VERSION_NAME)}
    suspend fun restore(){val p=context.settingsStore.data.first();_state.value=UpdateState(available=newer(p[cache]?.let{runCatching{AppRelease.cached(it)}.getOrNull()}),dismissed=p[dismissed].orEmpty())}
    suspend fun dismiss(){val version=state.value.available?.version?.toString()?:return;context.settingsStore.edit{it[dismissed]=version};_state.update{it.copy(dismissed=version)}}
    suspend fun check(automatic:Boolean):String=mutex.withLock {
        val now=System.currentTimeMillis();val p=context.settingsStore.data.first();val elapsed=now-(p[last]?:0)
        if(automatic&&elapsed in 0 until 24*60*60*1000L){restore();return@withLock ""}
        if(!automatic&&elapsed in 0 until 60_000L&&state.value.message.isNotBlank())return@withLock state.value.message
        _state.update{it.copy(checking=true)};context.settingsStore.edit{it[last]=now}
        try {
            val request=Request.Builder().url(AppRelease.ENDPOINT).header("Accept","application/vnd.github+json").header("User-Agent","SignetAndroidUpdater").header("X-GitHub-Api-Version","2022-11-28")
            p[etag]?.takeIf{it.length<=256}?.let{request.header("If-None-Match",it)}
            val release=client.newCall(request.build()).awaitResponse().use { response->
                when(response.code) {
                    304->p[cache]?.let{AppRelease.cached(it)}?:error("Нет сохранённого выпуска")
                    404->throw UpdateUnavailable("Публичный выпуск GitHub пока недоступен. Проверка не использует токены закрытого репозитория.")
                    403,429->throw UpdateUnavailable("GitHub ограничил запросы. Повторите проверку позже.")
                    else->{kotlin.check(response.isSuccessful);val raw=response.body!!.byteStream().use{String(it.readLimited(512*1024),Charsets.UTF_8)};val parsed=AppRelease.parse(raw,Build.SUPPORTED_ABIS.toList());context.settingsStore.edit{it[cache]=parsed.json();response.header("ETag")?.takeIf{tag->tag.length<=256}?.let{tag->it[etag]=tag}};parsed}
                }
            }
            val available=newer(release);val message=if(available!=null)"Доступна новая версия ${available.version}"else"Обновлений нет. Установлена версия ${BuildConfig.VERSION_NAME}"
            _state.update{it.copy(available=available,message=message)};message
        } catch(e:CancellationException){throw e}
        catch(e:Exception){val message=if(e is UpdateUnavailable)e.message!!else"Не удалось проверить обновления. Проверьте интернет и повторите позже.";_state.update{it.copy(message=message)};message}
        finally{_state.update{it.copy(checking=false)};client.connectionPool.evictAll()}
    }
    private class UpdateUnavailable(message:String):Exception(message)
    fun close(){client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
}
