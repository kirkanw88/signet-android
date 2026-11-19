package com.signet.client.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.signet.client.data.AppState
import com.signet.client.BuildConfig
import com.signet.client.vpn.VpnStatus
import com.signet.client.vpn.VpnPhase
import com.signet.core.*

@Composable fun LocationList(state:AppState,vpn:VpnStatus,probing:Boolean,select:(String)->Unit,favorite:(String)->Unit,ping:()->Unit,refresh:()->Unit,modifier:Modifier=Modifier,compact:Boolean=false,activeProbes:Set<String> = emptySet()) {
    var query by rememberSaveable{mutableStateOf("")};var favoriteOnly by rememberSaveable{mutableStateOf(false)}
    var sort by rememberSaveable{mutableStateOf(false)}
    var tools by rememberSaveable{mutableStateOf(false)}
    val subscription=state.subscriptions.firstOrNull{sub->sub.servers.any{it.id==state.selected}}?:state.subscriptions.firstOrNull()
    val announcements=state.subscriptions.map{it.metadata.announcement}.filter{it.isNotBlank()}.distinct().joinToString("\n\n")
    val displays=remember(state.servers){ServerDisplayModel.all(state.servers,hostNames=!BuildConfig.SIGNET_ACCOUNT)}
    val filtered=state.servers.filter { s->(!favoriteOnly||s.favorite)&&("${displays[s.id]?.title} ${s.countryName} ${displays[s.id]?.description} ${displays[s.id]?.transport}").contains(query,true) }
    LazyColumn(modifier,contentPadding=PaddingValues(bottom=16.dp)) {
        item(key="server-tools"){Column {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)){
                Text(subscription?.title?.lineSequence()?.firstOrNull()?.let{SubscriptionMetadata.text(it,100)}?:"Серверы",fontWeight=FontWeight.Medium,fontSize=18.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                subscription?.let{Text("${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(it.updated))} · Обновление ${if(BuildConfig.SIGNET_ACCOUNT)it.metadata.updateHours else 1} ч",color=AppColors.muted,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
            }
            IconButton(onClick=ping,modifier=Modifier.testTag("server-ping"),enabled=state.servers.isNotEmpty()&&vpn.phase !in listOf(VpnPhase.CONNECTING,VpnPhase.RECONNECTING,VpnPhase.DISCONNECTING)) {
                Icon(if(probing)Icons.Outlined.StopCircle else Icons.Outlined.Speed,if(probing)"Остановить измерение"else"Проверить задержку",tint=AppColors.accent)
            }
            IconButton(onClick=refresh,modifier=Modifier.testTag("server-refresh"),enabled=!state.busy){if(state.busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)else Icon(Icons.Outlined.Refresh,"Обновить подписки",tint=AppColors.accent)}
            IconButton(onClick={tools=!tools}){Icon(Icons.Outlined.MoreHoriz,"Поиск и сортировка",tint=AppColors.accent)}
        }
        subscription?.let{sub->
            val total=sub.info["total"]?:0
            val used=((sub.info["upload"]?:0).toDouble()+(sub.info["download"]?:0)).coerceIn(0.0,Long.MAX_VALUE.toDouble())
            if(sub.info.isNotEmpty())Surface(color=AppColors.surface,shape=RoundedCornerShape(5.dp),border=BorderStroke(.5.dp,AppColors.border),modifier=Modifier.fillMaxWidth().padding(vertical=5.dp)){
                Box(contentAlignment=Alignment.Center){
                    if(total>0)Box(Modifier.fillMaxWidth((used/total).toFloat().coerceIn(0f,1f)).height(18.dp).align(Alignment.CenterStart).background(AppColors.tint))
                    Text("${"%.1f".format(used/(1L shl 30))} ГБ / ${if(total>0)"%.1f ГБ".format(total.toDouble()/(1L shl 30))else"∞"}",fontSize=10.sp)
                }
            }
        }
        if(!compact&&tools) {
            OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),placeholder={Text("Страна, город, сервер")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,shape=RoundedCornerShape(16.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(favoriteOnly,{favoriteOnly=!favoriteOnly},label={Text("Избранное")});FilterChip(sort,{sort=!sort},label={Text("По задержке")})}
        }
        }}
            if(announcements.isNotBlank())item(key="subscription-announcement"){Text(announcements,Modifier.fillMaxWidth().padding(vertical=10.dp),color=AppColors.text,fontSize=12.sp,lineHeight=17.sp,maxLines=5,overflow=TextOverflow.Ellipsis)}
            items(if(sort)filtered.sortedBy{it.latencyMs?:Long.MAX_VALUE}else filtered,key={it.id}) { server->ServerRow(server,displays.getValue(server.id),state,vpn,select,favorite,server.id in activeProbes) }
            if(filtered.isEmpty())item{Text(if(state.servers.isEmpty())"Добавьте подписку, чтобы увидеть серверы"else"Ничего не найдено",Modifier.padding(vertical=24.dp),color=AppColors.muted)}
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ServerRow(server:Server,display:ServerDisplayModel,state:AppState,vpn:VpnStatus,select:(String)->Unit,favorite:(String)->Unit,probing:Boolean) {
    val active=vpn.serverId==server.id&&vpn.phase==VpnPhase.CONNECTED
    val selected=state.selected==server.id
    val color=if(active)AppColors.success else AppColors.accent
    Row(Modifier.fillMaxWidth().padding(vertical=1.dp).heightIn(min=56.dp).border(if(selected)1.5.dp else 0.dp,if(selected)color else Color.Transparent,RoundedCornerShape(10.dp)).background(if(selected)AppColors.tint else Color.Transparent,RoundedCornerShape(10.dp)).combinedClickable(enabled=server.enabled,onClick={select(server.id)},onLongClick={favorite(server.id)},onLongClickLabel="Избранное").padding(horizontal=8.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
        CountryFlag(display.country,display.isAuto)
        Column(Modifier.weight(1f).padding(horizontal=8.dp)) {
            Text(display.title,color=if(server.enabled)AppColors.text else AppColors.muted,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=14.sp)
            val format=if(state.subscriptions.firstOrNull{sub->sub.servers.any{it.id==server.id}}?.format=="XRAY_JSON")"JSON"else"URI"
            Row(Modifier.fillMaxWidth()){
                Text(display.transport,Modifier.weight(1f,fill=false),color=AppColors.muted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(" / $format",color=AppColors.muted,fontSize=11.sp,maxLines=1)
            }
            display.description?.let{Text(it,color=AppColors.muted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
        }
        Column(horizontalAlignment=Alignment.End) {
            if(probing)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
            val problem=if(BuildConfig.SIGNET_ACCOUNT)null else when(server.probeMessage){
                "SERVICE_ERROR","PROBE_ERROR","IO"->"Сбой Ping"
                "NO_NETWORK"->"Нет сети"
                "DNS"->"Ошибка DNS"
                "TLS"->"Ошибка TLS"
                "TIMEOUT"->"Таймаут"
                "FAILED"->"Не измерено"
                else->null
            }
            Text(when {server.probeMessage=="CONSENT_REQUIRED"->"Нужно согласие";server.probeMessage=="UNSUPPORTED"->"—";server.latencyMs!=null->"${server.latencyMs} ms";problem!=null->problem;server.available==false->"Нет ответа";else->"—"},color=if(server.available==false||problem!=null)AppColors.warning else AppColors.muted,fontSize=11.sp)
            if(active)Text("Подключён",color=color,fontSize=10.sp)
        }
        if(server.favorite)Icon(Icons.Outlined.Star,"Избранное",Modifier.padding(start=4.dp).size(13.dp),tint=AppColors.warning)
        Icon(Icons.Outlined.ChevronRight,"Выбрать сервер",Modifier.padding(start=6.dp).size(18.dp),tint=AppColors.muted)
    }
    HorizontalDivider(color=AppColors.border.copy(alpha=.4f))
}
