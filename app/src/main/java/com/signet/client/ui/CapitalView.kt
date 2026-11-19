package com.signet.client.ui

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.signet.client.data.CapitalError
import com.signet.client.data.ConnectionLog
import com.signet.client.graphics.*
import com.signet.client.vpn.VpnPhase
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable fun CapitalView(country:String?,phase:VpnPhase,settled:Boolean,source:CapitalImagery){
    val context=LocalContext.current
    val active=phase==VpnPhase.CONNECTED
    val cities=remember{context.assets.open("capitals.json").bufferedReader().use{JSONObject(it.readText())}}
    val city=remember(country){country?.let{code->cities.optJSONObject(code)?.let{CapitalCity(code,it.getString("name"),it.getDouble("latitude"),it.getDouble("longitude"))}}}
    var snapshot by remember(country,active){mutableStateOf<Bitmap?>(null)}
    var failure by remember(country,active){mutableStateOf<CapitalError?>(null)}
    var retry by remember(country){mutableIntStateOf(0)}
    LaunchedEffect(city,active,retry){
        if(!active||city==null)return@LaunchedEffect
        failure=null
        delay(350)
        repeat(2){attempt->
            try{
                ConnectionLog.add("IMAGERY","LOAD_START")
                snapshot=source.load(city);ConnectionLog.add("IMAGERY","READY");return@LaunchedEffect
            }catch(e:Exception){
                if(e is CancellationException&&e !is TimeoutCancellationException)throw e
                val code=CapitalError.from(e)
                ConnectionLog.add("IMAGERY","LOAD_FAILED",code.name)
                if(attempt==0&&code.retryable){delay(1000)}else{failure=code;return@LaunchedEffect}
            }
        }
    }
    val visible=phase==VpnPhase.CONNECTED&&settled&&snapshot!=null
    val approach by animateFloatAsState(if(visible)1f else .86f,tween(1300),label="capital-approach")
    Crossfade(targetState=if(visible)snapshot?.let{city to it}else null,animationSpec=tween(1100),modifier=Modifier.fillMaxSize(),label="capital-photo"){frame->if(frame!=null){
        Box(Modifier.fillMaxSize().graphicsLayer{scaleX=approach;scaleY=approach}.clip(RoundedCornerShape(24.dp)).background(Color(0xFF07101A))){
            // Fit retains the full photo; a large crop must not turn sharp roofs into an enlarged texture.
            Image(frame.second.asImageBitmap(),"${frame.first?.name} — satellite view",Modifier.fillMaxSize(),contentScale=ContentScale.Fit,filterQuality=FilterQuality.High)
            Text(frame.first?.name.orEmpty(),color=Color.White,fontWeight=FontWeight.SemiBold,modifier=Modifier.align(Alignment.Center).offset(y=(-50).dp).background(Color(0xDC0D1728),RoundedCornerShape(10.dp)).padding(horizontal=10.dp,vertical=5.dp))
            Icon(Icons.Outlined.LocationOn,null,tint=AppColors.accent,modifier=Modifier.align(Alignment.Center).offset(y=(-16).dp).size(32.dp))
            Text("© MapTiler · © OpenStreetMap contributors",color=Color.White,style=MaterialTheme.typography.labelSmall,modifier=Modifier.align(Alignment.BottomCenter).background(Color(0xD9000000)).clickable{openHttps(context,"https://www.maptiler.com/copyright/") {}}.padding(6.dp))
        }
    }}
    if(active&&city!=null&&phase==VpnPhase.CONNECTED&&settled&&snapshot==null){
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.BottomCenter){
            Row(Modifier.padding(12.dp).background(AppColors.surface,RoundedCornerShape(12.dp)).padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                if(failure==null)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
                Text(failure?.let{"${it.label} (${it.name})"}?:"Загрузка снимка столицы…",Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.labelSmall)
                if(failure!=null)TextButton(onClick={retry++}){Text("Повторить")}
            }
        }
    }
}
