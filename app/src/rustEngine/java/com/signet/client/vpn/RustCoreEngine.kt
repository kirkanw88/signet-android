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
        try {
            val instance = XrayCore.create(
                configJson = config.toString(),
                vpnService = session.service,
                tunFileDescriptor = XrayTunFileDescriptor(session.tunFd),
                tunRuntimeProfile = XrayTunRuntimeProfile.Mobile,
                dnsBootstrapMode = XrayDnsBootstrapMode.StaticOnly,
                geodataSearchDirectory = session.assets,
                tunMtu = 1500
            )
            try {
                instance.start()
                core = instance
            } catch (failure: Throwable) {
                runCatching { instance.close() }
                throw failure
            }
        } catch (failure: CoreFailure) { throw failure }
        catch (failure: Exception) { throw CoreFailure("RUST_NATIVE_CONFIG_OR_START") }
        catch (failure: LinkageError) {
            throw CoreFailure("RUST_SDK_LINKAGE")
        }
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
