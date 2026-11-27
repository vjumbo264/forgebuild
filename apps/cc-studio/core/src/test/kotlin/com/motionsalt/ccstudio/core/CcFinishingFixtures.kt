package com.motionsalt.ccstudio.core

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/** Writes independent CPU reference buffers for tools/verify_cc_gles.py. */
object CcFinishingFixtures {
    @JvmStatic fun main(args: Array<String>) {
        val dir = File(args.single()).apply { mkdirs() }
        val gojo = ImageIO.read(javaClass.getResourceAsStream("/gojo/gojo_before.png")!!)
        val target = ImageIO.read(javaClass.getResourceAsStream("/gojo/gojo_target.png")!!)
        val mark = BufferedImage(96, 64, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until mark.height) for (x in 0 until mark.width) {
            val f = (x in 15..22 && y in 10..51) || (x in 15..63 && y in 10..16) || (x in 15..48 && y in 28..34)
            mark.setRGB(x,y, when {
                f -> 0xffffe099.toInt()
                x > 67 && y > 40 -> 0x8070bbef.toInt()
                else -> 0xff331122.toInt()
            })
        }
        for ((name, image) in listOf("gojo" to gojo, "gojo_target" to target, "f" to mark)) {
            val w = image.width; val h = image.height
            val pixels = FloatArray(w*h*4)
            for (y in 0 until h) for (x in 0 until w) {
                val argb = image.getRGB(x, h-1-y); val at = (y*w+x)*4
                val alpha = ((argb ushr 24) and 255) / 255f
                for (c in 0..2) pixels[at+c] = ((((argb ushr (16-8*c)) and 255)*alpha).roundToInt()) / 255f
                pixels[at+3] = alpha
            }
            val frame = CcFrame(w,h,pixels)
            fun write(label: String, result: CcFrame) {
                val out = BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until h) for (x in 0 until w) {
                    val at = (y*w+x)*4
                    fun byte(c: Int) = (255 * result.rgba[at+c]).roundToInt().coerceIn(0,255)
                    out.setRGB(x,h-1-y,(byte(3) shl 24) or (byte(0) shl 16) or (byte(1) shl 8) or byte(2))
                }
                ImageIO.write(out,"png",File(dir,"${name}_$label.png"))
            }
            write("input",frame)
            check(CcFinishing.detail(frame,0.0,0.0) === frame)
            check(CcFinishing.unsharp(frame,0.0,1.0,0.0) === frame)
            check(CcFinishing.haze(frame,CcHaze(intensity=0.0)) === frame)
            write("clarity", CcFinishing.detail(frame, 100.0, 0.0))
            write("structure",CcFinishing.detail(frame, 0.0, -100.0))
            write("unsharp",CcFinishing.unsharp(frame,50.0,1.0,0.0))
            write("unsharp_extreme",CcFinishing.unsharp(frame,500.0,100.0,15.0))
            write("haze",CcFinishing.haze(frame,CcHaze()))
            write("haze_extreme",CcFinishing.haze(frame,CcHaze(1.0,0.0,1.0,.60,.10,.12,1.0,15.0,1)))
        }
    }
}
