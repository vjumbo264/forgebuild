package com.motionsalt.ccstudio.core

import kotlin.math.*

/** Straight encoded sRGB, row-major TOP-LEFT origin, Float64 CPU fitting. */
data class MatchFrame(val width:Int,val height:Int,val rgb:Array<DoubleArray>) {
    init { require(width>0&&height>0&&rgb.size==width*height) }
    fun sample(x:Double,y:Double):DoubleArray {
        val xx=x.coerceIn(0.0,width-1.0);val yy=y.coerceIn(0.0,height-1.0);val ix=floor(xx).toInt();val iy=floor(yy).toInt()
        val jx=min(ix+1,width-1);val jy=min(iy+1,height-1);val fx=xx-ix;val fy=yy-iy
        return DoubleArray(3){c->(rgb[iy*width+ix][c]*(1-fx)+rgb[iy*width+jx][c]*fx)*(1-fy)+(rgb[jy*width+ix][c]*(1-fx)+rgb[jy*width+jx][c]*fx)*fy}
    }
    fun resized(w:Int,h:Int)=MatchFrame(w,h,Array(w*h){sample((it%w+.5)*width/w-.5,(it/w+.5)*height/h-.5)})
    fun blur(sigma:Double):MatchFrame {
        val radius=ceil(4*sigma).toInt();val kernel=DoubleArray(2*radius+1){exp(-.5*((it-radius)/sigma).pow(2))};val sum=kernel.sum();for(i in kernel.indices)kernel[i]/=sum
        val tmp=Array(rgb.size){DoubleArray(3)};val out=Array(rgb.size){DoubleArray(3)}
        // Reflect-without-edge repetition matches OpenCV BORDER_REFLECT_101.
        fun reflect(i:Int,n:Int):Int { if(n==1)return 0;val p=2*(n-1);val k=(i%p+p)%p;return if(k<n)k else p-k }
        for(y in 0 until height)for(x in 0 until width)for(k in kernel.indices)for(c in 0..2)tmp[y*width+x][c]+=kernel[k]*rgb[y*width+reflect(x+k-radius,width)][c]
        for(y in 0 until height)for(x in 0 until width)for(k in kernel.indices)for(c in 0..2)out[y*width+x][c]+=kernel[k]*tmp[reflect(y+k-radius,height)*width+x][c]
        return MatchFrame(width,height,out)
    }
    fun apply(m:ParametricMatch)=copy(rgb=Array(rgb.size){m.apply(rgb[it])})
    companion object {
        fun fromArgb(w:Int,h:Int,p:IntArray)=MatchFrame(w,h,Array(p.size){doubleArrayOf(((p[it] ushr 16)and 255)/255.0,((p[it] ushr 8)and 255)/255.0,(p[it]and 255)/255.0)})
    }
}

data class MatchRegion(val left:Double,val top:Double,val right:Double,val bottom:Double) {
    fun contains(x:Double,y:Double)=x>=left&&x<right&&y>=top&&y<bottom
}

data class AlignedMatchPair(val base:MatchFrame,val target:MatchFrame,val valid:BooleanArray,val affine:DoubleArray,val correlation:Double)

