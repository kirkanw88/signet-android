package com.signet.core

import org.json.JSONObject
import java.net.URI

data class ReleaseVersion(val major:Int,val minor:Int,val patch:Int):Comparable<ReleaseVersion> {
    override fun compareTo(other:ReleaseVersion):Int=compareValuesBy(this,other,{it.major},{it.minor},{it.patch})
    override fun toString()="$major.$minor.$patch"
    companion object {fun parse(raw:String):ReleaseVersion {val m=Regex("^v?(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})$").matchEntire(raw)?:error("Неподдерживаемая версия");return ReleaseVersion(m.groupValues[1].toInt(),m.groupValues[2].toInt(),m.groupValues[3].toInt())}}
}
data class AppRelease(val version:ReleaseVersion,val page:String,val apk:String?,val sha256:String?) {
    fun json():String=JSONObject().put("version",version.toString()).put("page",page).put("apk",apk).put("sha256",sha256).toString()
    companion object {
        const val REPOSITORY="kirkanw88/signet-android"
        const val ENDPOINT="https://api.github.com/repos/$REPOSITORY/releases/latest"
        private fun trusted(url:String,kind:String):Boolean=runCatching {val u=URI(url);u.scheme=="https"&&u.host=="github.com"&&u.rawUserInfo==null&&u.rawQuery==null&&u.rawFragment==null&&u.port==-1&&u.normalize().path.startsWith("/$REPOSITORY/releases/$kind/")}.getOrDefault(false)
        fun parse(raw:String,abis:List<String>):AppRelease {
            require(raw.toByteArray().size<=512*1024)
            val j=JSONObject(raw);require(!j.optBoolean("draft")&&!j.optBoolean("prerelease"))
            val version=ReleaseVersion.parse(j.getString("tag_name"));val page=j.getString("html_url");require(trusted(page,"tag"))
            val a=j.getJSONArray("assets");require(a.length()<=200)
            val assets=(0 until a.length()).map{a.getJSONObject(it)}.filter { asset->
                val name=asset.optString("name");val url=asset.optString("browser_download_url")
                name.matches(Regex("[A-Za-z0-9_.-]+\\.apk"))&&trusted(url,"download")&&url.endsWith("/$name")&&asset.optLong("size") in 1..250*1024*1024
            }
            val chosen=abis.firstNotNullOfOrNull{abi->assets.firstOrNull{it.getString("name").contains(abi)}}?:assets.firstOrNull{it.getString("name").contains("universal",true)}
            val digest=chosen?.optString("digest")?.takeIf{it.matches(Regex("sha256:[a-fA-F0-9]{64}"))}?.substring(7)?.lowercase()
            return AppRelease(version,page,chosen?.getString("browser_download_url"),digest)
        }
        fun cached(raw:String):AppRelease {
            require(raw.length<=8192);val j=JSONObject(raw);val page=j.getString("page");require(trusted(page,"tag"))
            val apk=j.optString("apk").takeIf{it.isNotBlank()&&it!="null"};require(apk==null||trusted(apk,"download"))
            return AppRelease(ReleaseVersion.parse(j.getString("version")),page,apk,j.optString("sha256").takeIf{it.matches(Regex("[a-f0-9]{64}"))})
        }
    }
}
