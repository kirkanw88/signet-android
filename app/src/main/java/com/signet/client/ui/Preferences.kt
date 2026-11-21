package com.signet.client.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.signet.client.BuildConfig
import com.signet.client.MainViewModel
import com.signet.client.data.AppState
import com.signet.client.data.ConnectionLog
import com.signet.client.vpn.*
import com.signet.core.SubscriptionMetadata
import kotlinx.coroutines.*

private val Ink:Color @Composable get()=AppColors.text
private val SecondaryInk:Color @Composable get()=AppColors.muted
private val Accent:Color @Composable get()=AppColors.accent

@Composable fun Preferences(state:AppState,model:MainViewModel,subscriptions:()->Unit,servers:()->Unit,openDiagnostics:Boolean,diagnosticsHandled:()->Unit,close:()->Unit) {
    var section by rememberSaveable { mutableStateOf("main") }
    var apps by rememberSaveable{mutableStateOf(false)}
    var probes by rememberSaveable{mutableStateOf(false)}

    var account by rememberSaveable{mutableStateOf(false)}
    var editor by remember{mutableStateOf(false)}
    var routeText by remember{mutableStateOf("")}
    val s=state.settings;val context=LocalContext.current;val scope=rememberCoroutineScope()
    var dns by remember(s.dns){mutableStateOf(s.dns)}
    var health by remember(s.connectionCheckUrl){mutableStateOf(s.connectionCheckUrl)}
    val update by model.updates.state.collectAsStateWithLifecycle()
    val report by model.diagnostic.collectAsStateWithLifecycle()
    val logLines by ConnectionLog.entries.collectAsStateWithLifecycle()
    val scroll=rememberScrollState()
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){uri->if(uri!=null)scope.launch(Dispatchers.IO){runCatching{context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(report)}}}}
    val exportLog=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){uri->if(uri!=null)scope.launch(Dispatchers.IO){runCatching{context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(ConnectionLog.export())}}}}
    LaunchedEffect(openDiagnostics){if(openDiagnostics){section="diagnostics";model.diagnostics();diagnosticsHandled()}}
    fun parentSection():String=if(!BuildConfig.SIGNET_ACCOUNT)when(section){"logs"->"diagnostics";"updates"->"about";else->"main"}else"main"
    BackHandler { if(section!="main")section=parentSection() else close() }
    LaunchedEffect(section){scroll.scrollTo(0)}
    val title=when(section){"connection"->"Подключение";"routing"->"Сеть и маршрутизация";"appearance"->"Внешний вид";"subscription-options"->"Ping";"about"->"О приложении";"updates"->"Обновления";"diagnostics"->"Диагностика";"logs"->"Журнал подключения";"account"->"Аккаунт";else->"Настройки"}
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal=if(BuildConfig.SIGNET_ACCOUNT)20.dp else 14.dp),verticalArrangement=Arrangement.spacedBy(if(BuildConfig.SIGNET_ACCOUNT)16.dp else 10.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={if(section=="main")close()else{section=parentSection()}}){Icon(Icons.Outlined.ArrowBack,"Назад",tint=Ink)}
            Text(title,Modifier.weight(1f),fontSize=24.sp,fontWeight=FontWeight.SemiBold,color=Ink)
        }
        when(section){
            "main"->{
                if(!BuildConfig.SIGNET_ACCOUNT){
                    // Category-level navigation adapted for Universal. No placeholder/inert actions.
                    SettingsCard {
                        MenuItem(Icons.Outlined.Palette,"Внешний вид","Тема и анимация глобуса",{section="appearance"})
                        MenuItem(Icons.Outlined.VpnKey,"Подключение","Автоподключение, восстановление и защита",{section="connection"})
                        MenuItem(Icons.Outlined.Route,"Сеть и маршрутизация","DNS, приложения и Xray JSON",{section="routing"})
                        MenuItem(Icons.Outlined.Speed,"Ping","Настройка проверки задержки серверов",{section="subscription-options"})
                        MenuItem(Icons.Outlined.Troubleshoot,"Диагностика","Проверка соединения, журнал и отчёт",{section="diagnostics";model.diagnostics()})
                        MenuItem(Icons.Outlined.Info,"О приложении","Версия, обновления и исходный код",{section="about"})
                    }
                } else {
                    Text("Ваш доступ и приложение",fontSize=16.sp,color=SecondaryInk,modifier=Modifier.padding(vertical=4.dp))
                    SettingsCard {
                        MenuItem(Icons.Outlined.CreditCard,"Подписки","Добавить, отсканировать QR или обновить",subscriptions)
                        MenuItem(Icons.Outlined.Public,"Серверы","Выбрать локацию и измерить задержку",servers)
                        MenuItem(Icons.Outlined.PersonOutline,"Аккаунт","Войти, купить или продлить доступ",{section="account"})
                    }
                    SettingsCard {
                        MenuItem(Icons.Outlined.VpnKey,"Соединение","Автоподключение и проверка связи",{section="connection"})
                        MenuItem(Icons.Outlined.Route,"Маршрутизация","DNS, приложения и шаблон Xray",{section="routing"})
                        MenuItem(Icons.Outlined.Palette,"Оформление","Тема, качество планеты и анимация",{section="appearance"})
                        MenuItem(Icons.Outlined.SystemUpdate,"Обновления",update.available?.let{"Доступна версия ${it.version}"}?:"Новая версия приложения",{section="updates"})
                        MenuItem(Icons.Outlined.Troubleshoot,"Диагностика","Причина ошибки и отчёт без ключей",{section="diagnostics";model.diagnostics()})
                        MenuItem(Icons.Outlined.Article,"Журнал подключения","Логирование, просмотр и экспорт",{section="logs"})
                    }
                    Text("Signet VPN ${BuildConfig.VERSION_NAME}",fontSize=14.sp,color=SecondaryInk,modifier=Modifier.padding(8.dp))
                }
            }
            "subscription-options"->{
                SettingsCard {
                    MenuItem(Icons.Outlined.Speed,"Настройки Ping",
                        "Via Proxy, тайм-аут и параллельные проверки",{probes=true})
                }
                SettingsCard {
                    SettingsToggle("Ping при запуске","Измерять задержку серверов при открытии",
                        s.probes.pingOnLaunch){model.save(s.copy(probes=s.probes.copy(pingOnLaunch=it)))}
                    SettingsToggle("Периодическая проверка","Проверять выбранный сервер с интервалом",
                        s.probes.automatic){model.save(s.copy(probes=s.probes.copy(automatic=it)))}
                }
                Text("Обновление подписок выполняется автоматически. Интервал может задаваться провайдером.",
                    fontSize=12.sp,lineHeight=17.sp,color=SecondaryInk,modifier=Modifier.padding(horizontal=8.dp))
            }
            "about"->{
                SettingsCard {
                    SettingsHeading("Signet Universal")
                    Text("Версия ${BuildConfig.VERSION_NAME}",color=Ink,fontSize=16.sp,
                        modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp))
                    Text("Android · Xray-core",color=SecondaryInk,fontSize=12.sp,
                        modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp))
                    MenuItem(Icons.Outlined.SystemUpdate,"Проверить обновления",
                        update.available?.let{"Доступна версия ${it.version}"}?:"Проверить доступную версию",
                        {section="updates"})
                    MenuItem(Icons.Outlined.Code,"Исходный код",
                        "Репозиторий проекта",{
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/kirkanw88/signet-android")))
                        })
                }
            }
            "connection"->{
                SettingsCard {
                    SettingsToggle("Подключаться при открытии","Использовать выбранный сервер",s.autoConnect){model.save(s.copy(autoConnect=it))}
                    SettingsToggle("Восстанавливать соединение","При смене Wi-Fi или мобильной сети",s.reconnect){model.save(s.copy(reconnect=it))}
                    MenuItem(Icons.Outlined.Security,"Системные настройки VPN","Постоянный VPN и блокировка без VPN",{context.startActivity(Intent(AndroidSettings.ACTION_VPN_SETTINGS))})
                    if(BuildConfig.SIGNET_ACCOUNT)MenuItem(Icons.Outlined.Speed,"Настройки Ping","Режим, адрес и интервалы проверок",{probes=true})
                }
                SettingsCard {
                    SettingsHeading("Проверка подключения")
                    Text("HTTPS проверяется через VPN. Адрес должен разрешаться правилами профиля. Эта проверка не зависит от режима измерения задержки.",fontSize=15.sp,lineHeight=22.sp,color=SecondaryInk,modifier=Modifier.padding(horizontal=12.dp))
                    OutlinedTextField(health,{health=it},label={Text("HTTPS адрес")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(12.dp))
                    GlassButton(onClick={if(SubscriptionMetadata.safeLink(health)!=null)model.save(s.copy(connectionCheckUrl=health))else model.repository.notify("Укажите корректный HTTPS адрес")},modifier=Modifier.fillMaxWidth().padding(12.dp)){Text("Сохранить адрес проверки")}
                }
            }
            "routing"->{
                SettingsCard {
                    SettingsHeading("DNS")
                    Text("DNS из полного профиля сохраняется. Этот адрес используется, когда в профиле DNS не задан.",fontSize=15.sp,lineHeight=22.sp,color=SecondaryInk,modifier=Modifier.padding(horizontal=12.dp))
                    OutlinedTextField(dns,{dns=it},label={Text("Резервный DNS · IP")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(12.dp))
                    GlassButton(onClick={if(com.signet.core.TemplateCompatibility.numericAddress(dns))model.save(s.copy(dns=dns))else model.repository.notify("Укажите IPv4 или IPv6 адрес")},modifier=Modifier.fillMaxWidth().padding(12.dp)){Text("Сохранить DNS")}
                }
                SettingsCard {
                    SettingsHeading("Приложения через VPN")
                    Row(Modifier.padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("all" to "Все","exclude" to "Кроме","include" to "Только").forEach{(mode,label)->FilterChip(s.appMode==mode,{model.save(s.copy(appMode=mode))},label={Text(label)})}}
                    MenuItem(Icons.Outlined.Apps,"Выбрать приложения","Выбрано: ${s.packages.size}",{apps=true})
                }
                SettingsCard {

                    MenuItem(Icons.Outlined.Code,"Редактор маршрутов и DNS","Полная конфигурация Xray",{routeText=prettyJson(state.server?.config.orEmpty());editor=true},state.server!=null)
                    MenuItem(Icons.Outlined.Restore,"Восстановить профиль","Отменить локальные изменения",{scope.launch{state.server?.id?.let{model.repository.restore(it)};model.repository.notify("Исходный профиль восстановлен")}},state.server!=null)
                }
            }
            "appearance"->{
                if(!BuildConfig.SIGNET_ACCOUNT)SettingsCard {
                    SettingsHeading("Тема приложения")
                    listOf("light" to "Светлая","dark" to "Тёмная","system" to "Как на устройстве").forEach{(mode,label)->
                        Row(Modifier.fillMaxWidth().clickable{model.save(s.copy(theme=mode))}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                            RadioButton(s.theme==mode,{model.save(s.copy(theme=mode))})
                            Text(label,Modifier.padding(start=8.dp),fontSize=17.sp,color=Ink)
                        }
                    }
                }
                SettingsCard {
                    listOf(Triple("High","Высокое качество","До 60 кадров/с"),Triple("Balanced","Баланс","До 30 кадров/с"),Triple("Battery Saver","Экономия энергии","До 10 кадров/с")).forEach{(mode,label,detail)->
                        Row(Modifier.fillMaxWidth().clickable{model.save(s.copy(graphics=mode))}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(s.graphics==mode,{model.save(s.copy(graphics=mode))});Column(Modifier.padding(start=8.dp)){Text(label,fontSize=17.sp,color=Ink);Text(detail,fontSize=14.sp,color=SecondaryInk)}}
                    }
                    SettingsToggle("Уменьшить движение","Спокойная анимация планеты",s.reduceMotion){model.save(s.copy(reduceMotion=it))}
                    if(!BuildConfig.SIGNET_ACCOUNT)SettingsToggle("Показывать отправку и получение","Скорость и объём трафика на главной странице",s.showTrafficRates){model.save(s.copy(showTrafficRates=it))}
                }
            }
            "updates"->{
                SettingsCard {
                    if(BuildConfig.SIGNET_ACCOUNT)SettingsToggle("Проверять автоматически","Сообщать о новой версии в приложении",s.automaticUpdates){model.save(s.copy(automaticUpdates=it))}
                    GlassButton(onClick={model.checkUpdates()},enabled=!update.checking,modifier=Modifier.fillMaxWidth().padding(12.dp)){if(update.checking)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp);Text("Проверить обновление",Modifier.padding(start=8.dp))}
                    if(update.message.isNotBlank())Text(update.message,fontSize=15.sp,lineHeight=22.sp,color=SecondaryInk,modifier=Modifier.padding(12.dp))
                }
            }
            "logs"->{
                SettingsCard {SettingsToggle("Записывать события","Запуск VPN, проверка HTTPS и коды ошибок",s.logging){model.save(s.copy(logging=it))}}
                Text("Журнал хранится только в памяти: не более 200 строк. Ссылки подписки, UUID, HWID и ключи не записываются. При отключении логирования журнал очищается.",fontSize=15.sp,lineHeight=22.sp,color=SecondaryInk)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    GlassButton(onClick={(context.getSystemService(ClipboardManager::class.java)).setPrimaryClip(ClipData.newPlainText("Signet connection log",ConnectionLog.export()));model.repository.notify("Журнал скопирован")},enabled=logLines.isNotEmpty()){Icon(Icons.Outlined.ContentCopy,null);Text("Копировать",Modifier.padding(start=8.dp))}
                    IconButton(onClick={exportLog.launch("signet-connection-log.txt")},enabled=logLines.isNotEmpty()){Icon(Icons.Outlined.SaveAlt,"Экспорт журнала")}
                    IconButton(onClick=ConnectionLog::clear,enabled=logLines.isNotEmpty()){Icon(Icons.Outlined.DeleteOutline,"Очистить журнал")}
                }
                SettingsCard {SelectionContainer {Text(if(logLines.isEmpty())"Событий пока нет. Включите запись и выполните подключение."else logLines.joinToString("\n"),fontFamily=FontFamily.Monospace,fontSize=13.sp,lineHeight=20.sp,color=Ink,modifier=Modifier.fillMaxWidth().padding(14.dp))}}
            }
            "diagnostics"->{
                if(!BuildConfig.SIGNET_ACCOUNT)SettingsCard {
                    MenuItem(Icons.Outlined.Article,"Журнал Xray и подключения",
                        "Запись, просмотр и экспорт событий",{section="logs"})
                }
                Text("Отчёт показывает результат HTTPS и причину сбоя. Ссылка подписки и ключи в него не входят.",fontSize=15.sp,lineHeight=22.sp,color=SecondaryInk)
                GlassButton(onClick=model::diagnostics,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Troubleshoot,null);Text("Проверить соединение",Modifier.padding(start=8.dp))}
                if(report.isNotBlank()){
                    SettingsCard {SelectionContainer {Text(report,fontSize=16.sp,lineHeight=25.sp,color=Ink,modifier=Modifier.fillMaxWidth().padding(16.dp))}}
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        GlassButton(onClick={(context.getSystemService(ClipboardManager::class.java)).setPrimaryClip(ClipData.newPlainText("Signet diagnostics",report));model.repository.notify("Отчёт скопирован")}){Icon(Icons.Outlined.ContentCopy,null);Text("Копировать",Modifier.padding(start=8.dp))}
                        IconButton(onClick={export.launch("signet-diagnostics.txt")}){Icon(Icons.Outlined.SaveAlt,"Сохранить отчёт")}
                    }
                }
            }
            "account"->if(BuildConfig.SIGNET_ACCOUNT){
                SettingsCard {
                    MenuItem(Icons.Outlined.PersonOutline,"Аккаунт Signet","Вход, покупка и продление",{account=true})
                    MenuItem(Icons.Outlined.SupportAgent,"Поддержка","Написать в Telegram",{model.openAccount("support")})
                }

            }
        }
        Spacer(Modifier.height(20.dp))
    }
    if(apps)ApplicationsScreen(model){apps=false}
    if(probes)ProbePreferences(model){probes=false}

    if(BuildConfig.SIGNET_ACCOUNT&&account)AccountScreen(model){account=false}
    if(editor)JsonEditor(routeText,{routeText=it},{scope.launch(Dispatchers.IO){
        if(VpnState.status.value.phase!=VpnPhase.DISCONNECTED)model.repository.notify("Для проверки остановите VPN")
        else runCatching{NativeCore.call("testXray",org.json.JSONObject().put("xrayJson",routeText));state.server?.id?.let{model.repository.edit(it,routeText)};model.repository.notify("Проверено ядром и сохранено");withContext(Dispatchers.Main){editor=false}}
            .onFailure{model.repository.notify("Конфигурация не прошла проверку")}
    }},{editor=false})
}

