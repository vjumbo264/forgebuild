package com.forgebuild.clipforge.pipeline

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.forgebuild.clipforge.data.ForgeSettings
import com.forgebuild.clipforge.data.ProductionPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * On-device counterpart of the bot's Stage B render step (render.py): cuts the
 * requested segments from the source and concatenates them into one clip.
 * Uses Android's built-in MediaExtractor/MediaMuxer (stream copy, no re-encode,
 * no native dependency — keeps the APK lean). Cuts land on keyframes.
 * Captions / 9:16 reframe / watermark burn-in stay in the clone's cloud Stage B
 * (render.py/reframe.py/captions.py/watermark.py) — the on-device cut is the
 * quick local preview path of the same plan.
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
            val outDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "clips")
            outDir.mkdirs()
            val output = File(outDir, "${plan.title.replace(Regex("[^A-Za-z0-9_-]"), "_")}.mp4")

            val extractor = MediaExtractor()
            extractor.setDataSource(input.absolutePath)
            val trackCount = extractor.trackCount
            val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val indexMap = HashMap<Int, Int>(trackCount)
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    indexMap[i] = muxer.addTrack(format)
                }
            }
            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val info = MediaCodec.BufferInfo()
            var timeOffsetUs = 0L
            var lastSegmentEndUs = 0L

            plan.segments.forEachIndexed { idx, seg ->
                onProgress("Cutting segment ${idx + 1}/${plan.segments.size}…")
                val startUs = (seg.startSec * 1_000_000).toLong()
                val endUs = (seg.endSec * 1_000_000).toLong()
                var segmentLastUs = startUs
                for ((srcIndex, dstIndex) in indexMap) {
                    extractor.unselectTrack(srcIndex)
                    extractor.selectTrack(srcIndex)
                    extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    while (true) {
                        buffer.clear()
                        info.offset = 0
                        info.size = extractor.readSampleData(buffer, 0)
                        if (info.size < 0) break
                        val pts = extractor.sampleTime
                        if (pts < 0 || pts > endUs) break
                        val isVideo = extractor.getTrackFormat(srcIndex)
                            .getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
                        if (pts > segmentLastUs) segmentLastUs = pts
                        info.presentationTimeUs = pts - startUs + timeOffsetUs
                        info.flags = if (isVideo &&
                            (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                        ) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        muxer.writeSampleData(dstIndex, buffer, info)
                        extractor.advance()
                    }
                }
                timeOffsetUs += (segmentLastUs - startUs).coerceAtLeast(0L)
                lastSegmentEndUs = timeOffsetUs
            }

            muxer.stop()
            muxer.release()
            extractor.release()
            if (lastSegmentEndUs <= 0L || output.length() == 0L) {
                output.delete()
                return@withContext Result.Failure("No media samples found in the requested range")
            }
            onProgress("Done")
            Result.Success(output)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Unknown render error")
        }
    }
}
