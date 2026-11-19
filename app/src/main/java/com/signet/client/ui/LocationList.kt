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
    val displays=remember(state.servers){ServerDisplayModel.all(state.servers)}
    val filtered=state.servers.filter { s->(!favoriteOnly||s.favorite)&&("${displays[s.id]?.title} ${s.countryName} ${displays[s.id]?.description} ${displays[s.id]?.transport}").contains(query,true) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)){
                Text(subscription?.title?.lineSequence()?.firstOrNull()?.let{SubscriptionMetadata.text(it,100)}?:"Серверы",fontWeight=FontWeight.Medium,fontSize=18.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                subscription?.let{Text("${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(it.updated))} · Обновление ${it.metadata.updateHours} ч",color=Color(0xFF8FA2BF),fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
            }
            IconButton(onClick=ping,modifier=Modifier.testTag("server-ping"),enabled=state.servers.isNotEmpty()&&vpn.phase !in listOf(VpnPhase.CONNECTING,VpnPhase.RECONNECTING,VpnPhase.DISCONNECTING)) {
                Icon(if(probing)Icons.Outlined.StopCircle else Icons.Outlined.Speed,if(probing)"Остановить измерение"else"Проверить задержку",tint=Color(0xFF8FDCFF))
            }
            IconButton(onClick=refresh,modifier=Modifier.testTag("server-refresh"),enabled=!state.busy){if(state.busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)else Icon(Icons.Outlined.Refresh,"Обновить подписки",tint=Color(0xFF8FDCFF))}
            IconButton(onClick={tools=!tools}){Icon(Icons.Outlined.MoreHoriz,"Поиск и сортировка",tint=Color(0xFF8FDCFF))}
        }
        subscription?.let{sub->
            val total=sub.info["total"]?:0
            val used=((sub.info["upload"]?:0).toDouble()+(sub.info["download"]?:0)).coerceIn(0.0,Long.MAX_VALUE.toDouble())
            if(sub.info.isNotEmpty())Surface(color=Color(0xFF17283F),shape=RoundedCornerShape(5.dp),border=BorderStroke(.5.dp,Color.White.copy(alpha=.18f)),modifier=Modifier.fillMaxWidth().padding(vertical=5.dp)){
                Box(contentAlignment=Alignment.Center){
                    if(total>0)Box(Modifier.fillMaxWidth((used/total).toFloat().coerceIn(0f,1f)).height(18.dp).align(Alignment.CenterStart).background(Color(0xFF315879)))
                    Text("${"%.1f".format(used/(1L shl 30))} ГБ / ${if(total>0)"%.1f ГБ".format(total.toDouble()/(1L shl 30))else"∞"}",fontSize=10.sp)
                }
            }
        }
        if(!compact&&tools) {
            OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),placeholder={Text("Страна, город, сервер")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,shape=RoundedCornerShape(16.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(favoriteOnly,{favoriteOnly=!favoriteOnly},label={Text("Избранное")});FilterChip(sort,{sort=!sort},label={Text("По задержке")})}
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=16.dp)) {
            if(announcements.isNotBlank())item(key="subscription-announcement"){Text(announcements,Modifier.fillMaxWidth().padding(vertical=10.dp),color=Color(0xFFBFEAFF),fontSize=12.sp,lineHeight=17.sp,maxLines=5,overflow=TextOverflow.Ellipsis)}
            items(if(sort)filtered.sortedBy{it.latencyMs?:Long.MAX_VALUE}else filtered,key={it.id}) { server->ServerRow(server,displays.getValue(server.id),state,vpn,select,favorite,server.id in activeProbes) }
            if(filtered.isEmpty())item{Text(if(state.servers.isEmpty())"Добавьте подписку, чтобы увидеть серверы"else"Ничего не найдено",Modifier.padding(vertical=24.dp),color=Color(0xFF9BAAC2))}
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ServerRow(server:Server,display:ServerDisplayModel,state:AppState,vpn:VpnStatus,select:(String)->Unit,favorite:(String)->Unit,probing:Boolean) {
    val active=vpn.serverId==server.id&&vpn.phase==VpnPhase.CONNECTED
    val selected=state.selected==server.id
    val color=if(active)Color(0xFF32E6A1)else Color(0xFF39C8FF)
    Row(Modifier.fillMaxWidth().padding(vertical=2.dp).border(if(selected)1.5.dp else 0.dp,if(selected)color else Color.Transparent,RoundedCornerShape(12.dp)).background(if(selected)Color(0x44314A70)else Color.Transparent,RoundedCornerShape(12.dp)).combinedClickable(enabled=server.enabled,onClick={select(server.id)},onLongClick={favorite(server.id)},onLongClickLabel="Избранное").padding(horizontal=10.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        CountryFlag(display.country,display.isAuto)
        Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
            Text(display.title,color=if(server.enabled)Color.White else Color(0xFF9BAAC2),fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=15.sp)
            val format=if(state.subscriptions.firstOrNull{sub->sub.servers.any{it.id==server.id}}?.format=="XRAY_JSON")"JSON"else"URI"
            Text(if(display.isAuto)"Автоматический выбор сервера"else display.description?:"${display.transport} / $format",color=Color(0xFF9AAEC8),fontSize=11.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
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
            Text(when {server.probeMessage=="CONSENT_REQUIRED"->"Нужно согласие";server.probeMessage=="UNSUPPORTED"->"—";server.latencyMs!=null->"${server.latencyMs} ms";problem!=null->problem;server.available==false->"Нет ответа";else->"—"},color=if(server.available==false||problem!=null)Color(0xFFFFC857)else Color(0xFF9BAAC2),fontSize=11.sp)
            if(active)Text("Подключён",color=color,fontSize=10.sp)
        }
        if(server.favorite)Icon(Icons.Outlined.Star,"Избранное",Modifier.padding(start=4.dp).size(13.dp),tint=Color(0xFFFFC857))
        Icon(Icons.Outlined.ChevronRight,"Выбрать сервер",Modifier.padding(start=6.dp).size(18.dp),tint=Color(0xFF73859F))
    }
    HorizontalDivider(color=Color.White.copy(alpha=.04f))
}
