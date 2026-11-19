package com.signet.core

import org.junit.Test
import kotlin.test.*

class UpdatePollingTest {
    @Test fun successfulChecksExpireWhileAppIsOpenAndFailuresRetrySooner(){
        val time=1_000_000L
        assertFalse(UpdatePolling.shouldCheck(time+14*60_000,time,time))
        assertTrue(UpdatePolling.shouldCheck(time+15*60_000,time,time))
        assertFalse(UpdatePolling.shouldCheck(time+60_000,time,time-1000))
        assertTrue(UpdatePolling.shouldCheck(time+2*60_000,time,time-1000))
    }
    @Test fun firstLaunchAndClockRollbackNeverHideUpdates(){
        assertTrue(UpdatePolling.shouldCheck(1000,0,0))
        assertTrue(UpdatePolling.shouldCheck(1000,2000,2000))
    }
}
