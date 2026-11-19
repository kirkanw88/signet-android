package com.signet.client.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.signet.core.*
import com.signet.client.vpn.Probes
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable fun TemplateScreen(server:Server,close:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val report=remember(server.config){TemplateCompatibility.inspect(JSONObject(server.config))}
    var result by remember{mutableStateOf<String?>(null)};var checking by remember{mutableStateOf(false)}
    FullScreenPanel("Шаблон Remnawave / Xray JSON",close) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(ServerDisplayModel.from(server,hostNames=!com.signet.client.BuildConfig.SIGNET_ACCOUNT).title,style=MaterialTheme.typography.titleLarge)
            Text(report.text())
            Text("Порядок routing.rules, DNS, outbounds и balancers сохраняется. Android заменяет слушающие inbounds одним TUN с указанным tag. Правила для других входов не получают Android-трафик.")
            Text("Статическая проверка и принятие JSON ядром не подтверждают сетевую маршрутизацию. Ping в режиме «Полный профиль» использует предоставленные правила; DNS/UDP/IPv6 и split tunneling требуют отдельного теста трафика.",style=MaterialTheme.typography.bodySmall)
            result?.let{Text(it)}
        }
        GlassButton(onClick={scope.launch {checking=true;result=withContext(Dispatchers.IO){runCatching {if(Probes.validate(context,server))"Ядро приняло адаптированный JSON · PASS"else"Ядро отклонило JSON · FAIL. Проверьте geodata, ссылки и поддержку транспорта."}.getOrDefault("Не удалось выполнить проверку")};checking=false}},enabled=!checking&&report.accepted,modifier=Modifier.fillMaxWidth().padding(vertical=8.dp)){Text(if(checking)"Проверка…"else"Проверить JSON ядром")}
    }
}
