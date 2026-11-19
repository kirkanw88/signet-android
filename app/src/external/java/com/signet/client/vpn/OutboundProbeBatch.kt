package com.signet.client.vpn

import com.signet.core.*
import kotlinx.coroutines.CancellationException
import org.json.*

object OutboundProbeBatch {
    fun measure(servers:List<Server>,options:ProbeSettings,invoke:(JSONObject)->JSONObject,event:(String)->Unit={}):List<JSONObject> {
        if(servers.isEmpty())return emptyList()
        fun request(items:List<Server>):List<JSONObject> {
            val configs=JSONArray()
            for(server in items){val config=JSONObject(server.config);listOf("inbounds","env","metrics").forEach(config::remove);configs.put(JSONObject().put("xrayJson",config.toString()).put("outboundTag",server.outboundTag))}
            val values=invoke(JSONObject().put("configs",configs).put("timeout",(options.timeoutMs+999)/1000).put("url",options.url)).getJSONArray("results")
            require(values.length()==items.size)
            return items.mapIndexed{index,server->
                val value=values.getJSONObject(index);val success=value.optBoolean("success")&&value.optLong("delay",-1)>=0
                JSONObject().put("id",server.id).put("delay",if(success)value.getLong("delay")else JSONObject.NULL)
                    .put("status",if(success)"OK"else ProbeErrors.native(value.optString("error")))
            }
        }
        fun failure(server:Server,error:Exception)=JSONObject().put("id",server.id).put("status",if(error is CoreFailure)error.code else"PROBE_ERROR")
        try{return request(servers)}catch(e:CancellationException){throw e}catch(e:Exception){
            if(servers.size==1)return listOf(failure(servers.single(),e))
            event("BATCH_SPLIT")
        }
        return servers.map{server->
            try{request(listOf(server)).single()}
            catch(e:CancellationException){throw e}
            catch(e:Exception){failure(server,e)}
        }
    }
}
