package com.signet.client

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.signet.client.qr.QrImage
import org.junit.Test
import org.junit.Assert.assertEquals

class QrImageTest {
    @Test fun imageImportDecodesSubscriptionBeforeConfirmation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val text="https://example.invalid/subscription/qr-fixture"
        val matrix=QRCodeWriter().encode(text,BarcodeFormat.QR_CODE,512,512)
        val bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        for(y in 0 until 512)for(x in 0 until 512)bitmap.setPixel(x,y,if(matrix[x,y])android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        val file=context.cacheDir.resolve("qr-test.png")
        try {
            file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            assertEquals(text,QrImage.decode(context,Uri.fromFile(file)))
        } finally {bitmap.recycle();file.delete()}
    }
}
