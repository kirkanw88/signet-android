package com.signet.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlobeMotionTest {
    @Test fun cameraEasingAlwaysMonotonicAndBounded() {
        val short = GlobeMotion.easing(1f / 120f, false)
        val ordinary = GlobeMotion.easing(1f / 60f, false)
        val delayed = GlobeMotion.easing(0.1f, false)
        assertTrue(short in 0f..1f)
        assertTrue(short < ordinary && ordinary < delayed && delayed < 1f)
        assertEquals(0f, GlobeMotion.easing(0f, false))
        assertEquals(0f, GlobeMotion.easing(-1f, false))
        assertEquals(0f, GlobeMotion.easing(Float.NaN, false))
    }

    @Test fun zoomIsSmootherThanFocusAndNeverOvershoots() {
        val focus = GlobeMotion.easing(1f / 30f, false)
        val zoom = GlobeMotion.zoomEasing(1f / 30f, false)
        assertTrue(zoom > 0f && zoom < focus)
        assertTrue(GlobeMotion.zoomEasing(10f, false) <= 1f)
    }

    @Test fun reduceMotionSettlesCameraWithoutAnimation() {
        assertEquals(1f, GlobeMotion.easing(1f / 60f, true))
        assertEquals(1f, GlobeMotion.zoomEasing(1f / 60f, true))
    }
}
