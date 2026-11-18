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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.signet.client.data.AppState
import com.signet.client.vpn.VpnStatus
import com.signet.client.vpn.VpnPhase
import com.signet.core.*

@Composable fun LocationList(state:AppState,vpn:VpnStatus,probing:Boolean,select:(String)->Unit,favorite:(String)->Unit,ping:()->Unit,refresh:()->Unit,modifier:Modifier=Modifier,compact:Boolean=false,activeProbes:Set<String> = emptySet()) {
    var query by rememberSaveable{mutableStateOf("")};var favoriteOnly by rememberSaveable{mutableStateOf(false)}
    var sort by rememberSaveable{mutableStateOf(false)}
    val announcements=state.subscriptions.map{it.metadata.announcement}.filter{it.isNotBlank()}.distinct().joinToString("\n\n")
    val displays=remember(state.servers){ServerDisplayModel.all(state.servers)}
    val filtered=state.servers.filter { s->(!favoriteOnly||s.favorite)&&("${displays[s.id]?.title} ${s.countryName} ${displays[s.id]?.description} ${displays[s.id]?.transport}").contains(query,true) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Локации",Modifier.weight(1f),fontWeight=FontWeight.SemiBold,fontSize=18.sp)
            TextButton(onClick=ping,enabled=state.servers.isNotEmpty()&&vpn.phase !in listOf(VpnPhase.CONNECTING,VpnPhase.RECONNECTING,VpnPhase.DISCONNECTING)){if(probing)CircularProgressIndicator(Modifier.size(12.dp),strokeWidth=1.5.dp);Text(if(probing)" Стоп"else"Ping")}
            TextButton(onClick=refresh,enabled=!state.busy){if(state.busy)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp);Text(if(state.busy)" Обновление"else"Обновить")}
        }
        if(!compact) {
            OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),placeholder={Text("Страна, город, сервер")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,shape=RoundedCornerShape(16.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(favoriteOnly,{favoriteOnly=!favoriteOnly},label={Text("Избранное")});FilterChip(sort,{sort=!sort},label={Text("По задержке")})}
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=16.dp)) {
            if(announcements.isNotBlank())item(key="subscription-announcement"){Text(announcements,Modifier.fillMaxWidth().padding(vertical=16.dp),color=Color(0xFFBFEAFF),fontSize=13.sp,lineHeight=21.sp)}
            items(if(sort)filtered.sortedBy{it.latencyMs?:Long.MAX_VALUE}else filtered,key={it.id}) { server->ServerRow(server,displays.getValue(server.id),state,vpn,select,favorite,server.id in activeProbes) }
            if(filtered.isEmpty())item{Text(if(state.servers.isEmpty())"Добавьте подписку, чтобы увидеть серверы"else"Ничего не найдено",Modifier.padding(vertical=24.dp),color=Color(0xFF9BAAC2))}
        }
    }
}

@Composable private fun ServerRow(server:Server,display:ServerDisplayModel,state:AppState,vpn:VpnStatus,select:(String)->Unit,favorite:(String)->Unit,probing:Boolean) {
    val active=vpn.serverId==server.id&&vpn.phase==VpnPhase.CONNECTED
    val selected=state.selected==server.id
    val color=if(active)Color(0xFF32E6A1)else Color(0xFF39C8FF)
    Row(Modifier.fillMaxWidth().background(if(selected)Color(0xFF101F32)else Color.Transparent,RoundedCornerShape(14.dp)).clickable(enabled=server.enabled){select(server.id)}.padding(start=10.dp,top=10.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically) {
        CountryFlag(display.country,display.isAuto)
        Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
            Text(display.title,color=if(server.enabled)Color.White else Color(0xFF9BAAC2),fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=14.sp)
            display.description?.let{Text(it,color=Color(0xFF9BAAC2),fontSize=11.sp,maxLines=2,overflow=TextOverflow.Ellipsis)}
            Text(display.transport,color=Color(0xFF73859F),fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment=Alignment.End) {
            if(probing)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
            Text(when {server.probeMessage=="CONSENT_REQUIRED"->"Нужно согласие";server.probeMessage=="UNSUPPORTED"->"—";server.latencyMs!=null->"${server.latencyMs} ms";server.available==false->"Нет ответа";else->"—"},color=if(server.available==false)Color(0xFFFFC857)else Color(0xFF9BAAC2),fontSize=11.sp)
            Text(if(active)"Подключён"else if(!server.enabled)"Отключён"else if(selected)"Выбран"else server.probeMode.orEmpty(),color=if(selected||active)color else Color(0xFF73859F),fontSize=10.sp)
        }
        IconButton(onClick={favorite(server.id)},modifier=Modifier.size(40.dp)){Icon(if(server.favorite)Icons.Outlined.Star else Icons.Outlined.StarOutline,"Избранное",Modifier.size(18.dp),tint=if(server.favorite)Color(0xFFFFC857)else Color(0xFF73859F))}
    }
    HorizontalDivider(color=Color.White.copy(alpha=.04f))
}
