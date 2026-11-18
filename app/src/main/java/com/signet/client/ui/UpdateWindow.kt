package com.signet.client.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.signet.client.BuildConfig
import com.signet.client.MainViewModel
import com.signet.client.data.InstallPhase

@Composable fun UpdateWindow(model:MainViewModel){
    val state by model.installer.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    if(!state.visible)return
    val busy=state.phase in setOf(InstallPhase.DOWNLOADING,InstallPhase.VERIFYING,InstallPhase.INSTALLING)
    fun dismiss(){model.installer.cancel();model.dismissUpdate()}
    Dialog(onDismissRequest={if(!busy)dismiss()}){
        Surface(shape=RoundedCornerShape(28.dp),color=Color(0xFF0D1728),border=BorderStroke(1.dp,Color(0xFF538099))){
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF19394D),Color(0xFF0D1728)))).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Icon(Icons.Outlined.SystemUpdate,null,tint=Color(0xFF8AD9FF),modifier=Modifier.size(32.dp))
                    Text("Обновление Signet",fontSize=22.sp,fontWeight=FontWeight.SemiBold,color=Color.White)
                }
                Text("${BuildConfig.VERSION_NAME}  →  ${state.release?.version}",fontSize=18.sp,color=Color(0xFFB8E5FF))
                Text(state.message.ifBlank{"Доступна новая версия приложения"},color=Color(0xFFE6EFFA),lineHeight=22.sp)
                val total=state.release?.size?:0
                if(total>0){
                    LinearProgressIndicator(progress={(state.bytes.toDouble()/total).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=Color(0xFF78DCFF),trackColor=Color.White.copy(alpha=.12f))
                    Text("${"%.1f".format(state.bytes/(1024.0*1024))} / ${"%.1f".format(total/(1024.0*1024))} МБ",color=Color(0xFFAABDD4),fontSize=13.sp)
                }
                when(state.phase){
                    InstallPhase.OFFER,InstallPhase.ERROR->GlassButton(onClick={model.installer.start()},modifier=Modifier.fillMaxWidth()){Text(if(state.phase==InstallPhase.ERROR)"Повторить"else"Обновить приложение")}
                    InstallPhase.PERMISSION->{
                        GlassButton(onClick={context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")))},modifier=Modifier.fillMaxWidth()){Text("Разрешить установку")}
                        GlassButton(onClick={model.installer.start()},modifier=Modifier.fillMaxWidth()){Text("Продолжить")}
                    }
                    InstallPhase.CONFIRM->GlassButton(onClick={state.confirmation?.let{runCatching{context.startActivity(it)}}},enabled=state.confirmation!=null,modifier=Modifier.fillMaxWidth()){Text("Установить")}
                    InstallPhase.DONE->GlassButton(onClick={model.installer.hide()},modifier=Modifier.fillMaxWidth()){Text("Готово")}
                    else->Unit
                }
                if(state.phase!=InstallPhase.DONE)OutlinedButton(onClick={dismiss()},modifier=Modifier.fillMaxWidth()){Text(if(busy||state.phase==InstallPhase.CONFIRM)"Отменить"else"Позже")}
            }
        }
    }
}
