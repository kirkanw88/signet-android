package com.signet.core

object ProfileRefresh {
    fun reconcile(subscriptionId:String,previous:List<Server>,incoming:List<Server>):List<Server> {
        val autoBefore=previous.filter { it.isAuto };val autoAfter=incoming.filter { it.isAuto }
        val keys=incoming.groupingBy { it.profileKey ?: it.id }.eachCount()
        return incoming.mapIndexed { index,p ->
            val key=p.profileKey ?: p.id
            val generated=Profiles.id(subscriptionId+key+if(keys[key]!!>1)"#$index" else "")
            val old=previous.firstOrNull { it.id==generated }
                ?: p.profileKey?.let { stable->previous.singleOrNull { it.profileKey==stable } }
                ?: if(p.isAuto && autoBefore.size==1 && autoAfter.size==1)autoBefore.single() else null
            p.copy(id=old?.id?:generated,favorite=old?.favorite?:false,successes=old?.successes?:0)
        }
    }
    fun selection(selected:String?,available:List<Server>,initialImport:Boolean):String? =
        selected?.takeIf { id->available.any { it.id==id } } ?: if(selected==null && initialImport)available.firstOrNull()?.id else null
}
