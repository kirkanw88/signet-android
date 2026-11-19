package com.signet.core

object SubscriptionGroups {
    fun toggle(collapsed:Set<String>,id:String,active:Set<String>):Set<String> {
        require(id in active)
        val current=collapsed.intersect(active)
        return if(id in current)current-id else current+id
    }
    fun <T> pinnedFirst(items:List<T>,pinned:Set<String>,id:(T)->String):List<T> =
        items.filter{id(it) in pinned}+items.filter{id(it) !in pinned}
    fun <T> upsert(items:List<T>,item:T,id:(T)->String):List<T> {
        val key=id(item)
        return if(items.any{id(it)==key})items.map{if(id(it)==key)item else it}else items+item
    }
}
