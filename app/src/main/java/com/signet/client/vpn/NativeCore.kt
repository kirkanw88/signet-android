package com.signet.client.vpn

import libXray.LibXray
import org.json.JSONObject

object NativeCore {
    @Volatile private var protector: android.net.VpnService?=null
    private var registered=false
    private val controller=libXray.DialerController { fd -> protector?.protect(fd.toInt()) ?: false }
    @Synchronized fun attach(service: android.net.VpnService,dns: String) {
        protector=service
        // Xray appends callbacks. Register once, otherwise destroyed Services accumulate forever.
        if(!registered){LibXray.registerDialerController(controller);LibXray.registerListenerController(controller);registered=true}
        LibXray.setDNS(controller,dns)
    }
    @Synchronized fun detach(service: android.net.VpnService) {
        if(protector===service){LibXray.resetDNS();protector=null}
    }
    @Synchronized fun call(method: String, payload: JSONObject = JSONObject()): JSONObject {
        val response = JSONObject(LibXray.invoke(JSONObject().put("apiVersion", 3).put("method", method).put("payload", payload).toString()))
        check(response.optBoolean("success")) { "Ошибка ядра: $method" }
        return response.optJSONObject("data") ?: JSONObject()
    }
    fun version(): String = runCatching { call("xrayVersion").getString("version") }.getOrDefault("недоступно")
}
