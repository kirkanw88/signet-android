package com.signet.core
import org.junit.Test
import kotlin.test.*
import java.nio.file.Files

class VerifiedAssetsTest {
    private fun directory()=Files.createTempDirectory(Files.createDirectories(java.nio.file.Paths.get("build/test-assets")),"signet-assets-").toFile()
    private fun cleanup(directory:java.io.File){directory.listFiles()?.forEach{check(it.isFile);it.delete()};directory.delete()}
    @Test fun truncatedFileAndInterruptedTempAreRepaired(){
        val directory=directory()
        try{
            val target=java.io.File(directory,"geosite.dat");target.writeText("truncated")
            java.io.File(directory,"geosite.dat.pending").writeText("interrupted")
            val data="complete trusted APK asset".toByteArray()
            VerifiedAssets.prepare(directory,listOf("geosite.dat")){data.inputStream()}
            assertContentEquals(data,target.readBytes());assertFalse(java.io.File(directory,"geosite.dat.pending").exists())
        }finally{cleanup(directory)}
    }
    @Test fun failedCopyNeverReplacesPreviousFile(){
        val directory=directory()
        try{
            val target=java.io.File(directory,"geoip.dat");target.writeText("previous")
            var reads=0
            assertFails{VerifiedAssets.prepare(directory,listOf("geoip.dat")){reads++;(if(reads==1)"new"else"bad").byteInputStream()}}
            assertEquals("previous",target.readText());assertFalse(java.io.File(directory,"geoip.dat.pending").exists())
        }finally{cleanup(directory)}
    }
}
