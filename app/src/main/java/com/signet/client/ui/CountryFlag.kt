package com.signet.client.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable fun CountryFlag(code:String?,auto:Boolean=false,modifier:Modifier=Modifier) {
    val resource=remember(code){FlagResources.id(code?.uppercase(java.util.Locale.ROOT))}
    if(auto)Box(modifier.size(32.dp,24.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF3699FF)),contentAlignment=androidx.compose.ui.Alignment.Center){Icon(Icons.Filled.Star,"Флаг AUTO · автоматический выбор сервера",Modifier.size(19.dp),tint=Color.White)}
    else if(resource==0)Icon(Icons.Outlined.Flag,"Страна не указана",modifier.size(32.dp,24.dp),tint=AppColors.accent)
    else Image(painterResource(resource),"Флаг $code",modifier.size(32.dp,24.dp).clip(RoundedCornerShape(5.dp)).border(0.5.dp,Color.White.copy(alpha=.25f),RoundedCornerShape(5.dp)),contentScale=ContentScale.FillBounds)
}
