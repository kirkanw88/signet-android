package com.signet.client.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
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

@Composable fun SignetScreen(model: MainViewModel, connect: ()->Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val vpn by model.vpn.collectAsStateWithLifecycle()
    val probing by model.probing.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var add by rememberSaveable { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var ageKey by remember { mutableStateOf("") }
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state.ready,state.settings.probes) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if(state.ready&&state.settings.probes.automatic)while(true){
                if(model.state.value.server!=null&&!model.probing.value)model.measure(onlySelected=true,automatic=true)
                delay(state.settings.probes.autoMinutes*60_000L)
            }
        }
    }
    val snackbar=remember { SnackbarHostState() }
    val pending=MainActivity.pendingImport.value
    LaunchedEffect(pending) { if(pending != null) { input=pending; add=true; MainActivity.pendingImport.value=null } }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); model.repository.clearMessage() } }
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
        Scaffold(containerColor=Background, snackbarHost={SnackbarHost(snackbar)}, bottomBar={
            NavigationBar(containerColor=Background, tonalElevation=0.dp) {
                val labels=listOf("Главная","Серверы","Подписки","Настройки")
                val icons=listOf(Icons.Outlined.Public,Icons.Outlined.Dns,Icons.Outlined.CreditCard,Icons.Outlined.Tune)
                labels.forEachIndexed { i,label -> NavigationBarItem(selected=page==i,onClick={page=i},icon={Icon(icons[i],label)},label={Text(label,maxLines=1,fontSize=11.sp)},colors=NavigationBarItemDefaults.colors(indicatorColor=Surface,selectedIconColor=Primary,selectedTextColor=Primary,unselectedIconColor=Muted,unselectedTextColor=Muted)) }
            }
        }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Image(painterResource(R.drawable.signet_logo),"Signet VPN",Modifier.width(80.dp).height(48.dp))
                    Text("VPN / ANDROID",color=Muted,fontSize=10.sp,letterSpacing=2.sp)
                    IconButton(onClick={page=3}) { Icon(Icons.Outlined.Settings,"Настройки",tint=Muted) }
                }
                when(page) {
                    0 -> Home(state,vpn,model,connect,{page=2;add=true})
                    1 -> LocationList(state,vpn,probing,model::select,model::favorite,{model.measure()},model::refresh,Modifier.fillMaxSize().padding(horizontal=16.dp))
                    2 -> Subscriptions(state,{input="";add=true},{file.launch(arrayOf("text/*","application/json","application/octet-stream"))},{qr.launch(Intent(context,QrScannerActivity::class.java))},{qrImage.launch("image/*")},model::remove,model::refresh)
                    3 -> Preferences(state,model)
                }
            }
        }
        if(add) AlertDialog(onDismissRequest={add=false},title={Text("Добавить подписку")},text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("HTTPS ссылка, VLESS / Hysteria2 или полный Xray JSON. Ссылки подписки содержат ваш ключ доступа.",color=Muted)
                OutlinedTextField(value=input,onValueChange={if(it.length<=4*1024*1024)input=it},label={Text("Ссылка или конфигурация")},minLines=3,maxLines=7,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=ageKey,onValueChange={ageKey=it},label={Text("Ключ AGE · если требуется")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
            }
        },confirmButton={TextButton(onClick={model.import(input,ageKey);ageKey="";add=false},enabled=input.isNotBlank()&&!state.busy){Text("Импортировать")}},dismissButton={TextButton(onClick={ageKey="";add=false}){Text("Отмена")}})
    }
}

