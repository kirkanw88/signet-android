package com.signet.core
import org.junit.Test
import kotlin.test.*

class HomeMeasurementsTest {
    @Test fun controlsIncludingTrafficAlwaysFitBeforePlanet(){
        for(viewport in listOf(320,480,640,900))for(controls in listOf(180,260,380)){
            val globe=HomeMeasurements.globeHeight(viewport,36,controls)
            assertEquals(maxOf(viewport,36+controls),36+controls+globe)
            assertTrue(globe>=0)
        }
    }
    @Test fun speedUsesCounterDeltasAndActualElapsedTime(){
        val meter=TrafficMeter();assertEquals(TrafficRates(),meter.sample(1000,2000,1000))
        assertEquals(TrafficRates(1000,2000),meter.sample(4000,8000,4000))
        assertEquals(TrafficRates(),meter.sample(4000,8000,7000))
    }
    @Test fun reconnectAndClockResetDoNotCreateNegativeOrHugeRates(){
        val meter=TrafficMeter();meter.sample(100_000,200_000,1000)
        assertEquals(TrafficRates(),meter.sample(10,20,4000))
        assertEquals(TrafficRates(),meter.sample(20,30,3000))
    }
}
