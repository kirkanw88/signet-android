package com.signet.core
import org.junit.Test
import kotlin.test.*
class CapitalTilesTest {
    @Test fun capitalsHaveCenteredBoundedFootprint(){
        for((lat,lon) in listOf(56.9496 to 24.1052,59.437 to 24.7536,35.676 to 139.65,-33.925 to 18.424)){
            val p=CapitalTiles.plan(lat,lon)
            assertEquals(9,p.tiles.distinct().size)
            assertTrue(p.centerX in 1.0..2.0&&p.centerY in 1.0..2.0)
            assertTrue(p.tiles.all{it.x in 0 until (1 shl p.zoom)&&it.y in 0 until (1 shl p.zoom)})
        }
    }
    @Test fun datelineAndPolarEdgesDoNotFetchInvalidTiles(){
        for(lat in listOf(-85.05112878,85.05112878))for(lon in listOf(-180.0,180.0)){
            val p=CapitalTiles.plan(lat,lon)
            assertTrue(p.tiles.all{it.x in 0 until (1 shl p.zoom)&&it.y in 0 until (1 shl p.zoom)})
        }
        assertFailsWith<IllegalArgumentException>{CapitalTiles.plan(Double.NaN,20.0)}
        assertFailsWith<IllegalArgumentException>{CapitalTiles.plan(86.0,20.0)}
    }
}
