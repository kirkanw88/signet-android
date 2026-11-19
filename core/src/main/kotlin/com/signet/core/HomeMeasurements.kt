package com.signet.core

object HomeMeasurements {
    fun globeHeight(viewport:Int,heading:Int,controls:Int)=(viewport-heading-controls).coerceAtLeast(0)
}
data class TrafficRates(val upload:Long=0,val download:Long=0)
class TrafficMeter {
    private var time:Long?=null;private var up=0L;private var down=0L
    fun sample(uploaded:Long,downloaded:Long,now:Long):TrafficRates {
        val before=time;val elapsed=if(before==null)0 else now-before
        val result=if(elapsed<=0||uploaded<up||downloaded<down)TrafficRates()else TrafficRates(
            ((uploaded-up).toDouble()*1000/elapsed).coerceIn(0.0,Long.MAX_VALUE.toDouble()).toLong(),
            ((downloaded-down).toDouble()*1000/elapsed).coerceIn(0.0,Long.MAX_VALUE.toDouble()).toLong())
        time=now;up=uploaded.coerceAtLeast(0);down=downloaded.coerceAtLeast(0);return result
    }
}
