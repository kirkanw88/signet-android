package com.signet.client.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

@Composable fun SpaceBackdrop(modifier:Modifier=Modifier,content:@Composable BoxScope.()->Unit){
    val context=LocalContext.current
    val image=remember(context.assets){context.assets.open("space.png").use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inPreferredConfig=android.graphics.Bitmap.Config.RGB_565})}!!.asImageBitmap()}
    Box(modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.background)){
        Image(image,null,Modifier.matchParentSize(),contentScale=ContentScale.Crop,alpha=.72f)
        if(!AppColors.dark)Box(Modifier.matchParentSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background.copy(alpha=.97f)))
        content()
    }
}
