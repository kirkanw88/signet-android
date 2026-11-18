package com.signet.client.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Opt-in, bounded RAM only. Callers pass event codes, never request/configuration contents. */
object ConnectionLog {
    private val lines=MutableStateFlow<List<String>>(emptyList())
    val entries=lines.asStateFlow()
    @Volatile var enabled=false
        private set
    private val clock=DateTimeFormatter.ofPattern("HH:mm:ss")
    @Synchronized fun configure(value:Boolean){enabled=value;if(!value)lines.value=emptyList()else if(lines.value.isEmpty())add("APP","LOGGING_ENABLED")}
    @Synchronized fun clear(){lines.value=emptyList()}
    @Synchronized fun add(area:String,event:String,code:String="") {
        if(!enabled)return
        // Restrict to bounded machine codes; URLs, UUIDs, keys and arbitrary error messages cannot be appended.
        val safe=listOf(area,event,code).joinToString(" ").takeIf{it.length<=160&&it.matches(Regex("[A-Za-z0-9_ .:/=-]*"))}?:return
        if(safe.contains(Regex("(?i)https?://|[a-f0-9]{8}-[a-f0-9]{4}-")))return
        lines.value=(lines.value+"${LocalTime.now().format(clock)}  $safe").takeLast(200)
    }
    fun export():String="Signet connection log\n"+lines.value.joinToString("\n")
}
