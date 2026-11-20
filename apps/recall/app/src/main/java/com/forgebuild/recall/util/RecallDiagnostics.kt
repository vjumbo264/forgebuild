package com.forgebuild.recall.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rolling crash & scheduling reliability diagnostics recorder.
 * Ported from TaskFlow's CrashLogger and expanded with structured background
 * event entries (schedule, chime fire, missed-event suppression, reboot/time recovery)
 * so on-device reliability testing can be inspected directly inside RECALL.
 */
object RecallDiagnostics {
    private const val TAG = "RecallDiagnostics"
    private const val FILE_NAME = "recall_diagnostics.log"
    private const val MAX_LOG_BYTES = 64 * 1024

    private var originalHandler: Thread.UncaughtExceptionHandler? = null
    @Volatile
    private var isInstalled = false

    fun installCrashHandler(context: Context) {
        if (isInstalled) return
        isInstalled = true
        val appContext = context.applicationContext
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(appContext, thread.name, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record crash", e)
            } finally {
                originalHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun logFile(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)

    private fun nowStamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    @Synchronized
    private fun prependLine(context: Context, entry: String) {
        runCatching {
            val file = logFile(context)
            val existing = if (file.exists()) file.readText() else ""
            val combined = (entry + "\n" + existing).take(MAX_LOG_BYTES)
            file.writeText(combined)
        }
    }

    fun logEvent(context: Context, tag: String, message: String) {
        val entry = "[EVENT] ${nowStamp()} [$tag] $message"
        Log.i(TAG, entry)
        prependLine(context, entry)
    }

    fun logError(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        val sw = StringWriter()
        throwable?.printStackTrace(PrintWriter(sw))
        val trace = sw.toString()
        val entry = buildString {
            append("[ERROR] ").append(nowStamp()).append(" [").append(tag).append("] ").append(message)
            if (trace.isNotBlank()) append("\n").append(trace)
        }
        Log.w(TAG, entry)
        prependLine(context, entry)
    }

    @Synchronized
    fun recordCrash(context: Context, threadName: String, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val report = buildString {
            append("========================================\n")
            append("[CRASH] ").append(nowStamp()).append("\n")
            append("THREAD: ").append(threadName).append("\n")
            append("DEVICE: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
            append("ANDROID: API ").append(Build.VERSION.SDK_INT).append(" (").append(Build.VERSION.RELEASE).append(")\n")
            append("EXCEPTION: ").append(throwable.javaClass.name).append("\n")
            append("MESSAGE: ").append(throwable.message ?: "(none)").append("\n")
            append("STACKTRACE:\n").append(sw.toString())
        }
        Log.e(TAG, report)
        prependLine(context, report)
    }

    @Synchronized
    fun getLogs(context: Context): String = runCatching {
        val f = logFile(context)
        if (f.exists()) f.readText().ifBlank { "No diagnostic events recorded yet." }
        else "No diagnostic events recorded yet."
    }.getOrDefault("Unable to read diagnostic log.")

    @Synchronized
    fun clearLogs(context: Context) {
        runCatching {
            val f = logFile(context)
            if (f.exists()) f.delete()
        }
    }
}
