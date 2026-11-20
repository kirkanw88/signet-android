package com.signet.core

import kotlin.math.exp

/**
 * Frame-rate-independent camera smoothing for the Universal globe.
 * Each coefficient is clamped to avoid overshoot on long / paused frames.
 */
object GlobeMotion {
    fun easing(deltaSeconds: Float, reducedMotion: Boolean): Float =
        if (reducedMotion) 1f else smooth(deltaSeconds, 3.8f)

    fun zoomEasing(deltaSeconds: Float, reducedMotion: Boolean): Float =
        if (reducedMotion) 1f else smooth(deltaSeconds, 2.9f)

    private fun smooth(deltaSeconds: Float, rate: Float): Float {
        if (!deltaSeconds.isFinite() || deltaSeconds <= 0f) return 0f
        return (1f - exp(-rate * deltaSeconds.coerceAtMost(1f))).coerceIn(0f, 1f)
    }
}
