package com.signet.client.vpn

import android.net.Network
import android.net.VpnService
import android.os.SystemClock
import com.signet.client.data.ConnectionLog
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
        val started = SystemClock.elapsedRealtime()
        ConnectionLog.add("RUST","PREPARE_BEGIN")
        try {
            prepared = RustRuntimeConfig.prepare(server.config, fallbackDns) { host ->
                val dnsStarted = SystemClock.elapsedRealtime()
                try { network.getAllByName(host).mapNotNull { it.hostAddress } }
                finally { ConnectionLog.elapsed("RUST","BOOTSTRAP_LOOKUP",dnsStarted) }
            }
            ConnectionLog.elapsed("RUST","PREPARE_OK",started)
        } catch (e: Exception) {
            ConnectionLog.elapsed("RUST","PREPARE_FAILED",started)
            throw e
        }
    }

    override fun attach(service: VpnService, dns: String, network: Network) = Unit
    override fun freeMetricsPort() = 0

    @Synchronized override fun start(session: EngineSession) {
        check(core == null)
        val config = prepared ?: throw CoreFailure("RUST_PREPARE_REQUIRED")
        prepared = null
        val createStarted=SystemClock.elapsedRealtime()
        ConnectionLog.add("RUST","CREATE_BEGIN")
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
            ConnectionLog.elapsed("RUST","CREATE_FAILED",createStarted)
            throw CoreFailure("RUST_SDK_LINKAGE")
        } catch (failure: Exception) {
            ConnectionLog.elapsed("RUST","CREATE_FAILED",createStarted)
            logFailureShape("CREATE", failure)
            throw CoreFailure(classify("CREATE", failure))
        }
        ConnectionLog.elapsed("RUST","CREATE_OK",createStarted)
        val startStarted=SystemClock.elapsedRealtime()
        ConnectionLog.add("RUST","START_BEGIN")
        try {
            instance.start()
            core = instance
            ConnectionLog.elapsed("RUST","START_OK",startStarted)
        } catch (failure: LinkageError) {
            ConnectionLog.elapsed("RUST","START_FAILED",startStarted)
            runCatching { instance.close() }
            throw CoreFailure("RUST_SDK_LINKAGE")
        } catch (failure: Exception) {
            ConnectionLog.elapsed("RUST","START_FAILED",startStarted)
            runCatching { instance.close() }
            logFailureShape("START", failure)
            throw CoreFailure(classify("START", failure))
        }
    }

    private fun logFailureShape(stage: String, failure: Exception) {
        generateSequence<Throwable>(failure) { it.cause }.take(8).forEachIndexed { index, cause ->
            val type = cause.javaClass.simpleName.take(60).filter { it.isLetterOrDigit() || it == '_' }
            ConnectionLog.add("RUST", "${stage}_CAUSE", "index=$index type=$type")
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
        val running = stats.tunFdReadLoopExits == 0L && stats.tunFdWriteLoopExits == 0L
        if(!running) ConnectionLog.add("RUST","TUN_LOOP_EXIT")
        return running
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
