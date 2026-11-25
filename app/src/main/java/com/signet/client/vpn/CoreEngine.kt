package com.signet.client.vpn

import android.net.Network
import android.net.VpnService
import com.signet.core.Profiles
import com.signet.core.Server
import org.json.JSONObject
import java.io.File

/** Android owns the TUN fd. Every engine must stop before the service closes it. */
internal data class EngineSession(
    val service: VpnService,
    val network: Network,
    val bootstrapDns: String,
    val server: Server,
    val tunFd: Int,
    val assets: File,
    val fallbackDns: String,
    val metricsPort: Int
)

internal data class EngineTraffic(val uploaded: Long, val downloaded: Long)

internal interface CoreEngine {
    val name: String
    val dnsAddress: String
    fun attach(service: VpnService, dns: String, network: Network)
    fun prepare(server: Server, fallbackDns: String, network: Network) = Unit
    fun freeMetricsPort(): Int
    fun start(session: EngineSession)
    fun running(): Boolean
    /** null means the host reads libXray's loopback metrics endpoint. */
    fun traffic(): EngineTraffic? = null
    fun stop(service: VpnService)
}

internal class LibXrayEngine : CoreEngine {
    override val name = "libXray"
    override val dnsAddress = com.signet.core.AndroidDns.ADDRESS
    override fun attach(service: VpnService, dns: String, network: Network) =
        NativeCore.attach(service, dns, network)
    override fun freeMetricsPort(): Int =
        NativeCore.call("getFreePorts", JSONObject().put("count", 1))
            .getJSONArray("ports").getInt(0)
    override fun start(session: EngineSession) {
        val config = Profiles.androidRuntime(session.server, session.tunFd, session.metricsPort,
            session.assets.absolutePath, session.fallbackDns)
        NativeCore.call("runXray", JSONObject().put("xrayJson", config.toString()))
    }
    override fun running(): Boolean = NativeCore.call("getXrayState").optBoolean("running")
    override fun stop(service: VpnService) {
        try { NativeCore.call("stopXray") } finally { NativeCore.detach(service) }
    }
}
