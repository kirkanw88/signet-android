package com.signet.client.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.signet.client.data.CapitalError
import com.signet.client.data.ConnectionLog
import com.signet.client.graphics.*
import com.signet.client.vpn.VpnPhase
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable fun CapitalView(country:String?,phase:VpnPhase,settled:Boolean,source:CapitalImagery,onPhotoVisible:(Boolean)->Unit){
    val context=LocalContext.current
    val active=phase==VpnPhase.CONNECTED
    val cities=remember{context.assets.open("capitals.json").bufferedReader().use{JSONObject(it.readText())}}
    val city=remember(country){country?.let{code->cities.optJSONObject(code)?.let{CapitalCity(code,it.getString("name"),it.getDouble("latitude"),it.getDouble("longitude"))}}}
    var snapshot by remember(country,active){mutableStateOf<LandmarkPhoto?>(null)}
    var failure by remember(country,active){mutableStateOf<CapitalError?>(null)}
    var retry by remember(country){mutableIntStateOf(0)}
    var credits by remember(country,active){mutableStateOf(false)}
    var photoChoice by remember(country,active){mutableStateOf<Boolean?>(null)}
    LaunchedEffect(city,active,retry){
        if(!active||city==null)return@LaunchedEffect
        failure=null
        try{snapshot=source.load(city);ConnectionLog.add("PHOTO","READY")}
        catch(e:CancellationException){throw e}
        catch(e:Exception){failure=CapitalError.from(e);ConnectionLog.add("PHOTO","LOAD_FAILED",failure!!.name)}
    }
    val visible=active&&(photoChoice?:settled)&&snapshot!=null
    LaunchedEffect(visible){onPhotoVisible(visible)}
    DisposableEffect(Unit){onDispose{onPhotoVisible(false)}}
    val approach by animateFloatAsState(if(visible)1f else .86f,tween(1300),label="capital-approach")
    Crossfade(targetState=if(visible)snapshot else null,animationSpec=tween(1100),modifier=Modifier.fillMaxSize(),label="capital-photo"){photo->if(photo!=null){
        Box(Modifier.fillMaxSize().graphicsLayer{scaleX=approach;scaleY=approach}.clip(RoundedCornerShape(24.dp)).background(Color(0xFF07101A))){
            Image(photo.bitmap.asImageBitmap(),"${photo.landmark}, ${photo.city}",Modifier.fillMaxSize(),contentScale=ContentScale.Fit,filterQuality=FilterQuality.High)
            TextButton(onClick={photoChoice=false},modifier=Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0xCC07101A),RoundedCornerShape(24.dp))){Icon(Icons.Outlined.Public,null,tint=Color.White);Text("Планета",Modifier.padding(start=6.dp),color=Color.White)}
            IconButton(onClick={credits=true},modifier=Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color(0xCC07101A),RoundedCornerShape(24.dp))){Icon(Icons.Outlined.Info,"Автор и источник фотографии",tint=Color.White)}
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xF207101A)))).padding(14.dp)){
                Text(photo.city,color=Color.White,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Text(if(photo.cityView)"Вид города"else photo.landmark,color=Color.White,style=MaterialTheme.typography.bodyMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text("Фото: ${photo.author} · ${photo.license} · Wikimedia Commons",color=Color(0xFFD0D7E1),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.clickable{credits=true}.padding(top=5.dp))
            }
        }
    }}
    if(active&&snapshot!=null&&!visible)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopEnd){
        TextButton(onClick={photoChoice=true},modifier=Modifier.padding(6.dp).background(AppColors.surface,RoundedCornerShape(24.dp))){Icon(Icons.Outlined.Image,null);Text("Фото",Modifier.padding(start=6.dp))}
    }
    if(active&&city!=null&&settled&&snapshot==null){
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.BottomCenter){
            Row(Modifier.padding(12.dp).background(AppColors.surface,RoundedCornerShape(12.dp)).padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                if(failure==null)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
                Text(failure?.label?:"Загрузка фотографии…",Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.labelSmall)
                if(failure!=null)TextButton(onClick={retry++}){Text("Повторить")}
            }
        }
    }
    if(credits)snapshot?.let{photo->AlertDialog(onDismissRequest={credits=false},title={Text("О фотографии")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("${photo.landmark} · ${photo.city}",fontWeight=FontWeight.SemiBold)
            Text("Автор: ${photo.author}");Text("Лицензия: ${photo.license}")
            Text("Фото подготовлено для приложения в формате WebP. Источник: Wikimedia Commons.")
            TextButton(onClick={openHttps(context,photo.source){}}){Text("Открыть источник")}
            if(photo.licenseUrl.startsWith("https://"))TextButton(onClick={openHttps(context,photo.licenseUrl){}}){Text("Условия лицензии")}
        }
    },confirmButton={TextButton(onClick={credits=false}){Text("Закрыть")}})}
}
