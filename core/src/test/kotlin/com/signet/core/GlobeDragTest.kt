package com.signet.core
import org.junit.Test
import kotlin.test.*
class GlobeDragTest {
    @Test fun diagonalNoiseDoesNotStealHorizontalSwipe(){
        val drag=GlobeDrag(8f);drag.down(0f,0f)
        assertEquals(GlobeDragAxis.PENDING,drag.move(9f,10f,true).axis)
        assertEquals(GlobeDragAxis.ROTATE,drag.move(20f,11f,true).axis)
        val next=drag.move(22f,40f,true)
        assertEquals(GlobeDragAxis.ROTATE,next.axis);assertEquals(2f,next.dx)
    }
    @Test fun verticalScrollCanBeFollowedByANewRotation(){
        val drag=GlobeDrag(8f);drag.down(1f,1f)
        assertEquals(GlobeDragAxis.SCROLL,drag.move(2f,20f,true).axis)
        drag.reset();drag.down(30f,20f)
        assertEquals(GlobeDragAxis.ROTATE,drag.move(10f,21f,true).axis)
        drag.rebase(100f,100f)
        assertEquals(3f,drag.move(103f,102f,true).dx)
    }
    @Test fun universalFirstVerticalSwipeRotatesAndKeepsYAxis(){
        val drag=GlobeDrag(8f);drag.down(100f,100f)
        val first=drag.move(101f,121f,false)
        assertEquals(GlobeDragAxis.ROTATE,first.axis)
        assertEquals(21f,first.dy)
        val next=drag.move(101f,140f,false)
        assertEquals(GlobeDragAxis.ROTATE,next.axis)
        assertEquals(19f,next.dy)
    }
    @Test fun universalDiagonalSwipeAlsoRotatesFromFirstGesture(){
        val drag=GlobeDrag(8f);drag.down(20f,20f)
        assertEquals(GlobeDragAxis.ROTATE,drag.move(30f,36f,false).axis)
        assertEquals(GlobeDragAxis.ROTATE,drag.move(31f,43f,false).axis)
    }
    @Test fun nonScrollableGlobeAllowsVerticalRotation(){
        val drag=GlobeDrag(8f);drag.down(0f,0f)
        assertEquals(GlobeDragAxis.ROTATE,drag.move(1f,20f,false).axis)
    }
}
