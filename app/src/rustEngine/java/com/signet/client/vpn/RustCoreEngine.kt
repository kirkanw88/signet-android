package com.signet.client.vpn

import android.net.Network
import android.net.VpnService
import com.signet.core.TemplateCompatibility
import org.json.JSONArray
import org.json.JSONObject
import org.xrayrust.mobile.XrayCore
import org.xrayrust.mobile.XrayDnsBootstrapMode
import org.xrayrust.mobile.XrayTunFileDescriptor
import org.xrayrust.mobile.XrayTunInterface
import org.xrayrust.mobile.XrayTunRuntimeProfile

/**
 * Experimental SDK v0.10.1 adapter. The caller owns the borrowed TUN fd.
 * Unsupported profiles fail closed; never rewrite outbounds to force acceptance.
 */
internal class RustCoreEngine : CoreEngine {
    override val name = "xray-rust"
    override val dnsAddress = XrayTunInterface.DNS_ANCHOR
    private var core: XrayCore? = null
    private var prepared: JSONObject? = null

    @Synchronized override fun prepare(server: com.signet.core.Server, fallbackDns: String, network: Network) {
        prepared = null
        prepared = RustRuntimeConfig.prepare(server.config, fallbackDns) { host ->
            network.getAllByName(host).mapNotNull { it.hostAddress }
        }
    }

    override fun attach(service: VpnService, dns: String, network: Network) = Unit
    override fun freeMetricsPort() = 0

    @Synchronized override fun start(session: EngineSession) {
        check(core == null)
        val config = prepared ?: throw CoreFailure("RUST_PREPARE_REQUIRED")
        prepared = null
        val instance = try {
            XrayCore.create(
                configJson = config.toString(),
                vpnService = session.service,
                tunFileDescriptor = XrayTunFileDescriptor(session.tunFd),
                tunRuntimeProfile = XrayTunRuntimeProfile.Mobile,
                dnsBootstrapMode = XrayDnsBootstrapMode.StaticOnly,
                geodataSearchDirectory = session.assets,
                tunMtu = 1500
            )
        } catch (failure: LinkageError) {
            throw CoreFailure("RUST_SDK_LINKAGE")
        } catch (failure: Exception) {
            throw CoreFailure(classify("CREATE", failure))
        }
        try {
            instance.start()
            core = instance
        } catch (failure: LinkageError) {
            runCatching { instance.close() }
            throw CoreFailure("RUST_SDK_LINKAGE")
        } catch (failure: Exception) {
            runCatching { instance.close() }
            throw CoreFailure(classify("START", failure))
        }
    }

    // Raw native messages can include UUIDs, endpoints or credentials.
    // Only emit fixed categories, never exception text.
    private fun classify(stage: String, failure: Exception): String {
        val message = generateSequence<Throwable>(failure) { it.cause }.take(8)
            .joinToString(" ") { it.message.orEmpty() }.lowercase(java.util.Locale.ROOT)
        val category = when {
            "unknown field" in message || "unsupported" in message -> "UNSUPPORTED"
            "geosite" in message || "geoip" in message || "geodata" in message -> "GEODATA"
            "observatory" in message || "balancer" in message -> "BALANCER"
            "dns" in message -> "DNS"
            "certificate" in message || "tls" in message -> "TLS"
            "tun" in message || "descriptor" in message -> "TUN"
            failure is IllegalArgumentException -> "INVALID_CONFIG"
            else -> "FAILED"
        }
        return "RUST_${stage}_$category"
    }

    @Synchronized override fun running(): Boolean {
        val stats = core?.stats() ?: return false
        return stats.tunFdReadLoopExits == 0L && stats.tunFdWriteLoopExits == 0L
    }

    @Synchronized override fun traffic(): EngineTraffic? =
        core?.stats()?.let { EngineTraffic(it.uplinkBytes.coerceAtLeast(0), it.downlinkBytes.coerceAtLeast(0)) }

    @Synchronized override fun stop(service: VpnService) {
        prepared = null
        val instance = core ?: return
        core = null
        try { instance.stop() } finally { instance.close() }
    }
}
