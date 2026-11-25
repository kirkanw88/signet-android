package com.signet.client.vpn

internal object EngineFactory {
    const val rustAvailable = true
    fun create(preferRust: Boolean): CoreEngine =
        if (preferRust) RustCoreEngine() else LibXrayEngine()
}
