package com.motionsalt.ccstudio.core

import org.junit.Assert.*
import org.junit.Test
import javax.imageio.ImageIO
import org.json.JSONObject

class ParametricMatchTest {
    private fun image(name:String):MatchFrame {
        val img=ImageIO.read(javaClass.getResourceAsStream("/gojo/$name")!!)
        return MatchFrame.fromArgb(img.width,img.height,img.getRGB(0,0,img.width,img.height,null,0,img.width))
    }
    @Test fun originalGojoPairOwnAlignmentPassesAllQualityGates() {
        val pair=FrameAlignment.align(image("gojo_before.png"),image("gojo_target.png"))
        val train=pair.valid.indices.filter { pair.valid[it]&&it%pair.base.width<(pair.base.width*.8).toInt() }
        val m=ParametricSolver.fit(train.map{pair.base.rgb[it]}.toTypedArray(),train.map{pair.target.rgb[it]}.toTypedArray())
        val out=pair.base.apply(m)
        val valid=pair.valid.indices.filter { pair.valid[it] };val hold=valid.filter{it%pair.base.width>=(pair.base.width*.8).toInt()}
        fun stats(a:MatchFrame,b:MatchFrame,idx:List<Int>):ColourMetrics.Summary=ColourMetrics.summarize(idx.map { val p=MatchMath.lab(a.rgb[it]);val q=MatchMath.lab(b.rgb[it]);ColourMetrics.deltaE2000(ColourMetrics.Lab(p[0],p[1],p[2]),ColourMetrics.Lab(q[0],q[1],q[2])) }.toDoubleArray())
        val identity=stats(pair.base,pair.target,valid);val matched=stats(out,pair.target,valid);val held=stats(out,pair.target,hold);val blurred=stats(out.blur(2.0),pair.target.blur(2.0),valid)
        println("Gojo CIEDE2000: identity=$identity fitted=$matched heldout=$held blurred=$blurred")
        assertTrue(ColourMetrics.meetsGojoGate(identity,matched,blurred));assertEquals(10.12,identity.mean,.10)
        assertEquals(4.86,matched.mean,.486);assertEquals(11.36,matched.p95,1.136)
        assertEquals(4.83,held.mean,.483);assertEquals(9.93,held.p95,.993)
        val reload=ParametricMatch.fromJson(JSONObject(m.toJson().toString()))
        for(i in out.rgb.indices step 47){assertArrayEquals(out.rgb[i],reload.apply(pair.base.rgb[i]),1e-12);assertArrayEquals(pair.base.rgb[i],m.apply(pair.base.rgb[i],0.0),0.0)}
        // Model is a closed transform; replacing any reference after fitting cannot change it.
        val input=pair.base.rgb[9000];val saved=m.apply(input);pair.target.rgb.fill(doubleArrayOf(1.0,0.0,1.0));assertArrayEquals(saved,m.apply(input),0.0)
    }
    @Test fun curvesAreMonotoneAndResistantToEqualInputRowOrder() {
        val curve=MatchCurve(doubleArrayOf(0.0,.2,.7,1.0),doubleArrayOf(0.0,.1,.8,1.0))
        var prev=-1.0;for(i in 0..1000){val y=curve.apply(i/1000.0);assertTrue(y>=prev);prev=y}
        val p=Array(96){doubleArrayOf(.2,.2,.2)};val t=Array(96){i->doubleArrayOf(i/100.0,i/100.0,i/100.0)}
        val a=ParametricSolver.fit(p,t);val b=ParametricSolver.fit(p,t.reversedArray())
        assertArrayEquals(a.apply(doubleArrayOf(.2,.2,.2)),b.apply(doubleArrayOf(.2,.2,.2)),1e-8)
    }
    @Test fun letterboxProposalDoesNotInventBars() {
        val f=image("gojo_before.png");assertTrue(FrameAlignment.letterbox(f).isEmpty())
        val padded=MatchFrame(f.width,f.height+40,Array(f.width*(f.height+40)){i->val y=i/f.width;if(y<20||y>=f.height+20)DoubleArray(3) else f.rgb[(y-20)*f.width+i%f.width]})
        val bars=FrameAlignment.letterbox(padded);assertEquals(2,bars.size);assertEquals(20.0/padded.height,bars[0].bottom,1e-12)
    }
    @Test(expected=IllegalArgumentException::class) fun missingFramesNeverBecomeSyntheticResults() { ParametricSolver.fit(emptyArray(),emptyArray()) }
}
