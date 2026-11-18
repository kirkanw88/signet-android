package com.signet.core
import org.json.*
import org.junit.Test
import kotlin.test.*

class AppReleaseTest {
    private fun release():JSONObject=JSONObject().put("tag_name","v0.3.0").put("html_url","https://github.com/${AppRelease.REPOSITORY}/releases/tag/v0.3.0").put("assets",JSONArray(listOf("armeabi-v7a","arm64-v8a","x86_64").map{abi->JSONObject().put("name","signet-$abi.apk").put("size",41000000).put("browser_download_url","https://github.com/${AppRelease.REPOSITORY}/releases/download/v0.3.0/signet-$abi.apk")}))
    @Test fun semanticVersionsDoNotCompareLexically(){assertTrue(ReleaseVersion.parse("0.10.0")>ReleaseVersion.parse("0.2.9"));assertEquals(ReleaseVersion.parse("v0.2.0"),ReleaseVersion.parse("0.2.0"));assertFails{ReleaseVersion.parse("0.3.0-beta")}}
    @Test fun picksDeviceAbiWithoutDownloadingOtherApks(){val r=AppRelease.parse(release().toString(),listOf("arm64-v8a","armeabi-v7a"));assertTrue(r.apk!!.endsWith("arm64-v8a.apk"));assertEquals(r,AppRelease.cached(r.json()))}
    @Test fun noIncompatibleFallback(){assertNull(AppRelease.parse(release().toString(),listOf("unknown-cpu")).apk)}
    @Test fun rejectsExternalOrUnsafeUpdateLocations(){assertFails{AppRelease.parse(release().put("html_url","https://evil.example/release").toString(),listOf("arm64-v8a"))};val j=release();j.getJSONArray("assets").getJSONObject(1).put("browser_download_url","http://github.com/${AppRelease.REPOSITORY}/releases/download/v0.3.0/signet-arm64-v8a.apk");assertNull(AppRelease.parse(j.toString(),listOf("arm64-v8a")).apk)}
    @Test fun draftAndPrereleaseAreNotPublicUpdates(){assertFails{AppRelease.parse(release().put("draft",true).toString(),listOf("arm64-v8a"))};assertFails{AppRelease.parse(release().put("prerelease",true).toString(),listOf("arm64-v8a"))}}
}
