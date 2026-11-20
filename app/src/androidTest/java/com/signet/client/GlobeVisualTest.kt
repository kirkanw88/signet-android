package com.signet.client

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.signet.client.data.Repository
import com.signet.client.vpn.VpnPhase
import com.signet.client.vpn.VpnState
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.koin.java.KoinJavaComponent.get
import java.io.File
import java.util.UUID
import android.view.View
import android.view.ViewGroup
import com.signet.client.graphics.GlobeView

/** Synthetic metadata drives real GLES country frames; never connects to a server. */
class GlobeVisualTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun selectedCountryFrames()=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("localVpn")=="true")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val repository=get<Repository>(Repository::class.java);repository.awaitReady()
        assertEquals(VpnPhase.DISCONNECTED,VpnState.status.value.phase)
        val before=repository.state.value;val previousIds=before.subscriptions.map{it.id}.toSet()
        val run=UUID.randomUUID().toString()
        val places=listOf(Triple("RU","Moscow",55.75 to 37.62),Triple("DE","Berlin",52.52 to 13.40),Triple("FI","Helsinki",60.17 to 24.94),Triple("JP","Tokyo",35.68 to 139.69),Triple("TR","Ankara",39.93 to 32.86))
        try {
            repository.settings(before.settings.copy(reduceMotion=true,automaticUpdates=false))
            val configs=JSONArray()
            places.forEach{(code,city,coordinates)->
                val endpoint=JSONObject().put("address","127.0.0.1").put("port",10888)
                val outbound=JSONObject().put("tag","proxy").put("protocol","socks").put("settings",JSONObject().put("servers",JSONArray().put(endpoint)))
                configs.put(JSONObject()
                .put("remarks","VISUAL-$run-$code")
                .put("signet",JSONObject().put("countryCode",code).put("city",city).put("latitude",coordinates.first).put("longitude",coordinates.second))
                .put("outbounds",JSONArray().put(outbound)))
            }
            repository.import(configs.toString())
            val sub=repository.state.value.subscriptions.single{it.id !in previousIds}
            assertEquals(5,sub.servers.size)
            compose.mainClock.advanceTimeBy(6000)
            compose.waitForIdle()
            val directory=File(instrumentation.targetContext.getExternalFilesDir(null),"country-frames").apply{mkdirs()}
            val device=UiDevice.getInstance(instrumentation)
            for((code,city,_) in places) {
                repository.select(sub.servers.single{it.countryCode==code}.id)
                compose.waitUntil(10_000){compose.onAllNodesWithText("VISUAL-$run-$code").fetchSemanticsNodes().isNotEmpty()}
                compose.waitUntil(30_000){
                    var ready=false
                    compose.activityRule.scenario.onActivity{activity->findGlobe(activity.window.decorView)?.globe?.let{ready=it.frameReady&&it.renderedCountry==code}}
                    ready
                }
                // Allow the actual GL surface and camera interpolation to finish.
                Thread.sleep(3000)
                val globeBounds=compose.onNodeWithTag("home-globe").fetchSemanticsNode().boundsInRoot
                val connectBounds=compose.onNodeWithTag("home-connect").fetchSemanticsNode().boundsInRoot
                assertTrue("Dedicated globe area must be larger than the previous 140dp",globeBounds.height>200*compose.activity.resources.displayMetrics.density)
                assertFalse("Controls must not cover the globe",globeBounds.overlaps(connectBounds))
                compose.onNodeWithText("Локации").assertDoesNotExist()
                val screenshot=File(directory,"$code.png")
                assertTrue(device.takeScreenshot(screenshot));assertTrue(screenshot.length()>10_000)
                assertEquals(VpnPhase.DISCONNECTED,VpnState.status.value.phase)
            }
            compose.onNodeWithText("VISUAL-$run-TR").performClick()
            compose.onNodeWithTag("server-sheet").assertExists()
            compose.onNodeWithContentDescription("Ping всех подписок").assertExists()
            compose.onNodeWithContentDescription("Обновить все подписки").assertExists()
            compose.onNodeWithText("Ping").assertExists()
            compose.onNodeWithText("VISUAL-$run-DE").performScrollTo().performClick()
            compose.waitUntil(10_000){repository.state.value.selected==sub.servers.single{it.countryCode=="DE"}.id&&compose.onAllNodesWithTag("server-sheet").fetchSemanticsNodes().isEmpty()}
        }finally {
            repository.state.value.subscriptions.filter{it.id !in previousIds}.forEach{repository.remove(it.id)}
            before.selected?.let{repository.select(it)}
            repository.settings(before.settings)
        }
    }
    /**
     * Injects real Android motion events into the EGL view. Unlike GlobeDrag unit tests,
     * this detects accidental gesture interception by the Compose scroll parent.
     */
    @Test fun firstVerticalAndHorizontalSwipesRotateTheRenderedEarth() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        Assume.assumeTrue(instrumentation.targetContext.packageName.endsWith(".external"))
        var globeView:GlobeView?=null
        compose.activityRule.scenario.onActivity { activity->
            globeView=findGlobe(activity.window.decorView)
        }
        val globe=requireNotNull(globeView) { "Home must contain a live OpenGL globe" }
        compose.waitUntil(20_000){globe.globe.frameReady}
        val bounds=compose.onNodeWithTag("home-globe").fetchSemanticsNode().boundsInRoot
        assertTrue("Globe has space for touch",bounds.width>120f && bounds.height>180f)
        val pitchBefore=globe.globe.renderedPitchDegrees
        val moveBefore=globe.rotationMoveCount
        // Compose sends an actual MotionEvent sequence to the tagged interactive globe
        // rather than tapping screen coordinates that may belong to a system overlay.
        compose.onNodeWithTag("home-globe").performTouchInput { swipeUp(durationMillis=450) }
        compose.waitUntil(10_000) {
            kotlin.math.abs(globe.globe.renderedPitchDegrees-pitchBefore)>3f
        }
        val pitchAfter=globe.globe.renderedPitchDegrees
        assertTrue("Vertical drag must be received by the globe",
            globe.rotationMoveCount>moveBefore)
        assertTrue("Rendered pitch must change after first vertical drag; before=$pitchBefore after=$pitchAfter",
            kotlin.math.abs(pitchAfter-pitchBefore)>3f)

        val yawBefore=globe.globe.renderedYawDegrees
        compose.onNodeWithTag("home-globe").performTouchInput { swipeLeft(durationMillis=450) }
        compose.waitUntil(10_000){
            kotlin.math.abs(((globe.globe.renderedYawDegrees-yawBefore+540f)%360f)-180f)>3f
        }
        val yawAfter=globe.globe.renderedYawDegrees
        val yawDelta=kotlin.math.abs(((yawAfter-yawBefore+540f)%360f)-180f)
        assertTrue("Horizontal drag must change rendered yaw",yawDelta>3f)
    }

    /**
     * A diagonal drag must change BOTH camera axes in a single real Compose gesture.
     * In contrast to isolated gesture math, this checks actual EGL frame telemetry.
     */
    @Test fun firstDiagonalSwipeChangesBothRenderedAxes() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        Assume.assumeTrue(instrumentation.targetContext.packageName.endsWith(".external"))
        var found:GlobeView?=null
        compose.activityRule.scenario.onActivity { activity ->
            found=findGlobe(activity.window.decorView)
        }
        val globe=requireNotNull(found) { "Missing Android OpenGL globe" }
        compose.waitUntil(20_000){globe.globe.frameReady}
        val pitchBefore=globe.globe.renderedPitchDegrees
        val yawBefore=globe.globe.renderedYawDegrees
        val movesBefore=globe.rotationMoveCount
        compose.onNodeWithTag("home-globe").performTouchInput {
            swipe(
                start=androidx.compose.ui.geometry.Offset(size.width*.7f,size.height*.7f),
                end=androidx.compose.ui.geometry.Offset(size.width*.3f,size.height*.3f),
                durationMillis=450
            )
        }
        fun yawDifference():Float = kotlin.math.abs(((globe.globe.renderedYawDegrees-yawBefore+540f)%360f)-180f)
        compose.waitUntil(10_000) {
            kotlin.math.abs(globe.globe.renderedPitchDegrees-pitchBefore)>3f && yawDifference()>3f
        }
        assertTrue("Diagonal gesture must reach GLES",globe.rotationMoveCount>movesBefore)
        assertTrue("Diagonal swipe must change rendered pitch",kotlin.math.abs(globe.globe.renderedPitchDegrees-pitchBefore)>3f)
        assertTrue("Diagonal swipe must change rendered yaw",yawDifference()>3f)
    }

    private fun findGlobe(view:View):GlobeView? {
        if(view is GlobeView)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)findGlobe(view.getChildAt(i))?.let{return it}
        return null
    }
}
