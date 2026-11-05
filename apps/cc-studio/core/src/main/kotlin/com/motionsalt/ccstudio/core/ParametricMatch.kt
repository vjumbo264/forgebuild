package com.motionsalt.ccstudio.core

import kotlin.math.*
import org.json.JSONArray
import org.json.JSONObject

/** Faithful Appendix A model: matrix in linear sRGB, PCHIP encoded-RGB curves, periodic D65 Lab hue correction.
 * Knots are analytic curve control points, never an image lookup or sampled colour lattice.
 */
data class MatchCurve(val x: DoubleArray, val y: DoubleArray) {
    init { require(x.size == y.size && x.size >= 2 && x.indices.drop(1).all { x[it] > x[it-1] }) }
    val slopes: DoubleArray = run {
        val n=x.size; val h=DoubleArray(n-1) { x[it+1]-x[it] }; val d=DoubleArray(n-1) { (y[it+1]-y[it])/h[it] }
        val out=DoubleArray(n)
        if(n==2) { out[0]=d[0]; out[1]=d[0] } else {
            for(i in 1 until n-1) {
                if(d[i-1]*d[i] > 0) { val w1=2*h[i]+h[i-1]; val w2=h[i]+2*h[i-1]; out[i]=(w1+w2)/(w1/d[i-1]+w2/d[i]) }
            }
            fun end(h0:Double,h1:Double,d0:Double,d1:Double):Double {
                var v=((2*h0+h1)*d0-h0*d1)/(h0+h1)
                if(v*d0<=0) v=0.0 else if(d0*d1<0 && abs(v)>3*abs(d0)) v=3*d0
                return v
            }
            out[0]=end(h[0],h[1],d[0],d[1]);out[n-1]=end(h[n-2],h[n-3],d[n-2],d[n-3])
        };out
    }
    fun apply(v:Double):Double {
        var i=x.binarySearch(v); if(i<0)i=-i-2;i=i.coerceIn(0,x.size-2)
        val h=x[i+1]-x[i]; val t=(v-x[i])/h; val t2=t*t;val t3=t2*t
        return ((2*t3-3*t2+1)*y[i]+(t3-2*t2+t)*h*slopes[i]+(-2*t3+3*t2)*y[i+1]+(t3-t2)*h*slopes[i+1]).coerceIn(0.0,1.0)
    }
}

data class ParametricMatch(
    val matrix: Array<DoubleArray> = Array(3) { r -> DoubleArray(3) { c -> if(r==c)1.0 else 0.0 } },
    val offset: DoubleArray = DoubleArray(3),
    val curves: List<MatchCurve> = List(3) { MatchCurve(doubleArrayOf(0.0,1.0),doubleArrayOf(0.0,1.0)) },
    val hue: Array<DoubleArray> = Array(24) { DoubleArray(3) }
) {
    fun stage12(rgb:DoubleArray, matrixOn:Boolean=true, curvesOn:Boolean=true):DoubleArray {
        val linear=DoubleArray(3) { MatchMath.linear(rgb[it]) }
        val g=if(matrixOn)DoubleArray(3) { c -> MatchMath.encoded((0..2).sumOf { matrix[c][it]*linear[it] }+offset[c]) } else rgb.copyOf()
        return if(curvesOn)DoubleArray(3) { curves[it].apply(g[it]) } else g
    }
    fun hueApply(g:DoubleArray):DoubleArray {
        val lab=MatchMath.lab(g);val h=atan2(lab[2],lab[1]);val c=hypot(lab[1],lab[2]);val w=c/(c+10)
        val k=((h/(2*PI)%1+1)%1)*24;val i=floor(k).toInt()%24;val f=k-floor(k)
        val p=DoubleArray(3) { hue[i][it]*(1-f)+hue[(i+1)%24][it]*f }
        val c2=c*(1+p[1]*w);val h2=h+p[2]*w
        return MatchMath.rgb(doubleArrayOf(lab[0]+p[0]*w,c2*cos(h2),c2*sin(h2)))
    }
    fun apply(rgb:DoubleArray,strength:Double=1.0,matrixOn:Boolean=true,curvesOn:Boolean=true,hueOn:Boolean=true):DoubleArray {
        require(strength.isFinite())
        if(strength==0.0)return rgb.copyOf()
        val g=stage12(rgb,matrixOn,curvesOn);val out=if(hueOn)hueApply(g) else g
        return DoubleArray(3) { (rgb[it]+(out[it]-rgb[it])*strength.coerceIn(0.0,1.0)).coerceIn(0.0,1.0) }
    }
    fun applyArgb(argb:Int,strength:Double=1.0):Int {
        if(strength==0.0)return argb
        val rgb=doubleArrayOf(((argb ushr 16)and 255)/255.0,((argb ushr 8)and 255)/255.0,(argb and 255)/255.0)
        val v=apply(rgb,strength)
        return (argb and -0x1000000) or ((v[0]*255).roundToInt() shl 16) or ((v[1]*255).roundToInt() shl 8) or (v[2]*255).roundToInt()
    }
    fun toJson():JSONObject=JSONObject().put("version",1).put("space","linear-sRGB-matrix_encoded-sRGB-PCHIP_D65-Lab-hue")
        .put("matrix",JSONArray(matrix.map { JSONArray(it.toList()) })).put("offset",JSONArray(offset.toList()))
        .put("curves",JSONArray(curves.map { JSONObject().put("x",JSONArray(it.x.toList())).put("y",JSONArray(it.y.toList())) }))
        .put("hue",JSONArray(hue.map { JSONArray(it.toList()) }))
    companion object {
        fun fromJson(o:JSONObject):ParametricMatch {
            require(o.getInt("version")==1)
            fun vec(a:JSONArray)=DoubleArray(a.length()) { a.getDouble(it) }
            val m=o.getJSONArray("matrix");val c=o.getJSONArray("curves");val h=o.getJSONArray("hue")
            val model=ParametricMatch(Array(3){vec(m.getJSONArray(it))},vec(o.getJSONArray("offset")),List(3){val q=c.getJSONObject(it);MatchCurve(vec(q.getJSONArray("x")),vec(q.getJSONArray("y")))},Array(24){vec(h.getJSONArray(it))})
            require(model.matrix.all { row->row.size==3&&row.all { it.isFinite() } } && model.offset.size==3 && model.offset.all { it.isFinite() })
            require(model.hue.all { row->row.size==3&&row.all { it.isFinite() } })
            return model
        }
    }
}

