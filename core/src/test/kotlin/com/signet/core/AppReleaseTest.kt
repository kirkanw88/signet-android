package com.signet.core
import org.json.*
import org.junit.Test
import kotlin.test.*

class AppReleaseTest {
    @Test fun editionsNeverSelectEachOthersApkOrCache(){
        val j=release();val assets=j.getJSONArray("assets")
        val external=JSONObject(assets.getJSONObject(1).toString())
        external.put("name","signet-external-0.3.0-arm64-v8a.apk").put("browser_download_url","https://github.com/${AppRelease.REPOSITORY}/releases/download/v0.3.0/signet-external-0.3.0-arm64-v8a.apk")
        assets.put(external)
        val regular=AppRelease.parse(j.toString(),listOf("arm64-v8a"))
        val other=AppRelease.parse(j.toString(),listOf("arm64-v8a"),true)
        assertTrue(other.apk!!.contains("signet-external-"));assertFalse(regular.apk!!.contains("signet-external-"))
        assertEquals(other,AppRelease.cached(other.json(),true))
        assertFails{AppRelease.cached(other.json())};assertFails{AppRelease.cached(regular.json(),true)}
        assertNull(AppRelease.parse(release().toString(),listOf("arm64-v8a"),true).apk)
        j.put("assets",JSONArray().put(external));assertNull(AppRelease.parse(j.toString(),listOf("arm64-v8a")).apk)
    }
    private fun release():JSONObject=JSONObject().put("tag_name","v0.3.0").put("html_url","https://github.com/${AppRelease.REPOSITORY}/releases/tag/v0.3.0").put("assets",JSONArray(listOf("armeabi-v7a","arm64-v8a","x86_64").map{abi->JSONObject().put("name","signet-$abi.apk").put("size",41000000).put("browser_download_url","https://github.com/${AppRelease.REPOSITORY}/releases/download/v0.3.0/signet-$abi.apk")}))
    @Test fun semanticVersionsDoNotCompareLexically(){assertTrue(ReleaseVersion.parse("0.10.0")>ReleaseVersion.parse("0.2.9"));assertEquals(ReleaseVersion.parse("v0.2.0"),ReleaseVersion.parse("0.2.0"));assertFails{ReleaseVersion.parse("0.3.0-beta")}}
    @Test fun picksDeviceAbiWithoutDownloadingOtherApks(){val r=AppRelease.parse(release().toString(),listOf("arm64-v8a","armeabi-v7a"));assertTrue(r.apk!!.endsWith("arm64-v8a.apk"));assertEquals(r,AppRelease.cached(r.json()))}
    @Test fun noIncompatibleFallback(){assertNull(AppRelease.parse(release().toString(),listOf("unknown-cpu")).apk)}
    @Test fun rejectsExternalOrUnsafeUpdateLocations(){assertFails{AppRelease.parse(release().put("html_url","https://evil.example/release").toString(),listOf("arm64-v8a"))};val j=release();j.getJSONArray("assets").getJSONObject(1).put("browser_download_url","http://github.com/${AppRelease.REPOSITORY}/releases/download/v0.3.0/signet-arm64-v8a.apk");assertNull(AppRelease.parse(j.toString(),listOf("arm64-v8a")).apk)}
    @Test fun draftAndPrereleaseAreNotPublicUpdates(){assertFails{AppRelease.parse(release().put("draft",true).toString(),listOf("arm64-v8a"))};assertFails{AppRelease.parse(release().put("prerelease",true).toString(),listOf("arm64-v8a"))}}
    @Test fun assetsFromADifferentReleaseCannotBeUsed(){val j=release();j.getJSONArray("assets").getJSONObject(1).put("browser_download_url","https://github.com/${AppRelease.REPOSITORY}/releases/download/v0.2.0/signet-arm64-v8a.apk");assertNull(AppRelease.parse(j.toString(),listOf("arm64-v8a")).apk)}
}
