package com.signet.core

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.*

/** NOAA approximate solar position, UTC; sufficient for the visual day/night terminator. */
object SolarPosition {
    fun direction(epochMillis:Long):FloatArray {
        val time=Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC)
        val hour=time.hour+time.minute/60.0+time.second/3600.0
        val gamma=2*PI/time.toLocalDate().lengthOfYear()*(time.dayOfYear-1+(hour-12)/24.0)
        val equation=229.18*(.000075+.001868*cos(gamma)-.032077*sin(gamma)-.014615*cos(2*gamma)-.040849*sin(2*gamma))
        val declination=.006918-.399912*cos(gamma)+.070257*sin(gamma)-.006758*cos(2*gamma)+.000907*sin(2*gamma)-.002697*cos(3*gamma)+.00148*sin(3*gamma)
        val longitude=Geography.wrap((180-(hour*60+equation)/4).toFloat()).toDouble()
        return Geography.point(Math.toDegrees(declination),longitude)
    }
}
