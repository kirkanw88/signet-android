package com.signet.core
import java.time.Instant
import kotlin.math.*
import kotlin.test.*
import org.junit.Test
class SolarPositionTest {
    @Test fun equinoxNoonAndMidnightIlluminateOppositeLongitudes(){
        val noon=SolarPosition.direction(Instant.parse("2026-03-20T12:00:00Z").toEpochMilli())
        val midnight=SolarPosition.direction(Instant.parse("2026-03-20T00:00:00Z").toEpochMilli())
        assertTrue(noon[2]>.98);assertTrue(midnight[2]<-.98)
        assertTrue(abs(noon[1])<.03)
    }
    @Test fun seasonsAndVectorLengthAreCorrect(){
        for((date,sign) in listOf("2026-06-21T12:00:00Z" to 1,"2026-12-21T12:00:00Z" to -1)){
            val sun=SolarPosition.direction(Instant.parse(date).toEpochMilli())
            assertTrue(sun[1]*sign in .38f.. .42f)
            assertEquals(1.0,sqrt(sun.sumOf{it.toDouble()*it}),.00001)
        }
    }
}
