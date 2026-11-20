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
            // Per-subscription actions replace the removed global toolbar.
            compose.onNodeWithContentDescription("Ping подписки").assertExists()
            compose.onNodeWithContentDescription("Обновить подписку").assertExists()
            compose.onNodeWithContentDescription("Ping всех подписок").assertDoesNotExist()
            compose.onNodeWithContentDescription("Обновить все подписки").assertDoesNotExist()
            compose.onNodeWithText("VISUAL-$run-DE").performScrollTo().performClick()
            compose.waitUntil(10_000){repository.state.value.selected==sub.servers.single{it.countryCode=="DE"}.id&&compose.onAllNodesWithTag("server-sheet").fetchSemanticsNodes().isEmpty()}
        }finally {
            repository.state.value.subscriptions.filter{it.id !in previousIds}.forEach{repository.remove(it.id)}
            before.selected?.let{repository.select(it)}
            repository.settings(before.settings)
        }
    }
    /** Horizontal gestures rotate yaw; vertical gestures never change pitch. */
    @Test fun horizontalSwipeRotatesYawButVerticalSwipeDoesNotPitch() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        Assume.assumeTrue(instrumentation.targetContext.packageName.endsWith(".external"))
        var found:GlobeView?=null
        compose.activityRule.scenario.onActivity { activity ->
            found=findGlobe(activity.window.decorView)
        }
        val globe=requireNotNull(found) { "Missing Android OpenGL globe" }
        compose.waitUntil(20_000){globe.globe.frameReady}
        val pitchBefore=globe.globe.renderedPitchDegrees
        compose.onNodeWithTag("home-globe").performTouchInput { swipeUp(durationMillis=450) }
        compose.waitForIdle()
        assertTrue("Vertical swipe must not tilt the Earth",
            kotlin.math.abs(globe.globe.renderedPitchDegrees-pitchBefore)<1f)

        val yawBefore=globe.globe.renderedYawDegrees
        val movesBefore=globe.rotationMoveCount
        compose.onNodeWithTag("home-globe").performTouchInput { swipeLeft(durationMillis=450) }
        compose.waitUntil(10_000) {
            kotlin.math.abs(((globe.globe.renderedYawDegrees-yawBefore+540f)%360f)-180f)>3f
        }
        assertTrue("Horizontal swipe must reach OpenGL",globe.rotationMoveCount>movesBefore)
        assertTrue("Horizontal swipe must not tilt the Earth",
            kotlin.math.abs(globe.globe.renderedPitchDegrees-pitchBefore)<1f)
    }

    /** Diagonal swipes with dominant vertical movement should never rotate the globe. */
    @Test fun verticalDominantDiagonalSwipeDoesNotRotateEarth() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        Assume.assumeTrue(instrumentation.targetContext.packageName.endsWith(".external"))
        var found:GlobeView?=null
        compose.activityRule.scenario.onActivity { activity ->
            found=findGlobe(activity.window.decorView)
        }
        val globe=requireNotNull(found) { "Missing Android OpenGL globe" }
        compose.waitUntil(20_000){globe.globe.frameReady}
        val pitchBefore=globe.globe.renderedPitchDegrees
        val movesBefore=globe.rotationMoveCount
        compose.onNodeWithTag("home-globe").performTouchInput {
            swipe(
                start=androidx.compose.ui.geometry.Offset(bottomRight.x*.55f,bottomRight.y*.85f),
                end=androidx.compose.ui.geometry.Offset(bottomRight.x*.35f,bottomRight.y*.15f),
                durationMillis=450
            )
        }
        compose.waitForIdle()
        assertTrue("Vertical-dominant diagonal must not trigger rotation",globe.rotationMoveCount==movesBefore)
        assertTrue("Diagonal swipe must not tilt the Earth",
            kotlin.math.abs(globe.globe.renderedPitchDegrees-pitchBefore)<1f)
    }

    private fun findGlobe(view:View):GlobeView? {
        if(view is GlobeView)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)findGlobe(view.getChildAt(i))?.let{return it}
        return null
    }
}