object FrameAlignment {
    /** Six-parameter affine edge-map registration, normalized correlation Gauss-Newton, coarse to fine.
     * Translation is in pixels; linear terms use centered coordinates. Targets, never bases, are warped.
     */
    fun align(base:MatchFrame,targetInput:MatchFrame,ignore:List<MatchRegion> = emptyList()):AlignedMatchPair {
        val target=if(base.width!=targetInput.width||base.height!=targetInput.height)targetInput.resized(base.width,base.height) else targetInput
        val w=base.width;val h=base.height;val p=DoubleArray(6);var corr=0.0
        fun edge(frame:MatchFrame):DoubleArray {
            val f=frame.blur(1.2);val gray=DoubleArray(w*h){.299*f.rgb[it][0]+.587*f.rgb[it][1]+.114*f.rgb[it][2]}
            fun v(x:Int,y:Int)=gray[y.coerceIn(0,h-1)*w+x.coerceIn(0,w-1)]
            return DoubleArray(w*h){i->val x=i%w;val y=i/w;val gx=v(x+1,y-1)+2*v(x+1,y)+v(x+1,y+1)-v(x-1,y-1)-2*v(x-1,y)-v(x-1,y+1);val gy=v(x-1,y+1)+2*v(x,y+1)+v(x+1,y+1)-v(x-1,y-1)-2*v(x,y-1)-v(x+1,y-1);hypot(gx,gy)}
        }
        val eb=edge(base);val et=edge(target)
        fun sample(a:DoubleArray,x:Double,y:Double):Double {
            val xx=x.coerceIn(0.0,w-1.0);val yy=y.coerceIn(0.0,h-1.0);val ix=floor(xx).toInt();val iy=floor(yy).toInt();val jx=min(ix+1,w-1);val jy=min(iy+1,h-1);val fx=xx-ix;val fy=yy-iy
            return (a[iy*w+ix]*(1-fx)+a[iy*w+jx]*fx)*(1-fy)+(a[jy*w+ix]*(1-fx)+a[jy*w+jx]*fx)*fy
        }
        for(step in intArrayOf(4,2,1)) {
            val indices=(0 until w*h step step).filter { i -> val x=i%w;val y=i/w;x>=6&&y>=6&&x<w-6&&y<h-6&&ignore.none{it.contains(x.toDouble()/w,y.toDouble()/h)} }
            if(indices.size<48)continue
            val ref=DoubleArray(indices.size){eb[indices[it]]};val rm=ref.average();val rs=sqrt(ref.sumOf{(it-rm).pow(2)}/ref.size).coerceAtLeast(1e-9)
            val rn=DoubleArray(ref.size){(ref[it]-rm)/rs}
            for(iter in 0 until 60) {
                if(Thread.currentThread().isInterrupted)throw InterruptedException("Alignment cancelled")
                val valT=DoubleArray(indices.size);val jac=Array(indices.size){DoubleArray(6)}
                for(k in indices.indices) {
                    val x=indices[k]%w;val y=indices[k]/w;val xx=(x-w*.5)/w;val yy=(y-h*.5)/h
                    val tx=x+p[0]+p[2]*xx+p[3]*yy;val ty=y+p[1]+p[4]*xx+p[5]*yy
                    valT[k]=sample(et,tx,ty);val gx=(sample(et,tx+.5,ty)-sample(et,tx-.5,ty));val gy=(sample(et,tx,ty+.5)-sample(et,tx,ty-.5))
                    jac[k]=doubleArrayOf(gx,gy,gx*xx,gx*yy,gy*xx,gy*yy)
                }
                val mean=valT.average();val std=sqrt(valT.sumOf{(it-mean).pow(2)}/valT.size).coerceAtLeast(1e-9)
                val jmean=DoubleArray(6){c->jac.sumOf{it[c]}/jac.size};val jstd=DoubleArray(6){c->indices.indices.sumOf{(valT[it]-mean)*jac[it][c]}/(jac.size*std)}
                val a=Array(6){r->DoubleArray(6){c->if(r==c)1e-5 else 0.0}};val b=DoubleArray(6);var cross=0.0
                for(k in indices.indices) {
                    val norm=(valT[k]-mean)/std;val residual=rn[k]-norm;cross+=rn[k]*norm
                    val j=DoubleArray(6){(jac[k][it]-jmean[it]-norm*jstd[it])/std}
                    for(r in 0..5){b[r]+=j[r]*residual;for(c in 0..5)a[r][c]+=j[r]*j[c]}
                }
                corr=cross/indices.size
                val delta=MatchMath.solve(a,b);for(i in p.indices)p[i]+=delta[i].coerceIn(-1.0,1.0)
                if(delta.maxOf{abs(it)}<1e-4)break
            }
        }
        val valid=BooleanArray(w*h);val warped=Array(w*h){i->val x=i%w;val y=i/w;val xx=(x-w*.5)/w;val yy=(y-h*.5)/h;val tx=x+p[0]+p[2]*xx+p[3]*yy;val ty=y+p[1]+p[4]*xx+p[5]*yy
            valid[i]=x>=6&&y>=6&&x<w-6&&y<h-6&&tx>=0&&ty>=0&&tx<w-1&&ty<h-1&&ignore.none{it.contains(x.toDouble()/w,y.toDouble()/h)||it.contains(tx/w,ty/h)}
            target.sample(tx,ty)
        }
        return AlignedMatchPair(base,MatchFrame(w,h,warped),valid,p,corr)
    }

    /** Only propose long contiguous edge bands with >=98% nearly black pixels. Never silently crop real shadows. */
    fun letterbox(frame:MatchFrame):List<MatchRegion> {
        fun row(y:Int)=(0 until frame.width).count{x->frame.rgb[y*frame.width+x].all{it<.025}}>=frame.width*.98
        var top=0;while(top<frame.height/3&&row(top))top++
        var bottom=frame.height;while(bottom>frame.height*2/3&&row(bottom-1))bottom--
        return buildList { if(top>0)add(MatchRegion(0.0,0.0,1.0,top.toDouble()/frame.height));if(bottom<frame.height)add(MatchRegion(0.0,bottom.toDouble()/frame.height,1.0,1.0)) }
    }
}
