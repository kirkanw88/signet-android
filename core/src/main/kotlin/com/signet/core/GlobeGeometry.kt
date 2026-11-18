package com.signet.core

import kotlin.math.*

data class MapPoint(val lat:Double,val lon:Double)
object GlobeGeometry {
    fun angularRadius(center:MapPoint,points:Sequence<MapPoint>):Double=points.map { p->
        val a=Math.toRadians(center.lat);val b=Math.toRadians(p.lat);val delta=Math.toRadians(p.lon-center.lon)
        Math.toDegrees(acos((sin(a)*sin(b)+cos(a)*cos(b)*cos(delta)).coerceIn(-1.0,1.0)))
    }.maxOrNull()?:180.0
    fun zoom(radius:Double):Float=when {radius<=12->1.75f;radius>=35->1f;else->(1.75-(radius-12)/23*.75).toFloat()}
    /** Densify planar JTS triangles onto the sphere so their chords don't disappear below Earth. */
    fun appendTriangle(a:MapPoint,b:MapPoint,c:MapPoint,out:MutableList<Float>,depth:Int=0) {
        fun distance(p:MapPoint,q:MapPoint):Double {val x=(p.lon-q.lon)*cos(Math.toRadians((p.lat+q.lat)/2));return x*x+(p.lat-q.lat).pow(2)}
        val ab=distance(a,b);val bc=distance(b,c);val ca=distance(c,a)
        if(maxOf(ab,bc,ca)>9.0&&depth<14) {
            fun mid(p:MapPoint,q:MapPoint)=MapPoint((p.lat+q.lat)/2,(p.lon+q.lon)/2)
            when(maxOf(ab,bc,ca)) {
                ab->{val m=mid(a,b);appendTriangle(a,m,c,out,depth+1);appendTriangle(m,b,c,out,depth+1)}
                bc->{val m=mid(b,c);appendTriangle(a,b,m,out,depth+1);appendTriangle(a,m,c,out,depth+1)}
                else->{val m=mid(c,a);appendTriangle(a,b,m,out,depth+1);appendTriangle(m,b,c,out,depth+1)}
            }
        } else listOf(a,b,c).forEach{p->Geography.point(p.lat,((p.lon+180)%360+360)%360-180,1.006).forEach(out::add)}
    }
}