/** Constants match skimage.color's sRGB/D65 conversion used in the supplied prototype. */
object MatchMath {
    fun linear(v:Double)=if(v<=0.04045)v/12.92 else ((v+0.055)/1.055).pow(2.4)
    fun encoded(v:Double):Double { val x=v.coerceIn(0.0,1.0);return if(x<=0.0031308)12.92*x else 1.055*x.pow(1/2.4)-0.055 }
    fun lab(p:DoubleArray):DoubleArray {
        val r=linear(p[0]);val g=linear(p[1]);val b=linear(p[2])
        fun f(v:Double)=if(v>0.008856)v.pow(1/3.0) else 7.787*v+16/116.0
        val x=f((.412453*r+.357580*g+.180423*b)/.95047);val y=f(.212671*r+.715160*g+.072169*b);val z=f((.019334*r+.119193*g+.950227*b)/1.08883)
        return doubleArrayOf(116*y-16,500*(x-y),200*(y-z))
    }
    fun rgb(l:DoubleArray):DoubleArray {
        val y=(l[0]+16)/116;val x=l[1]/500+y;val z=max(0.0,y-l[2]/200)
        fun f(v:Double)=if(v>.2068966)v*v*v else (v-16/116.0)/7.787
        val xx=f(x)*.95047;val yy=f(y);val zz=f(z)*1.08883
        return doubleArrayOf(encoded(3.24048134*xx-1.53715152*yy-.49853633*zz),encoded(-.96925495*xx+1.87599*yy+.04155593*zz),encoded(.05564664*xx-.20404134*yy+1.05731107*zz))
    }
    fun wrap(v:Double)=((v+PI)%(2*PI)+2*PI)%(2*PI)-PI
    fun solve(a:Array<DoubleArray>, b:DoubleArray):DoubleArray {
        val n=b.size;val m=Array(n){a[it].copyOf()};val y=b.copyOf()
        for(k in 0 until n) {
            val p=(k until n).maxBy { abs(m[it][k]) };require(abs(m[p][k])>1e-15) { "Singular fitting data" }
            val swap=m[p];m[p]=m[k];m[k]=swap;val sy=y[p];y[p]=y[k];y[k]=sy
            val d=m[k][k];for(j in k until n)m[k][j]/=d;y[k]/=d
            for(i in 0 until n)if(i!=k){val f=m[i][k];for(j in k until n)m[i][j]-=f*m[k][j];y[i]-=f*y[k]}
        };return y
    }
}

