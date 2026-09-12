package com.forgebuild.clipforgeandroid.data

import android.content.Context
import org.json.JSONObject

/**
 * Downloads registry (operator fix #5) — associates every downloaded final MP4 with
 * the job's SERIES id (and job id) so the file can be found again from the series
 * view later, and the app can offer "Play" instead of "Download" on revisit.
 *
 * One JSONObject per downloaded video:
 *   { jobId, seriesId, uri, name, sizeBytes, downloadedAtEpoch }
 * `uri` is the MediaStore content Uri (API 29+) or the legacy file path (API 26-28).
 */
object DownloadsRegistry {
    private const val PREFS = "clipforge_downloads"
    private const val KEY = "videos"

    private fun loadAll(ctx: Context): JSONObject =
        runCatching {
            JSONObject(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "{}") ?: "{}")
        }.getOrElse { JSONObject() }

    private fun saveAll(ctx: Context, all: JSONObject) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, all.toString()).apply()
    }

    /** Record a finished download. [seriesId] may be blank for non-series jobs. */
    fun register(ctx: Context, jobId: String, seriesId: String, uri: String, name: String, sizeBytes: Long) {
        val all = loadAll(ctx)
        all.put(jobId, JSONObject()
            .put("jobId", jobId)
            .put("seriesId", seriesId)
            .put("uri", uri)
            .put("name", name)
            .put("sizeBytes", sizeBytes)
            .put("downloadedAtEpoch", System.currentTimeMillis() / 1000))
        saveAll(ctx, all)
    }

    /** The recorded download for a job id — or null when the video was never
     *  downloaded OR the file no longer exists on device storage (session-10 fix #2:
     *  the registry entry alone used to decide Play-vs-Download, so a deleted /
     *  never-finished file still showed "Play". Now the record is verified against
     *  real storage every time it is read). */
    fun forJob(ctx: Context, jobId: String): JSONObject? {
        val rec = loadAll(ctx).optJSONObject(jobId) ?: return null
        return if (isReachable(ctx, rec.optString("uri"))) rec else null
    }

    /** True when the recorded file still exists and is readable on device storage. */
    private fun isReachable(ctx: Context, uriString: String): Boolean {
        if (uriString.isBlank()) return false
        return try {
            if (uriString.startsWith("content://")) {
                ctx.contentResolver.openFileDescriptor(android.net.Uri.parse(uriString), "r")?.use { true } ?: false
            } else {
                java.io.File(uriString).exists()
            }
        } catch (_: Exception) { false }
    }

    /** Every recorded download belonging to [seriesId], sorted by job id. */
    fun forSeries(ctx: Context, seriesId: String): List<JSONObject> {
        val all = loadAll(ctx)
        val out = mutableListOf<JSONObject>()
        val keys = all.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val o = all.optJSONObject(k) ?: continue
            if (o.optString("seriesId") == seriesId && isReachable(ctx, o.optString("uri"))) out.add(o)
        }
        return out.sortedBy { it.optString("jobId") }
    }
}
