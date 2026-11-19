package com.signet.client.data

import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import com.signet.client.BuildConfig
import com.signet.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import java.util.concurrent.TimeUnit

enum class InstallPhase{OFFER,PERMISSION,DOWNLOADING,VERIFYING,INSTALLING,CONFIRM,ERROR,DONE}
data class InstallProgress(val visible:Boolean=false,val release:AppRelease?=null,val phase:InstallPhase=InstallPhase.OFFER,val bytes:Long=0,val message:String="",val confirmation:Intent?=null)

object UpdateInstallation {
    val state=MutableStateFlow(InstallProgress())
    fun prefs(context:Context)=context.getSharedPreferences("signet-installer",Context.MODE_PRIVATE)
    fun abandon(context:Context,id:Int){if(id>=0)runCatching{context.packageManager.packageInstaller.abandonSession(id)}}
    fun cleanup(context:Context,updated:Boolean=false){
        val saved=prefs(context);val id=saved.getInt("session",-1)
        // Recover a process death between createSession() and persisting its ID.
        runCatching{context.packageManager.packageInstaller.mySessions.filter{it.sessionId!=id&&it.appPackageName==context.packageName}.forEach{abandon(context,it.sessionId)}}
        if(id<0)return
        val target=runCatching{ReleaseVersion.parse(saved.getString("target", "")!!)}.getOrNull()
        val completed=target!=null&&ReleaseVersion.parse(BuildConfig.VERSION_NAME)>=target
        val stale=System.currentTimeMillis()-saved.getLong("created",0)>24*60*60*1000L
        if(updated||completed||stale||!saved.getBoolean("committed",false)){
            abandon(context,id);saved.edit().clear().apply()
        }
    }
}

