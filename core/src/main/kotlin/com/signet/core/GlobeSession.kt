package com.signet.core

class GlobeSession {
    private var active=false
    fun transition(attached:Boolean,resumed:Boolean):Boolean? {
        val next=attached&&resumed
        if(next==active)return null
        active=next;return next
    }
}
