package com.signet.client.vpn

import android.content.Context
import com.signet.core.VerifiedAssets
import java.io.File

object GeoAssets {
    private var ready:File?=null
    @Synchronized fun prepare(context:Context):File {
        ready?.let{return it}
        return VerifiedAssets.prepare(File(context.filesDir,"geo"),listOf("geoip.dat","geosite.dat")){context.assets.open(it)}.also{ready=it}
    }
}
