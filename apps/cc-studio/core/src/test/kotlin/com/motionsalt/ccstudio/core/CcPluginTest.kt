package com.motionsalt.ccstudio.core

import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CcPluginTest {
    @Test fun exactlyOneEffectPerLookAndSameShaderBytes() {
        val model = ParametricMatch()
        val looks = listOf(CcPlugin.Look("one","Look One",model), CcPlugin.Look("two","Look Two",model))
        val entries = CcPlugin.entries(looks, 1)
        val manifest = JSONObject(entries.getValue("manifest.json").decodeToString())
        assertEquals("motionsalt.cc",manifest.getString("id"))
        assertEquals(2,manifest.getJSONArray("effects").length())
        assertEquals(2,entries.keys.count { it.endsWith("/effect.json") })
        for (look in looks) {
            val effect = JSONObject(entries.getValue("effects/${look.id}/effect.json").decodeToString())
            assertEquals(8,effect.getJSONArray("passes").length())
            val params = effect.getJSONArray("params")
            assertEquals(5,(0 until params.length()).map { params.getJSONObject(it).getString("group") }.toSet().size)
            assertTrue(params.length() <= 40)
            assertEquals(1,effect.getJSONObject("newInstanceValues").getInt("matchStrength"))
            for ((i,pass) in CcPlugin.passes(model).withIndex()) {
                assertArrayEquals(pass.source.encodeToByteArray(),entries.getValue("effects/${look.id}/pass$i.frag"))
            }
        }
        ZipInputStream(ByteArrayInputStream(CcPlugin.zip(entries))).use { zip ->
            val seen = mutableSetOf<String>()
            while (true) {
                val entry = zip.nextEntry ?: break
                assertTrue(seen.add(entry.name))
                assertArrayEquals(entries.getValue(entry.name),zip.readBytes())
            }
            assertEquals(entries.keys,seen)
        }
    }

    companion object {
        @JvmStatic fun main(args: Array<String>) {
            val root = File(args.single()).apply { mkdirs() }
            fun load(name: String): MatchFrame {
                val image = ImageIO.read(CcPluginTest::class.java.getResourceAsStream("/gojo/$name")!!)
                return MatchFrame.fromArgb(image.width,image.height,image.getRGB(0,0,image.width,image.height,null,0,image.width))
            }
            val fit = MatchPipeline.fit(listOf(MatchInput(load("gojo_before.png"),load("gojo_target.png"))))
            val entries = CcPlugin.entries(listOf(CcPlugin.Look("gojo","Gojo CC",fit.model)),1)
            for ((name,bytes) in entries) File(root,name).apply { parentFile.mkdirs(); writeBytes(bytes) }
            File(root.parentFile,"motionsalt.cc.kfplug").writeBytes(CcPlugin.zip(entries))
            val match = fit.pairs.single().base.apply(fit.model)
            val image = java.awt.image.BufferedImage(match.width,match.height,java.awt.image.BufferedImage.TYPE_INT_RGB)
            for (y in 0 until match.height) for (x in 0 until match.width) {
                val rgb = match.rgb[y*match.width+x]
                val channels = rgb.map { (it * 255 + .5).toInt().coerceIn(0,255) }
                image.setRGB(x,y,(channels[0] shl 16) or (channels[1] shl 8) or channels[2])
            }
            ImageIO.write(image,"png",File(root.parentFile,"gojo_match_cpu.png"))
        }
    }
}
