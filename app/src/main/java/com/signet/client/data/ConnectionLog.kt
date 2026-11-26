package com.signet.client.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Always enabled, bounded private storage. Callers pass event codes, never request/configuration contents. */
object ConnectionLog {
    private val lines=MutableStateFlow<List<String>>(emptyList())
    val entries=lines.asStateFlow()
    @Volatile var enabled=true
        private set
    private val clock=DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private var storage: android.util.AtomicFile? = null
    @Synchronized fun initialize(context: android.content.Context) {
        if (storage != null) return
        val file = android.util.AtomicFile(java.io.File(context.noBackupFilesDir, "connection-log.txt"))
        storage = file
        val previous = runCatching { file.openRead().bufferedReader().use { it.readLines().takeLast(2000) } }.getOrDefault(emptyList())
        lines.value = (previous + lines.value).takeLast(2000)
        persist()
    }
    private fun persist() {
        val file = storage ?: return
        runCatching {
            val stream = file.startWrite()
            try { stream.write(lines.value.joinToString("\n").toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
            catch (e: Exception) { file.failWrite(stream); throw e }
        }
    }
    @Synchronized fun configure(value:Boolean){enabled=true;if(lines.value.isEmpty())add("APP","LOGGING_ENABLED")}
    @Synchronized fun clear(){lines.value=emptyList();persist()}
    @Synchronized fun add(area:String,event:String,code:String="") {
        if(!enabled)return
        // Restrict to bounded machine codes; URLs, UUIDs, keys and arbitrary error messages cannot be appended.
        val safe=listOf(area,event,code).joinToString(" ").takeIf{it.length<=160&&it.matches(Regex("[A-Za-z0-9_ .:/=-]*"))}?:return
        if(safe.contains(Regex("(?i)https?://|[a-f0-9]{8}-[a-f0-9]{4}-")))return
        lines.value=(lines.value+"${LocalTime.now().format(clock)}  $safe").takeLast(2000)
        persist()
    }
    // Monotonic durations, fixed labels only. No profile identifiers or native messages.
    fun elapsed(area:String,event:String,started:Long) {
        add(area,event,"ms="+(android.os.SystemClock.elapsedRealtime()-started).coerceAtLeast(0))
    }
    fun export():String="Signet connection log\n"+lines.value.joinToString("\n")
}
