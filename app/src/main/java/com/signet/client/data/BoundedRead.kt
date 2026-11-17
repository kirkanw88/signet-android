package com.signet.client.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import kotlin.coroutines.resumeWithException

fun InputStream.readLimited(max: Int): ByteArray {
    val output=ByteArrayOutputStream(minOf(max,8192)); val buffer=ByteArray(8192)
    while(output.size()<=max){val n=read(buffer,0,minOf(buffer.size,max+1-output.size()));if(n<0)break;output.write(buffer,0,n)}
    require(output.size()<=max){"Превышен допустимый размер"}
    return output.toByteArray()
}

suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object: Callback {
        override fun onFailure(call: Call,e: java.io.IOException) { if(continuation.isActive)continuation.resumeWithException(e) }
        override fun onResponse(call: Call,response: Response) {
            continuation.resume(response,onCancellation={_,value,_->value.close()})
        }
    })
}
