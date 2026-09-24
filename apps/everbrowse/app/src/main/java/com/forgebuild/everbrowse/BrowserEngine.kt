package com.forgebuild.everbrowse

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Resolves what the user typed into an address-bar entry: a URL, home, or a Google search. */
object AddressResolver {
    const val HOME_URL = "everbrowse://home"

    fun resolve(input: String): String {
        val q = input.trim()
        if (q.isEmpty() || q.equals("home", ignoreCase = true) || q == HOME_URL || q == "about:blank") {
            return HOME_URL
        }
        val looksLikeUrl = q.startsWith("http://") || q.startsWith("https://") ||
            (!q.contains(" ") && q.contains(".") && !q.startsWith("."))
        return if (looksLikeUrl) {
            if (q.startsWith("http://") || q.startsWith("https://")) q else "https://$q"
        } else {
            "https://www.google.com/search?q=" + Uri.encode(q)
        }
    }
}

/**
 * Downloads directly to the system Downloads folder.
 * Uses MediaStore.Downloads (API 29+) and Environment.DIRECTORY_DOWNLOADS (legacy).
 * Carries browser session cookies and headers so protected downloads succeed.
 */
object DownloadCoordinator {
    data class PendingDownload(val url: String, val suggestedName: String, val mimeType: String)

    fun saveToDownloads(
        context: Context,
        pending: PendingDownload,
        scope: CoroutineScope,
        onDone: (Boolean, String) -> Unit
    ) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = URL(pending.url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.instanceFollowRedirects = true

                    // Pass web cookies so downloads behind logins/sessions work properly
                    val cookie = CookieManager.getInstance().getCookie(pending.url)
                    if (!cookie.isNullOrBlank()) {
                        conn.setRequestProperty("Cookie", cookie)
                    }
                    conn.connect()

                    val name = sanitizeFilename(pending.suggestedName)
                    var savedPath = ""

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val resolver = context.contentResolver
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, name)
                            put(MediaStore.Downloads.MIME_TYPE, pending.mimeType)
                            put(MediaStore.Downloads.IS_PENDING, 1)
                        }
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                            ?: throw IllegalStateException("Cannot create MediaStore download entry")

                        resolver.openOutputStream(uri)?.use { out ->
                            conn.inputStream.use { input -> input.copyTo(out) }
                        } ?: throw IllegalStateException("Cannot open output stream for download")

                        contentValues.clear()
                        contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                        savedPath = "Downloads/$name"
                    } else {
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        if (!downloadsDir.exists()) downloadsDir.mkdirs()
                        val targetFile = File(downloadsDir, name)
                        FileOutputStream(targetFile).use { out ->
                            conn.inputStream.use { input -> input.copyTo(out) }
                        }
                        MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)
                        savedPath = targetFile.absolutePath
                    }
                    savedPath
                }
            }

            if (result.isSuccess) {
                onDone(true, result.getOrNull() ?: pending.suggestedName)
            } else {
                onDone(false, result.exceptionOrNull()?.message ?: "Download error")
            }
        }
    }

    fun guessName(url: String, contentDisposition: String?, mimeType: String?): Pair<String, String> {
        var name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val mime = when {
            !mimeType.isNullOrBlank() -> mimeType
            name.contains('.') -> MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(name.substringAfterLast('.').lowercase()) ?: "application/octet-stream"
            else -> "application/octet-stream"
        }
        if (!name.contains('.')) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { name = "$name.$it" }
        }
        return name to mime
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim()
    }
}
