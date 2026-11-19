package com.signet.client

import android.app.Application
import androidx.work.*
import com.signet.client.data.*
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

class SignetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin { androidContext(this@SignetApp); modules(module { single { Vault(get()) }; single { SettingsStore(get()) }; single { Repository(get(), get(), get()) } }) }
        val processName = if(android.os.Build.VERSION.SDK_INT >= 28) Application.getProcessName() else getSystemService(android.app.ActivityManager::class.java).runningAppProcesses.firstOrNull { it.pid == android.os.Process.myPid() }?.processName.orEmpty()
        if(!processName.endsWith(":probe"))UpdateInstallation.cleanup(this)
        if (!processName.endsWith(":probe")) WorkManager.getInstance(this).enqueueUniquePeriodicWork("subscriptions", if(BuildConfig.SIGNET_ACCOUNT)ExistingPeriodicWorkPolicy.KEEP else ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SubscriptionWorker>(if(BuildConfig.SIGNET_ACCOUNT)6L else 1L, TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
}
class SubscriptionWorker(context: android.content.Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        org.koin.java.KoinJavaComponent.get<Repository>(Repository::class.java).refreshAll(onlyDue=true)
        return Result.success()
    }
}
