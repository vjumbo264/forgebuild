package com.forgebuild.clipforge.pipeline

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.forgebuild.clipforge.data.ForgeSettings
import com.forgebuild.clipforge.data.ProductionPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * On-device counterpart of the bot's Stage B render pipeline
 * (render.py / reframe.py / captions.py / watermark.py), implemented with FFmpeg:
 * cut each segment, reframe to 9:16 vertical, burn captions + watermark, concat.
 */
object ClipRenderer {

    sealed class Result {
        data class Success(val output: File) : Result()
        data class Failure(val message: String) : Result()
    }

    suspend fun render(
        context: Context,
        input: File,
        plan: ProductionPlan,
        settings: ForgeSettings,
        onProgress: (String) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        try {
            val workDir = File(context.cacheDir, "render_${System.currentTimeMillis()}")
            workDir.mkdirs()
            val parts = mutableListOf<File>()

            plan.segments.forEachIndexed { idx, seg ->
                onProgress("Rendering segment ${idx + 1}/${plan.segments.size}…")
                val part = File(workDir, "part_$idx.mp4")
                val filters = mutableListOf<String>()
                if (plan.vertical && settings.verticalReframe) {
                    // 9:16 vertical reframe: scale to height 1280, crop center 720 wide
                    filters.add("scale=-2:1280,crop=720:1280")
                }
                if (plan.captionsEnabled && settings.captionsEnabled && seg.caption.isNotBlank()) {
                    val safe = seg.caption.replace("'", "").replace(":", " ")
                    filters.add(
                        "drawtext=text='$safe':fontsize=42:fontcolor=white:borderw=3:bordercolor=black:" +
                            "x=(w-text_w)/2:y=h*0.78"
                    )
                }
                val wm = if (plan.watermarkText.isNotBlank()) plan.watermarkText else settings.watermarkText
                if (wm.isNotBlank()) {
                    val safe = wm.replace("'", "").replace(":", " ")
                    filters.add(
                        "drawtext=text='$safe':fontsize=28:fontcolor=white@0.7:borderw=2:bordercolor=black@0.5:" +
                            "x=w-text_w-24:y=24"
                    )
                }
                val vf = if (filters.isEmpty()) "null" else filters.joinToString(",")
                val dur = (seg.endSec - seg.startSec).coerceAtLeast(0.5f)
                val cmd = "-y -ss ${seg.startSec} -t $dur -i \"${input.absolutePath}\" " +
                    "-vf \"$vf\" -c:v libx264 -preset veryfast -crf 26 -c:a aac -b:a 96k \"${part.absolutePath}\""
                val ok = runFfmpeg(cmd)
                if (!ok) return@withContext Result.Failure("FFmpeg failed on segment ${idx + 1}")
                parts.add(part)
            }

            val outDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "clips")
            outDir.mkdirs()
            val output = File(outDir, "${plan.title.replace(Regex("[^A-Za-z0-9_-]"), "_")}.mp4")

            if (parts.size == 1) {
                parts[0].renameTo(output)
            } else {
                onProgress("Joining ${parts.size} segments…")
                val listFile = File(workDir, "concat.txt")
                listFile.writeText(parts.joinToString("\n") { "file '${it.absolutePath}'" })
                val ok = runFfmpeg(
                    "-y -f concat -safe 0 -i \"${listFile.absolutePath}\" -c copy \"${output.absolutePath}\""
                )
                if (!ok) return@withContext Result.Failure("FFmpeg concat failed")
            }
            workDir.deleteRecursively()
            onProgress("Done")
            Result.Success(output)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Unknown render error")
        }
    }

    private suspend fun runFfmpeg(cmd: String): Boolean = suspendCancellableCoroutine { cont ->
        FFmpegKit.executeAsync(cmd) { session ->
            cont.resume(ReturnCode.isSuccess(session.returnCode))
        }
    }
}
