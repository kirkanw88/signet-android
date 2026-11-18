package com.signet.core

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Test
import kotlin.test.*

class UpdateTransferTest{
    private val data=ByteArray(200_000){(it%255).toByte()}
    private fun digest()=MessageDigest.getInstance("SHA-256").digest(data).joinToString(""){"%02x".format(it)}
    @Test fun boundedStreamHasExactDigestAndProgressWithoutAFile(){val output=ByteArrayOutputStream();var count=0L;UpdateTransfer.copy(data.inputStream(),output,data.size.toLong(),digest()){assertTrue(it>=count);count=it};assertContentEquals(data,output.toByteArray());assertEquals(data.size.toLong(),count)}
    @Test fun rejectCorruptTruncatedAndOversizedPayloads(){assertFails{UpdateTransfer.copy(data.inputStream(),ByteArrayOutputStream(),data.size.toLong(),"0".repeat(64)){}};assertFails{UpdateTransfer.copy(data.inputStream(),ByteArrayOutputStream(),data.size+1L,digest()){}};assertFails{UpdateTransfer.copy(data.inputStream(),ByteArrayOutputStream(),data.size-1L,digest()){}}}
    @Test fun redirectChainNeverAllowsHttpCredentialsOrForeignHosts(){listOf("http://github.com/file","https://evil.example/file","https://github.com.evil.example/file","https://user@github.com/file","https://release-assets.githubusercontent.com:444/file").forEach{assertFalse(UpdateTransfer.trustedHop(it))};assertTrue(UpdateTransfer.trustedHop("https://release-assets.githubusercontent.com/file?token=temporary"))}
}
