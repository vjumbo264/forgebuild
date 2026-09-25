package com.forgebuild.everbrowse

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

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

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadRecord(
    val id: String = UUID.randomUUID().toString(),
    var url: String,
    var fileName: String,
    var mimeType: String,
    var totalBytes: Long = -1L,
    var downloadedBytes: Long = 0L,
    var status: DownloadStatus = DownloadStatus.PENDING,
    var localPath: String? = null,
    var contentUri: String? = null,
    var errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = when {
            status == DownloadStatus.COMPLETED -> 1f
            totalBytes > 0 -> (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            status == DownloadStatus.DOWNLOADING || status == DownloadStatus.PAUSED -> 0.15f
            else -> 0f
        }
}

/**
 * DownloadCoordinator handles:
 * - Real buffered stream downloads with HTTP Range (206) pause & resume
 * - Full manual redirect chain following to preserve cookies and session credentials
 * - Browser navigation headers to prevent CDN HTML challenges or fake 8KB error pages
 * - Live interactive notification bar with progress percentage, speed/size, and Pause/Resume/Cancel actions
 * - Saving to the public system Downloads folder indexed by MediaScanner
 */
object DownloadCoordinator {
    const val CHANNEL_ID = "everbrowse_downloads"
    private const val PREFS_NAME = "everbrowse_downloads_prefs"
    private const val PREFS_KEY = "download_history"

    val downloads = mutableStateListOf<DownloadRecord>()

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeTasks = ConcurrentHashMap<String, DownloadTask>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private class DownloadTask(
        val record: DownloadRecord,
        val pending: PendingDownload,
        var job: Job? = null,
        var currentConn: HttpURLConnection? = null,
        @Volatile var isPaused: Boolean = false,
        @Volatile var isCancelled: Boolean = false
    )

    data class PendingDownload(
        val url: String,
        val suggestedName: String,
        val mimeType: String,
        val userAgent: String?,
        val referer: String?,
        val contentLength: Long
    )

    fun init(context: Context) {
        ensureChannel(context)
        loadHistory(context)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "EverBrowse Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Displays live progress and actions for downloading files"
                    setShowBadge(false)
                    enableVibration(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun startDownload(
        context: Context,
        pending: PendingDownload,
        scope: CoroutineScope? = null,
        onComplete: ((Boolean, String) -> Unit)? = null
    ): DownloadRecord {
        ensureChannel(context)
        val record = DownloadRecord(
            url = pending.url,
            fileName = sanitizeFilename(pending.suggestedName),
            mimeType = pending.mimeType,
            totalBytes = pending.contentLength,
            status = DownloadStatus.DOWNLOADING
        )
        downloads.add(0, record)
        saveHistory(context)

        val task = DownloadTask(record, pending)
        activeTasks[record.id] = task

        val notificationId = record.id.hashCode() and 0x7FFFFFFF
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        postProgressNotification(context, nm, notificationId, record)

        val actualScope = scope ?: downloadScope
        task.job = actualScope.launch(Dispatchers.IO) {
            executeDownloadLoop(context, nm, notificationId, task, onComplete)
        }
        return record
    }

    fun pauseDownload(context: Context, downloadId: String) {
        val task = activeTasks[downloadId]
        val record = downloads.find { it.id == downloadId } ?: task?.record ?: return

        task?.isPaused = true
        task?.currentConn?.disconnect()
        task?.job?.cancel()

        record.status = DownloadStatus.PAUSED
        saveHistory(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = record.id.hashCode() and 0x7FFFFFFF
        postPausedNotification(context, nm, notificationId, record)
    }

    fun resumeDownload(context: Context, downloadId: String) {
        val record = downloads.find { it.id == downloadId } ?: return
        if (record.status != DownloadStatus.PAUSED && record.status != DownloadStatus.FAILED) {
            return
        }

        record.status = DownloadStatus.DOWNLOADING
        record.errorMessage = null
        saveHistory(context)

        val pending = PendingDownload(
            url = record.url,
            suggestedName = record.fileName,
            mimeType = record.mimeType,
            userAgent = null,
            referer = null,
            contentLength = record.totalBytes
        )
        val task = DownloadTask(record, pending)
        activeTasks[record.id] = task

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = record.id.hashCode() and 0x7FFFFFFF
        postProgressNotification(context, nm, notificationId, record)

        task.job = downloadScope.launch(Dispatchers.IO) {
            executeDownloadLoop(context, nm, notificationId, task, null)
        }
    }

    fun cancelDownload(context: Context, downloadId: String) {
        val task = activeTasks.remove(downloadId)
        task?.isCancelled = true
        task?.currentConn?.disconnect()
        task?.job?.cancel()

        val record = downloads.find { it.id == downloadId }
        if (record != null) {
            record.status = DownloadStatus.CANCELLED
            record.errorMessage = "Cancelled by user"
            saveHistory(context)

            val dir = getDownloadDirectory(context)
            val partFile = File(dir, "${record.fileName}.part")
            if (partFile.exists()) partFile.delete()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = record.id.hashCode() and 0x7FFFFFFF
            nm.cancel(notificationId)
        }
    }

    fun removeDownload(context: Context, record: DownloadRecord, deleteFile: Boolean = false) {
        downloads.remove(record)
        activeTasks.remove(record.id)?.let {
            it.isCancelled = true
            it.currentConn?.disconnect()
            it.job?.cancel()
        }
        saveHistory(context)

        if (deleteFile && !record.localPath.isNullOrBlank()) {
            try {
                val file = File(record.localPath!!)
                if (file.exists()) {
                    file.delete()
                    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                }
            } catch (_: Exception) {}
        }
    }

    fun clearCompleted(context: Context) {
        downloads.removeAll {
            it.status == DownloadStatus.COMPLETED ||
            it.status == DownloadStatus.FAILED ||
            it.status == DownloadStatus.CANCELLED
        }
        saveHistory(context)
    }

    private fun executeDownloadLoop(
        context: Context,
        nm: NotificationManager,
        notificationId: Int,
        task: DownloadTask,
        onComplete: ((Boolean, String) -> Unit)?
    ) {
        val record = task.record
        val pending = task.pending
        val downloadsDir = getDownloadDirectory(context)
        val partFile = File(downloadsDir, "${record.fileName}.part")

        try {
            // 1. Handle data: URI
            if (pending.url.startsWith("data:")) {
                val finalFile = resolveUniqueFile(downloadsDir, record.fileName)
                FileOutputStream(finalFile).use { out ->
                    saveDataUrlToStream(pending.url, out)
                }
                record.status = DownloadStatus.COMPLETED
                record.localPath = finalFile.absolutePath
                record.totalBytes = finalFile.length()
                record.downloadedBytes = finalFile.length()
                activeTasks.remove(record.id)
                saveHistory(context)
                MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), arrayOf(record.mimeType)) { _, uri ->
                    if (uri != null) record.contentUri = uri.toString()
                }
                postCompleteNotification(context, nm, notificationId, record, finalFile)
                mainHandler.post { onComplete?.invoke(true, record.fileName) }
                return
            }

            var existingBytes = if (partFile.exists()) partFile.length() else 0L
            record.downloadedBytes = existingBytes

            // 2. HTTP/HTTPS connection with manual redirects to preserve cookies & headers
            var currentUrl = pending.url
            var connection: HttpURLConnection? = null
            var redirects = 0
            val maxRedirects = 10

            while (redirects < maxRedirects && !task.isCancelled && !task.isPaused) {
                val urlObj = URL(currentUrl)
                val conn = (urlObj.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30000
                    readTimeout = 60000
                    instanceFollowRedirects = false // Crucial: manual follow preserves session cookies

                    try {
                        CookieManager.getInstance().flush()
                        val cookie = CookieManager.getInstance().getCookie(currentUrl)
                        if (!cookie.isNullOrBlank()) {
                            setRequestProperty("Cookie", cookie)
                        }
                    } catch (_: Exception) {}

                    val ua = pending.userAgent?.ifBlank { null }
                        ?: "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                    setRequestProperty("User-Agent", ua)
                    if (!pending.referer.isNullOrBlank()) {
                        setRequestProperty("Referer", pending.referer)
                    }
                    setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                    setRequestProperty("Sec-Fetch-Dest", "document")
                    setRequestProperty("Sec-Fetch-Mode", "navigate")
                    setRequestProperty("Sec-Fetch-Site", "same-origin")
                    setRequestProperty("Sec-Fetch-User", "?1")
                    setRequestProperty("Upgrade-Insecure-Requests", "1")

                    if (existingBytes > 0) {
                        setRequestProperty("Range", "bytes=$existingBytes-")
                    }
                }

                task.currentConn = conn
                val responseCode = try {
                    conn.responseCode
                } catch (e: Exception) {
                    if (task.isCancelled || task.isPaused) return
                    throw e
                }

                if (responseCode in listOf(301, 302, 303, 307, 308)) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (!location.isNullOrBlank()) {
                        currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                            location
                        } else {
                            URL(urlObj, location).toString()
                        }
                        redirects++
                        continue
                    }
                }

                if (responseCode == 416) {
                    // Range not satisfiable (server file changed) -> restart from 0
                    partFile.delete()
                    existingBytes = 0L
                    conn.disconnect()
                    continue
                }

                if (responseCode != 200 && responseCode != 206) {
                    conn.disconnect()
                    throw IOException("Server returned HTTP $responseCode: ${conn.responseMessage}")
                }

                connection = conn
                break
            }

            if (task.isCancelled || task.isPaused) return

            val conn = connection ?: throw IOException("Could not establish download connection")

            // 3. Inspect server response headers
            val cd = conn.getHeaderField("Content-Disposition")
            val serverFileName = parseContentDispositionFilename(cd)
            if (!serverFileName.isNullOrBlank()) {
                record.fileName = sanitizeFilename(serverFileName)
            } else if (record.fileName.isBlank() || record.fileName == "download") {
                val guessed = URLUtil.guessFileName(currentUrl, cd, conn.contentType)
                record.fileName = sanitizeFilename(guessed)
            }

            val respMime = conn.contentType?.substringBefore(';') ?: ""
            if (respMime.isNotBlank() && respMime != "text/html" && respMime != "application/octet-stream") {
                record.mimeType = respMime
            }

            val respLength = conn.contentLengthLong
            if (conn.responseCode == 206) {
                val cr = conn.getHeaderField("Content-Range")
                val total = parseTotalFromContentRange(cr)
                if (total > 0) {
                    record.totalBytes = total
                } else if (respLength > 0) {
                    record.totalBytes = existingBytes + respLength
                }
            } else {
                // Full content response (server does not support resume, reset offset)
                existingBytes = 0L
                if (partFile.exists()) partFile.delete()
                if (respLength > 0) {
                    record.totalBytes = respLength
                }
            }

            // Guard against fake 8KB download bug (site returned an HTML error/captcha page instead of file)
            val ext = record.fileName.substringAfterLast('.', "").lowercase()
            val isBinaryTarget = ext in listOf("apk", "zip", "rar", "7z", "tar", "gz", "iso", "bin", "pdf", "mp4", "mkv", "mp3", "exe", "dmg")
            val isHtml = respMime.contains("text/html") || respMime.contains("application/xhtml")
            val isAttachment = cd?.contains("attachment", ignoreCase = true) == true
            if (isBinaryTarget && isHtml && !isAttachment && respLength in 1..65536) {
                conn.disconnect()
                throw IOException("Verification required: The site served a webpage (login or captcha) instead of the file. Please complete verification in the browser tab.")
            }

            // 4. Stream payload to .part file
            val append = (conn.responseCode == 206 && existingBytes > 0)
            val fos = FileOutputStream(partFile, append)
            val inputStream = conn.inputStream
            val buffer = ByteArray(65536)
            var bytesRead = 0
            var totalRead = if (append) existingBytes else 0L
            var lastNotifTime = 0L

            postProgressNotification(context, nm, notificationId, record)

            try {
                while (!task.isPaused && !task.isCancelled && inputStream.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    record.downloadedBytes = totalRead

                    val now = System.currentTimeMillis()
                    if (now - lastNotifTime > 400) {
                        lastNotifTime = now
                        postProgressNotification(context, nm, notificationId, record)
                    }
                }
                fos.flush()
            } finally {
                try { fos.close() } catch (_: Exception) {}
                try { inputStream.close() } catch (_: Exception) {}
                conn.disconnect()
            }

            // 5. Handle pause / cancel / completion
            if (task.isCancelled) {
                partFile.delete()
                record.status = DownloadStatus.CANCELLED
                record.errorMessage = "Cancelled by user"
                nm.cancel(notificationId)
                saveHistory(context)
                return
            }

            if (task.isPaused) {
                record.status = DownloadStatus.PAUSED
                postPausedNotification(context, nm, notificationId, record)
                saveHistory(context)
                return
            }

            if (totalRead == 0L) {
                partFile.delete()
                throw IOException("Downloaded 0 bytes from server")
            }

            // Download completed: rename .part to final file
            val finalFile = resolveUniqueFile(downloadsDir, record.fileName)
            if (partFile.renameTo(finalFile)) {
                record.status = DownloadStatus.COMPLETED
                record.localPath = finalFile.absolutePath
                record.downloadedBytes = finalFile.length()
                record.totalBytes = finalFile.length()

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(finalFile.absolutePath),
                    arrayOf(record.mimeType)
                ) { _, uri ->
                    if (uri != null) record.contentUri = uri.toString()
                }

                activeTasks.remove(record.id)
                saveHistory(context)
                postCompleteNotification(context, nm, notificationId, record, finalFile)
                mainHandler.post { onComplete?.invoke(true, record.fileName) }
            } else {
                throw IOException("Could not finalize downloaded file")
            }

        } catch (e: Exception) {
            if (task.isCancelled || task.isPaused) return
            val err = e.localizedMessage ?: "Download failed"
            record.status = DownloadStatus.FAILED
            record.errorMessage = err
            activeTasks.remove(record.id)
            saveHistory(context)
            postFailedNotification(context, nm, notificationId, record, err)
            mainHandler.post { onComplete?.invoke(false, err) }
        }
    }

    private fun postProgressNotification(
        context: Context,
        nm: NotificationManager,
        notificationId: Int,
        record: DownloadRecord
    ) {
        ensureChannel(context)
        val total = record.totalBytes
        val downloaded = record.downloadedBytes
        val isIndeterminate = total <= 0
        val percent = if (!isIndeterminate) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0

        val contentText = if (!isIndeterminate) {
            "$percent% • ${formatBytes(downloaded)} / ${formatBytes(total)}"
        } else {
            "${formatBytes(downloaded)} downloaded"
        }

        // Pause action PendingIntent
        val pauseIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = DownloadActionReceiver.ACTION_PAUSE_DOWNLOAD
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, record.id)
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel action PendingIntent
        val cancelIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = DownloadActionReceiver.ACTION_CANCEL_DOWNLOAD
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, record.id)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading ${record.fileName}")
            .setContentText(contentText)
            .setProgress(100, percent, isIndeterminate)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)

        nm.notify(notificationId, builder.build())
    }

    private fun postPausedNotification(
        context: Context,
        nm: NotificationManager,
        notificationId: Int,
        record: DownloadRecord
    ) {
        ensureChannel(context)
        val total = record.totalBytes
        val downloaded = record.downloadedBytes
        val isIndeterminate = total <= 0
        val percent = if (!isIndeterminate) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0

        val contentText = if (!isIndeterminate) {
            "Paused • $percent% (${formatBytes(downloaded)} / ${formatBytes(total)})"
        } else {
            "Paused • ${formatBytes(downloaded)}"
        }

        // Resume action PendingIntent
        val resumeIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = DownloadActionReceiver.ACTION_RESUME_DOWNLOAD
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, record.id)
        }
        val resumePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 3,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel action PendingIntent
        val cancelIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = DownloadActionReceiver.ACTION_CANCEL_DOWNLOAD
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, record.id)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Paused: ${record.fileName}")
            .setContentText(contentText)
            .setProgress(100, percent, isIndeterminate)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)

        nm.notify(notificationId, builder.build())
    }

    private fun postCompleteNotification(
        context: Context,
        nm: NotificationManager,
        notificationId: Int,
        record: DownloadRecord,
        file: File?
    ) {
        ensureChannel(context)
        val openIntent = createOpenFileIntent(context, file, record.mimeType)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText("${record.fileName} (${formatBytes(file?.length() ?: record.downloadedBytes)})")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        nm.notify(notificationId, builder.build())
    }

    private fun postFailedNotification(
        context: Context,
        nm: NotificationManager,
        notificationId: Int,
        record: DownloadRecord,
        error: String
    ) {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText("${record.fileName}: $error")
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        nm.notify(notificationId, builder.build())
    }

    fun getDownloadDirectory(context: Context): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                dir
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                if (!dir.exists()) dir.mkdirs()
                dir
            }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            dir
        }
    }

    fun openFile(context: Context, record: DownloadRecord) {
        val uri: Uri? = when {
            !record.localPath.isNullOrBlank() -> {
                val f = File(record.localPath!!)
                if (f.exists()) getFileUri(context, f) else null
            }
            !record.contentUri.isNullOrBlank() -> Uri.parse(record.contentUri)
            else -> null
        }
        if (uri == null) {
            Toast.makeText(context, "File not found or still downloading", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, record.mimeType.ifBlank { "*/*" })
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app available to open this file", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(context: Context, record: DownloadRecord) {
        val uri: Uri? = when {
            !record.localPath.isNullOrBlank() -> {
                val f = File(record.localPath!!)
                if (f.exists()) getFileUri(context, f) else null
            }
            !record.contentUri.isNullOrBlank() -> Uri.parse(record.contentUri)
            else -> null
        }
        if (uri == null) {
            Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = record.mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${record.fileName}"))
    }

    fun openDownloadsFolder(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                setDataAndType(Uri.fromFile(downloadsDir), "*/*")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Downloads folder: /Downloads", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createOpenFileIntent(context: Context, file: File?, mimeType: String): Intent {
        val uri = if (file != null) getFileUri(context, file) else Uri.EMPTY
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "*/*" })
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }

    private fun getFileUri(context: Context, file: File): Uri {
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            Uri.fromFile(file)
        }
    }

    fun guessName(url: String, contentDisposition: String?, mimeType: String?): Pair<String, String> {
        val parsedCd = parseContentDispositionFilename(contentDisposition)
        var name = if (!parsedCd.isNullOrBlank()) parsedCd else URLUtil.guessFileName(url, contentDisposition, mimeType)
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

    fun parseContentDispositionFilename(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrBlank()) return null
        val starPattern = Pattern.compile("filename\\*\\s*=\\s*UTF-8''([^;\\s]+)", Pattern.CASE_INSENSITIVE)
        val starMatcher = starPattern.matcher(contentDisposition)
        if (starMatcher.find()) {
            return runCatching { URLDecoder.decode(starMatcher.group(1), "UTF-8") }.getOrNull()
        }
        val quotedPattern = Pattern.compile("filename\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
        val quotedMatcher = quotedPattern.matcher(contentDisposition)
        if (quotedMatcher.find()) {
            return quotedMatcher.group(1)
        }
        val plainPattern = Pattern.compile("filename\\s*=\\s*([^;\\s]+)", Pattern.CASE_INSENSITIVE)
        val plainMatcher = plainPattern.matcher(contentDisposition)
        if (plainMatcher.find()) {
            return plainMatcher.group(1).trim { it == '"' || it == '\'' }
        }
        return null
    }

    fun parseTotalFromContentRange(contentRange: String?): Long {
        if (contentRange.isNullOrBlank()) return -1L
        val slashIndex = contentRange.lastIndexOf('/')
        if (slashIndex >= 0 && slashIndex < contentRange.length - 1) {
            val totalStr = contentRange.substring(slashIndex + 1).trim()
            return totalStr.toLongOrNull() ?: -1L
        }
        return -1L
    }

    private fun saveDataUrlToStream(dataUrl: String, out: OutputStream) {
        val comma = dataUrl.indexOf(',')
        if (comma < 0) throw IOException("Malformed data URL")
        val metadata = dataUrl.substring(5, comma)
        val data = dataUrl.substring(comma + 1)
        val isBase64 = metadata.contains(";base64", ignoreCase = true)
        val bytes = if (isBase64) {
            Base64.decode(data, Base64.DEFAULT)
        } else {
            URLDecoder.decode(data, "UTF-8").toByteArray(Charsets.UTF_8)
        }
        out.write(bytes)
        out.flush()
    }

    private fun resolveUniqueFile(dir: File, baseName: String): File {
        var file = File(dir, baseName)
        if (!file.exists()) return file

        val nameWithoutExt = baseName.substringBeforeLast('.', baseName)
        val ext = if (baseName.contains('.')) "." + baseName.substringAfterLast('.') else ""

        var counter = 1
        while (file.exists()) {
            file = File(dir, "$nameWithoutExt ($counter)$ext")
            counter++
        }
        return file
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim()
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun saveHistory(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val array = JSONArray()
            downloads.take(50).forEach { r ->
                val obj = JSONObject().apply {
                    put("id", r.id)
                    put("url", r.url)
                    put("fileName", r.fileName)
                    put("mimeType", r.mimeType)
                    put("totalBytes", r.totalBytes)
                    put("downloadedBytes", r.downloadedBytes)
                    put("status", r.status.name)
                    put("localPath", r.localPath ?: "")
                    put("contentUri", r.contentUri ?: "")
                    put("errorMessage", r.errorMessage ?: "")
                    put("timestamp", r.timestamp)
                }
                array.put(obj)
            }
            prefs.edit().putString(PREFS_KEY, array.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun loadHistory(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(PREFS_KEY, null) ?: return
            val array = JSONArray(json)
            downloads.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val statusStr = obj.optString("status", DownloadStatus.COMPLETED.name)
                val status = try {
                    val parsed = DownloadStatus.valueOf(statusStr)
                    if (parsed == DownloadStatus.DOWNLOADING || parsed == DownloadStatus.PENDING) {
                        DownloadStatus.PAUSED
                    } else parsed
                } catch (_: Exception) {
                    DownloadStatus.COMPLETED
                }

                downloads.add(
                    DownloadRecord(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        url = obj.optString("url", ""),
                        fileName = obj.optString("fileName", "download"),
                        mimeType = obj.optString("mimeType", "application/octet-stream"),
                        totalBytes = obj.optLong("totalBytes", -1L),
                        downloadedBytes = obj.optLong("downloadedBytes", 0L),
                        status = status,
                        localPath = obj.optString("localPath", "").takeIf { it.isNotBlank() },
                        contentUri = obj.optString("contentUri", "").takeIf { it.isNotBlank() },
                        errorMessage = obj.optString("errorMessage", "").takeIf { it.isNotBlank() },
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
    }
}
