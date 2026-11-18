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

private val Background=Color(0xFF050816)
private val Surface=Color(0xFF10182A)
private val Primary=Color(0xFF39C8FF)
private val Muted=Color(0xFF9BAAC2)
private val Green=Color(0xFF32E6A1)
private val Warning=Color(0xFFFFC857)
private val Error=Color(0xFFFF657A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SignetScreen(model: MainViewModel, connect: ()->Unit) {
    val storedState by model.state.collectAsStateWithLifecycle()
    val refreshing by model.refreshing.collectAsStateWithLifecycle()
    val activeProbes by model.probingIds.collectAsStateWithLifecycle()
    val state=storedState.copy(busy=storedState.busy||refreshing)
    val vpn by model.vpn.collectAsStateWithLifecycle()
    val probing by model.probing.collectAsStateWithLifecycle()
    val update by model.updates.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var showServers by rememberSaveable { mutableStateOf(false) }
    var openDiagnostics by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled=page!=0){page=if(page==2)3 else 0}
    var showAccount by rememberSaveable{mutableStateOf(false)}
    var add by rememberSaveable { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var ageKey by remember { mutableStateOf("") }
    var oauthConsent by remember{mutableStateOf(false)}
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
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
    LaunchedEffect(state.ready,state.settings.automaticUpdates){if(state.ready&&state.settings.automaticUpdates)model.checkUpdates(automatic=true)}
    LaunchedEffect(state.ready) { if(state.ready && state.settings.autoConnect && state.server != null && vpn.phase==VpnPhase.DISCONNECTED) connect() }
    val file=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) scope.launch(Dispatchers.IO) {
            runCatching {
                val bytes=context.contentResolver.openInputStream(uri)!!.use { stream -> stream.readLimited(4*1024*1024) }
                require(bytes.size <= 4*1024*1024)
                withContext(Dispatchers.Main) { input=String(bytes,Charsets.UTF_8); add=true }
            }.onFailure { model.repository.notify("Не удалось прочитать файл (максимум 4 МБ)") }
        }
    }
    val qr=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringExtra("content")?.let { input=it;add=true }
    }
    val qrImage=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri!=null)scope.launch(Dispatchers.IO) { runCatching { QrImage.decode(context,uri) }
            .onSuccess { text -> withContext(Dispatchers.Main) {input=text;add=true} }
            .onFailure { model.repository.notify("На изображении не найден поддерживаемый QR-код") } }
    }
    MaterialTheme(colorScheme=darkColorScheme(primary=Primary, secondary=Color(0xFF62F4E8), background=Background, surface=Surface, onSurface=Color.White, error=Error)) {
        SpaceBackdrop(Modifier.fillMaxSize()) {
        Scaffold(containerColor=Color.Transparent,contentColor=Color.White,snackbarHost={}) { insets ->
            Column(Modifier.fillMaxSize().padding(insets)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    if(page==0)Image(painterResource(R.drawable.signet_logo),"Signet VPN",Modifier.width(80.dp).height(48.dp))
                    else IconButton(onClick={page=if(page==2)3 else 0}){Icon(Icons.Outlined.ArrowBack,"Назад",tint=Color.White)}
                    if(page!=0)Text(if(page==3)"Настройки"else"Подписки",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Medium)
                    Row {
                        IconButton(onClick={openHttps(context,com.signet.core.SignetLinks.SUPPORT)}){Icon(Icons.Outlined.SupportAgent,"Техническая поддержка",tint=Color(0xFFCFDBEB))}
                        IconButton(onClick={page=if(page==3)0 else 3}) { Icon(if(page==3)Icons.Outlined.Close else Icons.Outlined.Settings,if(page==3)"Закрыть настройки"else"Настройки",tint=Color(0xFFCFDBEB)) }
                    }
                }
                SnackbarHost(snackbar,Modifier.fillMaxWidth()){data->Snackbar(data,containerColor=Surface,contentColor=Color.White,shape=RoundedCornerShape(16.dp),modifier=Modifier.padding(horizontal=16.dp))}
                update.available?.takeIf{it.version.toString()!=update.dismissed}?.let{UpdateBanner(it,model)}
                when(page) {
                    0 -> Home(state,vpn,model,connect,{page=2;add=true},{showServers=true})
                    2 -> Subscriptions(state,{input="";add=true},{file.launch(arrayOf("text/*","application/json","application/octet-stream"))},{qr.launch(Intent(context,QrScannerActivity::class.java))},{qrImage.launch("image/*")},model::remove,model::refresh,{showAccount=true},{
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
            ModalBottomSheet(onDismissRequest={showServers=false},sheetState=sheet,containerColor=Surface,contentColor=Color.White,modifier=Modifier.testTag("server-sheet")) {
                LocationList(state,vpn,probing,{id->model.select(id);scope.launch{sheet.hide();showServers=false}},model::favorite,{model.measure()},model::refresh,
                    Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal=20.dp),activeProbes=activeProbes)
            }
        }
        if(showAccount)AccountScreen(model){showAccount=false}
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
    val display=remember(state.servers,server?.id){com.signet.core.ServerDisplayModel.all(state.servers)[server?.id]}
    val status=when(phase){VpnPhase.CONNECTED->"Подключено";VpnPhase.CONNECTING->"Подключение";VpnPhase.RECONNECTING->"Переподключение";VpnPhase.ERROR->"Ошибка подключения";VpnPhase.DISCONNECTING->"Отключение";else->"Не подключено"}
    @Composable fun controls() {
        Row(Modifier.fillMaxWidth().clickable(onClick=servers).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
            CountryFlag(display?.country,display?.isAuto?:false)
            Text(display?.title?:"Выбрать сервер",Modifier.weight(1f).padding(horizontal=12.dp),fontSize=16.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
            Icon(Icons.Outlined.ChevronRight,"Список серверов",tint=Primary)
        }
        state.subscriptions.firstOrNull{sub->sub.servers.any{it.id==server?.id}}?.let{SubscriptionUsage(it)}
        vpn.error?.let{Text(it,color=Warning,fontSize=14.sp,lineHeight=20.sp,maxLines=3,overflow=TextOverflow.Ellipsis)}
        GlassButton(onClick={if(active)model.stop()else if(server==null)add()else connect()},enabled=phase!=VpnPhase.DISCONNECTING,modifier=Modifier.testTag("home-connect").fillMaxWidth().heightIn(min=58.dp)) {
            if(phase in listOf(VpnPhase.CONNECTING,VpnPhase.RECONNECTING))CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=Primary)else Icon(Icons.Outlined.PowerSettingsNew,null)
            Text(if(phase==VpnPhase.DISCONNECTING)"Отключение…"else if(active)"Отключиться"else if(server==null)"Добавить подписку"else"Подключиться",Modifier.padding(start=8.dp),maxLines=2)
        }
        if(phase==VpnPhase.CONNECTED)Text("↑ ${bytes(vpn.uploaded)}    ↓ ${bytes(vpn.downloaded)}",color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=6.dp))
    }
    if(LocalConfiguration.current.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)Row(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
        Globe(server,phase,state.settings,Modifier.weight(1.2f).fillMaxHeight())
        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Text(status,fontSize=22.sp,color=if(phase==VpnPhase.CONNECTED)Green else Color.White);controls()}
    } else Column(Modifier.fillMaxSize().padding(horizontal=16.dp).padding(bottom=12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(status,fontSize=24.sp,color=if(phase==VpnPhase.CONNECTED)Green else Color.White,maxLines=1)
        Globe(server,phase,state.settings,Modifier.weight(1f).fillMaxWidth())
        controls()
    }
}

@Composable private fun Globe(server:Server?,phase:VpnPhase,settings:Settings,modifier:Modifier) {
    val context=LocalContext.current;val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val view=remember { GlobeView(context,settings.graphics=="High") }
    DisposableEffect(lifecycle,view){
        val observer=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_RESUME->view.onResume();Lifecycle.Event.ON_PAUSE->view.onPause();else->Unit}}
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer);view.onPause() }
    }
    val marker by view.globe.capitalMarker.collectAsStateWithLifecycle()
    var labelSize by remember{mutableStateOf(IntSize.Zero)}
    var area by remember{mutableStateOf(IntSize.Zero)}
    Box(modifier.testTag("home-globe").onSizeChanged{area=it}) {
        AndroidView(factory={view},modifier=Modifier.fillMaxSize(),update={it.update(server,phase,settings.graphics,settings.reduceMotion)})
        AnimatedVisibility(marker.visible,enter=fadeIn(tween(350)),exit=fadeOut(tween(150)),modifier=Modifier.offset{
            IntOffset((marker.x-labelSize.width/2).coerceIn(0,(area.width-labelSize.width).coerceAtLeast(0)),(marker.y-labelSize.height).coerceAtLeast(0))
        }) {
            Column(Modifier.onSizeChanged{labelSize=it},horizontalAlignment=Alignment.CenterHorizontally){
                Text(marker.name,color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=15.sp,modifier=Modifier.background(Color(0xDC0D1728),RoundedCornerShape(10.dp)).padding(horizontal=10.dp,vertical=5.dp))
                Icon(Icons.Outlined.LocationOn,null,tint=Primary,modifier=Modifier.size(28.dp))
            }
        }
    }
}

