package com.signet.client.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.signet.client.MainViewModel
import com.signet.client.BuildConfig
import com.signet.core.AppRelease
import com.signet.core.SubscriptionMetadata

fun openHttps(context:Context,url:String,onError:()->Unit={}) {
    if(SubscriptionMetadata.safeLink(url)==null){onError();return}
    runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}.onFailure{onError()}
}

@Composable fun UpdateBanner(release:AppRelease,model:MainViewModel) {
    val context=LocalContext.current
    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(if(BuildConfig.SIGNET_ACCOUNT)16.dp else 12.dp),modifier=Modifier.fillMaxWidth().padding(horizontal=if(BuildConfig.SIGNET_ACCOUNT)16.dp else 12.dp,vertical=4.dp)) {
        Column(Modifier.padding(if(BuildConfig.SIGNET_ACCOUNT)12.dp else 8.dp)) {
            Text("Доступна новая версия ${release.version}",style=MaterialTheme.typography.titleSmall)
            Row{TextButton(onClick={model.installer.offer(release)}){Text("Обновить")};TextButton(onClick=model::dismissUpdate){Text("Позже")}}
        }
    }
}

/** Shared customer API account. Browser is used only for verified sign-in and payment. */
@Composable fun AccountScreen(model:MainViewModel,close:()->Unit){MobileAccountScreen(model,close=close)}