@Composable private fun SettingsCard(content:@Composable ColumnScope.()->Unit){
    Surface(shape=RoundedCornerShape(if(BuildConfig.SIGNET_ACCOUNT)22.dp else 14.dp),color=AppColors.surface,border=BorderStroke(1.dp,AppColors.border),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(if(BuildConfig.SIGNET_ACCOUNT)6.dp else 3.dp),content=content)
    }
}
@Composable private fun SettingsHeading(text:String){Text(text,fontSize=18.sp,fontWeight=FontWeight.SemiBold,color=Ink,modifier=Modifier.padding(12.dp))}
@Composable private fun MenuItem(icon:ImageVector,title:String,subtitle:String,onClick:()->Unit,enabled:Boolean=true){
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(enabled=enabled,onClick=onClick).padding(horizontal=if(BuildConfig.SIGNET_ACCOUNT)12.dp else 8.dp,vertical=if(BuildConfig.SIGNET_ACCOUNT)14.dp else 7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(if(BuildConfig.SIGNET_ACCOUNT)14.dp else 9.dp)){
        Box(Modifier.size(if(BuildConfig.SIGNET_ACCOUNT)42.dp else 34.dp).background(AppColors.tint,RoundedCornerShape(if(BuildConfig.SIGNET_ACCOUNT)14.dp else 10.dp)),contentAlignment=Alignment.Center){Icon(icon,null,tint=Accent,modifier=Modifier.size(if(BuildConfig.SIGNET_ACCOUNT)23.dp else 19.dp))}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(if(BuildConfig.SIGNET_ACCOUNT)4.dp else 2.dp)){Text(title,fontSize=if(BuildConfig.SIGNET_ACCOUNT)17.sp else 15.sp,lineHeight=if(BuildConfig.SIGNET_ACCOUNT)23.sp else 20.sp,fontWeight=FontWeight.Medium,color=Ink);Text(subtitle,fontSize=if(BuildConfig.SIGNET_ACCOUNT)14.sp else 12.sp,lineHeight=if(BuildConfig.SIGNET_ACCOUNT)20.sp else 17.sp,color=SecondaryInk)}
        if(enabled)Icon(Icons.Outlined.ChevronRight,null,tint=SecondaryInk,modifier=Modifier.size(20.dp))
    }
}
@Composable private fun SettingsToggle(title:String,subtitle:String,value:Boolean,onChange:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable{onChange(!value)}.padding(if(BuildConfig.SIGNET_ACCOUNT)12.dp else 8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(if(BuildConfig.SIGNET_ACCOUNT)12.dp else 8.dp)){
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(if(BuildConfig.SIGNET_ACCOUNT)4.dp else 2.dp)){Text(title,fontSize=if(BuildConfig.SIGNET_ACCOUNT)17.sp else 15.sp,lineHeight=if(BuildConfig.SIGNET_ACCOUNT)23.sp else 20.sp,fontWeight=FontWeight.Medium,color=Ink);Text(subtitle,fontSize=if(BuildConfig.SIGNET_ACCOUNT)14.sp else 12.sp,lineHeight=if(BuildConfig.SIGNET_ACCOUNT)20.sp else 17.sp,color=SecondaryInk)}
        Switch(value,onChange)
    }
}
