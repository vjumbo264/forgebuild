package com.forgebuild.everbrowse

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import com.forgebuild.engine.files.SafeSave
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * Download handling via the Engine SafeSave flow (Storage Access Framework).
 *
 * CONTRACT-DRIVEN DEVIATION from the raw request: the operator asked for
 * android.app.DownloadManager writing to the system Downloads folder. The
 * ForgeBuild Engine rule overrides that: downloads go through SafeSave (the
 * user picks the destination with the system document picker), NEVER the
 * native DownloadManager. The download bytes are fetched here and then saved
 * to the user-picked Uri.
 */
object DownloadCoordinator {
    data class PendingDownload(val url: String, val suggestedName: String)

    /** Fetches [pending] and writes it to [target]. Reports completion via [onDone]. */
    fun save(context: Context, pending: PendingDownload, target: Uri, scope: CoroutineScope, onDone: (Boolean) -> Unit) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = URL(pending.url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.instanceFollowRedirects = true
                    conn.connect()
                    conn.inputStream.use { input ->
                        SafeSave.writeStream(context, target, input)
                    }
                }.isSuccess
            }
            onDone(ok)
        }
    }

    /** Guesses a sensible file name + MIME from the URL / content disposition / mimetype hint. */
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
}
