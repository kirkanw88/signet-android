package com.signet.client.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import org.json.JSONObject

fun prettyJson(raw:String)=runCatching{JSONObject(raw).toString(4)}.getOrDefault(raw)

@Composable fun JsonEditor(raw:String,change:(String)->Unit,save:()->Unit,close:()->Unit){
    var error by remember{mutableStateOf<String?>(null)}
    val style=TextStyle(fontFamily=FontFamily.Monospace,fontSize=13.sp,lineHeight=20.sp,color=AppColors.text)
    val numbers=remember(raw){(1..(raw.count{it=='\n'}+1)).joinToString("\n")}
    FullScreenPanel("Конфигурация Xray",close){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            GlassButton(onClick={runCatching{JSONObject(raw).toString(4)}.onSuccess{change(it);error=null}.onFailure{error="Исправьте синтаксис JSON перед форматированием"}}){Text("Форматировать")}
        }
        Text("Содержит ключи доступа. Не публикуйте конфигурацию.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=10.dp))
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        Row(Modifier.weight(1f).fillMaxWidth().background(AppColors.surface).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(vertical=12.dp)){
            Text(numbers,style=style.copy(color=AppColors.muted),modifier=Modifier.padding(horizontal=12.dp))
            BasicTextField(raw,{if(it.length<=512*1024)change(it)},textStyle=style,cursorBrush=SolidColor(AppColors.accent),
                keyboardOptions=KeyboardOptions(autoCorrectEnabled=false),modifier=Modifier.widthIn(min=700.dp).padding(end=16.dp))
        }
        GlassButton(onClick=save,modifier=Modifier.fillMaxWidth().padding(vertical=10.dp)){Text("Проверить и сохранить")}
    }
}
