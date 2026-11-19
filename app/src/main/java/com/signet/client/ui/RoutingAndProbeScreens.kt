package com.signet.client.ui

import android.content.pm.ApplicationInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.signet.client.MainViewModel
import com.signet.client.data.readLimited
import com.signet.core.*
import kotlinx.coroutines.*

@Composable fun FullScreenPanel(title:String,close:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=Color.Transparent,contentColor=AppColors.text) {
        SpaceBackdrop(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(horizontal=16.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick=close){Text("Закрыть")}}
                content()
            }
        }
        }
    }
}

@Composable fun ProbePreferences(model:MainViewModel,close:()->Unit) {
    val observed by model.state.collectAsStateWithLifecycle()
    val current=observed.settings
    var value by remember{mutableStateOf(current.probes)}
    var timeout by remember{mutableStateOf(value.timeoutMs.toString())}
    var attempts by remember{mutableStateOf(value.attempts.toString())}
    var interval by remember{mutableStateOf(value.intervalMs.toString())}
    var parallel by remember{mutableStateOf(value.parallelism.toString())}
    var minutes by remember{mutableStateOf(value.autoMinutes.toString())}
    var error by remember{mutableStateOf<String?>(null)}
    FullScreenPanel("Настройки Ping",close) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            listOf(ProbeMode.TCP to "TCP Connect · доступность порта",ProbeMode.HTTPS to "HTTPS · TLS доступность узла",ProbeMode.XRAY to "Xray · полный профиль и маршрутизация",ProbeMode.OUTBOUND to "Интернет через выбранный outbound").forEach { (mode,label)->
                Row(Modifier.fillMaxWidth().clickable{value=value.copy(mode=mode)},verticalAlignment=Alignment.CenterVertically){RadioButton(value.mode==mode,{value=value.copy(mode=mode)});Text(label)}
            }
            Text("TCP и HTTPS не подтверждают работу VPN. AUTO проверяется как полный серверный профиль, без выбора отдельного узла.",style=MaterialTheme.typography.bodySmall)
            Text("Адрес HTTPS-проверки должен быть разрешён правилами профиля. Он также используется для подтверждения соединения VPN.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(value.url,{value=value.copy(url=it)},label={Text("HTTPS URL проверки Xray")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            ProbeNumber("Таймаут, мс · 500–30000",timeout){timeout=it}
            ProbeNumber("Попыток · 1–3",attempts){attempts=it}
            ProbeNumber("Интервал между попытками, мс · 200–60000",interval){interval=it}
            ProbeNumber("Максимум параллельных проверок · 1–5",parallel){parallel=it}
            Row(verticalAlignment=Alignment.CenterVertically){Text("Автопроверка выбранного сервера",Modifier.weight(1f));Switch(value.automatic,{value=value.copy(automatic=it)})}
            ProbeNumber("Автопроверка каждые, мин · 15–360",minutes){minutes=it}
            Text("Автопроверка выполняется только пока приложение открыто. Проверки полного профиля идут последовательно в изолированном ядре.",style=MaterialTheme.typography.bodySmall)
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
        GlassButton(onClick={runCatching {value=value.copy(timeoutMs=timeout.toInt(),attempts=attempts.toInt(),intervalMs=interval.toInt(),parallelism=parallel.toInt(),autoMinutes=minutes.toInt()).validated();model.save(model.state.value.settings.copy(probes=value));close()}.onFailure{error="Проверьте HTTPS URL и допустимые значения"}},modifier=Modifier.fillMaxWidth().padding(vertical=8.dp)){Text("Сохранить")}
    }
}
@Composable private fun ProbeNumber(label:String,value:String,change:(String)->Unit){OutlinedTextField(value,{if(it.length<=6&&it.all(Char::isDigit))change(it)},label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())}

