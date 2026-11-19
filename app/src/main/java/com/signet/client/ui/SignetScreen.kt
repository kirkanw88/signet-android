package com.signet.client.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import com.signet.client.qr.QrScannerActivity
import com.signet.client.qr.QrImage
import com.signet.client.*
import com.signet.client.R
import com.signet.client.data.*
import com.signet.client.graphics.GlobeView
import com.signet.client.vpn.*
import com.signet.core.Server
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date
import kotlin.math.*

private val Background:Color @Composable get()=MaterialTheme.colorScheme.background
private val Surface:Color @Composable get()=AppColors.surface
private val Primary:Color @Composable get()=AppColors.accent
private val Muted:Color @Composable get()=AppColors.muted
private val Green:Color @Composable get()=AppColors.success
private val Warning:Color @Composable get()=AppColors.warning
private val Error:Color @Composable get()=MaterialTheme.colorScheme.error

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SignetScreen(model: MainViewModel, connect: ()->Unit) {
    val storedState by model.state.collectAsStateWithLifecycle()
    val refreshing by model.refreshing.collectAsStateWithLifecycle()
    val refreshingGroups by model.refreshingSubscriptions.collectAsStateWithLifecycle()
    val activeProbes by model.probingIds.collectAsStateWithLifecycle()
    val state=storedState.copy(busy=storedState.busy||refreshing)
    val vpn by model.vpn.collectAsStateWithLifecycle()
    val probing by model.probing.collectAsStateWithLifecycle()
    val update by model.updates.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var showServers by rememberSaveable { mutableStateOf(false) }
    var openDiagnostics by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled=page!=0){page=if(page==2)3 else 0}
    var add by rememberSaveable { mutableStateOf(false) }
    var addMenu by rememberSaveable{mutableStateOf(false)}
    var input by remember { mutableStateOf("") }
    var ageKey by remember { mutableStateOf("") }
    var oauthConsent by remember{mutableStateOf(false)}
    var offeredUpdate by rememberSaveable{mutableStateOf("")}
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    LaunchedEffect(update.available?.version,update.dismissed){
        update.available?.takeIf{it.version.toString()!=update.dismissed&&it.version.toString()!=offeredUpdate}?.let{offeredUpdate=it.version.toString();model.installer.offer(it)}
    }
    val accountPage by model.accountPage.collectAsStateWithLifecycle()
    val accountCallback=MainActivity.pendingAccount.value
    LaunchedEffect(accountCallback){if(BuildConfig.SIGNET_ACCOUNT)accountCallback?.let{uri->try{if(model.accountPage.value==null)model.openAccount();model.account.callback(uri)}finally{MainActivity.pendingAccount.value=null}}}

    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state.ready,state.settings.probes,vpn.phase) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if(state.ready&&state.settings.probes.automatic)while(true){
                if(model.state.value.server!=null&&!model.probing.value&&vpn.phase in listOf(VpnPhase.DISCONNECTED,VpnPhase.CONNECTED))model.measure(onlySelected=true,automatic=true)
                delay(state.settings.probes.autoMinutes*60_000L)
            }
        }
    }
    val snackbar=remember { SnackbarHostState() }
    val pending=MainActivity.pendingImport.value
    LaunchedEffect(pending) { if(pending != null) { input=pending;ageKey="";oauthConsent=false;add=true; MainActivity.pendingImport.value=null } }
    LaunchedEffect(state.message) { state.message?.let { message->
        try{snackbar.showSnackbar(message)}finally{if(model.repository.state.value.message==message)model.repository.clearMessage()}
    } }
    LaunchedEffect(state.ready,state.settings.automaticUpdates){
        if(state.ready&&(state.settings.automaticUpdates||!BuildConfig.SIGNET_ACCOUNT))lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
            do{model.checkUpdates(automatic=true);if(BuildConfig.SIGNET_ACCOUNT)break;delay(2*60_000L)}while(true)
        }
    }
    LaunchedEffect(state.ready) { if(state.ready && state.settings.autoConnect && state.server != null && vpn.phase==VpnPhase.DISCONNECTED) connect() }
    val qr=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringExtra("content")?.let { input=it;add=true }
    }
    SignetTheme(if(BuildConfig.SIGNET_ACCOUNT)"dark"else state.settings.theme) {
        SpaceBackdrop(Modifier.fillMaxSize()) {
        Scaffold(containerColor=Color.Transparent,contentColor=AppColors.text,snackbarHost={}) { insets ->
            Column(Modifier.fillMaxSize().padding(insets)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    if(page==0)Image(painterResource(R.drawable.signet_logo),"Signet VPN",Modifier.width(80.dp).height(48.dp),colorFilter=if(AppColors.dark)null else androidx.compose.ui.graphics.ColorFilter.tint(AppColors.text))
                    else IconButton(onClick={page=if(page==2)3 else 0}){Icon(Icons.Outlined.ArrowBack,"Назад",tint=AppColors.text)}
                    if(page!=0)Text(if(page==3)"Настройки"else"Подписки",color=AppColors.text,fontSize=20.sp,fontWeight=FontWeight.Medium)
                    if(page==0&&!BuildConfig.SIGNET_ACCOUNT)Text("v${BuildConfig.VERSION_NAME}",fontSize=13.sp,color=AppColors.muted)
                    Row {
                        if(page==0&&!BuildConfig.SIGNET_ACCOUNT)IconButton(onClick={addMenu=true},enabled=!state.busy&&state.subscriptions.size<8,modifier=Modifier.testTag("home-add-subscription")){Icon(Icons.Outlined.Add,"Добавить подписку",tint=if(state.busy||state.subscriptions.size>=8)AppColors.muted else Primary)}
                        if(BuildConfig.SIGNET_ACCOUNT)IconButton(onClick={model.openAccount("support")}){Icon(Icons.Outlined.SupportAgent,"Техническая поддержка",tint=AppColors.muted)}
                        IconButton(onClick={page=if(page==3)0 else 3}) { Icon(if(page==3)Icons.Outlined.Close else Icons.Outlined.Settings,if(page==3)"Закрыть настройки"else"Настройки",tint=AppColors.muted) }
                    }
                }
                SnackbarHost(snackbar,Modifier.fillMaxWidth()){data->Snackbar(data,containerColor=Surface,contentColor=AppColors.text,shape=RoundedCornerShape(16.dp),modifier=Modifier.padding(horizontal=16.dp))}
                update.available?.let{UpdateBanner(it,model)}
                when(page) {
                    0 -> Home(state,vpn,model,connect,{if(BuildConfig.SIGNET_ACCOUNT)page=2;addMenu=true},{showServers=true})
                    2 -> Subscriptions(state,{addMenu=true},model::remove,model::refresh,model::openAccount,if(BuildConfig.SIGNET_ACCOUNT)model.account else null, model, {
                        runCatching{ClipboardTransfers.read(context,16*1024).also{require(com.signet.core.SubscriptionMetadata.safeLink(it)!=null)}}
                            .onSuccess{input=it;ageKey="";oauthConsent=false;add=true}.onFailure{model.repository.notify("В буфере нет корректной HTTPS ссылки подписки")}
                    })
                    3 -> Preferences(state,model,{page=2},{showServers=true},openDiagnostics,{openDiagnostics=false})
                }
            }
        }
        }
        if(showServers){
            val sheet=rememberModalBottomSheetState(skipPartiallyExpanded=true)
            ModalBottomSheet(onDismissRequest={showServers=false},sheetState=sheet,containerColor=Surface,contentColor=AppColors.text,modifier=Modifier.testTag("server-sheet")) {
                LocationList(state,vpn,probing,{id->model.select(id);scope.launch{sheet.hide();showServers=false}},model::favorite,{model.measure()},model::refresh,
                    Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal=20.dp),activeProbes=activeProbes,toggleSubscription=model::toggleSubscription,pinSubscription=model::pinSubscription,refreshSubscription=model::refreshSubscription,pingSubscription=model::measureSubscription,removeSubscription=model::remove,refreshingSubscriptions=refreshingGroups,addSubscription={showServers=false;addMenu=true})
            }
        }
        if(BuildConfig.SIGNET_ACCOUNT&&accountPage!=null)MobileAccountScreen(model,accountPage!!){model.accountPage.value=null}
        UpdateWindow(model)
        if(addMenu)ModalBottomSheet(onDismissRequest={addMenu=false},containerColor=Surface,contentColor=AppColors.text){
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text("Добавить подписку",fontSize=24.sp,fontWeight=FontWeight.SemiBold)
                GlassButton(onClick={addMenu=false;runCatching{ClipboardTransfers.read(context,16*1024).also{require(com.signet.core.SubscriptionMetadata.safeLink(it)!=null)}}.onSuccess{input=it;ageKey="";oauthConsent=false;add=true}.onFailure{model.repository.notify("В буфере нет корректной ссылки подписки")}},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.ContentPaste,null);Text("Вставить из буфера обмена",Modifier.padding(start=10.dp))}
                GlassButton(onClick={addMenu=false;qr.launch(Intent(context,QrScannerActivity::class.java))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.QrCodeScanner,null);Text("Отсканировать QR-код",Modifier.padding(start=10.dp))}
                if(!BuildConfig.SIGNET_ACCOUNT)GlassButton(onClick={addMenu=false;input="";ageKey="";oauthConsent=false;add=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Edit,null);Text("Добавить вручную",Modifier.padding(start=10.dp))}
            }
        }
        if(add) AlertDialog(onDismissRequest={add=false;input="";ageKey="";oauthConsent=false},title={Text("Добавить подписку")},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("HTTPS ссылка, VLESS / Hysteria2 или полный Xray JSON. Ссылки подписки содержат ваш ключ доступа.",color=Muted)
                if(input.length>32*1024) {
                    Text("Документ ${bytes(input.toByteArray().size.toLong())} готов к импорту. Предпросмотр ограничен, чтобы не перегружать память.",fontSize=12.sp,color=Muted)
                    Text(input.take(1500),fontSize=11.sp,maxLines=8,overflow=TextOverflow.Ellipsis)
                    TextButton(onClick={input="";ageKey="";oauthConsent=false}){Text("Очистить документ")}
                } else OutlinedTextField(value=input,onValueChange={if(it.length<=4*1024*1024)input=it},label={Text("Ссылка или конфигурация")},minLines=3,maxLines=7,modifier=Modifier.fillMaxWidth())
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(oauthConsent,{oauthConsent=it});Text("Разрешить OAuth-доступ XDrive из этой подписки",fontSize=12.sp)}
                OutlinedTextField(value=ageKey,onValueChange={ageKey=it},label={Text("Ключ AGE · если требуется")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
            }
        },confirmButton={TextButton(onClick={model.import(input,ageKey,oauthConsent);input="";ageKey="";oauthConsent=false;add=false},enabled=input.isNotBlank()&&!state.busy){Text("Импортировать")}},dismissButton={TextButton(onClick={input="";ageKey="";oauthConsent=false;add=false}){Text("Отмена")}})
    }
}

