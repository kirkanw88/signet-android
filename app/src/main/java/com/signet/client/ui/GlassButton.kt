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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** A static glass surface: no backdrop bitmap, blur buffer or continuous animation. */
@Composable fun GlassButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:Shape=RoundedCornerShape(22.dp),content:@Composable RowScope.()->Unit) {
    val alpha=if(enabled)1f else .4f
    Button(onClick=onClick,enabled=enabled,shape=shape,
        modifier=modifier.clip(shape).background(Brush.verticalGradient(listOf(Color.White.copy(alpha=.20f*alpha),Color(0xFF73D8FF).copy(alpha=.13f*alpha),Color.White.copy(alpha=.045f*alpha)))),
        colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent,contentColor=Color(0xFFE7F8FF),disabledContainerColor=Color.Transparent,disabledContentColor=Color.White.copy(alpha=.35f)),
        border=BorderStroke(1.dp,Brush.verticalGradient(listOf(Color.White.copy(alpha=.65f*alpha),Color(0xFFB4E9FF).copy(alpha=.12f*alpha)))),
        content=content)
}
