package com.signet.client.vpn

import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.*
import org.junit.Assert.*
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.*

/** Real local TLS, no public VPN endpoint or additional test library. */
class ConnectionHealthTest {
    @Test fun loggingIsBoundedAndRejectsUrlsAndUuids(){
        val log=com.signet.client.data.ConnectionLog
        try {
            log.configure(true);repeat(500){log.add("VPN","EVENT",it.toString())}
            assertEquals(200,log.entries.value.size);assertTrue(log.export().length<64*1024)
            val before=log.entries.value
            log.add("VPN","https://example.com/private-token")
            log.add("VPN","550e8400-e29b-41d4-a716-446655440000")
            assertEquals(before,log.entries.value)
            log.configure(false);assertTrue(log.entries.value.isEmpty())
        }finally {log.configure(false)}
    }
    @Test fun verifiedHttpErrorsStillProveHttpsTraffic()=runBlocking {
        val client=trustedClient()
        try {for(code in listOf(403,503)){
            status.set(code)
            val result=ConnectionHealth.check(url,record=false,client=client)
            assertTrue(result.healthy);assertEquals(code,result.httpCode);assertEquals(1,result.attempts)
        }}finally{close(client)}
    }
    @Test fun transientDnsDuringVpnStartupIsRetried()=runBlocking {
        val lookups=AtomicInteger()
        val client=trustedClient().newBuilder().dns(object:Dns {
            override fun lookup(hostname:String):List<InetAddress>{if(lookups.incrementAndGet()==1)throw UnknownHostException("fixture startup");return listOf(InetAddress.getByName("127.0.0.1"))}
        }).build()
        status.set(204)
        try {val result=ConnectionHealth.check(url,record=false,client=client);assertTrue(result.healthy);assertEquals(2,result.attempts)}finally{close(client)}
    }
    @Test fun untrustedCertificateAndPlainHttpAreRejected()=runBlocking {
        val client=OkHttpClient.Builder().retryOnConnectionFailure(false).build()
        try {
            val result=ConnectionHealth.check(url,record=false,client=client)
            assertFalse(result.healthy);assertEquals("TLS",result.category);assertEquals(1,result.attempts)
            assertTrue(runCatching{ConnectionHealth.check(url.replace("https:","http:"),record=false,client=client)}.exceptionOrNull() is IllegalArgumentException)
        }finally{close(client)}
    }
    companion object {
        private lateinit var directory:Path
        private lateinit var server:SSLServerSocket
        private lateinit var trust:X509TrustManager
        private lateinit var tls:SSLContext
        private val worker=Executors.newSingleThreadExecutor{r->Thread(r,"health-test-server").apply{isDaemon=true}}
        private val status=AtomicInteger(204)
        private val url get()="https://localhost:${server.localPort}/check"
        private fun trustedClient()=OkHttpClient.Builder().sslSocketFactory(tls.socketFactory,trust).retryOnConnectionFailure(false).followRedirects(false).build()
        private fun close(client:OkHttpClient){client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
        @BeforeClass @JvmStatic fun start(){
            directory=Files.createTempDirectory(Files.createDirectories(Path.of("build","tmp")),"health-tls-")
            val store=directory.resolve("server.p12")
            val executable=Path.of(System.getProperty("java.home"),"bin",if(System.getProperty("os.name").startsWith("Windows"))"keytool.exe"else"keytool").toString()
            val process=ProcessBuilder(executable,"-genkeypair","-alias","health-test","-keyalg","RSA","-keysize","2048","-validity","2","-dname","CN=localhost","-ext","SAN=dns:localhost","-storetype","PKCS12","-keystore",store.toString(),"-storepass","fixture-only","-keypass","fixture-only").redirectErrorStream(true).start()
            val output=process.inputStream.bufferedReader().readText();check(process.waitFor()==0){output}
            val keys=KeyStore.getInstance("PKCS12").apply{Files.newInputStream(store).use{load(it,"fixture-only".toCharArray())}}
            val managers=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply{init(keys,"fixture-only".toCharArray())}
            trust=(TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply{init(keys)}.trustManagers.single{it is X509TrustManager}) as X509TrustManager
            tls=SSLContext.getInstance("TLS").apply{init(managers.keyManagers,arrayOf(trust),null)}
            server=tls.serverSocketFactory.createServerSocket(0,10,InetAddress.getByName("127.0.0.1")) as SSLServerSocket
            worker.submit {
                while(!server.isClosed)try {
                    (server.accept() as SSLSocket).use {socket->
                        socket.soTimeout=3000;socket.startHandshake()
                        val reader=socket.inputStream.bufferedReader()
                        while(!reader.readLine().isNullOrEmpty()){}
                        socket.outputStream.write("HTTP/1.1 ${status.get()} Fixture\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray());socket.outputStream.flush()
                    }
                }catch(_:java.io.IOException){}
            }
        }
        @AfterClass @JvmStatic fun stop(){
            if(::server.isInitialized)server.close();worker.shutdownNow()
            if(::directory.isInitialized){Files.deleteIfExists(directory.resolve("server.p12"));Files.deleteIfExists(directory)}
        }
    }
}
