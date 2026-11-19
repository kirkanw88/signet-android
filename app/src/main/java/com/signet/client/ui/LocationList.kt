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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription

@OptIn(ExperimentalFoundationApi::class)
@Composable fun LocationList(state:AppState,vpn:VpnStatus,probing:Boolean,select:(String)->Unit,favorite:(String)->Unit,ping:()->Unit,refresh:()->Unit,modifier:Modifier=Modifier,compact:Boolean=false,activeProbes:Set<String> = emptySet(),toggleSubscription:(String)->Unit={},pinSubscription:(String)->Unit={},refreshSubscription:(String)->Unit={},pingSubscription:(String)->Unit={},removeSubscription:(String)->Unit={},refreshingSubscriptions:Set<String> = emptySet(),addSubscription:(()->Unit)?=null,management:Boolean=false) {
    var query by rememberSaveable{mutableStateOf("")};var favoriteOnly by rememberSaveable{mutableStateOf(false)}
    var sort by rememberSaveable{mutableStateOf(false)};var tools by rememberSaveable{mutableStateOf(false)}
    var removeId by rememberSaveable{mutableStateOf<String?>(null)}
    var settingsId by rememberSaveable{mutableStateOf<String?>(null)}
    val context=LocalContext.current
    val displays=remember(state.subscriptions){state.subscriptions.flatMap{ServerDisplayModel.all(it.servers,hostNames=!BuildConfig.SIGNET_ACCOUNT).entries}.associate{it.key to it.value}}
    val filtering=query.isNotBlank()||favoriteOnly
    val canPing=vpn.phase !in listOf(VpnPhase.CONNECTING,VpnPhase.RECONNECTING,VpnPhase.DISCONNECTING)
    var matches=0
    LazyColumn(modifier,contentPadding=PaddingValues(bottom=16.dp)){
        item(key="server-tools"){
            Column {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text(if(management)"Подписок: ${state.subscriptions.size}"else"Серверы",Modifier.weight(1f),fontWeight=FontWeight.Medium,fontSize=20.sp)
                    addSubscription?.let{add->TextButton(onClick=add,enabled=!state.busy&&state.subscriptions.size<8){Icon(Icons.Outlined.Add,null);Text("Добавить")}}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                    IconButton(onClick=ping,modifier=Modifier.testTag("server-ping"),enabled=state.servers.isNotEmpty()&&canPing){Icon(if(probing)Icons.Outlined.StopCircle else Icons.Outlined.Speed,if(probing)"Остановить измерение"else"Ping всех подписок",tint=AppColors.accent)}
                    IconButton(onClick=refresh,modifier=Modifier.testTag("server-refresh"),enabled=!state.busy){if(state.busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)else Icon(Icons.Outlined.Refresh,"Обновить все подписки",tint=AppColors.accent)}
                    IconButton(onClick={tools=!tools}){Icon(if(tools)Icons.Outlined.Close else Icons.Outlined.Search,if(tools)"Закрыть поиск"else"Поиск и сортировка",tint=AppColors.accent)}
                }
                if(!compact&&tools){
                    OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),placeholder={Text("Подписка, хост или страна")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,shape=RoundedCornerShape(16.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(favoriteOnly,{favoriteOnly=!favoriteOnly},label={Text("Избранное")});FilterChip(sort,{sort=!sort},label={Text("По задержке")})}
                }
            }
        }
        for(sub in SubscriptionGroups.pinnedFirst(state.subscriptions,state.settings.pinnedSubscriptions){it.id}){
            val index=state.subscriptions.indexOfFirst{it.id==sub.id}
            val label=SubscriptionMetadata.text(sub.title.lineSequence().firstOrNull(),120).let{if(it.isBlank()||it=="Подписка")"Подписка №${index+1}"else it}
            val visible=sub.servers.filter{s->(!favoriteOnly||s.favorite)&&("$label ${displays[s.id]?.title} ${s.countryName} ${displays[s.id]?.description} ${displays[s.id]?.transport}").contains(query,true)}
            if(filtering&&visible.isEmpty())continue
            matches+=visible.size
            val expanded=filtering||sub.id !in state.settings.collapsedSubscriptions
            item(key="${sub.id}:header"){
                var menu by remember{mutableStateOf(false)}
                val angle by animateFloatAsState(if(expanded)0f else -90f,label="subscription-expand")
                val selected=sub.servers.any{it.id==state.selected}
                val pinned=sub.id in state.settings.pinnedSubscriptions
                val here=activeProbes.any{id->sub.servers.any{it.id==id}}
                Surface(onClick={toggleSubscription(sub.id)},color=AppColors.surface,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,if(selected)AppColors.accent else AppColors.border),modifier=Modifier.fillMaxWidth().padding(top=10.dp,bottom=4.dp)){
                    Column(Modifier.padding(horizontal=10.dp,vertical=8.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Outlined.ExpandMore,if(expanded)"Свернуть подписку"else"Развернуть подписку",Modifier.size(24.dp).rotate(angle),tint=AppColors.accent)
                            Column(Modifier.weight(1f).padding(start=6.dp)){
                                Text(label,fontSize=16.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                                Text("Серверов: ${sub.servers.size} · Обновлено "+android.text.format.DateUtils.getRelativeTimeSpanString(sub.updated,System.currentTimeMillis(),android.text.format.DateUtils.MINUTE_IN_MILLIS),color=AppColors.muted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                            Box {
                                IconButton(onClick={menu=true}){Icon(Icons.Outlined.MoreVert,"Действия с подпиской",tint=AppColors.muted)}
                                if(pinned)Icon(Icons.Outlined.PushPin,"Закреплена",Modifier.align(Alignment.TopEnd).padding(top=3.dp,end=3.dp).size(12.dp),tint=AppColors.accent)
                                DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                                    if(!BuildConfig.SIGNET_ACCOUNT)DropdownMenuItem(text={Text("Настройка")},leadingIcon={Icon(Icons.Outlined.Settings,null)},onClick={menu=false;settingsId=sub.id})
                                    DropdownMenuItem(text={Text(if(pinned)"Открепить подписку"else"Закрепить подписку")},leadingIcon={Icon(Icons.Outlined.PushPin,null)},onClick={menu=false;pinSubscription(sub.id)})
                                    DropdownMenuItem(text={Text("Экспорт ссылки")},enabled=sub.url!=null,onClick={menu=false;sub.url?.let{ClipboardTransfers.copy(context,"Subscription",it);android.widget.Toast.makeText(context,"Ссылка скопирована",android.widget.Toast.LENGTH_SHORT).show()}})
                                    DropdownMenuItem(text={Text("Удалить подписку")},onClick={menu=false;removeId=sub.id})
                                }
                            }
                        }
                        if(expanded) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically){
                                TextButton(onClick={refreshSubscription(sub.id)},enabled=sub.url!=null&&!state.busy){
                                    if(sub.id in refreshingSubscriptions)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
                                    else Icon(Icons.Outlined.Refresh,null,Modifier.size(18.dp))
                                    Text("Обновить",Modifier.padding(start=6.dp),fontSize=12.sp)
                                }
                                TextButton(onClick={pingSubscription(sub.id)},enabled=canPing&&sub.servers.isNotEmpty()&&(!probing||here)){
                                    Icon(if(here)Icons.Outlined.StopCircle else Icons.Outlined.Speed,null,Modifier.size(18.dp))
                                    Text(if(here)"Остановить"else"Ping",Modifier.padding(start=6.dp),fontSize=12.sp)
                                }
                            }
                            SubscriptionUsage(sub)
                        }
                        sub.error?.let{Text(it,color=AppColors.warning,fontSize=12.sp,maxLines=3)}
                    }
                }
            }
            if(expanded){
                if(sub.metadata.announcement.isNotBlank())item(key="${sub.id}:announcement"){Column(Modifier.padding(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    var announcementExpanded by rememberSaveable(sub.id){mutableStateOf(false)}
                    Text(sub.metadata.announcement,fontSize=13.sp,color=AppColors.muted,maxLines=if(announcementExpanded)Int.MAX_VALUE else 3,overflow=TextOverflow.Ellipsis)
                    if(sub.metadata.announcement.length>180)TextButton(onClick={announcementExpanded=!announcementExpanded}){Text(if(announcementExpanded)"Свернуть сообщение"else"Показать сообщение полностью")}
                }}
                items(if(sort)visible.sortedBy{it.latencyMs?:Long.MAX_VALUE}else visible,key={"${sub.id}:${it.id}"}){server->ServerRow(server,displays.getValue(server.id),state,vpn,select,favorite,server.id in activeProbes,Modifier.animateItem())}
                if(visible.isEmpty())item(key="${sub.id}:empty"){Text("В подписке пока нет серверов",Modifier.padding(12.dp),color=AppColors.muted)}
            }
        }
        if(state.subscriptions.isEmpty()||(filtering&&matches==0))item(key="empty"){Text(if(state.subscriptions.isEmpty())"Добавьте подписку, чтобы увидеть серверы"else"Ничего не найдено",Modifier.padding(vertical=24.dp),color=AppColors.muted)}
    }
    settingsId?.let{id->state.subscriptions.firstOrNull{it.id==id}?.let{sub->
        AlertDialog(onDismissRequest={settingsId=null},title={Text("Настройка подписки")},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(SubscriptionMetadata.text(sub.title,200),fontWeight=FontWeight.SemiBold)
                val url=sub.url
                if(url!=null){
                    OutlinedTextField(value=url,onValueChange={},readOnly=true,label={Text("Ссылка подписки")},minLines=2,maxLines=5,modifier=Modifier.fillMaxWidth())
                    TextButton(onClick={ClipboardTransfers.copy(context,"Subscription",url);android.widget.Toast.makeText(context,"Ссылка скопирована",android.widget.Toast.LENGTH_SHORT).show()}){Icon(Icons.Outlined.ContentCopy,null);Text("Копировать ссылку",Modifier.padding(start=8.dp))}
                }else Text("Подписка добавлена из конфигурации. Ссылки для обновления нет.",color=AppColors.muted)
                TextButton(onClick={pinSubscription(id)}){Icon(Icons.Outlined.PushPin,null);Text(if(id in state.settings.pinnedSubscriptions)"Открепить подписку"else"Закрепить подписку",Modifier.padding(start=8.dp))}
            }
        },confirmButton={TextButton(onClick={settingsId=null}){Text("Закрыть")}},dismissButton={TextButton(onClick={settingsId=null;removeId=id}){Text("Удалить подписку",color=AppColors.warning)}})
    }}
    removeId?.let{id->
        val label=state.subscriptions.firstOrNull{it.id==id}?.title.orEmpty()
        AlertDialog(onDismissRequest={removeId=null},title={Text("Удалить подписку?")},text={Text("$label\nУдалится только эта подписка с устройства. Если она используется, VPN будет отключён.")},confirmButton={TextButton(onClick={removeSubscription(id);removeId=null}){Text("Удалить")}},dismissButton={TextButton(onClick={removeId=null}){Text("Отмена")}})
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ServerRow(server:Server,display:ServerDisplayModel,state:AppState,vpn:VpnStatus,select:(String)->Unit,favorite:(String)->Unit,probing:Boolean,modifier:Modifier=Modifier) {
    val active=vpn.serverId==server.id&&vpn.phase==VpnPhase.CONNECTED
    val selected=state.selected==server.id
    val color=if(active)AppColors.success else AppColors.accent
    Row(modifier.fillMaxWidth().semantics { this.selected=selected; stateDescription=if(active)"Подключён" else if(selected)"Выбран" else "Не выбран" }.padding(vertical=1.dp).heightIn(min=56.dp).border(if(selected)1.5.dp else 0.dp,if(selected)color else Color.Transparent,RoundedCornerShape(10.dp)).background(if(selected)AppColors.tint else Color.Transparent,RoundedCornerShape(10.dp)).combinedClickable(enabled=server.enabled,onClick={select(server.id)},onLongClick={favorite(server.id)},onLongClickLabel="Избранное").padding(horizontal=8.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
        CountryFlag(display.country,display.isAuto)
        Column(Modifier.weight(1f).padding(horizontal=8.dp)) {
            Text(display.title,color=if(server.enabled)AppColors.text else AppColors.muted,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=15.sp)
            Row(Modifier.fillMaxWidth()){
                Text(display.transport,Modifier.weight(1f),color=AppColors.muted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            display.description?.let{Text(it,color=AppColors.muted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
        }
        Column(horizontalAlignment=Alignment.End) {
            if(probing)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
            val problem=if(BuildConfig.SIGNET_ACCOUNT)null else when(server.probeMessage){
                "SERVICE_ERROR","PROBE_ERROR","IO"->"Сбой Ping"
                "NO_NETWORK"->"Нет сети"
                "CONFIG_ERROR"->"Ошибка профиля"
                "CORE_ERROR"->"Ошибка ядра"
                "CORE_BUSY"->"Измеритель занят"
                "ASSET_ERROR"->"Ошибка геоданных"
                "DNS_SETUP_ERROR"->"DNS измерителя"
                "HTTP_ERROR"->"Ответ сайта"
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
