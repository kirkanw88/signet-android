package com.signet.core

import kotlin.math.*

data class CapitalTile(val x:Int,val y:Int,val column:Int,val row:Int)
data class CapitalTilePlan(val zoom:Int,val tiles:List<CapitalTile>,val centerX:Double,val centerY:Double)
object CapitalTiles {
    /** Nine local tiles, never a world download; X wraps at the dateline, Y never wraps. */
    fun plan(latitude:Double,longitude:Double,zoom:Int=17):CapitalTilePlan {
        require(latitude.isFinite()&&longitude.isFinite()&&latitude in -85.05112878..85.05112878&&longitude in -180.0..180.0&&zoom in 2..20)
        val count=1 shl zoom
        val x=((longitude+180)/360*count).coerceIn(0.0,Math.nextDown(count.toDouble()))
        val lat=Math.toRadians(latitude)
        val y=((1-asinh(tan(lat))/PI)/2*count).coerceIn(0.0,Math.nextDown(count.toDouble()))
        val left=floor(x).toInt()-1;val top=(floor(y).toInt()-1).coerceIn(0,count-3)
        return CapitalTilePlan(zoom,(0..2).flatMap{row->(0..2).map{column->CapitalTile(Math.floorMod(left+column,count),top+row,column,row)}},x-left,y-top)
    }
}
