package com.forgebuild.clipforge.data

import org.json.JSONArray
import org.json.JSONObject

/** Clone connection bound to the app (the "Shadow Clone" of motionssalt/clipforge). */
data class CloneConnection(val owner: String, val repo: String, val pat: String) {
    val fullName: String get() = "$owner/$repo"
}

/** ClipForge job, mirroring the bot's /tasks and /done views. */
data class Job(
    val id: String,
    val title: String,
    val source: String,
    val state: String,
    val createdAt: Long,
    val updatedAt: Long,
    val error: String? = null
) {
    fun isActive(): Boolean = state in listOf(
        "queued", "stage_a_running", "awaiting_torrent_selection",
        "awaiting_plan", "stage_b_queued", "stage_b_running"
    )
}

/** Projected clip segment inside the source video (seconds). */
data class ClipSegment(val startSec: Float, val endSec: Float, val caption: String = "")

/** Production plan mirroring production.json from the bot pipeline. */
data class ProductionPlan(
    val title: String,
    val segments: List<ClipSegment>,
    val captionsEnabled: Boolean = true,
    val watermarkText: String = "",
    val vertical: Boolean = true
)

/** Narrator / branding settings, mirroring branding/*.json from the bot. */
data class ForgeSettings(
    val narratorVoice: String = "en-US-AriaNeural",
    val watermarkText: String = "",
    val captionsEnabled: Boolean = true,
    val verticalReframe: Boolean = true,
    val musicEnabled: Boolean = false
)

fun JSONObject.optFloatOrNull(name: String): Float? =
    if (has(name)) optDouble(name).toFloat() else null

fun jobFromJson(o: JSONObject): Job = Job(
    id = o.optString("id", o.optString("job_id")),
    title = o.optString("title", "Untitled clip"),
    source = o.optString("source", ""),
    state = o.optString("state", "queued"),
    createdAt = o.optLong("created_at", 0L),
    updatedAt = o.optLong("updated_at", 0L),
    error = if (o.isNull("error")) null else o.optString("error")
)

fun planFromJson(o: JSONObject): ProductionPlan {
    val segs = mutableListOf<ClipSegment>()
    val arr: JSONArray? = o.optJSONArray("segments") ?: o.optJSONArray("cuts")
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val start = s.optFloatOrNull("start") ?: s.optFloatOrNull("start_sec") ?: 0f
            val end = s.optFloatOrNull("end") ?: s.optFloatOrNull("end_sec") ?: (start + 30f)
            segs.add(ClipSegment(start, end, s.optString("caption", s.optString("voiceover", ""))))
        }
    }
    return ProductionPlan(
        title = o.optString("title", "ClipForge clip"),
        segments = segs,
        captionsEnabled = o.optBoolean("captions", true),
        watermarkText = o.optString("watermark", ""),
        vertical = o.optBoolean("vertical", true)
    )
}

fun planToJson(p: ProductionPlan): JSONObject {
    val o = JSONObject()
    o.put("title", p.title)
    o.put("captions", p.captionsEnabled)
    o.put("watermark", p.watermarkText)
    o.put("vertical", p.vertical)
    val arr = JSONArray()
    p.segments.forEach { s ->
        arr.put(JSONObject().put("start", s.startSec.toDouble()).put("end", s.endSec.toDouble()).put("caption", s.caption))
    }
    o.put("segments", arr)
    return o
}
