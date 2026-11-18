package com.signet.client.qr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.signet.core.ImportReview

object QrImage {
    fun decode(context:Context,uri:Uri):String {
        val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it,null,options) }
        require(options.outWidth>0 && options.outHeight>0) { "Изображение не читается" }
        var sample=1
        while(maxOf(options.outWidth,options.outHeight)/sample>1536)sample*=2
        options.inJustDecodeBounds=false;options.inSampleSize=sample
        val bitmap=context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it,null,options) } ?: error("Не удалось прочитать изображение")
        try {
            val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
            val source=RGBLuminanceSource(bitmap.width,bitmap.height,pixels)
            val result=MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)),mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),DecodeHintType.TRY_HARDER to true)).text
            require(ImportReview.supported(result)) { "Неподдерживаемое содержимое QR" };return result
        } finally { bitmap.recycle() }
    }
}
