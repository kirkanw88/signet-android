package com.signet.core

/** Separate successful freshness from a failed attempt, including clock adjustments. */
object UpdatePolling {
    fun shouldCheck(now:Long,lastAttempt:Long,lastSuccess:Long):Boolean {
        if(lastAttempt<=0||now<lastAttempt)return true
        val interval=if(lastSuccess>0&&lastSuccess>=lastAttempt)15*60_000L else 2*60_000L
        return now-lastAttempt>=interval
    }
}
