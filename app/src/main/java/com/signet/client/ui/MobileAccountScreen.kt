package com.signet.client.ui

import android.graphics.BitmapFactory
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import androidx.lifecycle.repeatOnLifecycle
import com.signet.client.MainViewModel
import com.signet.client.data.*
import kotlinx.coroutines.*
import org.json.*
import java.util.UUID

private fun JSONArray?.objects():List<JSONObject> = if(this==null)emptyList()else(0 until length()).map{getJSONObject(it)}
private val AccountMuted=Color(0xFFB2C1D7)

@Composable fun MobileAccountScreen(model:MainViewModel,initial:String="account",close:()->Unit){
    val state by model.account.state.collectAsStateWithLifecycle()
    val app by model.state.collectAsStateWithLifecycle()
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var page by remember(initial){mutableStateOf(initial)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val subscriptions=state.account?.optJSONArray("subscriptions").objects()
    val local=app.subscriptions.firstOrNull{sub->sub.servers.any{it.id==app.selected}}
    val current=subscriptions.firstOrNull{it.optString("subscription_url")==local?.url}?:subscriptions.firstOrNull{it.optBoolean("is_active")}?:subscriptions.firstOrNull()
    val subId=current?.optLong("id")?.takeIf{it>0}
    LaunchedEffect(state.loggedIn,page){if(state.loggedIn&&page!="support")lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(isActive){model.account.refresh();delay(30_000)}}}
    FullScreenPanel(when(page){"support"->"Поддержка";"devices"->"Устройства";"settings"->"Настройки подписки";"balance"->"Баланс";else->"Аккаунт Signet"},close){
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(vertical=10.dp))}
        if(!state.ready)Text("Загрузка…")
        else if(!state.loggedIn){
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)){
                Text("Войти в существующий аккаунт",style=MaterialTheme.typography.headlineSmall)
                Text("Если вы пользуетесь ботом — выберите Telegram. Если зарегистрированы в кабинете — свой Google или Яндекс. Привязка Telegram не обязательна.",color=AccountMuted)
                listOf("telegram" to "Войти через Telegram","google" to "Войти через Google","yandex" to "Войти через Яндекс").forEach{(provider,title)->
                    GlassButton(onClick={scope.launch{model.account.loginUrl(provider)?.let{url->openSignetPanel(context,url){model.repository.notify("Не найден браузер для безопасного входа")}}}},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text(title)}
                }
            }
        }else when(page){
            "support"->NativeSupport(model)
            "devices"->if(subId!=null)NativeDevices(model,subId)else Text("В аккаунте нет подписки")
            else->Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)){
                Text(state.account?.optString("name")?:"Signet",style=MaterialTheme.typography.titleLarge)
                if(state.account!=null)Text("Баланс: ${state.account!!.optLong("balance_rub")} ₽",style=MaterialTheme.typography.headlineSmall)
                if(page=="balance"){
                    GlassButton(onClick={openSignetPanel(context,"https://signet-cab.com/cabinet#balance")},enabled=state.account?.optBoolean("balance_topup_allowed")==true,modifier=Modifier.fillMaxWidth()){Text("Пополнить баланс")}
                }
                current?.let{sub->
                    Text(sub.optString("plan","Signet VPN"),fontWeight=FontWeight.SemiBold)
                    Text("${sub.optString("status")}\nДо ${sub.optString("expires_at")}\nУстройств: ${sub.optInt("device_limit")}",color=AccountMuted)
                    if(page=="settings"){
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                            Text("Автопродление с баланса",Modifier.weight(1f))
                            Switch(sub.optBoolean("auto_payment_enabled"),{enabled->scope.launch{if(model.account.request("/subscriptions/$subId/settings","PUT",JSONObject().put("autopay_enabled",enabled))!=null)model.account.refresh()}},enabled=!state.busy&&!sub.optBoolean("is_trial"))
                        }
                    }
                    GlassButton(onClick={model.import(sub.optString("subscription_url"))},enabled=!state.busy&&sub.optString("subscription_url").startsWith("https://"),modifier=Modifier.fillMaxWidth()){Text("Использовать подписку в VPN")}
                    GlassButton(onClick={openSignetPanel(context,"https://signet-cab.com/cabinet?open_subscription=$subId#subs")},modifier=Modifier.fillMaxWidth()){Text("Продлить или изменить тариф")}
                }
                GlassButton(onClick={page="devices"},enabled=subId!=null,modifier=Modifier.fillMaxWidth()){Text("Управление устройствами")}
                GlassButton(onClick={page="settings"},enabled=subId!=null,modifier=Modifier.fillMaxWidth()){Text("Настройки подписки")}
                GlassButton(onClick={page="support"},modifier=Modifier.fillMaxWidth()){Text("Поддержка")}
                GlassButton(onClick={scope.launch{model.account.refresh()}},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Обновить данные")}
                GlassButton(onClick={scope.launch{model.account.logout()}},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Выйти из аккаунта")}
            }
        }
    }
}

