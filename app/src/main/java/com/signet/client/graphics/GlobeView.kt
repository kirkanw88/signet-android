package com.signet.client.graphics

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.*
import android.view.MotionEvent
import com.signet.core.Geography
import com.signet.core.GlobeGeometry
import com.signet.core.MapPoint
import com.signet.core.Server
import com.signet.client.vpn.VpnPhase
import org.json.*
import org.locationtech.jts.geom.*
import org.locationtech.jts.triangulate.polygon.PolygonTriangulator
import java.nio.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class GlobeView(context: Context, highQuality:Boolean=false) : GLSurfaceView(context) {
    val globe = GlobeRenderer(context.applicationContext)
    private var lastX = 0f; private var lastY = 0f
    init {
        setEGLContextClientVersion(2)
        if(highQuality)setEGLConfigChooser { egl,display ->
            fun choose(samples:Int):EGLConfig? {
                val attrs=intArrayOf(12324,8,12323,8,12322,8,12321,8,12325,16,12352,4,12338,if(samples>0)1 else 0,12337,samples,12344)
                val count=IntArray(1);egl.eglChooseConfig(display,attrs,null,0,count)
                if(count[0]==0)return null
                val values=arrayOfNulls<EGLConfig>(count[0]);egl.eglChooseConfig(display,attrs,values,values.size,count)
                return values.firstOrNull()
            }
            choose(4)?:choose(0)?:error("OpenGL ES 2.0 недоступен")
        } else setEGLConfigChooser(8,8,8,8,16,0)
        setRenderer(globe); preserveEGLContextOnPause = false
        contentDescription = "Интерактивная трёхмерная Земля. Проведите пальцем для вращения."
    }
    fun update(server: Server?, phase: VpnPhase, quality: String, reduced: Boolean) {
        queueEvent { globe.update(server, phase, quality, reduced) }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; parent.requestDisallowInterceptTouchEvent(true) }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX; val dy = event.y - lastY
                queueEvent { globe.drag(dx * .25f, dy * .2f) }; lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_UP -> { performClick(); parent.requestDisallowInterceptTouchEvent(false) }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}

class GlobeRenderer(private val context: Context) : GLSurfaceView.Renderer {
    private var program = 0; private var texture = 0;private var nightTexture=0
    private var zoom=1f;private var countryZoom=1f;private var manual=false;private var focusLat:Double?=null;private var focusLon:Double?=null
    private val identity=FloatArray(16).apply{android.opengl.Matrix.setIdentityM(this,0)}
    private var stars=floats(floatArrayOf());private var starUv=floats(floatArrayOf())
    private var width = 1; private var height = 1
    private var yaw = -18f; private var pitch = 12f
    private var targetYaw: Float? = null; private var targetPitch = 12f
    private var phase = VpnPhase.DISCONNECTED; private var quality = "Balanced"; private var reduced = false
    private var selected: String? = null; private var country: String? = null
    private var vertex = floats(floatArrayOf()); private var uv = floats(floatArrayOf()); private var indices = shorts(shortArrayOf())
    private var count = 0
    private var borders = emptyList<FloatBuffer>(); private var fill: FloatBuffer? = null; private var marker: FloatBuffer? = null
    private val model = FloatArray(16); private val view = FloatArray(16); private val projection = FloatArray(16); private val mv = FloatArray(16); private val mvp = FloatArray(16)
    private var lastFrame = System.nanoTime()
    private var start = System.nanoTime()
    private var geography: JSONObject? = null
    private var countryData: JSONObject? = null
    private var markerCoords: Pair<Double, Double>? = null
    private var pulseStart=0L
    private val pulseBuffer=floats(FloatArray(65*3))
    private val scaledModel=FloatArray(16)

