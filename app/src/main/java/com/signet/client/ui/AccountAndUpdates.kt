package com.signet.client.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.signet.client.MainViewModel
import com.signet.core.AppRelease
import com.signet.core.SubscriptionMetadata

fun openHttps(context:Context,url:String,onError:()->Unit={}) {
    if(SubscriptionMetadata.safeLink(url)==null){onError();return}
    runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}.onFailure{onError()}
}

@Composable fun UpdateBanner(release:AppRelease,model:MainViewModel) {
    val context=LocalContext.current
    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Доступна новая версия ${release.version}",style=MaterialTheme.typography.titleSmall)
            Row{TextButton(onClick={openHttps(context,release.apk?:release.page){model.repository.notify("Не найден браузер для открытия выпуска")}}){Text(if(release.apk!=null)"Скачать APK"else"Открыть выпуск")};TextButton(onClick=model::dismissUpdate){Text("Позже")}}
        }
    }
}

/** Existing Signet browser identity/payment flow. No invented native session or payment success. */
@Composable fun AccountScreen(model:MainViewModel,close:()->Unit) {
    val context=LocalContext.current
    fun open(path:String)=openHttps(context,"https://signet-cab.com$path"){model.repository.notify("Не удалось открыть кабинет")}
    FullScreenPanel("Аккаунт Signet",close) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("Один аккаунт для Signet",style=MaterialTheme.typography.headlineSmall)
            Text("Войдите в существующий кабинет через Google или Яндекс. При первом входе аккаунт создаётся в Signet. Подписки и платежи остаются в вашем действующем кабинете.")
            GlassButton(onClick={open("/auth/google")},modifier=Modifier.fillMaxWidth()){Text("Войти через Google")}
            GlassButton(onClick={open("/auth/yandex")},modifier=Modifier.fillMaxWidth()){Text("Войти через Яндекс")}
            GlassButton(onClick={openHttps(context,"https://t.me/signet_it_bot"){model.repository.notify("Не удалось открыть Telegram")}},modifier=Modifier.fillMaxWidth()){Text("Открыть Telegram-бот Signet")}
            HorizontalDivider()
            Text("Подписки и оплата",style=MaterialTheme.typography.titleLarge)
            Text("Покупка, продление, тарифы, устройства и подтверждение оплаты выполняются защищённым кабинетом. Приложение не получает данные банковской карты.")
            GlassButton(onClick={open("/cabinet")},modifier=Modifier.fillMaxWidth()){Text("Покупка и продление")}
            Text("После входа скопируйте ссылку подписки или QR из кабинета и добавьте в Signet. Подписка из веб-кабинета и Telegram использует ваш существующий аккаунт.")
            TextButton(onClick={open("/support")}){Text("Поддержка и восстановление доступа")}
            Row{TextButton(onClick={open("/terms")}){Text("Условия")};TextButton(onClick={open("/privacy")}){Text("Конфиденциальность")}}
        }
    }
}
