package com.signet.core

object UniversalDefaults {
    const val GRAPHICS="High"
    const val CONNECTION_URL="https://www.gstatic.com/generate_204"
    const val SUBSCRIPTION_INTERVAL_MS=60*60_000L
    fun subscriptionDue(updated:Long,now:Long)=updated<=0||now<updated||now-updated>=SUBSCRIPTION_INTERVAL_MS
}