@Composable private fun Home(state:AppState,vpn:VpnStatus,model:MainViewModel,connect:()->Unit,add:()->Unit,servers:()->Unit) {
    val phase=vpn.phase
    val active=vpn.tunActive||phase in listOf(VpnPhase.CONNECTED,VpnPhase.CONNECTING,VpnPhase.RECONNECTING)
    val server=if(phase==VpnPhase.CONNECTED)state.servers.firstOrNull{it.id==vpn.serverId}else state.server
    val display=remember(state.servers,server?.id){com.signet.core.ServerDisplayModel.all(state.subscriptions.firstOrNull{it.servers.any{node->node.id==server?.id}}?.servers.orEmpty(),hostNames=!BuildConfig.SIGNET_ACCOUNT)[server?.id]}
    val status=when(phase){VpnPhase.CONNECTED->"Подключено";VpnPhase.CONNECTING->"Подключение";VpnPhase.RECONNECTING->"Переподключение";VpnPhase.ERROR->"Ошибка подключения";VpnPhase.DISCONNECTING->"Отключение";else->"Не подключено"}
    @Composable fun controls() {
        Row(Modifier.fillMaxWidth().clickable(onClick=servers).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
            CountryFlag(display?.country,display?.isAuto?:false)
            Text(display?.title?:"Выбрать сервер",Modifier.weight(1f).padding(horizontal=12.dp),fontSize=16.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
            Icon(Icons.Outlined.ChevronRight,"Список серверов",tint=Primary)
        }
        state.subscriptions.firstOrNull{sub->sub.servers.any{it.id==server?.id}}?.let{SubscriptionUsage(it)}
        vpn.error?.let{Text(it,color=Warning,fontSize=14.sp,lineHeight=20.sp,maxLines=3,overflow=TextOverflow.Ellipsis)}
        if(phase==VpnPhase.CONNECTED)Surface(color=Surface,shape=RoundedCornerShape(16.dp),modifier=Modifier.testTag("home-traffic").fillMaxWidth().padding(vertical=6.dp)){
            Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)){
                Column(Modifier.weight(1f)){
                    Text("↑ Отправка",color=Muted,fontSize=11.sp)
                    Text("${bytes(vpn.uploadRate)}/с",fontSize=16.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
                    Text("Всего ${bytes(vpn.uploaded)}",color=Muted,fontSize=10.sp,maxLines=1)
                }
                Column(Modifier.weight(1f)){
                    Text("↓ Получение",color=Muted,fontSize=11.sp)
                    Text("${bytes(vpn.downloadRate)}/с",fontSize=16.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
                    Text("Всего ${bytes(vpn.downloaded)}",color=Muted,fontSize=10.sp,maxLines=1)
                }
            }
        }
        GlassButton(onClick={if(active)model.stop()else if(server==null)add()else connect()},enabled=phase!=VpnPhase.DISCONNECTING,modifier=Modifier.testTag("home-connect").fillMaxWidth().heightIn(min=58.dp)) {
            if(phase in listOf(VpnPhase.CONNECTING,VpnPhase.RECONNECTING))CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=Primary)else Icon(Icons.Outlined.PowerSettingsNew,null)
            Text(if(phase==VpnPhase.DISCONNECTING)"Отключение…"else if(active)"Отключиться"else if(server==null)"Добавить подписку"else"Подключиться",Modifier.padding(start=8.dp),maxLines=2)
        }

    }
    val density=LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport=with(density){(maxHeight-12.dp).roundToPx().coerceAtLeast(0)}
        val scroll=rememberScrollState()
        if(LocalConfiguration.current.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)Row(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)){
            Globe(server,phase,state.settings,Modifier.weight(1.2f).fillMaxHeight(),model.capitalImagery)
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll),horizontalAlignment=Alignment.CenterHorizontally){Text(status,fontSize=22.sp,color=if(phase==VpnPhase.CONNECTED)Green else AppColors.text);controls()}
        }else SubcomposeLayout(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal=16.dp).padding(bottom=12.dp)){constraints->
            val natural=constraints.copy(minHeight=0,maxHeight=Constraints.Infinity)
            val footer=subcompose("controls"){Column(Modifier.fillMaxWidth()){controls()}}.single().measure(natural)
            val heading=subcompose("status"){Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){Text(status,fontSize=24.sp,color=if(phase==VpnPhase.CONNECTED)Green else AppColors.text,maxLines=1)}}.single().measure(natural)
            val height=com.signet.core.HomeMeasurements.globeHeight(viewport,heading.height,footer.height)
            val planet=if(height>0)subcompose("globe"){Globe(server,phase,state.settings,Modifier.fillMaxSize(),model.capitalImagery)}.single().measure(Constraints.fixed(constraints.maxWidth,height))else null
            layout(constraints.maxWidth,(heading.height+height+footer.height).coerceAtLeast(viewport)){
                heading.placeRelative(0,0)
                planet?.placeRelative(0,heading.height)
                footer.placeRelative(0,heading.height+height)
            }
        }
    }
}