/** Stream directly into Android's installation session. No APK in Downloads/files/cache. */
class NativeUpdater(private val context:Context,private val scope:CoroutineScope){
    val state=UpdateInstallation.state.asStateFlow()
    private var job:Job?=null
    @Volatile private var call:Call?=null
    private val client=OkHttpClient.Builder().followRedirects(false).retryOnConnectionFailure(false).connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(5,TimeUnit.MINUTES).build()
    fun offer(release:AppRelease){if(job?.isActive==true)return;UpdateInstallation.state.value=InstallProgress(true,release)}
    fun cancel(){call?.cancel();job?.cancel();val saved=UpdateInstallation.prefs(context);UpdateInstallation.abandon(context,saved.getInt("session",-1));saved.edit().clear().apply();UpdateInstallation.state.update{it.copy(visible=false,confirmation=null)}}
    fun hide(){UpdateInstallation.state.update{it.copy(visible=false)}}
    fun confirm(activity:Context){
        val confirmation=state.value.confirmation?:return
        runCatching{activity.startActivity(confirmation)}.onSuccess{
            UpdateInstallation.state.update{it.copy(phase=InstallPhase.INSTALLING,message="Установка обновления в Android")}
        }.onFailure{UpdateInstallation.state.update{it.copy(phase=InstallPhase.ERROR,message="Не удалось открыть установщик Android")}}
    }
    fun start(){
        if(job?.isCompleted==false)return
        val release=state.value.release?:return
        if(release.apk==null||release.sha256==null||release.size !in 1..UpdateTransfer.MAX_BYTES){
            UpdateInstallation.state.update{it.copy(phase=InstallPhase.ERROR,message="Не удалось подтвердить файл обновления для этого устройства. Повторите проверку обновлений.")};return
        }
        if(!context.packageManager.canRequestPackageInstalls()){
            UpdateInstallation.state.update{it.copy(phase=InstallPhase.PERMISSION,message="Разрешите установку обновлений для Signet и вернитесь в приложение.")};return
        }
        job=scope.launch(Dispatchers.IO){
            var sessionId=-1;var committed=false
            try{
                require(release.version>ReleaseVersion.parse(BuildConfig.VERSION_NAME))
                val url=release.apk?:error("Нет APK для этого устройства")
                val digest=release.sha256?:error("Не удалось подтвердить файл обновления. Повторите проверку обновлений.")
                require(UpdateTransfer.trustedHop(url)&&release.size in 1..UpdateTransfer.MAX_BYTES)
                val saved=UpdateInstallation.prefs(context);UpdateInstallation.abandon(context,saved.getInt("session",-1))
                val installer=context.packageManager.packageInstaller
                val params=PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply{
                    setAppPackageName(context.packageName);setAppLabel("Signet VPN");setSize(release.size)
                    if(Build.VERSION.SDK_INT>=31)setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                sessionId=installer.createSession(params)
                check(saved.edit().putInt("session",sessionId).putString("target",release.version.toString()).putLong("created",System.currentTimeMillis()).putBoolean("committed",false).commit())
                UpdateInstallation.state.update{it.copy(phase=InstallPhase.DOWNLOADING,bytes=0,message="Загрузка обновления")}
                installer.openSession(sessionId).use{session->
                    var next=url;var downloaded=false
                    repeat(6){
                        if(downloaded)return@repeat
                        require(UpdateTransfer.trustedHop(next))
                        val request=Request.Builder().url(next).header("User-Agent","SignetAndroidUpdater").build()
                        val downloadCall=client.newCall(request);call=downloadCall
                        downloadCall.awaitResponse().use{response->
                            if(response.code in 300..399){next=response.request.url.resolve(response.header("Location")?:error("Нет адреса загрузки"))?.toString()?:error("Некорректный адрес")}
                            else{
                                check(response.isSuccessful)
                                val body=response.body?:error("Пустой файл")
                                require(body.contentLength() in listOf(-1L,release.size))
                                session.openWrite("base.apk",0,release.size).use{output->
                                    var percent=-1L;val task=currentCoroutineContext()
                                    body.byteStream().use{input->UpdateTransfer.copy(input,output,release.size,digest){count->
                                        task.ensureActive();val step=count*100/release.size
                                        if(step!=percent){percent=step;UpdateInstallation.state.update{it.copy(bytes=count,phase=if(count==release.size)InstallPhase.VERIFYING else InstallPhase.DOWNLOADING,message=if(count==release.size)"Проверка обновления"else"Загрузка обновления")}}
                                    }}
                                    session.fsync(output)
                                }
                                downloaded=true
                            }
                        }
                    }
                    check(downloaded){"Не удалось загрузить обновление"};currentCoroutineContext().ensureActive()
                    val result=Intent(context,UpdateInstallReceiver::class.java).setAction("com.signet.client.INSTALL_RESULT").setData(Uri.parse("signet-install://$sessionId"))
                    val flags=PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=31)PendingIntent.FLAG_MUTABLE else 0
                    val pending=PendingIntent.getBroadcast(context,sessionId,result,flags)
                    // Android enforces this package name, signing certificate and upgrade version.
                    UpdateInstallation.state.update{it.copy(phase=InstallPhase.INSTALLING,message="Подготовка установки")}
                    check(saved.edit().putBoolean("committed",true).commit())
                    session.commit(pending.intentSender);committed=true
                }
            }catch(e:CancellationException){throw e}
            catch(e:Exception){UpdateInstallation.state.update{it.copy(phase=InstallPhase.ERROR,message=if(e is IllegalArgumentException)"Файл обновления не прошёл проверку размера или контрольной суммы"else"Не удалось загрузить обновление. Проверьте соединение и свободное место.",confirmation=null)}}
            finally{
                call=null
                if(!committed){
                    UpdateInstallation.abandon(context,sessionId)
                    val saved=UpdateInstallation.prefs(context)
                    if(saved.getInt("session",-1)==sessionId)saved.edit().clear().apply()
                }
            }
        }
    }
    fun close(){call?.cancel();job?.cancel();client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
}

class UpdateInstallReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action==Intent.ACTION_MY_PACKAGE_REPLACED){UpdateInstallation.cleanup(context,updated=true);return}
        if(intent.action!="com.signet.client.INSTALL_RESULT")return
        val saved=UpdateInstallation.prefs(context);val id=saved.getInt("session",-1)
        if(id<0||intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1)!=id)return
        when(intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)){
            PackageInstaller.STATUS_PENDING_USER_ACTION->{
                val confirmation=if(Build.VERSION.SDK_INT>=33)intent.getParcelableExtra(Intent.EXTRA_INTENT,Intent::class.java)else @Suppress("DEPRECATION") intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                UpdateInstallation.state.update{it.copy(visible=true,phase=InstallPhase.CONFIRM,confirmation=confirmation,message="Обновление проверено. Подтвердите установку в Android.")}
            }
            PackageInstaller.STATUS_SUCCESS->{
                saved.edit().clear().apply();UpdateInstallation.state.update{it.copy(phase=InstallPhase.DONE,message="Обновление установлено",confirmation=null)}
            }
            else->{
                UpdateInstallation.abandon(context,id);saved.edit().clear().apply()
                val message=when(intent.getIntExtra(PackageInstaller.EXTRA_STATUS,0)){
                    PackageInstaller.STATUS_FAILURE_STORAGE->"Недостаточно места для установки"
                    PackageInstaller.STATUS_FAILURE_ABORTED->"Установка отменена"
                    PackageInstaller.STATUS_FAILURE_CONFLICT->"Android отклонил обновление: версия или подпись не совпадает"
                    else->"Android не смог установить обновление"
                }
                UpdateInstallation.state.update{it.copy(phase=InstallPhase.ERROR,message=message,confirmation=null)}
            }
        }
    }
}
