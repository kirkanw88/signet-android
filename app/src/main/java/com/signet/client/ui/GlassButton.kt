package com.signet.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.signet.client.BuildConfig

/**
 * Lightweight liquid-glass styling. Translucency, a specular top edge and
 * restrained tint mimic glass without allocating a blur/rendering buffer.
 * This intentionally leaves the globe itself completely unframed.
 */
@Composable
fun LiquidGlassFrame(
    onClick:()->Unit,
    modifier:Modifier=Modifier,
    enabled:Boolean=true,
    prominent:Boolean=false,
    shape:Shape=RoundedCornerShape(14.dp),
    content:@Composable ()->Unit,
) {
    val dark=AppColors.dark
    val opacity=if(enabled)1f else .45f
    val tint=AppColors.accent
    val fill=when {
        prominent&&dark->listOf(tint.copy(alpha=.83f*opacity),Color(0xFF119EDB).copy(alpha=.80f*opacity),Color(0xFF0A719F).copy(alpha=.83f*opacity))
        prominent->listOf(tint.copy(alpha=.91f*opacity),tint.copy(alpha=.84f*opacity),tint.copy(alpha=.90f*opacity))
        dark->listOf(Color.White.copy(alpha=.16f*opacity),Color(0xFF6BC9F4).copy(alpha=.075f*opacity),Color(0xFF101C31).copy(alpha=.48f*opacity))
        else->listOf(Color.White.copy(alpha=.90f*opacity),AppColors.tint.copy(alpha=.76f*opacity),Color.White.copy(alpha=.85f*opacity))
    }
    val edge=if(dark)Color.White else AppColors.accent
    Surface(
        onClick=onClick,enabled=enabled,modifier=modifier,shape=shape,color=Color.Transparent,
        contentColor=if(prominent)Color.White.copy(alpha=opacity) else AppColors.text.copy(alpha=opacity),
        border=BorderStroke(1.dp,Brush.verticalGradient(listOf(edge.copy(alpha=(if(prominent).58f else .36f)*opacity),edge.copy(alpha=.13f*opacity),edge.copy(alpha=.20f*opacity))))
    ) {
        Box(Modifier.background(Brush.verticalGradient(fill),shape)){
            // Narrow, non-animated specular strip: high clarity without OLED glare.
            Box(Modifier.fillMaxWidth().height(1.dp).background(
                Brush.horizontalGradient(listOf(Color.Transparent,Color.White.copy(alpha=.24f*opacity),Color.Transparent))
            ))
            content()
        }
    }
}

@Composable fun GlassButton(
    onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    shape:Shape=RoundedCornerShape(if(BuildConfig.SIGNET_ACCOUNT)22.dp else 14.dp),
    content:@Composable RowScope.()->Unit,
) {
    LiquidGlassFrame(onClick=onClick,modifier=modifier,enabled=enabled,shape=shape){
        ProvideTextStyle(MaterialTheme.typography.labelLarge){
            Row(Modifier.defaultMinSize(
                minWidth=if(BuildConfig.SIGNET_ACCOUNT)58.dp else 48.dp,
                minHeight=48.dp
            ).padding(
                horizontal=if(BuildConfig.SIGNET_ACCOUNT)20.dp else 12.dp,
                vertical=if(BuildConfig.SIGNET_ACCOUNT)12.dp else 8.dp
            ),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically,content=content)
        }
    }
}
