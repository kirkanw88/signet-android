package com.signet.client.vpn

import libXray.LibXray
import org.json.JSONObject

class CoreFailure(val code:String):IllegalStateException("Ошибка ядра: $code")

object NativeCore {
    @Volatile private var protector: android.net.VpnService?=null
    @Volatile private var underlying:android.net.Network?=null
    private var registered=false
    private val listener=libXray.DialerController { fd -> protector?.protect(fd.toInt()) ?: false }
    private val controller=libXray.DialerController { fd ->
        if(protector?.protect(fd.toInt())!=true)false else runCatching {
            underlying?.let { network->android.os.ParcelFileDescriptor.fromFd(fd.toInt()).use{network.bindSocket(it.fileDescriptor)} }
            true
        }.getOrDefault(false)
    }
    @Synchronized fun attach(service: android.net.VpnService,dns: String,network:android.net.Network?=null) {
        protector=service;underlying=network
        // Xray appends callbacks. Register once, otherwise destroyed Services accumulate forever.
        if(!registered){LibXray.registerDialerController(controller);LibXray.registerListenerController(listener);registered=true}
        LibXray.setDNS(controller,dns)
    }
    @Synchronized fun detach(service: android.net.VpnService) {
        if(protector===service){LibXray.resetDNS();protector=null;underlying=null}
    }
    @Synchronized fun call(method: String, payload: JSONObject = JSONObject()): JSONObject {
        val response = JSONObject(LibXray.invoke(JSONObject().put("apiVersion", 3).put("method", method).put("payload", payload).toString()))
        if(!response.optBoolean("success"))com.signet.client.data.ConnectionLog.add("CORE",method,"FAILED")
        if(!response.optBoolean("success"))throw CoreFailure(com.signet.core.ProbeErrors.native(response.optString("error")))
        return response.optJSONObject("data") ?: JSONObject()
    }
    fun version(): String = runCatching { call("xrayVersion").getString("version") }.getOrDefault("недоступно")
}
