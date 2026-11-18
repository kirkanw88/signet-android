package com.signet.client.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.signet.core.SignetLinks

/** Standard Custom Tabs protocol: retain browser SSO and server-rendered account/support UI.
 * No WebView password interception, JS bridge, copied cookies, admin token or invented mobile API.
 */
fun openCabinet(context:Context,support:Boolean=false,onError:()->Unit={}) {
    openSignetPanel(context,SignetLinks.CABINET+if(support)"/cabinet#help"else"/cabinet",onError)
}

fun openSignetPanel(context:Context,url:String,onError:()->Unit={}) {
    val uri=Uri.parse(url)
    if(uri.scheme!="https"||uri.host!="signet-cab.com"||uri.userInfo!=null){onError();return}
    val intent=Intent(Intent.ACTION_VIEW,uri).apply{
        addCategory(Intent.CATEGORY_BROWSABLE)
        putExtras(Bundle().apply{putBinder("android.support.customtabs.extra.SESSION",null)})
        putExtra("android.support.customtabs.extra.TOOLBAR_COLOR",android.graphics.Color.rgb(8,17,29))
        putExtra("android.support.customtabs.extra.TITLE_VISIBILITY",1)
        putExtra("android.support.customtabs.extra.SHARE_MENU_ITEM",false)
    }
    val browsers=context.packageManager.queryIntentServices(Intent("android.support.customtabs.action.CustomTabsService"),0).map{it.serviceInfo.packageName}
    val preferred=context.packageManager.resolveActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://signet-cab.com")),0)?.activityInfo?.packageName
    val provider=preferred?.takeIf{it in browsers}?:browsers.firstOrNull()
    if(provider==null){onError();return}
    intent.setPackage(provider)
    runCatching{context.startActivity(intent)}.onFailure{onError()}
}
