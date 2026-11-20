package com.signet.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.BorderStroke
import com.signet.client.BuildConfig
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** A static glass surface: no backdrop bitmap, blur buffer or continuous animation. */
@Composable fun GlassButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:Shape=RoundedCornerShape(if(BuildConfig.SIGNET_ACCOUNT)22.dp else 14.dp),content:@Composable RowScope.()->Unit) {
    val alpha=if(enabled)1f else .4f
    val fill=if(AppColors.dark)listOf(Color.White.copy(alpha=.20f*alpha),Color(0xFF73D8FF).copy(alpha=.13f*alpha),Color.White.copy(alpha=.045f*alpha))
        else listOf(AppColors.surface.copy(alpha=.9f*alpha),AppColors.tint.copy(alpha=.8f*alpha),AppColors.surface.copy(alpha=.85f*alpha))
    Surface(onClick=onClick,enabled=enabled,shape=shape,modifier=modifier,color=Color.Transparent,contentColor=AppColors.text.copy(alpha=alpha),
        border=BorderStroke(1.dp,Brush.verticalGradient(listOf(AppColors.border.copy(alpha=alpha),AppColors.border.copy(alpha=.7f*alpha)))),
    ){
        ProvideTextStyle(MaterialTheme.typography.labelLarge){
            Row(Modifier.background(Brush.verticalGradient(fill),shape)
                .defaultMinSize(minWidth=if(BuildConfig.SIGNET_ACCOUNT)58.dp else 48.dp,minHeight=48.dp).padding(horizontal=if(BuildConfig.SIGNET_ACCOUNT)20.dp else 12.dp,vertical=if(BuildConfig.SIGNET_ACCOUNT)12.dp else 8.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,content=content)
        }
    }
}
