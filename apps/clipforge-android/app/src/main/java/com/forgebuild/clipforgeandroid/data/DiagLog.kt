package com.forgebuild.clipforgeandroid.data

import android.content.Context

/**
 * On-device diagnostic log (session-10 fix #8): records the exact failing GitHub
 * request (method, path, HTTP code, response excerpt) and every Contents-API
 * create-vs-update intent, so a device-specific failure — e.g. HTTP 403 on the
 * operator's friend's phone while the same PAT works on the operator's own phone —
 * is diagnosable from EVIDENCE instead of guesses. Nothing leaves the device.
 *
 * Ring buffer of the last 200 lines in SharedPreferences; exportable to
 * filesDir/clipforge-diagnostic-log.txt from Settings -> About.
 */
object DiagLog {
    private const val PREFS = "clipforge_diag"
    private const val KEY = "lines"
    private const val MAX_LINES = 200

    @Synchronized
    fun log(ctx: Context?, tag: String, message: String) {
        ctx ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val line = "${java.time.Instant.now()} [$tag] ${message.take(500)}"
            val existing = prefs.getString(KEY, "") ?: ""
            val lines = if (existing.isEmpty()) mutableListOf() else existing.split("\n").toMutableList()
            lines.add(line)
            while (lines.size > MAX_LINES) lines.removeAt(0)
            prefs.edit().putString(KEY, lines.joinToString("\n")).apply()
        } catch (_: Exception) {}
    }

    fun lines(ctx: Context?): List<String> = try {
        val raw = ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY, "") ?: ""
        if (raw.isBlank()) emptyList() else raw.split("\n")
    } catch (_: Exception) {
        emptyList()
    }

    fun clear(ctx: Context?) {
        try {
            ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.remove(KEY)?.apply()
        } catch (_: Exception) {}
    }

    /** Write the log to a shareable text file; returns the absolute path or null. */
    fun export(ctx: Context?): String? = try {
        if (ctx == null) null else {
            val f = java.io.File(ctx.filesDir, "clipforge-diagnostic-log.txt")
            f.writeText(lines(ctx).joinToString("\n"))
            f.absolutePath
        }
    } catch (_: Exception) {
        null
    }
}