@Composable private fun Home(state:AppState,vpn:VpnStatus,model:MainViewModel,connect:()->Unit,add:()->Unit) {
    val phase=vpn.phase
    val active=phase in listOf(VpnPhase.CONNECTED,VpnPhase.CONNECTING,VpnPhase.RECONNECTING)
    val server=if(phase==VpnPhase.CONNECTED)state.servers.firstOrNull{it.id==vpn.serverId}else state.server
    val display=server?.let{com.signet.core.ServerDisplayModel.from(it)}
    val status=when(phase){VpnPhase.CONNECTED->"????????";VpnPhase.CONNECTING->"???????????";VpnPhase.RECONNECTING->"???????????????";VpnPhase.ERROR->"?????? ???????????";VpnPhase.DISCONNECTING->"??????????";else->"?? ??????????"}
    val probing by model.probing.collectAsStateWithLifecycle()
    val landscape=LocalConfiguration.current.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
    @Composable fun connectionButton() {
        Button(onClick={if(active)model.stop()else if(server==null)add()else connect()},enabled=phase!=VpnPhase.DISCONNECTING,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(18.dp)) {
            if(phase in listOf(VpnPhase.CONNECTING,VpnPhase.RECONNECTING))CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=Background)else Icon(Icons.Outlined.PowerSettingsNew,null)
            Text(if(phase==VpnPhase.DISCONNECTING)"???????????"else if(active)"???????????"else if(server==null)"???????? ????????"else"????????????",Modifier.padding(start=8.dp),maxLines=2)
        }
        if(phase==VpnPhase.ERROR)TextButton(onClick=model::stop){Text("?????????? VPN")}
    }
    if(landscape) Row(Modifier.fillMaxSize().padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Text(status,fontSize=22.sp,color=if(phase==VpnPhase.CONNECTED)Green else Color.White);Globe(server,phase,state.settings,Modifier.weight(1f).fillMaxWidth());Text(display?.title?:"???? ??? ?? ?????",maxLines=2,overflow=TextOverflow.Ellipsis)}
        Column(Modifier.weight(1f)){LocationList(state,vpn,probing,model::select,model::favorite,{model.measure()},model::refresh,Modifier.weight(1f),compact=true);connectionButton()}
    } else Column(Modifier.fillMaxSize().padding(horizontal=16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(status,fontSize=24.sp,fontWeight=FontWeight.Light,color=if(phase==VpnPhase.CONNECTED)Green else Color.White,maxLines=1)
        Box(Modifier.fillMaxWidth().height(if(LocalConfiguration.current.screenHeightDp<720)140.dp else 200.dp),contentAlignment=Alignment.Center){Globe(server,phase,state.settings,Modifier.fillMaxSize())}
        Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(bottom=8.dp)) {CountryFlag(display?.country,display?.isAuto?:false);Text(display?.title?:"???? ??? ?? ?????",Modifier.padding(start=10.dp),fontSize=17.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
        vpn.error?.let{Text(it,color=Warning,fontSize=11.sp,maxLines=2,overflow=TextOverflow.Ellipsis)}
        LocationList(state,vpn,probing,model::select,model::favorite,{model.measure()},model::refresh,Modifier.weight(1f),compact=true)
        connectionButton()
        if(phase==VpnPhase.CONNECTED)Text("? ${bytes(vpn.uploaded)}    ? ${bytes(vpn.downloaded)}",color=Muted,fontSize=11.sp,modifier=Modifier.padding(vertical=4.dp))else Spacer(Modifier.height(8.dp))
    }
}

@Composable private fun Globe(server:Server?,phase:VpnPhase,settings:Settings,modifier:Modifier) {
    val context=LocalContext.current;val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val view=remember { GlobeView(context) }
    DisposableEffect(lifecycle,view){
        val observer=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_RESUME->view.onResume();Lifecycle.Event.ON_PAUSE->view.onPause();else->Unit}}
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer);view.onPause() }
    }
    AndroidView(factory={view},modifier=modifier,update={it.update(server,phase,settings.graphics,settings.reduceMotion)})
}

