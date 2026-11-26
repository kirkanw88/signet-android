package com.signet.client.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

@Composable fun SpaceBackdrop(modifier:Modifier=Modifier,animated:Boolean=false,content:@Composable BoxScope.()->Unit){
    val context=LocalContext.current
    val image=remember(context.assets){context.assets.open("space.png").use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inPreferredConfig=android.graphics.Bitmap.Config.RGB_565})}!!.asImageBitmap()}
    val drift=if(animated){val transition=rememberInfiniteTransition(label="space-drift");val value by transition.animateFloat(-1f,1f,infiniteRepeatable(tween(5000,easing=LinearEasing),RepeatMode.Reverse),label="space-motion");value}else 0f
    Box(modifier.clipToBounds().background(androidx.compose.material3.MaterialTheme.colorScheme.background)){
        Image(image,null,Modifier.matchParentSize().graphicsLayer { if(animated){scaleX=1.12f;scaleY=1.12f;translationX=size.width*.035f*drift;translationY=size.height*.02f*drift} },contentScale=ContentScale.Crop,alpha=.72f)
        if(!AppColors.dark)Box(Modifier.matchParentSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background.copy(alpha=.97f)))
        content()
    }
}
