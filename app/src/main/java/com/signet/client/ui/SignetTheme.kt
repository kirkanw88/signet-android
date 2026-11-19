package com.signet.client.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

object AppColors {
    val text:Color @Composable get()=MaterialTheme.colorScheme.onSurface
    val muted:Color @Composable get()=MaterialTheme.colorScheme.onSurfaceVariant
    val surface:Color @Composable get()=MaterialTheme.colorScheme.surface
    val accent:Color @Composable get()=MaterialTheme.colorScheme.primary
    val border:Color @Composable get()=MaterialTheme.colorScheme.outlineVariant
    val tint:Color @Composable get()=MaterialTheme.colorScheme.primaryContainer
    val dark:Boolean @Composable get()=MaterialTheme.colorScheme.background==Color(0xFF050816)
    val success:Color @Composable get()=if(dark)Color(0xFF32E6A1)else Color(0xFF007A50)
    val warning:Color @Composable get()=if(dark)Color(0xFFFFC857)else Color(0xFF8B5200)
}

@Composable fun SignetTheme(mode:String,content:@Composable ()->Unit){
    val dark=when(mode){"light"->false;"system"->isSystemInDarkTheme();else->true}
    val scheme=if(dark)darkColorScheme(primary=Color(0xFF39C8FF),secondary=Color(0xFF62F4E8),background=Color(0xFF050816),surface=Color(0xFF10182A),onSurface=Color(0xFFF0F5FD),onSurfaceVariant=Color(0xFFB2C1D7),primaryContainer=Color(0xFF1B3146),onPrimaryContainer=Color(0xFFD5F0FF),outlineVariant=Color(0xFF334359),error=Color(0xFFFF657A))
    else lightColorScheme(primary=Color(0xFF00648B),secondary=Color(0xFF006A65),background=Color(0xFFF4F7FC),surface=Color(0xFFFCFDFF),onSurface=Color(0xFF152236),onSurfaceVariant=Color(0xFF485B73),primaryContainer=Color(0xFFDCEFFA),onPrimaryContainer=Color(0xFF123B51),outlineVariant=Color(0xFFB8C9DB),error=Color(0xFFB3213C))
    val activity=LocalActivity.current as? ComponentActivity
    DisposableEffect(activity,dark){
        val style=if(dark)SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)else SystemBarStyle.light(scheme.background.toArgb(),scheme.background.toArgb())
        activity?.enableEdgeToEdge(statusBarStyle=style,navigationBarStyle=style)
        onDispose{}
    }
    MaterialTheme(colorScheme=scheme,content=content)
}
