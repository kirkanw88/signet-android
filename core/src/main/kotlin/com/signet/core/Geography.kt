package com.signet.core

import kotlin.math.*

object Geography {
    fun point(lat: Double, lon: Double, radius: Double = 1.0): FloatArray {
        require(lat in -90.0..90.0 && lon in -180.0..180.0)
        val p = Math.toRadians(lat); val l = Math.toRadians(lon)
        return floatArrayOf((radius * cos(p) * sin(l)).toFloat(), (radius * sin(p)).toFloat(), (radius * cos(p) * cos(l)).toFloat())
    }
    fun wrap(degrees: Float): Float = ((degrees + 180f) % 360f + 360f) % 360f - 180f
    fun approach(current: Float, target: Float, factor: Float) = current + wrap(target - current) * factor
}