@Composable private fun Globe(server:Server?,phase:VpnPhase,settings:Settings,modifier:Modifier,imagery:com.signet.client.graphics.CapitalImagery?) {
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var renderer by remember{mutableStateOf<com.signet.client.graphics.GlobeRenderer?>(null)}
    var photoCovered by remember{mutableStateOf(false)}
    val parentScroll=!BuildConfig.SIGNET_ACCOUNT&&LocalConfiguration.current.orientation!=android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val emptyMarker=remember{kotlinx.coroutines.flow.MutableStateFlow(com.signet.client.graphics.CapitalMarker())}
    val marker by (renderer?.capitalMarker?:emptyMarker).collectAsStateWithLifecycle()
    val lightTheme=!AppColors.dark
    var labelSize by remember{mutableStateOf(IntSize.Zero)}
    var area by remember{mutableStateOf(IntSize.Zero)}
    Box(modifier.testTag("home-globe").clip(RoundedCornerShape(if(AppColors.dark)0.dp else 24.dp)).onSizeChanged{area=it}) {
        AndroidView(factory={context->GlobeView(context,settings.graphics=="High").also{it.bindHost(lifecycle);renderer=it.globe}},modifier=Modifier.fillMaxSize(),onRelease={it.releaseHost()},update={it.bindHost(lifecycle);it.photoCover(photoCovered);it.update(server,phase,settings.graphics,settings.reduceMotion,lightTheme,parentScroll)})
        AnimatedVisibility(marker.visible,enter=fadeIn(tween(350)),exit=fadeOut(tween(150)),modifier=Modifier.offset{
            IntOffset((marker.x-labelSize.width/2).coerceIn(0,(area.width-labelSize.width).coerceAtLeast(0)),(marker.y-labelSize.height).coerceAtLeast(0))
        }) {
            Column(Modifier.onSizeChanged{labelSize=it},horizontalAlignment=Alignment.CenterHorizontally){
                Text(marker.name,color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=15.sp,modifier=Modifier.background(Color(0xDC0D1728),RoundedCornerShape(10.dp)).padding(horizontal=10.dp,vertical=5.dp))
                Icon(Icons.Outlined.LocationOn,null,tint=Primary,modifier=Modifier.size(28.dp))
            }
        }
        if(!BuildConfig.SIGNET_ACCOUNT&&imagery!=null)CapitalView(server?.countryCode,phase,marker.visible,imagery){photoCovered=it}
    }
}