@Composable private fun NativeDevices(model:MainViewModel,subId:Long){
    val scope=rememberCoroutineScope();val state by model.account.state.collectAsStateWithLifecycle()
    var devices by remember(subId){mutableStateOf(emptyList<JSONObject>())}
    var rename by remember{mutableStateOf<JSONObject?>(null)};var label by remember{mutableStateOf("")}
    var remove by remember{mutableStateOf<JSONObject?>(null)}
    suspend fun load(){model.account.request("/subscriptions/$subId/devices")?.let{devices=it.optJSONArray("devices").objects()}}
    LaunchedEffect(subId){load()}
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp)){
        GlassButton(onClick={scope.launch{load()}},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Обновить устройства")}
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){
            if(devices.isEmpty()&&!state.busy)item{Text("Устройств пока нет",color=AccountMuted)}
            items(devices,key={it.getString("id")}){device->
                Card(colors=CardDefaults.cardColors(containerColor=Color(0xE0101B2D))){Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(device.optString("label"),fontWeight=FontWeight.SemiBold)
                    Text("${device.optString("platform")} · ${device.optString("client_type")}\n${device.optString("last_connected_at")}",color=AccountMuted)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedButton(onClick={rename=device;label=device.optString("label")},enabled=!state.busy){Text("Переименовать")}
                        OutlinedButton(onClick={remove=device},enabled=!state.busy){Text("Удалить")}
                    }
                }}
            }
        }
    }
    rename?.let{device->AlertDialog(onDismissRequest={rename=null},title={Text("Название устройства")},text={OutlinedTextField(label,{if(it.length<=80)label=it},singleLine=true)},confirmButton={TextButton(onClick={scope.launch{if(model.account.request("/subscriptions/$subId/devices/${device.getString("id")}","PUT",JSONObject().put("label",label))!=null){rename=null;load()}}},enabled=label.isNotBlank()&&!state.busy){Text("Сохранить")}},dismissButton={TextButton(onClick={rename=null}){Text("Отмена")}})}
    remove?.let{device->AlertDialog(onDismissRequest={remove=null},title={Text("Удалить устройство?")},text={Text(device.optString("label"))},confirmButton={TextButton(onClick={scope.launch{if(model.account.request("/subscriptions/$subId/devices/${device.getString("id")}","DELETE")!=null){remove=null;load()}}},enabled=!state.busy){Text("Удалить")}},dismissButton={TextButton(onClick={remove=null}){Text("Отмена")}})}
}

@Composable private fun NativeSupport(model:MainViewModel){
    val state by model.account.state.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope();val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
    var history by remember{mutableStateOf(JSONObject())};var before by remember{mutableStateOf<Long?>(null)}
    var text by remember{mutableStateOf("")};var image by remember{mutableStateOf<String?>(null)}
    var requestId by remember{mutableStateOf(UUID.randomUUID().toString())}
    var preview by remember{mutableStateOf<android.graphics.Bitmap?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)scope.launch(Dispatchers.IO){
        runCatching{context.contentResolver.openInputStream(uri)!!.use{it.readLimited(5*1024*1024)}}.onSuccess{bytes->image=android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP);requestId=UUID.randomUUID().toString()}.onFailure{model.repository.notify("Изображение должно быть не больше 5 МБ")}
    }}
    suspend fun load(){model.account.request("/support/messages"+(before?.let{"?before=$it"}?:""))?.let{history=it}}
    LaunchedEffect(before){lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
        do{load();if(before!=null)break;delay(15_000)}while(isActive)
    }}
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(history.optBoolean("has_more"))OutlinedButton(onClick={before=history.optLong("next_before").takeIf{it>0}},enabled=!state.busy){Text("Предыдущие")}
            if(before!=null)OutlinedButton(onClick={before=null},enabled=!state.busy){Text("Последние")}
            IconButton(onClick={scope.launch{load()}},enabled=!state.busy){Icon(Icons.Outlined.Refresh,"Обновить сообщения")}
        }
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=8.dp)){
            items(history.optJSONArray("messages").objects(),key={it.getLong("id")}){message->
                Surface(color=if(message.optString("sender_type")=="user")Color(0xE01A3950)else Color(0xE0142033),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Text(if(message.optString("sender_type")=="user")"Вы"else"Поддержка",fontWeight=FontWeight.SemiBold)
                        Text(message.optString("message_text"))
                        if(!message.isNull("image_url"))OutlinedButton(onClick={scope.launch{
                            model.account.image(message.getLong("id"))?.let{bytes->
                                val bitmap=withContext(Dispatchers.IO){val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds);var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1024)sample*=2;BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample;inPreferredConfig=android.graphics.Bitmap.Config.RGB_565})}
                                preview=bitmap
                            }
                        }},enabled=!state.busy){Text("Открыть изображение")}
                        Text(message.optString("created_at"),style=MaterialTheme.typography.bodySmall,color=AccountMuted)
                    }
                }
            }
        }
        if(image!=null)Row(verticalAlignment=Alignment.CenterVertically){Text("Изображение прикреплено",Modifier.weight(1f));TextButton(onClick={image=null;requestId=UUID.randomUUID().toString()}){Text("Убрать")}}
        OutlinedTextField(text,{if(it.length<=4000){text=it;requestId=UUID.randomUUID().toString()}},placeholder={Text("Сообщение поддержке")},modifier=Modifier.fillMaxWidth(),maxLines=4)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedButton(onClick={picker.launch("image/*")},enabled=!state.busy){Text("Фото")}
            GlassButton(onClick={scope.launch{
                val payload=JSONObject().put("text",text).put("request_id",requestId);image?.let{payload.put("image",it)}
                if(model.account.request("/support/messages","POST",payload)!=null){text="";image=null;requestId=UUID.randomUUID().toString();before=null;load()}
            }},enabled=!state.busy&&(text.isNotBlank()||image!=null),modifier=Modifier.weight(1f)){Text("Отправить")}
        }
    }
    preview?.let{bitmap->FullScreenPanel("Изображение",{preview=null}){Image(bitmap.asImageBitmap(),null,Modifier.fillMaxSize())}}
}
