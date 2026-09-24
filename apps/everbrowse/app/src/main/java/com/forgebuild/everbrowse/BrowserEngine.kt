package com.forgebuild.everbrowse

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadRecord(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val fileName: String,
    val mimeType: String,
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
            status == DownloadStatus.DOWNLOADING -> 0.15f
            else -> 0f
        }

    val isIndeterminate: Boolean
        get() = status == DownloadStatus.DOWNLOADING && totalBytes <= 0
}

/**
 * Downloads directly to the system Downloads folder with:
 * - Full cookie, User-Agent, and Referer headers
 * - HTTP->HTTPS redirect following
 * - MediaStore.Downloads (API 29+) with RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS
 * - Direct public Downloads folder fallback with MediaScanner indexing
 * - System notification tray progress and tap-to-open PendingIntent
 * - Material 3 Download Manager state observation and persistence
 */
object DownloadCoordinator {

    const val CHANNEL_ID = "everbrowse_downloads"
    private const val PREFS_NAME = "everbrowse_downloads_prefs"
    private const val PREFS_KEY = "download_history"

    val downloads = mutableStateListOf<DownloadRecord>()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private var initialized = false
    private val mainHandler = Handler(Looper.getMainLooper())

    val activeCount: Int
        get() = downloads.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING }

    data class PendingDownload(
        val url: String,
        val suggestedName: String,
        val mimeType: String,
        val userAgent: String? = null,
        val referer: String? = null,
        val contentLength: Long = -1L
    )

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        createNotificationChannel(context)
        loadHistory(context)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for file downloads in EverBrowse"
                setShowBadge(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun startDownload(
        context: Context,
        pending: PendingDownload,
        scope: CoroutineScope,
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ): DownloadRecord {
        init(context)

        val cleanName = sanitizeFilename(pending.suggestedName)
        val record = DownloadRecord(
            url = pending.url,
            fileName = cleanName,
            mimeType = pending.mimeType,
            totalBytes = pending.contentLength,
            status = DownloadStatus.DOWNLOADING
        )

        downloads.add(0, record)
        saveHistory(context)

        val notificationId = (record.id.hashCode() and 0x7FFFFFFF)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Show initial ongoing notification
        postProgressNotification(context, nm, notificationId, record)

        val job = scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    executeDownloadStream(context, nm, notificationId, record, pending)
                }
            }

            activeJobs.remove(record.id)

            if (result.isSuccess) {
                val savedFile = result.getOrNull()
                record.status = DownloadStatus.COMPLETED
                record.localPath = savedFile?.absolutePath
                record.downloadedBytes = record.totalBytes.takeIf { it > 0 } ?: (savedFile?.length() ?: 0L)
                saveHistory(context)

                postCompleteNotification(context, nm, notificationId, record, savedFile)
                mainHandler.post {
                    onDone(true, record.fileName)
                }
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Download failed"
                if (record.status != DownloadStatus.CANCELLED) {
                    record.status = DownloadStatus.FAILED
                    record.errorMessage = err
                    saveHistory(context)
                    postFailedNotification(context, nm, notificationId, record, err)
                }
                mainHandler.post {
                    onDone(false, err)
                }
            }
        }

        activeJobs[record.id] = job
        return record
    }

    fun cancelDownload(context: Context, recordId: String) {
        val job = activeJobs.remove(recordId)
        job?.cancel()
        val index = downloads.indexOfFirst { it.id == recordId }
        if (index >= 0) {
            val record = downloads[index]
            record.status = DownloadStatus.CANCELLED
            record.errorMessage = "Cancelled by user"
            saveHistory(context)

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(record.id.hashCode() and 0x7FFFFFFF)
        }
    }

    fun removeDownload(context: Context, record: DownloadRecord, deleteFile: Boolean = false) {
        downloads.remove(record)
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
        downloads.removeAll { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
        saveHistory(context)
    }

    private fun executeDownloadStream(
        context: Context,
        nm: NotificationManager,
        notificationId: Int,
        record: DownloadRecord,
        pending: PendingDownload
    ): File? {
        val resolver = context.contentResolver
        var contentUri: Uri? = null
        var targetFile: File? = null
        var outputStream: OutputStream? = null

        // 1. On Android 10+ (API 29+), write directly via ContentResolver + MediaStore.Downloads
        // This avoids Scoped Storage EACCES permission denied errors on modern Android.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, record.fileName)
                    put(MediaStore.Downloads.MIME_TYPE, record.mimeType.ifBlank { "application/octet-stream" })
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    val stream = resolver.openOutputStream(uri, "w")
                    if (stream != null) {
                        contentUri = uri
                        outputStream = stream
                        record.contentUri = uri.toString()
                    }
                }
            } catch (_: Exception) {
                contentUri = null
                outputStream = null
            }
        }

        // 2. Fallback to public Downloads directory or app-specific external files
        if (outputStream == null) {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = resolveUniqueFile(downloadsDir, record.fileName)
            try {
                outputStream = FileOutputStream(file)
                targetFile = file
                record.localPath = file.absolutePath
            } catch (_: Exception) {
                // Last-resort fallback: app-specific external files dir (always writable)
                val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val fallbackFile = resolveUniqueFile(fallbackDir, record.fileName)
                outputStream = FileOutputStream(fallbackFile)
                targetFile = fallbackFile
                record.localPath = fallbackFile.absolutePath
            }
        }

        val out = outputStream ?: throw IOException("Could not create output stream for download")

        // Handle data: URI
        if (pending.url.startsWith("data:")) {
            saveDataUrlToStream(pending.url, out)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && contentUri != null) {
                val cv = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(contentUri, cv, null, null)
            }
            if (targetFile != null) {
                record.totalBytes = targetFile.length()
                record.downloadedBytes = targetFile.length()
                MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(record.mimeType), null)
            }
            return targetFile
        }

        // Handle HTTP/HTTPS connection with redirect loop
        var currentUrl = pending.url
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var redirects = 0
        val maxRedirects = 10

        while (redirects < maxRedirects) {
            val url = URL(currentUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30000
                readTimeout = 60000
                instanceFollowRedirects = true
                val cookie = CookieManager.getInstance().getCookie(currentUrl)
                if (!cookie.isNullOrBlank()) {
                    setRequestProperty("Cookie", cookie)
                }
                val ua = pending.userAgent ?: "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                setRequestProperty("User-Agent", ua)
                if (!pending.referer.isNullOrBlank()) {
                    setRequestProperty("Referer", pending.referer)
                }
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Encoding", "identity")
            }

            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308
            ) {
                val newUrl = connection.getHeaderField("Location")
                if (!newUrl.isNullOrBlank()) {
                    currentUrl = if (newUrl.startsWith("http")) newUrl else URL(url, newUrl).toString()
                    connection.disconnect()
                    redirects++
                    continue
                }
            }

            if (status !in 200..299) {
                connection.disconnect()
                throw IOException("Server returned HTTP $status: ${connection.responseMessage}")
            }
            break
        }

        val conn = connection ?: throw IOException("Failed to establish network connection")
        val contentLen = conn.contentLengthLong
        if (contentLen > 0) {
            record.totalBytes = contentLen
        }

        inputStream = conn.inputStream

        // High-speed 64KB buffer for large files (100MB+)
        val buffer = ByteArray(65536)
        var bytesRead: Int
        var totalRead = 0L
        var lastNotifTime = 0L

        try {
            out.use { stream ->
                inputStream.use { input ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        stream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        record.downloadedBytes = totalRead

                        val now = System.currentTimeMillis()
                        if (now - lastNotifTime > 300) {
                            lastNotifTime = now
                            postProgressNotification(context, nm, notificationId, record)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && contentUri != null) {
                runCatching { resolver.delete(contentUri, null, null) }
            }
            targetFile?.delete()
            throw e
        } finally {
            conn.disconnect()
        }

        if (totalRead == 0L) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && contentUri != null) {
                runCatching { resolver.delete(contentUri, null, null) }
            }
            targetFile?.delete()
            throw IOException("Downloaded 0 bytes from server")
        }

        // On Android 10+ (API 29+), mark file complete in MediaStore (IS_PENDING = 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && contentUri != null) {
            val completedValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(contentUri, completedValues, null, null)
        }

        // Scan file so media scanner and download provider index it immediately
        if (targetFile != null) {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf(record.mimeType)
            ) { path, uri ->
                if (uri != null) {
                    record.contentUri = uri.toString()
                }
            }
        }

        return targetFile
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

    private fun postProgressNotification(
        context: Context,
        nm: NotificationManager,
        notificationId: Int,
        record: DownloadRecord
    ) {
        val total = record.totalBytes
        val downloaded = record.downloadedBytes
        val isIndeterminate = total <= 0

        val percent = if (!isIndeterminate) ((downloaded * 100) / total).toInt() else 0
        val contentText = if (!isIndeterminate) {
            "$percent% • ${formatBytes(downloaded)} / ${formatBytes(total)}"
        } else {
            "${formatBytes(downloaded)} downloaded"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading ${record.fileName}")
            .setContentText(contentText)
            .setProgress(100, percent, isIndeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        nm.notify(notificationId, builder.build())
    }

    private fun postCompleteNotification(
        context: Context,
        nm: NotificationManager,
        notificationId: Int,
        record: DownloadRecord,
        file: File?
    ) {
        val openIntent = createOpenFileIntent(context, file, record.mimeType)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
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
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText("${record.fileName}: $error")
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        nm.notify(notificationId, builder.build())
    }

    fun openFile(context: Context, record: DownloadRecord) {
        val uri: Uri? = when {
            !record.contentUri.isNullOrBlank() -> Uri.parse(record.contentUri)
            !record.localPath.isNullOrBlank() -> {
                val f = File(record.localPath!!)
                if (f.exists()) getFileUri(context, f) else null
            }
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
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app available to open this file", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(context: Context, record: DownloadRecord) {
        val uri: Uri? = when {
            !record.contentUri.isNullOrBlank() -> Uri.parse(record.contentUri)
            !record.localPath.isNullOrBlank() -> {
                val f = File(record.localPath!!)
                if (f.exists()) getFileUri(context, f) else null
            }
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
                    // Active downloads from previous process are marked failed or cancelled
                    if (parsed == DownloadStatus.DOWNLOADING || parsed == DownloadStatus.PENDING) {
                        DownloadStatus.FAILED
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
