package com.signet.client.vpn

import android.app.Service
import android.content.*
import android.net.*
import android.os.*
import com.signet.core.*
import com.signet.client.data.awaitResponse
import com.signet.client.data.readLimited
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import org.json.*
import java.io.File
import java.net.*
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class ProbeMeasurement(val delay:Long?,val status:String)
object Probes {
    private val serviceFailure get()=if(com.signet.client.BuildConfig.SIGNET_ACCOUNT)"FAILED"else"SERVICE_ERROR"
    suspend fun measure(context:Context,servers:List<Server>):Map<String,Long?> = measureDetailed(context,servers,ProbeSettings()).mapValues{it.value.delay}
    suspend fun validate(context:Context,server:Server):Boolean=measureDetailed(context,listOf(server),ProbeSettings(mode=ProbeMode.XRAY,timeoutMs=15000),validateOnly=true)[server.id]?.status=="VALID"
    suspend fun measureDetailed(context:Context,servers:List<Server>,options:ProbeSettings,validateOnly:Boolean=false):Map<String,ProbeMeasurement> = withTimeout((options.timeoutMs.toLong()+5000)*servers.size.coerceAtLeast(1)+5000) {
        options.validated();require(servers.size in 1..10)
        val array=JSONArray();servers.forEach{array.put(JSONObject().put("id",it.id).put("config",it.config))}
        val fallbackDns=if(com.signet.client.BuildConfig.SIGNET_ACCOUNT)"1.1.1.1"else com.signet.client.data.SettingsStore(context).load().dns
        val request=JSONObject().put("servers",array).put("options",options.json()).put("validateOnly",validateOnly).put("bootstrapDns",fallbackDns).toString().toByteArray()
        require(request.size<=16*1024*1024){"Конфигурация слишком велика для измерения"}
        val pipe=ParcelFileDescriptor.createPipe()
        val writer=CoroutineScope(currentCoroutineContext()).launch(Dispatchers.IO){runCatching{ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use{it.write(request)}}}
        var connection:ServiceConnection?=null
        try {
            suspendCancellableCoroutine { continuation->
                val reply=Messenger(Handler(Looper.getMainLooper()){m->
                    val result=runCatching {
                        val response=JSONObject(m.data.getString("result")?:"{}")
                        val values=response.optJSONArray("results")?:return@runCatching servers.associate{it.id to ProbeMeasurement(null,if(!com.signet.client.BuildConfig.SIGNET_ACCOUNT&&response.optString("status") in ProbeErrors.codes)response.getString("status")else serviceFailure)}
                        servers.associate{s->val value=(0 until values.length()).map{values.getJSONObject(it)}.firstOrNull{it.optString("id")==s.id};s.id to ProbeMeasurement(if(value?.isNull("delay")==false)value.optLong("delay")else null,value?.optString("status")?:"FAILED")}
                    }.getOrElse{servers.associate{it.id to ProbeMeasurement(null,serviceFailure)}}
                    if(continuation.isActive)continuation.resume(result);true
                })
                connection=object:ServiceConnection {
                    override fun onServiceConnected(name:ComponentName?,binder:IBinder?){runCatching{Messenger(binder).send(Message.obtain(null,1).apply{replyTo=reply;data=Bundle().apply{putParcelable("requestFd",pipe[0])}});pipe[0].close()}.onFailure{if(continuation.isActive)continuation.resume(servers.associate{it.id to ProbeMeasurement(null,serviceFailure)})}}
                    override fun onServiceDisconnected(name:ComponentName?){if(continuation.isActive)continuation.resume(servers.associate{it.id to ProbeMeasurement(null,serviceFailure)})}
                    override fun onBindingDied(name:ComponentName?){onServiceDisconnected(name)}
                    override fun onNullBinding(name:ComponentName?){onServiceDisconnected(name)}
                }
                if(!context.bindService(Intent(context,ProbeService::class.java),connection!!,Context.BIND_AUTO_CREATE))continuation.resume(servers.associate{it.id to ProbeMeasurement(null,serviceFailure)})
            }
        } finally {connection?.let{runCatching{context.unbindService(it)}};writer.cancel();pipe.forEach{runCatching{it.close()}}}
    }
}
