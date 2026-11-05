import com.motionsalt.ccstudio.core.*
import java.io.File
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import org.json.JSONObject

/** Audit-only reproduction of the pre-pivot app path. NOT the replacement match solver. */
fun main(args: Array<String>) {
    require(args.size == 2) { "Pass fixture directory and evidence output directory" }
    val dir = File(args[0]); val output = File(args[1]); output.mkdirs()
    val base = ImageIO.read(File(dir, "gojo_before.png"))
    val target = ImageIO.read(File(dir, "gojo_target.png"))
    require(base.width == 560 && base.height == 259 && base.width == target.width && base.height == target.height)
    val w = base.width; val h = base.height
    val pairs = mutableListOf<PixelPair>()
    fun rgb(v: Int) = Triple(((v ushr 16) and 255) / 255f, ((v ushr 8) and 255) / 255f, (v and 255) / 255f)
    for (y in 0 until h step 8) for (x in 0 until w step 8) pairs.add(PixelPair(rgb(base.getRGB(x, y)), rgb(target.getRGB(x, y))))
    val fit = LutSolver.fit(pairs, 17)
    val before = StackRenderer.PixelBuffer(w, h, base.getRGB(0, 0, w, h, null, 0, w))
    val recreated = StackRenderer.applyLut(before, fit.transform, 1f)
    val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, w, h, recreated.pixels, 0, w)
    ImageIO.write(image, "png", File(output, "legacy_recreated.png"))
    val idErrors = mutableListOf<Double>(); val outErrors = mutableListOf<Double>()
    for (y in 4 until h - 4) for (x in 4 until w - 4) {
        val reference = ColourMetrics.lab(target.getRGB(x, y))
        idErrors.add(ColourMetrics.deltaE2000(ColourMetrics.lab(base.getRGB(x, y)), reference))
        outErrors.add(ColourMetrics.deltaE2000(ColourMetrics.lab(recreated.pixels[y * w + x]), reference))
    }
    val id = ColourMetrics.summarize(idErrors.toDoubleArray()); val rec = ColourMetrics.summarize(outErrors.toDoubleArray())
    val result = JSONObject().put("scope", "Legacy solver audit; unaligned originals, 4px border. No new no-LUT solver or acceptance claim.")
        .put("legacy_training_cie76", JSONObject().put("mean", fit.meanDeltaE).put("p95", fit.p95DeltaE))
        .put("identity_ciede2000", JSONObject().put("mean", id.mean).put("p95", id.p95).put("count", id.count))
        .put("legacy_ciede2000", JSONObject().put("mean", rec.mean).put("p95", rec.p95).put("count", rec.count))
    File(output, "legacy_kotlin_metrics.json").writeText(result.toString(2))
    println(result.toString(2))
}
