package com.signet.client.vpn

internal object EngineFactory {
    const val rustAvailable = false
    fun create(preferRust: Boolean): CoreEngine = LibXrayEngine()
}
