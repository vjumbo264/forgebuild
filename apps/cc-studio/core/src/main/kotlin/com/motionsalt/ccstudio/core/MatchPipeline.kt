package com.motionsalt.ccstudio.core

import kotlin.math.*

data class MatchInput(val base:MatchFrame,val target:MatchFrame,val sameFrame:Boolean=true,val ignored:List<MatchRegion> = emptyList())
data class MatchReport(val identity:ColourMetrics.Summary,val fitted:ColourMetrics.Summary,val holdout:ColourMetrics.Summary,val blurred:ColourMetrics.Summary,val aligned:Boolean)
data class MatchOutput(val model:ParametricMatch,val pairs:List<AlignedMatchPair>,val reports:List<MatchReport>)
object MatchPipeline {
    fun fit(inputs:List<MatchInput>,progress:(Int,String)->Unit={_,_->}):MatchOutput {
        require(inputs.isNotEmpty()) { "Select at least one complete pair" }
        val pairs=inputs.mapIndexed { i,input ->
            progress(0,"Align pair ${i+1}/${inputs.size}")
            if(input.sameFrame)FrameAlignment.align(input.base,input.target,input.ignored) else {
                val t=input.target.resized(input.base.width,input.base.height)
                AlignedMatchPair(input.base,t,BooleanArray(input.base.rgb.size){j->val x=(j%input.base.width).toDouble()/input.base.width;val y=(j/input.base.width).toDouble()/input.base.height;input.ignored.none{it.contains(x,y)}},DoubleArray(6),0.0)
            }
        }
        val training=pairs.map { p->p.valid.indices.filter{p.valid[it]&&it%p.base.width<(p.base.width*.8).toInt()} }
        val count=training.minOf { it.size };require(count>=48) { "Not enough common pixels after crop and exclusions" }
        val pb=ArrayList<DoubleArray>();val pt=ArrayList<DoubleArray>()
        // Equal counts give every pair equal influence. Preserve every pixel for one regression pair.
        for(i in pairs.indices) {
            val p=pairs[i];val indices=training[i];val b=Array(count){p.base.rgb[indices[it*indices.size/count]]}
            val t=if(inputs[i].sameFrame)Array(count){p.target.rgb[indices[it*indices.size/count]]} else distributionTargets(b,Array(count){p.target.rgb[indices[it*indices.size/count]]})
            pb.addAll(b);pt.addAll(t)
        }
        val model=ParametricSolver.fit(pb.toTypedArray(),pt.toTypedArray(),progress)
        val reports=pairs.mapIndexed{i,p->
            val out=p.base.apply(model);val valid=p.valid.indices.filter{p.valid[it]};val held=valid.filter{it%p.base.width>=(p.base.width*.8).toInt()}
            if(inputs[i].sameFrame)MatchReport(stats(p.base,p.target,valid),stats(out,p.target,valid),stats(out,p.target,held),stats(out.blur(2.0),p.target.blur(2.0),valid),true)
            else {
                // Distribution errors, NOT falsely described as aligned pixel accuracy.
                val bt=distributionTargets(Array(valid.size){p.base.rgb[valid[it]]},Array(valid.size){p.target.rgb[valid[it]]})
                val ot=distributionTargets(Array(valid.size){out.rgb[valid[it]]},Array(valid.size){p.target.rgb[valid[it]]})
                val id=summary(Array(valid.size){p.base.rgb[valid[it]]},bt);val fitted=summary(Array(valid.size){out.rgb[valid[it]]},ot)
                MatchReport(id,fitted,fitted,fitted,false)
            }
        }
        return MatchOutput(model,pairs,reports)
    }
    fun stats(a:MatchFrame,b:MatchFrame,idx:List<Int>)=summary(Array(idx.size){a.rgb[idx[it]]},Array(idx.size){b.rgb[idx[it]]})
    private fun summary(a:Array<DoubleArray>,b:Array<DoubleArray>)=ColourMetrics.summarize(DoubleArray(a.size){i->val x=MatchMath.lab(a[i]);val y=MatchMath.lab(b[i]);ColourMetrics.deltaE2000(ColourMetrics.Lab(x[0],x[1],x[2]),ColourMetrics.Lab(y[0],y[1],y[2]))})
    /** Sliced transport over RGB axes and joint RGB directions; no false spatial correspondences. */
    private fun distributionTargets(base:Array<DoubleArray>,target:Array<DoubleArray>):Array<DoubleArray> {
        val working=Array(base.size){base[it].copyOf()};val dirs=arrayOf(doubleArrayOf(1.0,0.0,0.0),doubleArrayOf(0.0,1.0,0.0),doubleArrayOf(0.0,0.0,1.0),doubleArrayOf(.577350269,.577350269,.577350269),doubleArrayOf(.707106781,-.707106781,0.0),doubleArrayOf(.40824829,.40824829,-.816496581))
        repeat(4){for(d in dirs){fun dot(p:DoubleArray):Double { return (0..2).sumOf{p[it]*d[it]} }
            val bo=base.indices.sortedBy{dot(working[it])};val to=target.indices.sortedBy{dot(target[it])};for(k in bo.indices){val i=bo[k];val shift=dot(target[to[k]])-dot(working[i]);for(c in 0..2)working[i][c]=(working[i][c]+shift*d[c]).coerceIn(0.0,1.0)}}}
        return working
    }
    fun heatmap(pair:AlignedMatchPair,model:ParametricMatch):IntArray {
        val out=pair.base.apply(model)
        return IntArray(pair.valid.size){i->if(!pair.valid[i])0xff101217.toInt() else {
            val x=MatchMath.lab(out.rgb[i]);val y=MatchMath.lab(pair.target.rgb[i]);val e=ColourMetrics.deltaE2000(ColourMetrics.Lab(x[0],x[1],x[2]),ColourMetrics.Lab(y[0],y[1],y[2]));val t=(e/15).coerceIn(0.0,1.0)
            val stops=arrayOf(intArrayOf(5,3,22),intArrayOf(87,15,109),intArrayOf(188,55,84),intArrayOf(249,142,9),intArrayOf(252,255,164));val k=(t*4).toInt().coerceAtMost(3);val f=t*4-k
            val c=IntArray(3){(stops[k][it]*(1-f)+stops[k+1][it]*f).roundToInt()};0xff000000.toInt() or (c[0] shl 16) or (c[1] shl 8) or c[2]
        }}
    }
}