private data class VisibleApp(val info:ApplicationInfo,val name:String,val system:Boolean)
@Composable fun ApplicationsScreen(model:MainViewModel,close:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var apps by remember{mutableStateOf(emptyList<VisibleApp>())};var loaded by remember{mutableStateOf(false)}
    var query by remember{mutableStateOf("")};var systems by remember{mutableStateOf(false)}
    var selected by remember{mutableStateOf(model.state.value.settings.packages)}
    var mode by remember{mutableStateOf(model.state.value.settings.appMode)}
    var preview by remember{mutableStateOf<ImportedApps?>(null)};var error by remember{mutableStateOf<String?>(null)}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)scope.launch(Dispatchers.IO){runCatching{context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(AppRouting(mode,selected).json())}}.onFailure{model.repository.notify("Не удалось сохранить настройки")}}}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch(Dispatchers.IO){runCatching{val raw=context.contentResolver.openInputStream(uri)!!.use{String(it.readLimited(256*1024),Charsets.UTF_8)};AppRoutingTransfer.parse(raw)}.onSuccess{withContext(Dispatchers.Main){preview=it}}.onFailure{withContext(Dispatchers.Main){error="Не удалось прочитать схему маршрутизации"}}}}
    LaunchedEffect(Unit){apps=withContext(Dispatchers.IO){context.packageManager.getInstalledApplications(0).filter{it.packageName!=context.packageName}.map { VisibleApp(it,runCatching{it.loadLabel(context.packageManager).toString()}.getOrDefault(it.packageName),it.flags and ApplicationInfo.FLAG_SYSTEM!=0) }.sortedBy{it.name.lowercase()}};loaded=true}
    val filtered=apps.filter{(systems||!it.system||it.info.packageName in selected)&&(it.name.contains(query,true)||it.info.packageName.contains(query,true))}
    FullScreenPanel("Приложения через VPN",close) {
        Row(verticalAlignment=Alignment.CenterVertically){listOf("all" to "Все","include" to "Только","exclude" to "Кроме").forEach{(key,label)->FilterChip(mode==key,{mode=key},label={Text(label)},modifier=Modifier.padding(end=8.dp))}}
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Название или package name")},singleLine=true)
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(systems,{systems=it});Text("Системные приложения");Spacer(Modifier.weight(1f));Text("${selected.size} выбрано")}
        Row{TextButton(onClick={selected=selected+filtered.map{it.info.packageName}}){Text("Выбрать все")};TextButton(onClick={selected=emptySet()}){Text("Снять выбор")}}
        var importMenu by remember{mutableStateOf(false)};var exportMenu by remember{mutableStateOf(false)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Box(Modifier.weight(1f)){
                GlassButton(onClick={importMenu=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.FileDownload,null);Text("Импорт",Modifier.padding(start=8.dp))}
                DropdownMenu(importMenu,{importMenu=false}){
                    DropdownMenuItem(text={Text("Из буфера обмена")},onClick={importMenu=false;runCatching{AppRoutingTransfer.parse(ClipboardTransfers.read(context,256*1024))}.onSuccess{preview=it;error=null}.onFailure{error="Не удалось распознать список пакетов Android"}})
                    DropdownMenuItem(text={Text("Из файла")},onClick={importMenu=false;import.launch(arrayOf("application/json","text/*"))})
                }
            }
            Box(Modifier.weight(1f)){
                GlassButton(onClick={exportMenu=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.FileUpload,null);Text("Экспорт",Modifier.padding(start=8.dp))}
                DropdownMenu(exportMenu,{exportMenu=false}){
                    DropdownMenuItem(text={Text("В буфер: список для Happ / Incy")},onClick={exportMenu=false;ClipboardTransfers.copy(context,"Android apps",AppRoutingTransfer.plain(selected));model.repository.notify("Список приложений скопирован")})
                    DropdownMenuItem(text={Text("В буфер: настройки Signet")},onClick={exportMenu=false;ClipboardTransfers.copy(context,"Signet apps",AppRouting(mode,selected).json());model.repository.notify("Настройки скопированы")})
                    DropdownMenuItem(text={Text("В файл JSON")},onClick={exportMenu=false;export.launch("signet-routing-v1.json")})
                }
            }
        }
        Text("Доступны приложения текущего профиля, которые разрешает показывать Android. Для рабочего профиля используйте Signet внутри него.",style=MaterialTheme.typography.bodySmall)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        if(!loaded)LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f)) {
            items(filtered,key={it.info.packageName}) { app->
                Row(Modifier.fillMaxWidth().clickable{selected=if(app.info.packageName in selected)selected-app.info.packageName else selected+app.info.packageName}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    var icon by remember(app.info.packageName){mutableStateOf<android.graphics.Bitmap?>(null)}
                    LaunchedEffect(app.info.packageName){icon=withContext(Dispatchers.IO){runCatching{app.info.loadIcon(context.packageManager).toBitmap(64,64)}.getOrNull()}}
                    icon?.let{Image(it.asImageBitmap(),null,Modifier.size(32.dp))}?:Spacer(Modifier.size(32.dp))
                    Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(app.name,maxLines=2);Text(app.info.packageName,style=MaterialTheme.typography.bodySmall,maxLines=2)}
                    Checkbox(app.info.packageName in selected,{checked->selected=if(checked)selected+app.info.packageName else selected-app.info.packageName})
                }
            }
        }
        GlassButton(onClick={if(mode=="include"&&selected.isEmpty())error="Выберите хотя бы одно приложение"else{model.save(model.state.value.settings.copy(appMode=mode,packages=selected));close()}},modifier=Modifier.fillMaxWidth().padding(vertical=8.dp)){Text("Сохранить")}
    }
    preview?.let { routing->
        val missing=routing.packages-apps.map{it.info.packageName}.toSet()
        var importedMode by remember(routing){mutableStateOf(routing.mode)}
        AlertDialog(onDismissRequest={preview=null},title={Text("Импорт приложений")},text={Column(Modifier.verticalScroll(rememberScrollState())){
            Text("Приложений: ${routing.packages.size}\nНе найдены или скрыты Android: ${missing.size}")
            if(routing.mode==null)Text("В списке нет режима. Выберите, как использовать приложения:")
            listOf("include" to "Только эти через VPN","exclude" to "Эти без VPN","all" to "Все через VPN").forEach{(key,label)->
                Row(Modifier.fillMaxWidth().clickable{importedMode=key},verticalAlignment=Alignment.CenterVertically){RadioButton(importedMode==key,{importedMode=key});Text(label)}
            }
            if(routing.rejected.isNotEmpty())Text("Не будут импортированы (${routing.rejected.size}):\n"+routing.rejected.take(5).joinToString("\n"){it.take(100)},color=MaterialTheme.colorScheme.error)
            Text("Настройки применятся после сохранения и переподключения.")
        }},confirmButton={TextButton(onClick={mode=importedMode!!;selected=routing.packages;preview=null},enabled=importedMode!=null){Text("Применить")}},dismissButton={TextButton(onClick={preview=null}){Text("Отмена")}})
    }
}