object ParametricSolver {
    /** Appendix A fitting order and constants, four robust linear solves then 48-bin monotone PCHIP then four hue rounds. */
    fun fit(base:Array<DoubleArray>,target:Array<DoubleArray>,onProgress:(Int,String)->Unit={_,_->}):ParametricMatch {
        require(base.size==target.size&&base.size>=48) { "At least 48 valid aligned samples required" }
        require(base.all { p->p.size==3&&p.all { it.isFinite()&&it in 0.0..1.0 } }&&target.all { p->p.size==3&&p.all { it.isFinite()&&it in 0.0..1.0 } })
        val n=base.size;val x=Array(n){i->doubleArrayOf(MatchMath.linear(base[i][0]),MatchMath.linear(base[i][1]),MatchMath.linear(base[i][2]),1.0)}
        val y=Array(n){i->DoubleArray(3){MatchMath.linear(target[i][it])}};val weights=DoubleArray(n){1.0};var beta=Array(3){DoubleArray(4)}
        repeat(4) {
            if(Thread.currentThread().isInterrupted)throw InterruptedException("Match cancelled")
            val a=Array(4){r->DoubleArray(4){c->if(r==c)1e-4 else 0.0}};val rhs=Array(3){DoubleArray(4)}
            for(i in 0 until n)for(r in 0..3){for(c in 0..3)a[r][c]+=x[i][r]*weights[i]*x[i][c];for(c in 0..2)rhs[c][r]+=x[i][r]*weights[i]*y[i][c]}
            beta=Array(3){MatchMath.solve(a,rhs[it])}
            for(i in 0 until n){val error=(0..2).maxOf { c->abs((0..3).sumOf { beta[c][it]*x[i][it] }-y[i][c]) };weights[i]=1/max(1.0,error/.06)}
            onProgress(it+1,"Robust linear-light matrix")
        }
        val m=ParametricMatch(Array(3){beta[it].copyOfRange(0,3)},DoubleArray(3){beta[it][3]})
        val g=Array(n){m.stage12(base[it],curvesOn=false)}
        val curves=List(3){c->
            val order=(0 until n).sortedBy { g[it][c] };val xs=DoubleArray(48);val ys=DoubleArray(48)
            // Equal input values contain no ordering information. Average their target values before
            // equal-count binning so clipped shadows cannot depend on sort implementation or row order.
            val sortedTarget=DoubleArray(n) { target[order[it]][c] }
            var startTie=0
            while(startTie<n) {
                var endTie=startTie+1
                while(endTie<n && g[order[endTie]][c]==g[order[startTie]][c])endTie++
                if(endTie-startTie>1) { var mean=0.0;for(i in startTie until endTie)mean+=sortedTarget[i];mean/=(endTie-startTie);for(i in startTie until endTie)sortedTarget[i]=mean }
                startTie=endTie
            }
            for(k in 0 until 48){val start=k*n/48;val end=(k+1)*n/48;for(i in start until end){xs[k]+=g[order[i]][c];ys[k]+=sortedTarget[i]};xs[k]/=(end-start);ys[k]/=(end-start)}
            for(i in 1 until 48)ys[i]=max(ys[i],ys[i-1])
            val smooth=DoubleArray(48){i->(-2..2).sumOf { ys[(i+it).coerceIn(0,47)] }/5}
            val xx=doubleArrayOf(0.0)+xs+doubleArrayOf(1.0)
            val yy=doubleArrayOf((smooth[0]-xx[1]).coerceIn(0.0,1.0))+smooth+doubleArrayOf((smooth[47]+1-xx[48]).coerceIn(0.0,1.0))
            for(i in 1 until yy.size)yy[i]=max(yy[i],yy[i-1])
            val keep=xx.indices.filter { it==0||xx[it]-xx[it-1]>1e-4 }
            MatchCurve(keep.map { xx[it] }.toDoubleArray(),keep.map { yy[it] }.toDoubleArray())
        }
        val model=m.copy(curves=curves);val stage=Array(n){model.stage12(base[it])}
        onProgress(5,"Monotone PCHIP tone curves")
        repeat(4){round->
            if(Thread.currentThread().isInterrupted)throw InterruptedException("Match cancelled")
            val num=Array(24){DoubleArray(3)};val den=DoubleArray(24)
            for(i in 0 until n) {
                val lc=MatchMath.lab(model.hueApply(stage[i]));val lt=MatchMath.lab(target[i]);val lg=MatchMath.lab(stage[i])
                val cc=hypot(lc[1],lc[2]);val ct=hypot(lt[1],lt[2]);val cg=hypot(lg[1],lg[2]);val wg=cg/(cg+10)
                val k=((atan2(lg[2],lg[1])/(2*PI)%1+1)%1)*24;val i0=floor(k).toInt()%24;val f=k-floor(k)
                val res=doubleArrayOf(lt[0]-lc[0],(if(cc>3)ct/max(cc,1e-3)-1 else 0.0).coerceIn(-.8,.8),(if(cc>3)MatchMath.wrap(atan2(lt[2],lt[1])-atan2(lc[2],lc[1])) else 0.0).coerceIn(-1.0,1.0))
                for((bin,kw) in listOf(i0 to 1-f,(i0+1)%24 to f)) {for(c in 0..2)num[bin][c]+=kw*wg*res[c];den[bin]+=kw*wg*wg}
            }
            val delta=Array(24){bin->DoubleArray(3){num[bin][it]/(den[bin]+3)}}
            for(bin in 0 until 24)for(c in 0..2)model.hue[bin][c]+=.25*delta[(bin+23)%24][c]+.5*delta[bin][c]+.25*delta[(bin+1)%24][c]
            onProgress(6+round,"Hue-selective refinement ${round+1}/4")
        }
        return model
    }
}