@Composable private fun Subscriptions(state:AppState,add:()->Unit,remove:(String)->Unit,refresh:()->Unit,account:(String)->Unit,mobile:MobileAccount?,model:MainViewModel,paste:()->Unit){
    if(!BuildConfig.SIGNET_ACCOUNT){
        val vpn by model.vpn.collectAsStateWithLifecycle();val probing by model.probing.collectAsStateWithLifecycle()
        val active by model.probingIds.collectAsStateWithLifecycle();val refreshing by model.refreshingSubscriptions.collectAsStateWithLifecycle()
        LocationList(state,vpn,probing,model::select,model::favorite,{model.measure()},model::refresh,Modifier.fillMaxSize().padding(horizontal=20.dp),activeProbes=active,toggleSubscription=model::toggleSubscription,pinSubscription=model::pinSubscription,refreshSubscription=model::refreshSubscription,pingSubscription=model::measureSubscription,removeSubscription=model::remove,refreshingSubscriptions=refreshing,addSubscription=add,management=true)
        return
    }
    val context=LocalContext.current
    val remote=if(mobile!=null){val observed by mobile.state.collectAsStateWithLifecycle();observed}else MobileAccountState()
    LaunchedEffect(remote.loggedIn){if(remote.loggedIn)mobile?.refresh()}
    val current=state.subscriptions.firstOrNull{sub->sub.servers.any{it.id==state.selected}}?:state.subscriptions.firstOrNull()
    var confirmRemove by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Текущая подписка",fontSize=26.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=8.dp,bottom=4.dp))
        if(current==null)Text(if(BuildConfig.SIGNET_ACCOUNT)"Добавьте подписку из личного кабинета"else"Добавьте ссылку подписки или QR-код",color=Muted)
        else Card(colors=CardDefaults.cardColors(containerColor=Surface)){
            Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Signet VPN",fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                SubscriptionUsage(current)
                current.error?.let{Text(it,color=Warning,fontSize=13.sp)}
                GlassButton(onClick=refresh,enabled=!state.busy,modifier=Modifier.fillMaxWidth()){
                    if(state.busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)else Icon(Icons.Outlined.Refresh,null)
                    Text("Обновить подписку",Modifier.padding(start=10.dp))
                }
            }
        }
        if(BuildConfig.SIGNET_ACCOUNT){
        AccountAction(Icons.Outlined.AccountBalanceWallet,remote.account?.let{"Баланс: ${it.optLong("balance_rub")} ₽"}?:"Баланс и пополнение",{account("balance")})
        AccountAction(Icons.Outlined.Devices,"Управление устройствами",{account("devices")})
        AccountAction(Icons.Outlined.Tune,"Настройки подписки",{account("settings")})
        }
        AccountAction(Icons.Outlined.Add,"Добавить подписку",add)
        AccountAction(Icons.Outlined.FileDownload,"Импорт ссылки из буфера",paste)
        current?.url?.let{url->AccountAction(Icons.Outlined.FileUpload,"Экспорт ссылки в буфер",{ClipboardTransfers.copy(context,"Signet subscription",url);android.widget.Toast.makeText(context,"Ссылка скопирована",android.widget.Toast.LENGTH_SHORT).show()})}
        if(current!=null)AccountAction(Icons.Outlined.DeleteOutline,"Удалить с устройства",{confirmRemove=true})
        Spacer(Modifier.height(20.dp))
    }
    if(confirmRemove&&current!=null)AlertDialog(onDismissRequest={confirmRemove=false},title={Text("Удалить подписку с устройства?")},text={Text(if(BuildConfig.SIGNET_ACCOUNT)"Подписка и устройства в кабинете сохранятся."else"Удалится только сохранённая на этом устройстве подписка.")},confirmButton={TextButton(onClick={remove(current.id);confirmRemove=false}){Text("Удалить")}},dismissButton={TextButton(onClick={confirmRemove=false}){Text("Отмена")}})
}