    fun update(server: Server?, state: VpnPhase, graphics: String, reduceMotion: Boolean) {
        if(state==VpnPhase.CONNECTED && phase!=state)pulseStart=System.nanoTime()
        phase = state; quality = graphics; reduced = reduceMotion
        val key=server?.let{"${it.id}:${it.countryCode}:${it.latitude}:${it.longitude}:${it.city}"}
        if (selected != key) {
            selected = key; country = server?.countryCode;manual=false
            countryData = country?.let { geography?.optJSONObject(it) }
            val lat = server?.latitude ?: countryData?.optDouble("lat")?.takeIf { it.isFinite() }
            val lon = server?.longitude ?: countryData?.optDouble("lon")?.takeIf { it.isFinite() }
            focusLat=lat;focusLon=lon;targetYaw = lon?.toFloat()?.unaryMinus(); targetPitch = lat?.toFloat() ?: 12f
            markerCoords = if (server?.city != null && server.latitude != null && server.longitude != null) server.latitude!! to server.longitude!! else null
            buildCountry()
        }
    }
    fun drag(dx: Float, dy: Float) { manual=true;yaw += dx; pitch = (pitch + dy).coerceIn(-85f, 85f); targetYaw = null }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Context can be recreated after backgrounding. Every GPU object is owned by this context.
        GLES20.glClearColor(5f/255, 8f/255, 22f/255, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        program = GLES20.glCreateProgram()
        val vs = shader(GLES20.GL_VERTEX_SHADER, """
            uniform mat4 uMvp; uniform mat4 uModel; uniform mediump int uMode;
            attribute vec3 aPosition; attribute vec2 aUv;
            varying vec2 vUv; varying vec3 vNormal;
            void main(){ vUv=aUv; vNormal=mat3(uModel)*normalize(aPosition); gl_Position=uMvp*vec4(aPosition,1.0); gl_PointSize=uMode==5 ? 1.2+aUv.x*1.4 : 8.0; }
        """.trimIndent())
        val fs = shader(GLES20.GL_FRAGMENT_SHADER, """
            precision mediump float; uniform sampler2D uTexture; uniform sampler2D uNight; uniform mediump int uMode; uniform vec4 uColor; uniform float uTime;
            varying vec2 vUv; varying vec3 vNormal;
            float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
            float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1.0,0.0)),f.x),mix(hash(i+vec2(0.0,1.0)),hash(i+vec2(1.0,1.0)),f.x),f.y);}
            void main(){
                if(uMode==0){
                    vec3 normal=normalize(vNormal);
                    float incidence=dot(normal, normalize(vec3(-0.6,0.45,1.0)));
                    float sunlight=max(incidence,0.0);
                    vec3 earth=texture2D(uTexture,vUv).rgb;
                    float rim=pow(1.0-max(normal.z,0.0),3.0);
                    vec3 night=texture2D(uNight,vUv).rgb;
                    float darkness=1.0-smoothstep(-0.12,0.22,incidence);
                    vec3 color=earth*(0.055+0.95*sunlight)+night*darkness*1.35+vec3(0.04,0.20,0.36)*rim;
                    gl_FragColor=vec4(color,1.0);
                } else if(uMode==4){
                    vec2 p=vec2(vUv.x*28.0+uTime*0.003,vUv.y*18.0);
                    float n=noise(p)*0.55+noise(p*2.1)*0.28+noise(p*4.3)*0.17;
                    float cloud=smoothstep(0.56,0.77,n);
                    float light=0.2+0.8*max(dot(normalize(vNormal),normalize(vec3(-0.6,0.45,1.0))),0.0);
                    gl_FragColor=vec4(vec3(light),cloud*0.55);
                } else if(uMode==2){
                    float rim=pow(1.0-abs(normalize(vNormal).z),4.0);
                    gl_FragColor=vec4(0.12,0.58,0.96,rim*0.28);
                } else if(uMode==3){
                    float d=length(gl_PointCoord-vec2(0.5)); if(d>0.5) discard;
                    gl_FragColor=vec4(uColor.rgb,uColor.a);
                } else if(uMode==5){float d=length(gl_PointCoord-vec2(0.5));if(d>0.5)discard;gl_FragColor=vec4(vec3(0.65+vUv.x*0.3),(0.16+vUv.x*0.36)*(1.0-smoothstep(0.2,0.5,d)));}
                else { gl_FragColor=uColor; }
            }
        """.trimIndent())
        GLES20.glAttachShader(program, vs); GLES20.glAttachShader(program, fs); GLES20.glLinkProgram(program)
        val linked = IntArray(1); GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        check(linked[0] != 0) { GLES20.glGetProgramInfoLog(program) }
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        mesh()
        val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0); texture = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open("earth.jpg").use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1; while (bounds.outWidth / sample > 2048) sample *= 2
        val bitmap = context.assets.open("earth.jpg").use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }) }!!
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0); bitmap.recycle()
        GLES20.glGenTextures(1,ids,0);nightTexture=ids[0]
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,nightTexture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        val night=context.assets.open("night.jpg").use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=2;inPreferredConfig=android.graphics.Bitmap.Config.RGB_565})}!!
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,night,0);night.recycle()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        val random=kotlin.random.Random(7421);val points=FloatArray(320*3);val brightness=FloatArray(320*2)
        for(i in 0 until 320){points[i*3]=random.nextFloat()*2-1;points[i*3+1]=random.nextFloat()*2-1;points[i*3+2]=.999f;brightness[i*2]=random.nextFloat()}
        stars=floats(points);starUv=floats(brightness)

        geography = context.assets.open("countries.json").bufferedReader().use { JSONObject(it.readText()) }
        countryData = country?.let { geography?.optJSONObject(it) }
        if(targetYaw==null && countryData!=null && !manual){targetYaw=countryData!!.optDouble("lon").toFloat().unaryMinus();targetPitch=countryData!!.optDouble("lat").toFloat()}
        buildCountry()
        lastFrame = System.nanoTime()
    }
    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { width = w; height = h; GLES20.glViewport(0, 0, w, h) }
    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime(); val dt = ((now-lastFrame)/1e9).coerceIn(0.0, .1).toFloat(); lastFrame = now
        if (!reduced && phase in listOf(VpnPhase.CONNECTING, VpnPhase.RECONNECTING)) yaw += dt * 280
        else if (targetYaw != null) { yaw = Geography.approach(yaw, targetYaw!!, (dt*3).coerceAtMost(1f)); pitch += (targetPitch-pitch)*dt*3 }
        else if (!reduced && phase == VpnPhase.DISCONNECTED) yaw += dt * 3
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        Matrix.setIdentityM(model, 0); Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f); Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
        val aspect = width.toFloat()/height
        Matrix.perspectiveM(projection, 0, 36f, aspect, .1f, 20f)
        val targetZoom=if(phase==VpnPhase.CONNECTED)countryZoom else 1f
        zoom+=(targetZoom-zoom)*(dt*2.4f).coerceAtMost(1f)
        Matrix.setLookAtM(view, 0, 0f, 0f, (if (aspect < 1f) 3.7f/aspect else 3.7f)/zoom, 0f,0f,0f,0f,1f,0f)
        Matrix.multiplyMM(mv,0,view,0,model,0); Matrix.multiplyMM(mvp,0,projection,0,mv,0)
        GLES20.glUseProgram(program)
        GLES20.glDepthMask(false);GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uMvp"),1,false,identity,0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uModel"),1,false,identity,0)
        setBuffers(stars,starUv);mode(5);GLES20.glDrawArrays(GLES20.GL_POINTS,0,320);GLES20.glDepthMask(true)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uMvp"),1,false,mvp,0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uModel"),1,false,model,0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uTime"), (now-start)/1e9f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,nightTexture);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uNight"),1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture); GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uTexture"),0)
        setBuffers(vertex, uv); mode(0); GLES20.glDrawElements(GLES20.GL_TRIANGLES,count,GLES20.GL_UNSIGNED_SHORT,indices)
        if (quality != "Battery Saver") {
            Matrix.scaleM(scaledModel,0,model,0,1.025f,1.025f,1.025f)
            Matrix.multiplyMM(mv,0,view,0,scaledModel,0); Matrix.multiplyMM(mvp,0,projection,0,mv,0)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uMvp"),1,false,mvp,0)
            GLES20.glDepthMask(false); mode(2); GLES20.glDrawElements(GLES20.GL_TRIANGLES,count,GLES20.GL_UNSIGNED_SHORT,indices); GLES20.glDepthMask(true)
            Matrix.multiplyMM(mv,0,view,0,model,0); Matrix.multiplyMM(mvp,0,projection,0,mv,0)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uMvp"),1,false,mvp,0)
        }
        if(quality=="High"){
            Matrix.scaleM(scaledModel,0,model,0,1.0015f,1.0015f,1.0015f)
            Matrix.multiplyMM(mv,0,view,0,scaledModel,0);Matrix.multiplyMM(mvp,0,projection,0,mv,0)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uMvp"),1,false,mvp,0)
            GLES20.glDepthMask(false);mode(4);GLES20.glDrawElements(GLES20.GL_TRIANGLES,count,GLES20.GL_UNSIGNED_SHORT,indices);GLES20.glDepthMask(true)
            Matrix.multiplyMM(mv,0,view,0,model,0);Matrix.multiplyMM(mvp,0,projection,0,mv,0)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uMvp"),1,false,mvp,0)
        }
        val green = phase == VpnPhase.CONNECTED
        val color = floatArrayOf(.224f,.64f,.90f,1f)
        mode(1); GLES20.glDepthMask(false)
        fill?.let { setBuffers(it); color(color, 1f); GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,it.capacity()/3) }
        color(color, 1f); GLES20.glLineWidth(2f)
        borders.forEach { setBuffers(it); GLES20.glDrawArrays(GLES20.GL_LINE_STRIP,0,it.capacity()/3) }
        marker?.let { setBuffers(it); mode(3); color(color,1f); GLES20.glDrawArrays(GLES20.GL_POINTS,0,1) }
        GLES20.glDepthMask(true)
        val targetFrame = when { reduced || quality == "Battery Saver" -> 100L; quality == "High" -> 16L; else -> 33L }
        val elapsed = (System.nanoTime()-now)/1_000_000
        if (elapsed < targetFrame) Thread.sleep(targetFrame-elapsed)
    }
    private fun buildCountry() {
        val polys = countryData?.optJSONArray("polygons")
        val lines = mutableListOf<FloatBuffer>(); val triangles = ArrayList<Float>()
        val factory = GeometryFactory()
        if (polys != null) for (i in 0 until polys.length()) {
            val rings = polys.getJSONArray(i)
            val jtsRings = mutableListOf<LinearRing>()
            for (j in 0 until rings.length()) {
                val ring = rings.getJSONArray(j); val coords = ArrayList<Coordinate>(); val line = ArrayList<Float>()
                var previous = 0.0
                for (k in 0 until ring.length()) {
                    val p = ring.getJSONArray(k); var lon = p.getDouble(0); val lat = p.getDouble(1)
                    if(k==0 && j>0 && jtsRings.isNotEmpty()){val anchor=jtsRings.first().coordinate.x;while(lon-anchor>180)lon-=360;while(lon-anchor < -180)lon+=360}
                    if (k > 0) { while(lon-previous>180) lon-=360; while(lon-previous < -180) lon+=360 }
                    previous = lon; coords.add(Coordinate(lon,lat)); Geography.point(lat,p.getDouble(0),1.007).forEach(line::add)
                }
                lines.add(floats(line.toFloatArray()))
                if (coords.size >= 4) runCatching { jtsRings.add(factory.createLinearRing(coords.toTypedArray())) }
            }
            if(jtsRings.isNotEmpty()) runCatching {
                val polygon = factory.createPolygon(jtsRings.first(),jtsRings.drop(1).toTypedArray())
                val mesh = PolygonTriangulator.triangulate(polygon)
                for(t in 0 until mesh.numGeometries) {
                    val p=mesh.getGeometryN(t).coordinates.take(3).map{MapPoint(it.y,it.x)}
                    if(p.size==3)GlobeGeometry.appendTriangle(p[0],p[1],p[2],triangles)
                }
            }
        }
        val center=MapPoint(focusLat?:countryData?.optDouble("lat")?:12.0,focusLon?:countryData?.optDouble("lon")?:18.0)
        val allPoints=sequence {if(polys!=null)for(i in 0 until polys.length()){val rings=polys.getJSONArray(i);for(j in 0 until rings.length()){val ring=rings.getJSONArray(j);for(k in 0 until ring.length()){val p=ring.getJSONArray(k);yield(MapPoint(p.getDouble(1),p.getDouble(0)))}}}}
        countryZoom=if(polys==null)1f else GlobeGeometry.zoom(GlobeGeometry.angularRadius(center,allPoints))
        borders = lines; fill = triangles.takeIf { it.isNotEmpty() }?.let { floats(it.toFloatArray()) }
        marker = markerCoords?.let { floats(Geography.point(it.first,it.second,1.015)) }
    }
    private fun mesh() {
        val positions = ArrayList<Float>(); val tex = ArrayList<Float>(); val idx = ArrayList<Short>()
        val rows=48; val cols=96
        for (y in 0..rows) for(x in 0..cols) {
            val lat=90.0-y*180.0/rows; val lon=x*360.0/cols-180
            Geography.point(lat,lon).forEach(positions::add); tex.add(x.toFloat()/cols); tex.add(y.toFloat()/rows)
        }
        for(y in 0 until rows) for(x in 0 until cols) {
            val a=y*(cols+1)+x; val b=a+cols+1
            listOf(a,b,a+1,a+1,b,b+1).forEach { idx.add(it.toShort()) }
        }
        vertex=floats(positions.toFloatArray()); uv=floats(tex.toFloatArray()); indices=shorts(idx.toShortArray()); count=idx.size
    }
    private fun setBuffers(position: FloatBuffer, tex: FloatBuffer? = null) {
        val p=GLES20.glGetAttribLocation(program,"aPosition"); GLES20.glEnableVertexAttribArray(p); GLES20.glVertexAttribPointer(p,3,GLES20.GL_FLOAT,false,0,position)
        val u=GLES20.glGetAttribLocation(program,"aUv")
        if(tex != null){ GLES20.glEnableVertexAttribArray(u); GLES20.glVertexAttribPointer(u,2,GLES20.GL_FLOAT,false,0,tex) } else GLES20.glDisableVertexAttribArray(u)
    }
    private fun mode(value:Int)=GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uMode"),value)
    private fun color(c:FloatArray,alpha:Float)=GLES20.glUniform4f(GLES20.glGetUniformLocation(program,"uColor"),c[0],c[1],c[2],alpha)
    private fun shader(type:Int,source:String):Int=GLES20.glCreateShader(type).also { GLES20.glShaderSource(it,source); GLES20.glCompileShader(it); val status=IntArray(1); GLES20.glGetShaderiv(it,GLES20.GL_COMPILE_STATUS,status,0); check(status[0]!=0){GLES20.glGetShaderInfoLog(it)} }
    companion object {
        fun floats(data:FloatArray):FloatBuffer=ByteBuffer.allocateDirect(data.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(data);position(0)}
        fun shorts(data:ShortArray):ShortBuffer=ByteBuffer.allocateDirect(data.size*2).order(ByteOrder.nativeOrder()).asShortBuffer().apply{put(data);position(0)}
    }
}
