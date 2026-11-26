package com.signet.client.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role

@OptIn(ExperimentalFoundationApi::class)
@Composable fun LocationList(state:AppState,vpn:VpnStatus,probing:Boolean,select:(String)->Unit,favorite:(String)->Unit,ping:()->Unit,refresh:()->Unit,modifier:Modifier=Modifier,compact:Boolean=false,activeProbes:Set<String> = emptySet(),toggleSubscription:(String)->Unit={},pinSubscription:(String)->Unit={},refreshSubscription:(String)->Unit={},pingSubscription:(String)->Unit={},removeSubscription:(String)->Unit={},refreshingSubscriptions:Set<String> = emptySet(),addSubscription:(()->Unit)?=null,management:Boolean=false,pingServer:(String)->Unit={}) {
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
        // No redundant global toolbar in Universal: each subscription owns its actions.
        if(BuildConfig.SIGNET_ACCOUNT) item(key="server-tools"){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text("Серверы",Modifier.weight(1f),fontSize=20.sp)
                IconButton(onClick=ping,enabled=state.servers.isNotEmpty()&&canPing){
                    Icon(Icons.Outlined.Speed,"Ping",tint=AppColors.accent)
                }
                IconButton(onClick=refresh){Icon(Icons.Outlined.Refresh,"Обновить")}
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
                if(!BuildConfig.SIGNET_ACCOUNT) {
                    Column(Modifier.fillMaxWidth().padding(top=3.dp,bottom=3.dp)
                        .border(1.dp,AppColors.border.copy(alpha=.28f),RoundedCornerShape(12.dp))
                        .background(AppColors.surface.copy(alpha=.30f),RoundedCornerShape(12.dp))
                        .padding(horizontal=8.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top,
                            horizontalArrangement=Arrangement.spacedBy(3.dp)){
                            IconButton(onClick={toggleSubscription(sub.id)},modifier=Modifier.size(30.dp)){
                                Icon(Icons.Outlined.ExpandMore,if(expanded)"Свернуть"else"Развернуть",
                                    Modifier.size(19.dp).rotate(angle),tint=AppColors.muted)
                            }
                            Column(Modifier.weight(1f).padding(top=3.dp),verticalArrangement=Arrangement.spacedBy(1.dp)){
                                Text(label,fontSize=13.sp,lineHeight=16.sp,fontWeight=FontWeight.SemiBold,
                                    maxLines=2,overflow=TextOverflow.Ellipsis)
                                Text("Обновлено "+android.text.format.DateUtils.getRelativeTimeSpanString(
                                    sub.updated,System.currentTimeMillis(),android.text.format.DateUtils.MINUTE_IN_MILLIS)+
                                    " · каждые ${sub.metadata.updateHours} ч",
                                    fontSize=10.sp,lineHeight=12.sp,color=AppColors.muted,maxLines=1,
                                    overflow=TextOverflow.Ellipsis)
                            }
                            IconButton(onClick={pingSubscription(sub.id)},modifier=Modifier.size(32.dp),
                                enabled=canPing&&sub.servers.isNotEmpty()&&(!probing||here)){
                                Icon(if(here)Icons.Outlined.StopCircle else Icons.Outlined.Speed,
                                    if(here)"Остановить Ping"else"Ping подписки",Modifier.size(19.dp),tint=AppColors.muted)
                            }
                            IconButton(onClick={refreshSubscription(sub.id)},modifier=Modifier.size(32.dp),
                                enabled=sub.url!=null&&!state.busy){
                                if(sub.id in refreshingSubscriptions)CircularProgressIndicator(Modifier.size(17.dp),strokeWidth=2.dp)
                                else Icon(Icons.Outlined.Refresh,"Обновить подписку",Modifier.size(19.dp),tint=AppColors.muted)
                            }
                            Box{
                                IconButton(onClick={menu=true},modifier=Modifier.size(28.dp)){
                                    Icon(Icons.Outlined.MoreVert,"Действия",Modifier.size(18.dp),tint=AppColors.muted)
                                }
                                DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                                    if(addSubscription!=null)DropdownMenuItem(text={Text("Добавить подписку")},
                                        enabled=state.subscriptions.size<8,onClick={menu=false;addSubscription()})
                                    DropdownMenuItem(text={Text("Настройка")},onClick={menu=false;settingsId=sub.id})
                                    DropdownMenuItem(text={Text(if(pinned)"Открепить"else"Закрепить")},
                                        onClick={menu=false;pinSubscription(sub.id)})
                                    DropdownMenuItem(text={Text("Экспорт ссылки")},enabled=sub.url!=null,
                                        onClick={menu=false;sub.url?.let{ClipboardTransfers.copy(context,"Subscription",it)}})
                                    DropdownMenuItem(text={Text("Удалить подписку")},onClick={menu=false;removeId=sub.id})
                                }
                            }
                        }
                        SubscriptionUsage(sub)
                        sub.error?.let{Text(it,color=AppColors.warning,fontSize=10.sp,maxLines=2)}
                    }
                } else {
                    Surface(onClick={toggleSubscription(sub.id)},color=AppColors.surface,
                        shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,AppColors.border),
                        modifier=Modifier.fillMaxWidth().padding(top=10.dp,bottom=4.dp)){
                        Column(Modifier.padding(10.dp)){
                            Row(verticalAlignment=Alignment.CenterVertically){
                                Icon(Icons.Outlined.ExpandMore,null,Modifier.size(24.dp).rotate(angle))
                                Text(label,Modifier.weight(1f),fontSize=16.sp,maxLines=2)
                                IconButton(onClick={menu=true}){Icon(Icons.Outlined.MoreVert,"Действия")}
                                DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                                    DropdownMenuItem(text={Text("Удалить")},onClick={menu=false;removeId=sub.id})
                                }
                            }
                            if(expanded) {
                                SubscriptionUsage(sub)
                                Row {
                                    TextButton(onClick={refreshSubscription(sub.id)}){Text("Обновить")}
                                    TextButton(onClick={pingSubscription(sub.id)}){Text("Ping")}
                                }
                            }
                        }
                    }
                }
            }
            if(expanded){
                if(sub.metadata.announcement.isNotBlank())item(key="${sub.id}:announcement"){
                    Column(Modifier.fillMaxWidth().padding(top=1.dp,bottom=3.dp)
                        .background(AppColors.surface.copy(alpha=.20f),RoundedCornerShape(10.dp))
                        .padding(horizontal=8.dp,vertical=5.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
                        Text(sub.metadata.announcement,fontSize=10.sp,lineHeight=14.sp,color=AppColors.muted,
                            textAlign=androidx.compose.ui.text.style.TextAlign.Center,modifier=Modifier.fillMaxWidth(),
                            softWrap=true)
                    }
                }
                items(if(sort)visible.sortedBy{it.latencyMs?:Long.MAX_VALUE}else visible,key={"${sub.id}:${it.id}"}){server->ServerRow(server,displays.getValue(server.id),state,vpn,select,favorite,server.id in activeProbes,Modifier.animateItem().then(if(canPing&&!probing&&server.enabled)Modifier.pointerInput(server.id){
                    var distance=0f
                    detectHorizontalDragGestures(onDragStart={distance=0f},onDragCancel={distance=0f},onDragEnd={if(distance>=64.dp.toPx())pingServer(server.id);distance=0f}){change,amount->change.consume();distance+=amount}
                }else Modifier))}
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
    val largeText=!BuildConfig.SIGNET_ACCOUNT&&LocalConfiguration.current.fontScale>=1.5f
    Row(modifier.fillMaxWidth().semantics { this.selected=selected; stateDescription=if(active)"Подключён" else if(selected)"Выбран" else "Не выбран" }.padding(vertical=if(BuildConfig.SIGNET_ACCOUNT)1.dp else 3.dp).heightIn(min=if(BuildConfig.SIGNET_ACCOUNT)56.dp else 43.dp).border(if(selected)1.dp else 0.dp,if(selected)AppColors.border.copy(alpha=.72f) else Color.Transparent,RoundedCornerShape(10.dp)).background(if(selected)AppColors.tint.copy(alpha=.28f) else Color.Transparent,RoundedCornerShape(10.dp)).combinedClickable(enabled=server.enabled,role=Role.RadioButton,onClick={select(server.id)},onLongClick={favorite(server.id)},onLongClickLabel=if(server.favorite)"Убрать из избранного"else"В избранное").padding(horizontal=if(BuildConfig.SIGNET_ACCOUNT)8.dp else 6.dp,vertical=if(BuildConfig.SIGNET_ACCOUNT)7.dp else 5.dp),verticalAlignment=if(BuildConfig.SIGNET_ACCOUNT)Alignment.CenterVertically else Alignment.Top) {
        CountryFlag(display.country,display.isAuto)
        Column(Modifier.weight(1f).padding(horizontal=if(BuildConfig.SIGNET_ACCOUNT)8.dp else 6.dp),
            verticalArrangement=Arrangement.spacedBy(0.dp)) {
            Text(display.title,color=if(server.enabled)AppColors.text else AppColors.muted,fontWeight=FontWeight.SemiBold,lineHeight=16.sp,maxLines=if(largeText)2 else if(BuildConfig.SIGNET_ACCOUNT)2 else 1,overflow=TextOverflow.Ellipsis,fontSize=if(BuildConfig.SIGNET_ACCOUNT)15.sp else 14.sp)
            if(!BuildConfig.SIGNET_ACCOUNT) {
                if(display.transport.isNotBlank())Text(display.transport,color=AppColors.muted,fontSize=9.sp,lineHeight=11.sp,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
                display.description?.takeIf{it.isNotBlank()}?.let{Text(it,color=AppColors.muted.copy(alpha=.82f),
                    fontSize=9.sp,lineHeight=11.sp,maxLines=if(largeText)3 else 2,overflow=TextOverflow.Ellipsis)}
            } else {
                Row(Modifier.fillMaxWidth()){
                    Text(display.transport,Modifier.weight(1f),color=AppColors.muted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                display.description?.let{Text(it,color=AppColors.muted,fontSize=12.sp,maxLines=if(largeText)2 else 1,overflow=TextOverflow.Ellipsis)}
            }
        }
        Column(horizontalAlignment=Alignment.End) {
            if(probing)CircularProgressIndicator(Modifier.size(if(BuildConfig.SIGNET_ACCOUNT)18.dp else 14.dp),strokeWidth=2.dp)
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
            Text(when {server.probeMessage=="CONSENT_REQUIRED"->"Нужно согласие";server.probeMessage=="UNSUPPORTED"->"—";server.latencyMs!=null->"${server.latencyMs} ms";problem!=null->problem;server.available==false->"Нет ответа";else->"—"},color=if(server.available==false||problem!=null)AppColors.warning else AppColors.muted,fontSize=if(BuildConfig.SIGNET_ACCOUNT)11.sp else 10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(active&&BuildConfig.SIGNET_ACCOUNT)Text("Подключён",color=color,fontSize=10.sp)
        }
        if(BuildConfig.SIGNET_ACCOUNT) {
            if(server.favorite)Icon(Icons.Outlined.Star,"Избранное",Modifier.size(13.dp),tint=AppColors.warning)
            if(!largeText)Icon(if(selected)Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                if(selected)"Выбран"else"Выбрать сервер",Modifier.size(20.dp),tint=if(selected)AppColors.accent else AppColors.muted)
        }
    }
    if(BuildConfig.SIGNET_ACCOUNT)HorizontalDivider(color=AppColors.border.copy(alpha=.4f))
}