@Composable private fun Subscriptions(state:AppState,add:()->Unit,file:()->Unit,qr:()->Unit,image:()->Unit,remove:(String)->Unit,refresh:()->Unit,account:()->Unit,paste:()->Unit){
    val context=LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal=22.dp)){
        Title("Подписки","Ваш доступ к Signet")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){GlassButton(onClick=add){Icon(Icons.Outlined.Add,null);Text("Добавить")};GlassButton(onClick=qr){Icon(Icons.Outlined.QrCodeScanner,"QR-код")};GlassButton(onClick=file){Icon(Icons.Outlined.FileOpen,"Из файла")}}
        Row(verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick=image){Text("QR из изображения")}
            IconButton(onClick=paste){Icon(Icons.Outlined.ContentPaste,"Добавить ссылку из буфера")}
        }
        TextButton(onClick=account){Text("Аккаунт Signet · подписки и оплата")}
        IconButton(onClick=refresh,enabled=!state.busy){if(state.busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)else Icon(Icons.Outlined.Refresh,"Обновить подписки")}
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)){
            if(state.subscriptions.isEmpty())item{Empty("Добро пожаловать","Добавьте ссылку из личного кабинета Signet или импортируйте конфигурацию. Данные хранятся на устройстве в зашифрованном виде.")}
            items(state.subscriptions,key={it.id}){s->Card(colors=CardDefaults.cardColors(containerColor=Surface)){
                Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){Text(com.signet.core.SubscriptionMetadata.text(s.title,200),Modifier.weight(1f),fontWeight=FontWeight.SemiBold);s.url?.let{url->IconButton(onClick={ClipboardTransfers.copy(context,"Signet subscription",url);android.widget.Toast.makeText(context,"Ссылка скопирована",android.widget.Toast.LENGTH_SHORT).show()}){Icon(Icons.Outlined.ContentCopy,"Копировать ссылку подписки",tint=Muted)}};IconButton(onClick={remove(s.id)}){Icon(Icons.Outlined.DeleteOutline,"Удалить подписку",tint=Muted)}}
                    Text("${s.servers.size} серверов · ${if(s.url!=null)"Автообновление" else "Локальный профиль"}",color=Muted,fontSize=12.sp)
                    Text(if(s.format=="XRAY_JSON")"Xray JSON · DNS и маршрутизация сохранены"else"URI-подписка · исходного Xray JSON-шаблона в ней нет",color=Muted,fontSize=12.sp)
                    if(s.metadata.announcement.isNotBlank())Text(s.metadata.announcement,color=Primary,fontSize=13.sp)
                    s.error?.let{Text(it,color=Warning,fontSize=12.sp)}
                    s.metadata.webPage?.let{url->TextButton(onClick={openHttps(context,url)}){Text("Страница подписки")}}
                    s.metadata.support?.let{url->TextButton(onClick={openHttps(context,url)}){Text("Поддержка подписки")}}
                    SubscriptionUsage(s)
                    if(state.settings.cabinet.startsWith("https://"))TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(state.settings.cabinet)))}){Text("Продлить в кабинете")}
                }
            }}
        }
    }
}

@Composable private fun SubscriptionUsage(subscription:Subscription){
    val info=subscription.info
    if(info.isEmpty())return
    val total=info["total"]?.coerceAtLeast(0)
    val used=((info["upload"]?:0).coerceAtLeast(0).toDouble()+(info["download"]?:0).coerceAtLeast(0)).coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()
    Column(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
        if(total!=null){
            if(total>0)LinearProgressIndicator(progress={(used.toDouble()/total).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=if(used>=total)Warning else Primary,trackColor=Color.White.copy(alpha=.15f))
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
