package com.signet.core
import org.junit.Test
import kotlin.test.*
import kotlin.math.*
class GlobeGeometryTest {
    @Test fun capitalFocusZoomStaysOutsideSphere(){assertEquals(2.6f,GlobeGeometry.zoom(80.0));assertTrue(GlobeGeometry.zoom(7.0)>GlobeGeometry.zoom(25.0));assertTrue(3.7f/GlobeGeometry.zoom(0.0)>1.125f)}
    @Test fun sphericalDistanceHandlesDateline(){assertTrue(GlobeGeometry.angularRadius(MapPoint(0.0,179.0),sequenceOf(MapPoint(0.0,-179.0)))<2.01)}
    @Test fun fillIsDensifiedOnTheSphere(){val out=mutableListOf<Float>();GlobeGeometry.appendTriangle(MapPoint(0.0,0.0),MapPoint(0.0,20.0),MapPoint(20.0,0.0),out);assertTrue(out.size>9);assertEquals(0,out.size%9);out.chunked(3).forEach{assertTrue(abs(sqrt(it.sumOf{x->x.toDouble()*x})-1.006)<0.0001)}}
}