@Composable private fun AccountAction(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,click:()->Unit){
    Surface(onClick=click,color=AppColors.surface,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,AppColors.text.copy(alpha=.12f)),modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(horizontal=16.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
            Icon(icon,null,tint=Primary,modifier=Modifier.size(23.dp));Text(title,Modifier.weight(1f),fontSize=16.sp);Icon(Icons.Outlined.ChevronRight,null,tint=Muted)
        }
    }
}

@Composable internal fun SubscriptionUsage(subscription:Subscription){
    val info=subscription.info
    if(info.isEmpty())return
    val total=info["total"]?.coerceAtLeast(0)
    val used=((info["upload"]?:0).coerceAtLeast(0).toDouble()+(info["download"]?:0).coerceAtLeast(0)).coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()
    Column(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
        if(total!=null){
            if(total>0)LinearProgressIndicator(progress={(used.toDouble()/total).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=if(used>=total)Warning else Primary,trackColor=AppColors.text.copy(alpha=.15f))
            Text(if(total>0)"${bytes(used)} из ${bytes(total)}"else"${bytes(used)} · Безлимитный трафик",color=if(total!=null&&total>0&&used>=total)Warning else Muted,fontSize=12.sp)
        }
        info["expire"]?.let{expiry->
            if(expiry>0&&expiry<=Long.MAX_VALUE/1000){val end=expiry*1000;Text("${if(end<=System.currentTimeMillis())"Истекла"else"Действует до"} ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(end))}",color=if(end<=System.currentTimeMillis())Warning else Muted,fontSize=12.sp)}
            else if(expiry==0L)Text("Без ограничения срока",color=Muted,fontSize=12.sp)
        }
    }
}

@Composable private fun Title(title:String,subtitle:String){Column(Modifier.padding(top=8.dp,bottom=20.dp)){Text(title,fontSize=28.sp,fontWeight=FontWeight.Light);Text(subtitle,color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=6.dp))}}
@Composable private fun Section(text:String){Text(text,color=Muted,fontSize=10.sp,letterSpacing=2.sp,modifier=Modifier.padding(top=22.dp,bottom=8.dp))}
@Composable private fun Toggle(text:String,value:Boolean,change:(Boolean)->Unit){Row(Modifier.fillMaxWidth().heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically){Text(text,Modifier.weight(1f),fontSize=14.sp);Switch(value,change)}}
@Composable private fun Empty(title:String,subtitle:String){Column(Modifier.fillMaxWidth().padding(vertical=32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Outlined.Public,null,tint=Primary,modifier=Modifier.size(36.dp));Text(title,fontSize=20.sp);Text(subtitle,color=Muted,fontSize=14.sp)}}
@Composable private fun Latency(s:Server){Text(if(s.available==false)"Недоступен" else s.latencyMs?.let{"$it ms · HTTP"}?:"—",color=when{ s.available==false->Error;s.latencyMs==null->Muted;s.latencyMs!!<=80->Green;s.latencyMs!!<=160->Warning;else->Error},fontSize=11.sp)}
private fun bytes(n:Long):String=when{n>=(1L shl 30)->"%.1f ГБ".format(n.toDouble()/(1L shl 30));n>=(1L shl 20)->"%.1f МБ".format(n.toDouble()/(1L shl 20));n>=1024->"%.1f КБ".format(n/1024.0);else->"$n Б"}
