package com.signet.client.data

import com.signet.core.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import java.io.IOException

class SubscriptionDownloadTest {
    private val clients=mutableListOf<OkHttpClient>()
    @After fun close(){clients.forEach{it.connectionPool.evictAll();it.dispatcher.executorService.shutdown()}}
    private val original="https://sub.example/s/id?token=a%2Bb"
    private val uri="vless://00000000-0000-4000-8000-000000000001@host.example:443#Signet_Latvia"
    private val config="""{"remarks":"Signet_Auto","meta":{"serverDescription":"Automatic profile from panel"},"dns":{"servers":["9.9.9.9"]},"outbounds":[{"tag":"proxy-a","protocol":"vless","settings":{"vnext":[{"address":"host.example","port":443}]},"streamSettings":{"network":"xhttp","security":"tls"}},{"tag":"direct","protocol":"freedom"}],"routing":{"balancers":[{"tag":"auto","selector":["proxy-"]}],"rules":[{"type":"field","network":"tcp,udp","balancerTag":"auto"}]}}"""
    private fun client(answer:(Request)->Response)=OkHttpClient.Builder().followRedirects(false).addInterceptor{answer(it.request())}.build().also{clients.add(it)}
    private fun reply(request:Request,code:Int,text:String,location:String?=null):Response {
        val response=Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture").header("profile-title",if(request.url.encodedPath.endsWith("/json"))"JSON title"else"URI title").body(text.toResponseBody())
        location?.let{response.header("Location",it)}
        return response.build()
    }
    @Test fun baseUriNegotiatesFullJsonAndPreservesAutoDescriptionAndRouting()=runBlocking {
        val seen=mutableListOf<Request>();val json=JSONArray().put(JSONObject(config)).toString()
        val http=client{request->seen.add(request);reply(request,200,if(request.url.encodedPath.endsWith("/json")&&request.header("User-Agent")!!.startsWith("Happ/"))json else uri)}
        val result=SubscriptionDownload.fetch(http,original,"SignetVPN/test",mapOf("X-Hwid" to "test-device"))
        assertEquals(listOf(original,"https://sub.example/s/id/json?token=a%2Bb"),seen.map{it.url.toString()})
        assertTrue(seen.all{it.header("X-Hwid")=="test-device"})
        assertTrue(seen.all{it.header("User-Agent")=="Happ/compatible SignetVPN/test"})
        assertEquals("JSON title",result.headers["profile-title"]);assertEquals(json,result.text)
        val server=Profiles.parseJson(result.text).single();assertTrue(server.isAuto)
        assertEquals("Signet_Auto",ServerDisplayModel.from(server).title)
        assertEquals("Automatic profile from panel",ServerDisplayModel.from(server).description)
        val before=JSONObject(config);val runtime=Profiles.runtime(server,42,12345,"/geo","1.1.1.1")
        for(field in listOf("routing","dns","outbounds"))assertEquals(before.get(field).toString(),runtime.get(field).toString())
    }
    @Test fun uriOnlyProviderIsRetainedForMissingInvalidOrFailedJson()=runBlocking {
        for(status in listOf(404,403,500,200)){
            val http=client{r->reply(r,if(r.url.encodedPath.endsWith("/json"))status else 200,if(r.url.encodedPath.endsWith("/json"))"{\"outbounds\":[{\"type\":\"vless\",\"tag\":\"proxy\"}]}"else uri)}
            assertEquals(uri,SubscriptionDownload.fetch(http,original,"SignetVPN/test",emptyMap()).text)
        }
        val http=client{r->if(r.url.encodedPath.endsWith("/json"))throw IOException("fixture")else reply(r,200,uri)}
        assertEquals(uri,SubscriptionDownload.fetch(http,original,"SignetVPN/test",emptyMap()).text)
    }
    @Test fun alreadyJsonAndBase64JsonRequireNoExtraRequest()=runBlocking {
        for(text in listOf(config,java.util.Base64.getEncoder().encodeToString(config.toByteArray()))){
            var requests=0;val http=client{r->requests++;reply(r,200,text)}
            assertEquals(text,SubscriptionDownload.fetch(http,original,"SignetVPN/test",emptyMap()).text)
            assertEquals(1,requests)
        }
    }
    @Test fun jsonWithoutExtendedMetadataCanRetrievePanelDescription()=runBlocking {
        val plain=JSONObject(config).apply{remove("meta")}.toString()
        val http=client{r->reply(r,200,if(r.header("User-Agent")!!.startsWith("Happ/"))config else plain)}
        val result=SubscriptionDownload.fetch(http,original,"SignetVPN/test",emptyMap())
        assertEquals("Automatic profile from panel",Profiles.parseJson(result.text).single().description)
    }
    @Test fun optionalJsonCannotRedirectIdentityToAnotherOrigin()=runBlocking {
        val seen=mutableListOf<Request>();val http=client{r->seen.add(r);if(r.url.encodedPath.endsWith("/json"))reply(r,302,"","https://other.example/json")else reply(r,200,uri)}
        assertEquals(uri,SubscriptionDownload.fetch(http,original,"SignetVPN/test",mapOf("X-Hwid" to "test-device")).text)
        assertEquals(2,seen.size);assertTrue(seen.all{it.url.host=="sub.example"})
    }
    @Test fun deniedOriginalDoesNotProbeOtherPaths()=runBlocking {
        var requests=0;val http=client{r->requests++;reply(r,403,"")}
        try{SubscriptionDownload.fetch(http,original,"SignetVPN/test",emptyMap());fail("Denied subscriptions must fail")}
        catch(expected:IllegalArgumentException){assertEquals(1,requests)}
    }
    @Test fun firstImportOfEachSourceNegotiatesJsonOnBaseWithoutJsonRoute()=runBlocking {
        val seen=mutableListOf<Request>()
        val http=client{r->
            seen.add(r)
            when {
                r.url.encodedPath.endsWith("/json")->reply(r,404,"")
                r.header("User-Agent")!!.startsWith("Happ/")->reply(r,200,JSONArray().put(JSONObject(config)).toString())
                else->reply(r,200,uri)
            }
        }
        val imported=listOf("https://sub.example/first","https://other.example/second").map{source->
            val result=SubscriptionDownload.fetch(http,source,"SignetVPN/test",mapOf("X-Hwid" to "test-device"))
            ProfileRefresh.reconcile(Profiles.id(source),emptyList(),Profiles.parseJson(result.text)).single()
        }
        assertEquals(2,seen.size)
        assertTrue(imported.all{it.isAuto&&it.name=="Signet_Auto"})
        assertNotEquals(imported[0].id,imported[1].id)
        assertTrue(seen.all{it.header("X-Hwid")=="test-device"})
        for(server in imported)for(field in listOf("routing","dns","outbounds")){
            assertEquals(JSONObject(config).get(field).toString(),JSONObject(server.config).get(field).toString())
        }
    }
    @Test fun explicitJsonAddressAlsoNegotiatesBeforeAnyUriFallback()=runBlocking {
        for(source in listOf("https://sub.example/id/json","https://sub.example/profile.json?token=a%2Bb")){
            var requests=0
            val http=client{r->requests++;reply(r,200,if(r.header("User-Agent")!!.startsWith("Happ/"))config else uri)}
            val result=SubscriptionDownload.fetch(http,source,"SignetVPN/test",emptyMap())
            assertTrue(Profiles.parseJson(result.text).single().isAuto)
            assertEquals(1,requests)
        }
    }
}
