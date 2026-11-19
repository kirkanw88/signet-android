package com.signet.core

import kotlin.math.abs
import kotlin.math.max

enum class GlobeDragAxis{PENDING,ROTATE,SCROLL}
data class GlobeDragStep(val axis:GlobeDragAxis,val dx:Float=0f,val dy:Float=0f)
/** Lock a direction once; small diagonal finger noise must not hand a horizontal drag to scrolling. */
class GlobeDrag(private val slop:Float){
    var axis=GlobeDragAxis.PENDING
        private set
    private var startX=0f;private var startY=0f;private var lastX=0f;private var lastY=0f
    fun down(x:Float,y:Float){axis=GlobeDragAxis.PENDING;rebase(x,y)}
    fun rebase(x:Float,y:Float){startX=x;startY=y;lastX=x;lastY=y}
    fun reset(){axis=GlobeDragAxis.PENDING}
    fun move(x:Float,y:Float,scrollable:Boolean):GlobeDragStep{
        val dx=x-lastX;val dy=y-lastY;lastX=x;lastY=y
        val horizontal=abs(x-startX);val vertical=abs(y-startY);val distance=max(horizontal,vertical)
        if(axis==GlobeDragAxis.PENDING&&distance>slop){
            axis=when{
                !scrollable||horizontal>vertical*1.25f->GlobeDragAxis.ROTATE
                vertical>horizontal*1.25f->GlobeDragAxis.SCROLL
                distance>slop*2->if(horizontal>=vertical)GlobeDragAxis.ROTATE else GlobeDragAxis.SCROLL
                else->GlobeDragAxis.PENDING
            }
        }
        return if(axis==GlobeDragAxis.ROTATE)GlobeDragStep(axis,dx,dy)else GlobeDragStep(axis)
    }
}
