package com.signet.core
import org.junit.Test
import kotlin.test.*

class ProbeErrorsTest {
    @Test fun nativeErrorsOnlyProduceSafeCodes(){
        val raw="failed to build ping batch config: invalid secret.example token=private"
        assertEquals("CONFIG_ERROR",ProbeErrors.native(raw));assertFalse(ProbeErrors.native(raw).contains("private"))
        assertEquals("DNS",ProbeErrors.native("lookup secret.example: no such host"))
        assertEquals("TIMEOUT",ProbeErrors.native("context deadline exceeded"))
    }
}
