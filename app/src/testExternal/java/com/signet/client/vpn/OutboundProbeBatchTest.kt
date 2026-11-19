package com.signet.client.vpn

import com.signet.core.*
import org.json.*
import org.junit.Test
import org.junit.Assert.*

class OutboundProbeBatchTest {
    private fun server(name:String)=Profiles.parseJson("""{"remarks":"$name","outbounds":[{"tag":"$name","protocol":"vless"}]}""").single()
    @Test fun badConfigCannotFailEveryOtherServerInABatch(){
        val servers=listOf(server("good"),server("bad"));var calls=0
        val results=OutboundProbeBatch.measure(servers,ProbeSettings(),{request->
            calls++;val configs=request.getJSONArray("configs")
            if(configs.length()>1||configs.getJSONObject(0).getString("outboundTag")=="bad")throw CoreFailure("CONFIG_ERROR")
            JSONObject().put("results",JSONArray().put(JSONObject().put("success",true).put("delay",42)))
        })
        assertEquals(3,calls);assertEquals("OK",results[0].getString("status"));assertEquals(42,results[0].getLong("delay"))
        assertEquals("CONFIG_ERROR",results[1].getString("status"));assertFalse(results[1].has("delay"))
    }
    @Test fun aSingleFailedProfileIsNotRetriedOrGivenFakeLatency(){
        var calls=0
        val result=OutboundProbeBatch.measure(listOf(server("bad")),ProbeSettings(),{calls++;throw CoreFailure("CONFIG_ERROR")}).single()
        assertEquals(1,calls);assertEquals("CONFIG_ERROR",result.getString("status"));assertFalse(result.has("delay"))
    }
}
