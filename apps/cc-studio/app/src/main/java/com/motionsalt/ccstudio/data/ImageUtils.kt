package com.motionsalt.ccstudio.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

object ImageUtils {

    /**
     * Safely decode, rotate according to EXIF orientation, and downscale large frames
     * into project-local storage. Supports JPEG, PNG, WEBP, and HEIC.
     */
    fun importImageToProject(
        context: Context,
        uri: Uri,
        destDir: File,
        baseName: String = "frame"
    ): String {
        if (!destDir.exists()) destDir.mkdirs()
        val tempRaw = File(destDir, "raw_${System.currentTimeMillis()}.tmp")

        // 1. Copy stream to temp file to read EXIF and bounds
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempRaw).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not open input stream for URI: $uri")

        // 2. Read dimensions & downscale if larger than 2048
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tempRaw.absolutePath, options)
        val origW = options.outWidth
        val origH = options.outHeight

        var sampleSize = 1
        val maxDim = max(origW, origH)
        if (maxDim > 2048) {
            sampleSize = (maxDim / 2048f).toInt().coerceAtLeast(1)
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(tempRaw.absolutePath, decodeOptions)
            ?: throw IllegalStateException("Failed to decode image from file: $uri")

        // 3. EXIF orientation correction
        val rotation = try {
            val exif = ExifInterface(tempRaw.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: Exception) {
            0f
        }

        val finalBitmap = if (rotation != 0f) {
            val matrix = Matrix().apply { postRotate(rotation) }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated != decoded) decoded.recycle()
            rotated
        } else {
            decoded
        }

        // 4. Save to destination file (PNG or high-quality JPEG)
        val finalFile = File(destDir, "${baseName}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(finalFile).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        finalBitmap.recycle()
        tempRaw.delete()

        return finalFile.absolutePath
    }
}
