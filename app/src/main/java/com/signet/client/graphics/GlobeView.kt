package com.signet.client.graphics

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.opengl.*
import android.view.MotionEvent
import com.signet.core.Geography
import com.signet.core.GlobeGeometry
import com.signet.core.MapPoint
import com.signet.core.SolarPosition
import com.signet.core.Server
import com.signet.client.vpn.VpnPhase
import org.json.*
import org.locationtech.jts.geom.*
import org.locationtech.jts.triangulate.polygon.PolygonTriangulator
import java.nio.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CapitalMarker(val name:String="",val x:Int=0,val y:Int=0,val visible:Boolean=false)

class GlobeView(context: Context, highQuality:Boolean=false) : GLSurfaceView(context) {
    val globe = GlobeRenderer(context.applicationContext,highQuality)
    private var lastX = 0f; private var lastY = 0f
    private var startX=0f;private var startY=0f;private var gestureAxis=0
    private var allowParentScroll=false
    private val touchSlop=android.view.ViewConfiguration.get(context).scaledTouchSlop
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
    fun update(server: Server?, phase: VpnPhase, quality: String, reduced: Boolean, lightTheme:Boolean=false,parentScroll:Boolean=false) {
        allowParentScroll=parentScroll
        queueEvent { globe.lightTheme=lightTheme;globe.update(server, phase, quality, reduced) }
    }
    override fun onLayout(changed:Boolean,left:Int,top:Int,right:Int,bottom:Int){
        super.onLayout(changed,left,top,right,bottom)
        val location=IntArray(2);getLocationInWindow(location)
        val rootWidth=rootView.width;val rootHeight=rootView.height
        queueEvent{globe.backdropRect(location[0],location[1],rootWidth,rootHeight)}
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y;startX=event.x;startY=event.y;gestureAxis=0;parent.requestDisallowInterceptTouchEvent(!allowParentScroll) }
            MotionEvent.ACTION_MOVE -> {
                if(allowParentScroll){
                    val dx=event.x-startX;val dy=event.y-startY
                    if(gestureAxis==0&&max(abs(dx),abs(dy))>touchSlop)gestureAxis=if(abs(dy)>abs(dx))2 else 1
                    if(gestureAxis==2){parent.requestDisallowInterceptTouchEvent(false);return false}
                    if(gestureAxis==0)return true
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                val dx = event.x - lastX; val dy = event.y - lastY
                queueEvent { globe.drag(dx * .25f, dy * .2f) }; lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_UP -> { performClick(); parent.requestDisallowInterceptTouchEvent(false) }
            MotionEvent.ACTION_CANCEL -> {gestureAxis=0;parent.requestDisallowInterceptTouchEvent(false)}
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}

class GlobeRenderer(private val context: Context,initialHigh:Boolean=false) : GLSurfaceView.Renderer {
    var lightTheme=false
    private val markerState=MutableStateFlow(CapitalMarker());val capitalMarker=markerState.asStateFlow()
    private var capitals:JSONObject?=null;private var capital:JSONObject?=null
    private var capitalRevealed=false
    private val markerPoint=FloatArray(4);private val markerClip=FloatArray(4);private val markerWorld=FloatArray(4)
    private var windowX=0;private var windowY=0;private var windowWidth=0;private var windowHeight=0
    @Volatile var frameReady=false
        private set
    @Volatile var renderedCountry:String?=null
        private set
    private var program = 0; private var texture = 0;private var nightTexture=0;private var spaceTexture=0
    private var detailDay=0;private var detailNight=0;private var detailReady=false
    private var detailKey:String?=null
    private val detailRect=floatArrayOf(0f,0f,1f,1f)
    private val skyVertex=floats(floatArrayOf(-1f,-1f,.999f,1f,-1f,.999f,-1f,1f,.999f,1f,1f,.999f))
    private var skyUv=floats(floatArrayOf(0f,1f,1f,1f,0f,0f,1f,0f))
    private var skyAspect=2f
    private val sunEarth=FloatArray(4);private val sunWorld=FloatArray(4)
    private var nextSunUpdate=0L
    private var zoom=1f;private var countryZoom=1f;private var manual=false;private var focusLat:Double?=null;private var focusLon:Double?=null
    private val identity=FloatArray(16).apply{android.opengl.Matrix.setIdentityM(this,0)}
    private var width = 1; private var height = 1
    private var yaw = -18f; private var pitch = 12f
    private var targetYaw: Float? = null; private var targetPitch = 12f
    private var phase = VpnPhase.DISCONNECTED; private var quality = if(initialHigh)"High"else"Balanced"; private var reduced = false
    private var selected: String? = null; private var country: String? = null
    private var vertex = floats(floatArrayOf()); private var uv = floats(floatArrayOf()); private var indices = shorts(shortArrayOf())
    private var count = 0
    private var borders = emptyList<FloatBuffer>(); private var fill: FloatBuffer? = null; private var marker: FloatBuffer? = null
    private val model = FloatArray(16); private val view = FloatArray(16); private val projection = FloatArray(16); private val mv = FloatArray(16); private val mvp = FloatArray(16)
    private var lastFrame = System.nanoTime()
    private var geography: JSONObject? = null
    private var countryData: JSONObject? = null
    private var markerCoords: Pair<Double, Double>? = null
    private val scaledModel=FloatArray(16)
    private val borderColor=floatArrayOf(.224f,.64f,.90f,1f)
    private val borderHighlight=floatArrayOf(.45f,.85f,1f,1f)
    private val borderCore=floatArrayOf(.86f,.97f,1f,1f)

    fun update(server: Server?, state: VpnPhase, graphics: String, reduceMotion: Boolean) {
        val focusConnection=state in listOf(VpnPhase.CONNECTING,VpnPhase.RECONNECTING,VpnPhase.CONNECTED)&&phase !in listOf(VpnPhase.CONNECTING,VpnPhase.RECONNECTING,VpnPhase.CONNECTED)
        phase = state; quality = graphics; reduced = reduceMotion
        val key=server?.let{"${it.id}:${it.countryCode}:${it.latitude}:${it.longitude}:${it.city}"}
        if (selected != key) {
            frameReady=false
            selected = key; country = server?.countryCode;manual=false
            countryData = country?.let { geography?.optJSONObject(it) }
            capital=country?.let{capitals?.optJSONObject(it)};capitalRevealed=false;markerState.value=CapitalMarker()
            val lat = capital?.optDouble("latitude") ?: countryData?.optDouble("lat")?.takeIf { it.isFinite() }
            val lon = capital?.optDouble("longitude") ?: countryData?.optDouble("lon")?.takeIf { it.isFinite() }
            focusLat=lat;focusLon=lon;targetYaw = lon?.toFloat()?.unaryMinus(); targetPitch = lat?.toFloat() ?: 12f
            markerCoords=capital?.let{it.getDouble("latitude") to it.getDouble("longitude")}
            buildCountry()
            if(program!=0)loadDetail()
        }
        if(focusConnection){manual=false;targetYaw=focusLon?.toFloat()?.unaryMinus();targetPitch=focusLat?.toFloat()?:12f}
        if(state!=VpnPhase.CONNECTED){capitalRevealed=false;markerState.value=CapitalMarker()}
    }
    fun drag(dx: Float, dy: Float) { manual=true;yaw += dx; pitch = (pitch + dy).coerceIn(-85f, 85f); targetYaw = null }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        detailReady=false;detailKey=null
        frameReady=false;renderedCountry=null
        // Context can be recreated after backgrounding. Every GPU object is owned by this context.
        GLES20.glClearColor(5f/255, 8f/255, 22f/255, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        program = GLES20.glCreateProgram()
        val vs = shader(GLES20.GL_VERTEX_SHADER, """
            uniform mat4 uMvp; uniform mat4 uModel; uniform mediump int uMode; uniform vec2 uScreenOffset;
            attribute vec3 aPosition; attribute vec2 aUv;
            varying vec2 vUv; varying vec3 vNormal; varying vec2 vScreen;
            void main(){ vUv=aUv; vScreen=aPosition.xy*0.5+0.5; vNormal=mat3(uModel)*normalize(aPosition); gl_Position=uMvp*vec4(aPosition,1.0); gl_Position.xy+=uScreenOffset*gl_Position.w; gl_PointSize=6.0; }
        """.trimIndent())
        val fs = shader(GLES20.GL_FRAGMENT_SHADER, """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D uTexture; uniform sampler2D uNight; uniform sampler2D uDetailDay; uniform sampler2D uDetailNight;
            uniform vec4 uDetailRect; uniform float uDetailBlend; uniform mediump int uMode; uniform vec4 uColor; uniform vec3 uSun; uniform float uLightTheme;
            varying vec2 vUv; varying vec3 vNormal; varying vec2 vScreen;
            void main(){
                if(uMode==0){
                    vec3 normal=normalize(vNormal);
                    vec3 sun=normalize(uSun);
                    float incidence=dot(normal,sun);
                    float sunlight=max(incidence,0.0);
                    vec3 earth=texture2D(uTexture,vUv).rgb;
                    vec2 detailUv=(vUv-uDetailRect.xy)/uDetailRect.zw;
                    vec2 edge=min(detailUv,vec2(1.0)-detailUv);
                    float detailWeight=uDetailBlend*smoothstep(0.0,0.08,min(edge.x,edge.y));
                    earth=mix(earth,texture2D(uDetailDay,clamp(detailUv,0.0,1.0)).rgb,detailWeight);
                    float rim=pow(1.0-max(normal.z,0.0),4.5);
                    vec3 night=texture2D(uNight,vUv).rgb;
                    night=mix(night,texture2D(uDetailNight,clamp(detailUv,0.0,1.0)).rgb,detailWeight);
                    float darkness=1.0-smoothstep(-0.08,0.12,incidence);
                    vec3 linear=pow(earth,vec3(2.2))*(mix(0.008,0.045,uLightTheme)+mix(0.98,1.10,uLightTheme)*sunlight);
                    float city=max(night.r,max(night.g,night.b));
                    linear+=pow(night,vec3(2.2))*darkness*smoothstep(0.03,0.55,city)*1.8;
                    vec3 color=pow(max(linear,vec3(0.0)),vec3(0.4545));
                    float ocean=smoothstep(0.025,0.15,earth.b-max(earth.r,earth.g)*0.9);
                    color+=vec3(0.65,0.78,0.9)*pow(max(dot(reflect(-sun,normal),vec3(0.0,0.0,1.0)),0.0),36.0)*ocean*sunlight*0.16;
                    color+=vec3(0.06,0.22,0.42)*rim*(0.06+sunlight*0.6);
                    gl_FragColor=vec4(color,1.0);
                } else if(uMode==2){
                    float rim=pow(1.0-abs(normalize(vNormal).z),4.0);
                    float sunlit=smoothstep(-0.25,0.35,dot(normalize(vNormal),normalize(uSun)));
                    gl_FragColor=vec4(0.16,0.48,0.82,rim*(0.04+sunlit*0.13));
                } else if(uMode==3){
                    float d=length(gl_PointCoord-vec2(0.5)); if(d>0.5) discard;
                    gl_FragColor=vec4(uColor.rgb,uColor.a);
                } else if(uMode==6){
                    vec3 space=texture2D(uTexture,vUv).rgb*0.72;
                    space=mix(space,vec3(0.95686,0.96863,0.98824),0.95*uLightTheme);
                    gl_FragColor=vec4(space,1.0);
                }
                else { gl_FragColor=uColor; }
            }
        """.trimIndent())
        GLES20.glAttachShader(program, vs); GLES20.glAttachShader(program, fs); GLES20.glLinkProgram(program)
        val linked = IntArray(1); GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        check(linked[0] != 0) { GLES20.glGetProgramInfoLog(program) }
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        val detailIds=IntArray(2);GLES20.glGenTextures(2,detailIds,0);detailDay=detailIds[0];detailNight=detailIds[1]
        // Complete tiny textures even before a country is selected (all sampler uniforms must be valid).
        for(id in detailIds){GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id);GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGB,1,1,0,GLES20.GL_RGB,GLES20.GL_UNSIGNED_SHORT_5_6_5,ByteBuffer.allocateDirect(2));GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)}
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
        val bitmap = context.assets.open("earth.jpg").use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }) }!!
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0); bitmap.recycle()
        GLES20.glGenTextures(1,ids,0);nightTexture=ids[0]
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,nightTexture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        val night=context.assets.open("night.jpg").use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=1;inPreferredConfig=android.graphics.Bitmap.Config.ARGB_8888})}!!
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,night,0);night.recycle()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glGenTextures(1,ids,0);spaceTexture=ids[0];GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,spaceTexture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        val sky=context.assets.open("space.png").use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inPreferredConfig=android.graphics.Bitmap.Config.RGB_565})}!!
        skyAspect=sky.width.toFloat()/sky.height;GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,sky,0);sky.recycle()

        geography = context.assets.open("countries.json").bufferedReader().use { JSONObject(it.readText()) }
        capitals=context.assets.open("capitals.json").bufferedReader().use{JSONObject(it.readText())}
        capital=country?.let{capitals?.optJSONObject(it)}
        countryData = country?.let { geography?.optJSONObject(it) }
        capital?.let{focusLat=it.getDouble("latitude");focusLon=it.getDouble("longitude");markerCoords=focusLat!! to focusLon!!;if(!manual){targetYaw=focusLon!!.toFloat().unaryMinus();targetPitch=focusLat!!.toFloat()}}
        if(targetYaw==null && countryData!=null && !manual){targetYaw=countryData!!.optDouble("lon").toFloat().unaryMinus();targetPitch=countryData!!.optDouble("lat").toFloat()}
        buildCountry()
        loadDetail()
        lastFrame = System.nanoTime()
    }
    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w; height = h; GLES20.glViewport(0, 0, w, h)
        updateBackdrop()
    }
    fun backdropRect(x:Int,y:Int,w:Int,h:Int){windowX=x;windowY=y;windowWidth=w;windowHeight=h;updateBackdrop()}
    private fun updateBackdrop(){
        val rw=windowWidth.takeIf{it>0}?:width;val rh=windowHeight.takeIf{it>0}?:height
        val aspect=rw.toFloat()/rh;val u=if(aspect<skyAspect)aspect/skyAspect else 1f;val v=if(aspect>skyAspect)skyAspect/aspect else 1f
        val left=.5f-u/2+u*windowX/rw;val right=left+u*width/rw
        val top=.5f-v/2+v*windowY/rh;val bottom=top+v*height/rh
        skyUv=floats(floatArrayOf(left,bottom,right,bottom,left,top,right,top))
    }
    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime(); val dt = ((now-lastFrame)/1e9).coerceIn(0.0, .1).toFloat(); lastFrame = now
        if (targetYaw != null) { yaw = Geography.approach(yaw, targetYaw!!, (dt*3).coerceAtMost(1f)); pitch += (targetPitch-pitch)*dt*3 }
        else if (!reduced && phase == VpnPhase.DISCONNECTED) yaw += dt * 3
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        Matrix.setIdentityM(model, 0); Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f); Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
        val aspect = width.toFloat()/height
        Matrix.perspectiveM(projection, 0, 36f, aspect, .1f, 20f)
        val targetZoom=if(phase in listOf(VpnPhase.CONNECTING,VpnPhase.RECONNECTING,VpnPhase.CONNECTED))countryZoom else 1f
        zoom+=(targetZoom-zoom)*(dt*2.4f).coerceAtMost(1f)
        val cameraDistance=(if (aspect < 1f) 3.7f/aspect else 3.7f)/zoom
        Matrix.setLookAtM(view, 0, 0f, 0f, cameraDistance, 0f,0f,0f,0f,1f,0f)
        Matrix.multiplyMM(mv,0,view,0,model,0); Matrix.multiplyMM(mvp,0,projection,0,mv,0)
        GLES20.glUseProgram(program)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uLightTheme"),if(lightTheme)1f else 0f)
        val borderOffset=GLES20.glGetUniformLocation(program,"uScreenOffset")
        GLES20.glUniform2f(borderOffset,0f,0f)
        GLES20.glDepthMask(false);GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uMvp"),1,false,identity,0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uModel"),1,false,identity,0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,spaceTexture);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uTexture"),0)
        setBuffers(skyVertex,skyUv);mode(6);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDepthMask(true)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uMvp"),1,false,mvp,0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uModel"),1,false,model,0)
        val utc=System.currentTimeMillis()
        if(utc>=nextSunUpdate||utc<nextSunUpdate-60_000){val direction=SolarPosition.direction(utc);for(i in 0..2)sunEarth[i]=direction[i];sunEarth[3]=0f;nextSunUpdate=utc+60_000}
        Matrix.multiplyMV(sunWorld,0,model,0,sunEarth,0);GLES20.glUniform3fv(GLES20.glGetUniformLocation(program,"uSun"),1,sunWorld,0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,nightTexture);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uNight"),1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture); GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uTexture"),0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,detailDay);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uDetailDay"),2)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,detailNight);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uDetailNight"),3)
        GLES20.glUniform4fv(GLES20.glGetUniformLocation(program,"uDetailRect"),1,detailRect,0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uDetailBlend"),if(detailReady)((zoom-1.1f)/.9f).coerceIn(0f,1f)else 0f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        setBuffers(vertex, uv); mode(0); GLES20.glDrawElements(GLES20.GL_TRIANGLES,count,GLES20.GL_UNSIGNED_SHORT,indices)
        if (quality != "Battery Saver") {
            Matrix.scaleM(scaledModel,0,model,0,1.025f,1.025f,1.025f)
            Matrix.multiplyMM(mv,0,view,0,scaledModel,0); Matrix.multiplyMM(mvp,0,projection,0,mv,0)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uMvp"),1,false,mvp,0)
            GLES20.glDepthMask(false); mode(2); GLES20.glDrawElements(GLES20.GL_TRIANGLES,count,GLES20.GL_UNSIGNED_SHORT,indices); GLES20.glDepthMask(true)
            Matrix.multiplyMM(mv,0,view,0,model,0); Matrix.multiplyMM(mvp,0,projection,0,mv,0)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uMvp"),1,false,mvp,0)
        }
        val focused=phase in listOf(VpnPhase.CONNECTING,VpnPhase.RECONNECTING,VpnPhase.CONNECTED)
        mode(1); GLES20.glDepthMask(false)
        fill?.let { setBuffers(it); color(if(focused)borderHighlight else borderColor,if(focused).17f else .07f); GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,it.capacity()/3) }
        // ES2 GPUs often clamp line width to 1px. Offset passes form a constant-width cyan edge.
        GLES20.glLineWidth(1f)
        if(focused){
            color(borderHighlight,.86f)
            for(pass in 0..3){
                val x=when(pass){0->-2.5f/width;1->2.5f/width;else->0f}
                val y=when(pass){2->-2.5f/height;3->2.5f/height;else->0f}
                GLES20.glUniform2f(borderOffset,x,y)
                borders.forEach{setBuffers(it);GLES20.glDrawArrays(GLES20.GL_LINE_STRIP,0,it.capacity()/3)}
            }
        }
        GLES20.glUniform2f(borderOffset,0f,0f)
        color(if(focused)borderCore else borderColor,if(focused)1f else .58f)
        borders.forEach { setBuffers(it); GLES20.glDrawArrays(GLES20.GL_LINE_STRIP,0,it.capacity()/3) }
        if(phase==VpnPhase.CONNECTED&&capital!=null){
            val settled=!manual&&abs(Geography.wrap((targetYaw?:yaw)-yaw))<.2f&&abs(targetPitch-pitch)<.2f&&abs(targetZoom-zoom)<.015f
            if(settled)capitalRevealed=true
            val coordinates=markerCoords!!;val point=Geography.point(coordinates.first,coordinates.second,1.015)
            for(i in 0..2)markerPoint[i]=point[i];markerPoint[3]=1f
            Matrix.multiplyMV(markerClip,0,mvp,0,markerPoint,0);Matrix.multiplyMV(markerWorld,0,model,0,markerPoint,0)
            val x=((markerClip[0]/markerClip[3]+1)*width/2).roundToInt();val y=((1-markerClip[1]/markerClip[3])*height/2).roundToInt()
            val visible=capitalRevealed&&markerClip[3]>0&&markerWorld[2]*cameraDistance>1.02f&&x in 0..width&&y in 0..height
            markerState.value=CapitalMarker(capital!!.getString("name"),x,y,visible)
        }else if(markerState.value.visible)markerState.value=CapitalMarker()
        GLES20.glDepthMask(true)
        renderedCountry=country;frameReady=true
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
    private fun loadDetail(){
        val lat=focusLat;val lon=focusLon
        if(lat==null||lon==null||capital==null){detailReady=false;return}
        val left=(((lon+180)/360*8192)-1024).toInt().coerceIn(0,8192-2048)
        val top=(((90-lat)/180*4096)-1024).toInt().coerceIn(0,4096-2048)
        val key="$left:$top";if(detailReady&&detailKey==key)return
        detailReady=false;detailKey=key
        detailRect[0]=left/8192f;detailRect[1]=top/4096f;detailRect[2]=2048/8192f;detailRect[3]=2048/4096f
        val limit=IntArray(1);GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE,limit,0)
        var sample=1;while(2048/sample>limit[0].coerceAtLeast(64))sample*=2
        detailReady=runCatching{
            for((asset,id) in listOf("earth-day-8k.jpg" to detailDay,"earth-night-8k.jpg" to detailNight)){
                context.assets.open(asset).use{stream->
                    val decoder=BitmapRegionDecoder.newInstance(stream,false)!!
                    try{
                        val crop=decoder.decodeRegion(Rect(left,top,left+2048,top+2048),BitmapFactory.Options().apply{inPreferredConfig=android.graphics.Bitmap.Config.RGB_565;inSampleSize=sample})!!
                        try{
                            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id)
                            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,crop,0)
                            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
                            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
                            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
                            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
                        }finally{crop.recycle()}
                    }finally{decoder.recycle()}
                }
            }
            true
        }.getOrDefault(false)
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
