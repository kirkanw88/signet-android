package com.signet.client.vpn

import android.net.Network
import android.net.VpnService
import org.json.JSONObject

/**
 * Session-scoped boundary for the existing Go engine. Keep its control protocol
 * private to this adapter; a Rust backend must implement its own lifecycle and
 * statistics rather than emulate libXray's JSON RPC.
 */
internal interface CoreEngine {
    fun attach(service: VpnService, dns: String, network: Network)
    fun freeMetricsPort(): Int
    fun start(config: JSONObject)
    fun running(): Boolean
    fun stop(service: VpnService)
}

internal class LibXrayEngine : CoreEngine {
    override fun attach(service: VpnService, dns: String, network: Network) =
        NativeCore.attach(service, dns, network)
    override fun freeMetricsPort(): Int =
        NativeCore.call("getFreePorts", JSONObject().put("count", 1))
            .getJSONArray("ports").getInt(0)
    override fun start(config: JSONObject) {
        NativeCore.call("runXray", JSONObject().put("xrayJson", config.toString()))
    }
    override fun running(): Boolean = NativeCore.call("getXrayState").optBoolean("running")
    override fun stop(service: VpnService) {
        try { NativeCore.call("stopXray") } finally { NativeCore.detach(service) }
    }
}
