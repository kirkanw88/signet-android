package com.signet.client.qr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.common.GlobalHistogramBinarizer
import com.signet.core.ImportReview
import com.signet.client.data.readLimited

object QrImage {
    fun decode(context:Context,uri:Uri):String {
        val bytes=context.contentResolver.openInputStream(uri)!!.use { it.readLimited(16*1024*1024) }
        val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
        require(options.outWidth>0 && options.outHeight>0) { "Изображение не читается" }
        var sample=1
        while(maxOf(options.outWidth,options.outHeight)/sample>1536)sample*=2
        options.inJustDecodeBounds=false;options.inSampleSize=sample
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,options) ?: error("Не удалось прочитать изображение")
        try {
            val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
            val source=RGBLuminanceSource(bitmap.width,bitmap.height,pixels)
            val hints=mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),DecodeHintType.TRY_HARDER to true,DecodeHintType.CHARACTER_SET to "UTF-8")
            val result=sequenceOf(source,source.invert()).flatMap { light->sequenceOf(HybridBinarizer(light),GlobalHistogramBinarizer(light)) }
                .mapNotNull{binarizer->runCatching{MultiFormatReader().decode(BinaryBitmap(binarizer),hints).text}.getOrNull()}.firstOrNull()?.let(ImportReview::normalize)
                ?:error("QR не найден")
            require(ImportReview.supported(result)) { "Неподдерживаемое содержимое QR" };return result
        } finally { bitmap.recycle() }
    }
}