@Composable private fun Subscriptions(state:AppState,add:()->Unit,file:()->Unit,qr:()->Unit,image:()->Unit,remove:(String)->Unit,refresh:()->Unit){
    val context=LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal=22.dp)){
        Title("Подписки","Ваш доступ к Signet")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick=add){Icon(Icons.Outlined.Add,null);Text("Добавить")};OutlinedButton(onClick=qr){Icon(Icons.Outlined.QrCodeScanner,"QR-код")};OutlinedButton(onClick=file){Icon(Icons.Outlined.FileOpen,"Из файла")}}
        TextButton(onClick=image){Text("QR из изображения")}
        TextButton(onClick=refresh,enabled=!state.busy){Text(if(state.busy)"Обновление…" else "Обновить все подписки")}
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)){
            if(state.subscriptions.isEmpty())item{Empty("Добро пожаловать","Добавьте ссылку из личного кабинета Signet или импортируйте конфигурацию. Данные хранятся на устройстве в зашифрованном виде.")}
            items(state.subscriptions,key={it.id}){s->Card(colors=CardDefaults.cardColors(containerColor=Surface)){
                Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){Text(s.title,Modifier.weight(1f),fontWeight=FontWeight.SemiBold);IconButton(onClick={remove(s.id)}){Icon(Icons.Outlined.DeleteOutline,"Удалить подписку",tint=Muted)}}
                    Text("${s.servers.size} серверов · ${if(s.url!=null)"Автообновление" else "Локальный профиль"}",color=Muted,fontSize=12.sp)
                    val expire=s.info["expire"]?.takeIf{it>0};val total=s.info["total"]?:0;val used=(s.info["upload"]?:0)+(s.info["download"]?:0)
                    if(expire!=null){val remaining=expire*1000-System.currentTimeMillis();val days=remaining/86_400_000;Text(if(remaining<0)"Подписка истекла" else "До ${DateFormat.getDateInstance().format(Date(expire*1000))}",color=if(days<3)Warning else Muted)}
                    if(total>0){LinearProgressIndicator(progress={(used.toFloat()/total).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth());Text("${bytes(used)} из ${bytes(total)}",color=if(used>=total)Warning else Muted,fontSize=12.sp)}
                    if(state.settings.cabinet.startsWith("https://"))TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(state.settings.cabinet)))}){Text("Продлить в кабинете")}
                }
            }}
        }
    }
}

