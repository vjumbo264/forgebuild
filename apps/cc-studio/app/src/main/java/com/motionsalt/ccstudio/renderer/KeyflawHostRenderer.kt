package com.motionsalt.ccstudio.renderer

import android.graphics.Bitmap
import android.opengl.GLES20 as GL
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.SystemClock
import com.motionsalt.ccstudio.core.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/** Canonical textures match Keyflaw GL UVs (bottom at v=0). Only bitmap upload changes row order. */
class KeyflawHostRenderer:GLSurfaceView.Renderer {
    enum class ViewMode { RESULT,WIPE,SIDE_BY_SIDE,TOP_BOTTOM,VS_TARGET,DIFFERENCE,CHANNELS,SCOPES }
    @Volatile var values:Map<String,Double> = CcPlugin.initial
    @Volatile var activeViewMode=ViewMode.RESULT
    @Volatile var isPressingBefore=false
    @Volatile var wipeFraction=.5f
    @Volatile var isWipeVertical=true
    @Volatile var targetSplit=false
    @Volatile var diffGain=2f
    @Volatile var selectedChannel=0
    @Volatile var averageFrameTimeMs=0f;private set
    @Volatile var currentFps=0f;private set
    @Volatile var glRendererString="Waiting for GL";private set
    @Volatile var glVersionString="Waiting for GL";private set
    @Volatile var compiledEffectsCount=0;private set
    val compileStatus=ConcurrentHashMap<String,String>()
    val parameterUpdateCount=java.util.concurrent.atomic.AtomicInteger(0)
    private var baseBitmap:Bitmap?=null;private var targetBitmap:Bitmap?=null
    @Volatile private var basePending:Bitmap?=null
    @Volatile private var targetPending:Bitmap?=null
    @Volatile private var pendingGraph:List<CcPlugin.Pass>?=null
    private var graph:List<CcPlugin.Pass> = emptyList()
    private var graphPrograms = IntArray(0)
    private var baseTexture=0;private var targetTexture=0
    private var imageW=1;private var imageH=1;private var viewW=1;private var viewH=1
    private val programs=HashMap<String,Int>();private val locations=HashMap<Pair<Int,String>,Int>()
    private data class Target(val texture:Int,val fbo:Int,val w:Int,val h:Int)
    private val targets=HashMap<String,Target>()
    private var displayProgram=0;private var scopeProgram=0;private var scopeTime=0L
    @Volatile var onHistogram: ((Array<IntArray>)->Unit)?=null
    private val scopePixels=ByteBuffer.allocateDirect(64*64*4)
    @Volatile private var clearTarget=false
    private val vertices=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f));position(0)}
    private var frames=0;private var elapsed=0L
    fun setBaseBitmap(bitmap:Bitmap?){if(bitmap!=null)basePending=bitmap}
    fun setTargetBitmap(bitmap:Bitmap?){targetPending=bitmap;clearTarget=bitmap==null}
    fun setMatchModel(model:ParametricMatch){pendingGraph=CcPlugin.passes(model)}
    fun setCcValues(newValues:Map<String,Double>){values=newValues;parameterUpdateCount.incrementAndGet()}
    override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?) {
        programs.clear();locations.clear();targets.clear();baseTexture=0;targetTexture=0;compiledEffectsCount=0
        glRendererString=GL.glGetString(GL.GL_RENDERER)?:"Unknown";glVersionString=GL.glGetString(GL.GL_VERSION)?:"Unknown"
        GL.glDisable(GL.GL_DEPTH_TEST);GL.glDisable(GL.GL_DITHER);GL.glClearColor(0f,0f,0f,1f)
        displayProgram=program("display",DISPLAY);scopeProgram=program("scope",COPY)
        baseBitmap?.let{basePending=it};targetBitmap?.let{targetPending=it}
        if(pendingGraph==null && graph.isNotEmpty())pendingGraph=graph
    }
    override fun onSurfaceChanged(gl:GL10?,width:Int,height:Int){viewW=width;viewH=height}
    override fun onDrawFrame(gl:GL10?) {
        val start=SystemClock.elapsedRealtimeNanos()
        basePending?.let{bitmap->baseTexture=upload(bitmap,baseTexture);imageW=bitmap.width;imageH=bitmap.height;baseBitmap=bitmap;basePending=null}
        if(clearTarget){if(targetTexture!=0)GL.glDeleteTextures(1,intArrayOf(targetTexture),0);targetTexture=0;targetBitmap=null;clearTarget=false}
        targetPending?.let{targetTexture=upload(it,targetTexture);targetBitmap=it;targetPending=null}
        pendingGraph?.let { next ->
            graph=next
            graphPrograms=IntArray(next.size){i->program("cc/$i",next[i].source)}
            pendingGraph=null
        }
        if(baseTexture==0)return
        var current=baseTexture
        val parameters=values
        val retained=IntArray(graph.size)
        var sw=imageW;var sh=imageH
        if(!isPressingBefore && graphPrograms.all{it!=0}) for((index,pass) in graph.withIndex()) {
            if(pass.gate!=null && (parameters[pass.gate]?:0.0)<.5){retained[index]=current;continue}
            val dst=target("cc/$index",max(1,(imageW*pass.scale).roundToInt()),max(1,(imageH*pass.scale).roundToInt()),pass.floatTarget)
            val previous=pass.reference?.let{retained[it]}?:baseTexture
            draw(graphPrograms[index],current,previous,dst,parameters,sw,sh)
            current=dst.texture;sw=dst.w;sh=dst.h;retained[index]=current
        }
        if(activeViewMode==ViewMode.SCOPES && start-scopeTime>=125_000_000L && scopeProgram!=0) {
            scopeTime=start
            val dst=target("scope",64,64,false)
            GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,dst.fbo);GL.glViewport(0,0,64,64)
            GL.glUseProgram(scopeProgram);quad(scopeProgram);texture(scopeProgram,"uTexture",current,0)
            GL.glDrawArrays(GL.GL_TRIANGLE_STRIP,0,4)
            scopePixels.clear();GL.glReadPixels(0,0,64,64,GL.GL_RGBA,GL.GL_UNSIGNED_BYTE,scopePixels)
            val counts=Array(3){IntArray(32)}
            for(i in 0 until 64*64)for(c in 0..2)counts[c][(scopePixels.get(i*4+c).toInt() and 255)/8]++
            onHistogram?.invoke(counts)
        }
        GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,0);GL.glViewport(0,0,viewW,viewH);GL.glClear(GL.GL_COLOR_BUFFER_BIT)
        // Preserve native frame aspect, including rectangular unsharp radii.
        val scale=min(viewW.toFloat()/imageW,viewH.toFloat()/imageH);val dw=(imageW*scale).roundToInt();val dh=(imageH*scale).roundToInt()
        GL.glViewport((viewW-dw)/2,(viewH-dh)/2,dw,dh);GL.glUseProgram(displayProgram);quad(displayProgram)
        texture(displayProgram,"uTexture",if(isPressingBefore)baseTexture else current,0);texture(displayProgram,"uBefore",baseTexture,1);texture(displayProgram,"uTarget",if(targetTexture!=0)targetTexture else baseTexture,2)
        GL.glUniform1i(loc(displayProgram,"uMode"),if(isPressingBefore || (targetTexture==0 && activeViewMode in setOf(ViewMode.VS_TARGET,ViewMode.DIFFERENCE)))0 else activeViewMode.ordinal)
        one(displayProgram,"uWipe",wipeFraction);one(displayProgram,"uHorizontal",if(isWipeVertical)0f else 1f);one(displayProgram,"uGain",diffGain);one(displayProgram,"uChannel",selectedChannel.toFloat());one(displayProgram,"uSplit",if(targetSplit)1f else 0f)
        GL.glDrawArrays(GL.GL_TRIANGLE_STRIP,0,4)
        elapsed+=SystemClock.elapsedRealtimeNanos()-start;frames++
        if(frames>=30){averageFrameTimeMs=elapsed/frames/1e6f;currentFps=if(averageFrameTimeMs>0)min(60f,1000f/averageFrameTimeMs)else 0f;elapsed=0;frames=0}
    }
    private fun draw(p:Int,src:Int,prev:Int,dst:Target,values:Map<String,Double>,sw:Int,sh:Int) {
        GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,dst.fbo);GL.glViewport(0,0,dst.w,dst.h);GL.glUseProgram(p);quad(p)
        texture(p,"uTexture",src,0);texture(p,"uInput",baseTexture,1);texture(p,"uPrev",prev,2);texture(p,"uOriginal",baseTexture,3)
        GL.glUniform2f(loc(p,"uTexelSize"),1f/dst.w,1f/dst.h);GL.glUniform2f(loc(p,"uSrcTexelSize"),1f/sw,1f/sh)
        one(p,"uTime",0f);one(p,"uLayerDuration",0f)
        for(param in CcPlugin.params) one(p,"u"+param.id.replaceFirstChar{it.uppercase()},(values[param.id]?:param.initial).toFloat())
        GL.glDrawArrays(GL.GL_TRIANGLE_STRIP,0,4)
    }
    private fun target(key:String,w:Int,h:Int,floating:Boolean):Target {
        targets[key]?.let{if(it.w==w&&it.h==h)return it;GL.glDeleteTextures(1,intArrayOf(it.texture),0);GL.glDeleteFramebuffers(1,intArrayOf(it.fbo),0)}
        val t=IntArray(1);val f=IntArray(1);GL.glGenTextures(1,t,0);GL.glBindTexture(GL.GL_TEXTURE_2D,t[0]);params()
        if(floating)GLES30.glTexImage2D(GL.GL_TEXTURE_2D,0,GLES30.GL_RGBA16F,w,h,0,GL.GL_RGBA,GLES30.GL_HALF_FLOAT,null) else GL.glTexImage2D(GL.GL_TEXTURE_2D,0,GL.GL_RGBA,w,h,0,GL.GL_RGBA,GL.GL_UNSIGNED_BYTE,null)
        GL.glGenFramebuffers(1,f,0);GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,f[0]);GL.glFramebufferTexture2D(GL.GL_FRAMEBUFFER,GL.GL_COLOR_ATTACHMENT0,GL.GL_TEXTURE_2D,t[0],0)
        if(floating && GL.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER)!=GL.GL_FRAMEBUFFER_COMPLETE){
            GL.glBindTexture(GL.GL_TEXTURE_2D,t[0]);GL.glTexImage2D(GL.GL_TEXTURE_2D,0,GL.GL_RGBA,w,h,0,GL.GL_RGBA,GL.GL_UNSIGNED_BYTE,null)
        }
        check(GL.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER)==GL.GL_FRAMEBUFFER_COMPLETE){"Unsupported render target: $key"}
        return Target(t[0],f[0],w,h).also{targets[key]=it}
    }
    private fun upload(bitmap:Bitmap,old:Int):Int{var id=old;if(id==0){val a=IntArray(1);GL.glGenTextures(1,a,0);id=a[0]};GL.glBindTexture(GL.GL_TEXTURE_2D,id);params();val flip=android.graphics.Matrix().apply{setScale(1f,-1f)};val upright=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,flip,false);GLUtils.texImage2D(GL.GL_TEXTURE_2D,0,upright,0);if(upright!==bitmap)upright.recycle();return id}
    private fun params(){GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,GL.GL_LINEAR);GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_LINEAR);GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_S,GL.GL_CLAMP_TO_EDGE);GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_T,GL.GL_CLAMP_TO_EDGE)}
    private fun loc(p:Int,key:String)=locations.getOrPut(p to key){GL.glGetUniformLocation(p,key)}
    private fun one(p:Int,key:String,v:Float){GL.glUniform1f(loc(p,key),v)}
    private fun texture(p:Int,key:String,t:Int,unit:Int){GL.glActiveTexture(GL.GL_TEXTURE0+unit);GL.glBindTexture(GL.GL_TEXTURE_2D,t);GL.glUniform1i(loc(p,key),unit)}
    private fun quad(p:Int){val a=GL.glGetAttribLocation(p,"aPosition");if(a>=0){vertices.position(0);GL.glEnableVertexAttribArray(a);GL.glVertexAttribPointer(a,2,GL.GL_FLOAT,false,0,vertices)}}
    private fun program(key:String,source:String):Int {
        fun shader(type:Int,s:String):Int{val id=GL.glCreateShader(type);GL.glShaderSource(id,s);GL.glCompileShader(id);val ok=IntArray(1);GL.glGetShaderiv(id,GL.GL_COMPILE_STATUS,ok,0);check(ok[0]!=0){GL.glGetShaderInfoLog(id)};return id}
        return try {val v=shader(GL.GL_VERTEX_SHADER,VERTEX);val f=shader(GL.GL_FRAGMENT_SHADER,source);val p=GL.glCreateProgram();GL.glAttachShader(p,v);GL.glAttachShader(p,f);GL.glLinkProgram(p);val ok=IntArray(1);GL.glGetProgramiv(p,GL.GL_LINK_STATUS,ok,0);check(ok[0]!=0){GL.glGetProgramInfoLog(p)};GL.glDeleteShader(v);GL.glDeleteShader(f);programs.put(key,p)?.let{GL.glDeleteProgram(it)};compileStatus[key]="Compiled";compiledEffectsCount++;p} catch(e:Exception){compileStatus[key]=e.message?:"GL compile failure";0}
    }
    companion object {
        const val VERTEX="attribute vec2 aPosition;varying vec2 vTex;varying vec2 vLayerUv;void main(){vTex=aPosition*.5+.5;vLayerUv=vTex;gl_Position=vec4(aPosition,0.,1.); }"
        const val COPY="precision highp float;uniform highp sampler2D uTexture;varying vec2 vTex;void main(){gl_FragColor=texture2D(uTexture,vTex);}"
        const val DISPLAY="""precision highp float;
uniform highp sampler2D uTexture;uniform highp sampler2D uBefore;uniform highp sampler2D uTarget;
uniform int uMode;uniform float uWipe;uniform float uHorizontal;uniform float uGain;uniform float uChannel;uniform float uSplit;varying vec2 vTex;
void main(){vec2 uv=vTex;vec4 res=texture2D(uTexture,uv);vec4 before=texture2D(uBefore,uv);vec4 target=texture2D(uTarget,uv);float axis=uHorizontal>.5?1.-uv.y:uv.x;
if(uMode==0)gl_FragColor=res;
else if(uMode==1)gl_FragColor=axis<uWipe?before:res;
else if(uMode==2)gl_FragColor=uv.x<.5?texture2D(uBefore,vec2(uv.x*2.,uv.y)):texture2D(uTexture,vec2((uv.x-.5)*2.,uv.y));
else if(uMode==3)gl_FragColor=uv.y>.5?texture2D(uBefore,vec2(uv.x,(uv.y-.5)*2.)):texture2D(uTexture,vec2(uv.x,uv.y*2.));
else if(uMode==4){if(uSplit>.5)gl_FragColor=uv.x<.5?texture2D(uTexture,vec2(uv.x*2.,uv.y)):texture2D(uTarget,vec2((uv.x-.5)*2.,uv.y));else gl_FragColor=axis<uWipe?res:target;}
else if(uMode==5)gl_FragColor=vec4(abs(res.rgb-target.rgb)*uGain,1.);
else if(uMode==6){float l=dot(res.rgb,vec3(.2126,.7152,.0722));if(uChannel<.5)gl_FragColor=res;else if(uChannel<1.5)gl_FragColor=vec4(res.r,0.,0.,1.);else if(uChannel<2.5)gl_FragColor=vec4(0.,res.g,0.,1.);else if(uChannel<3.5)gl_FragColor=vec4(0.,0.,res.b,1.);else gl_FragColor=vec4(l,l,l,1.);}
else gl_FragColor=res;}"""
    }
}
