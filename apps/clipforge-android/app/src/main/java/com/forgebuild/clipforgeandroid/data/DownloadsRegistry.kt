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

    /** The recorded download for a job id, or null when the video was never downloaded. */
    fun forJob(ctx: Context, jobId: String): JSONObject? =
        loadAll(ctx).optJSONObject(jobId)

    /** Every recorded download belonging to [seriesId], sorted by job id. */
    fun forSeries(ctx: Context, seriesId: String): List<JSONObject> {
        val all = loadAll(ctx)
        val out = mutableListOf<JSONObject>()
        val keys = all.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val o = all.optJSONObject(k) ?: continue
            if (o.optString("seriesId") == seriesId) out.add(o)
        }
        return out.sortedBy { it.optString("jobId") }
    }
}