@Composable private fun Preferences(state:AppState,model:MainViewModel){
    val s=state.settings;val context=LocalContext.current;val scope=rememberCoroutineScope();val report by model.diagnostic.collectAsStateWithLifecycle()
    var dns by remember(s.dns){mutableStateOf(s.dns)};var cabinet by remember(s.cabinet){mutableStateOf(s.cabinet)}
    var showApps by rememberSaveable{mutableStateOf(false)}
    var showProbeSettings by rememberSaveable{mutableStateOf(false)}
    val probing by model.probing.collectAsStateWithLifecycle()
    var routeText by remember{mutableStateOf("")};var routeDialog by remember{mutableStateOf(false)}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){uri->if(uri!=null)scope.launch(Dispatchers.IO){runCatching{context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(report)}}}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Title("Настройки","Соединение под вашим контролем")
        Toggle("Подключаться при открытии",s.autoConnect){model.save(s.copy(autoConnect=it))}
        Toggle("Переподключаться при смене сети",s.reconnect){model.save(s.copy(reconnect=it))}
        TextButton(onClick={context.startActivity(Intent(AndroidSettings.ACTION_VPN_SETTINGS))}){Text("Системные Always-on и блокировка без VPN")}
        Section("DNS И МАРШРУТИЗАЦИЯ")
        OutlinedTextField(dns,{dns=it},label={Text("Резервный DNS · IP")},supportingText={Text("DNS из полного Xray-профиля сохраняется")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        TextButton(onClick={if(dns.matches(Regex("[0-9a-fA-F:.]+")))model.save(s.copy(dns=dns))else model.repository.notify("Укажите IPv4 или IPv6 адрес")}){Text("Сохранить DNS")}
        Text("Приложения через VPN",fontSize=15.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("all" to "Все","exclude" to "Кроме","include" to "Только").forEach{(mode,label)->FilterChip(s.appMode==mode,{model.save(s.copy(appMode=mode))},label={Text(label)})}}
        TextButton(onClick={showApps=true}){Text("Выбрать приложения (${s.packages.size})")}
        Text("Изменения маршрутизации применяются при следующем подключении.",color=Muted,fontSize=12.sp)
        TextButton(onClick={routeText=state.server?.config?:"";routeDialog=true},enabled=state.server!=null){Text("Редактор маршрутов и DNS · Xray JSON")}
        TextButton(onClick={scope.launch{state.server?.id?.let{model.repository.restore(it)};model.repository.notify("Исходный профиль восстановлен")}},enabled=state.server!=null){Text("Восстановить исходный профиль")}
        Section("ПЛАНЕТА")
        listOf("High" to "Качество · 60 кадров/с","Balanced" to "Баланс · 30 кадров/с","Battery Saver" to "Экономия · 10 кадров/с").forEach{(mode,label)->Row(Modifier.fillMaxWidth().clickable{model.save(s.copy(graphics=mode))},verticalAlignment=Alignment.CenterVertically){RadioButton(s.graphics==mode,{model.save(s.copy(graphics=mode))});Text(label,fontSize=14.sp)}}
        Toggle("Уменьшить движение",s.reduceMotion){model.save(s.copy(reduceMotion=it))}
        Text("В фоне графика останавливается. Логи трафика не сохраняются, кеш ответов не создаётся. Размер каждой подписки ограничен 4 МБ.",color=Muted,fontSize=12.sp)
        Section("ПОДПИСКА")
        Toggle("Передавать HWID установки",s.hwid){model.save(s.copy(hwid=it))}
        Text("Включайте, если подписка требует контроль устройств. Передаётся случайный ID этой установки, версия Android и модель устройства.",color=Muted,fontSize=12.sp)
        OutlinedTextField(cabinet,{cabinet=it},label={Text("HTTPS адрес личного кабинета")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        TextButton(onClick={if(cabinet.isEmpty()||cabinet.startsWith("https://"))model.save(s.copy(cabinet=cabinet))}){Text("Сохранить адрес")}
        Section("ДИАГНОСТИКА")
        Text("Signet ${BuildConfig.VERSION_NAME} · libXray 26.11.9",color=Muted,fontSize=12.sp)
        Button(onClick=model::diagnostics){Text("Проверить соединение")}
        if(report.isNotBlank()){Text(report,color=Muted,fontSize=12.sp);TextButton(onClick={export.launch("signet-diagnostics.txt")}){Text("Экспорт без ключей и адресов серверов")}}
        Spacer(Modifier.height(24.dp))
    }
    if(showApps)ApplicationsScreen(model){showApps=false}
    if(showProbeSettings)ProbePreferences(model){showProbeSettings=false}
    if(routeDialog)AlertDialog(onDismissRequest={routeDialog=false},title={Text("Конфигурация Xray")},text={Column{Text("Конфигурация содержит секретные ключи. Не публикуйте её. Изменения заменяются при обновлении подписки.",color=Warning,fontSize=12.sp);OutlinedTextField(routeText,{if(it.length<=512*1024)routeText=it},modifier=Modifier.fillMaxWidth().heightIn(max=300.dp),maxLines=12)}},confirmButton={TextButton(onClick={scope.launch(Dispatchers.IO){if(VpnState.status.value.phase!=VpnPhase.DISCONNECTED)model.repository.notify("Для проверки остановите VPN")else runCatching{NativeCore.call("testXray",org.json.JSONObject().put("xrayJson",routeText));state.server?.id?.let{model.repository.edit(it,routeText)};model.repository.notify("Проверено ядром и сохранено");withContext(Dispatchers.Main){routeDialog=false}}.onFailure{model.repository.notify("Конфигурация не прошла проверку")}}}){Text("Проверить и сохранить")}},dismissButton={TextButton(onClick={routeDialog=false}){Text("Закрыть")}})
}

@Composable private fun Title(title:String,subtitle:String){Column(Modifier.padding(top=8.dp,bottom=20.dp)){Text(title,fontSize=28.sp,fontWeight=FontWeight.Light);Text(subtitle,color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=6.dp))}}
@Composable private fun Section(text:String){Text(text,color=Muted,fontSize=10.sp,letterSpacing=2.sp,modifier=Modifier.padding(top=22.dp,bottom=8.dp))}
@Composable private fun Toggle(text:String,value:Boolean,change:(Boolean)->Unit){Row(Modifier.fillMaxWidth().heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically){Text(text,Modifier.weight(1f),fontSize=14.sp);Switch(value,change)}}
@Composable private fun Empty(title:String,subtitle:String){Column(Modifier.fillMaxWidth().padding(vertical=32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Outlined.Public,null,tint=Primary,modifier=Modifier.size(36.dp));Text(title,fontSize=20.sp);Text(subtitle,color=Muted,fontSize=14.sp)}}
@Composable private fun Latency(s:Server){Text(if(s.available==false)"Недоступен" else s.latencyMs?.let{"$it ms · HTTP"}?:"—",color=when{ s.available==false->Error;s.latencyMs==null->Muted;s.latencyMs!!<=80->Green;s.latencyMs!!<=160->Warning;else->Error},fontSize=11.sp)}
private fun bytes(n:Long):String=when{n>=(1L shl 30)->"%.1f ГБ".format(n.toDouble()/(1L shl 30));n>=(1L shl 20)->"%.1f МБ".format(n.toDouble()/(1L shl 20));n>=1024->"%.1f КБ".format(n/1024.0);else->"$n Б"}
