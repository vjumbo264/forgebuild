package com.forgebuild.clipforgeandroid.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class TaskStatus(
    val jobId: String,
    val state: String,
    val message: String,
    val createdAt: Long,
    val updatedAt: Long,
    val releaseTag: String,
    val releaseUrl: String,
    val runId: Long,
    val runUrl: String,
    val seriesEnabled: Boolean,
    val seriesId: String,
    val part: Int,
    val startSeconds: Int,
) {
    val isTerminal: Boolean
        get() = state == "complete" || state == "error" || state == "cancelled"

    val isComplete: Boolean
        get() = state == "complete"

    /** Serialize back to the status.json shape — used by the cache-first store
     *  (filesDir/tasks.json) so the second open renders instantly from disk. */
    fun toJson(): JSONObject = JSONObject()
        .put("job_id", jobId)
        .put("state", state)
        .put("message", message)
        .put("created_at_epoch", createdAt)
        .put("updated_at_epoch", updatedAt)
        .put("release_tag", releaseTag)
        .put("release_url", releaseUrl)
        .put("run", JSONObject()
            .put("workflow_run_id", runId)
            .put("workflow_run_url", runUrl))
        .put("series", JSONObject()
            .put("enabled", seriesEnabled)
            .put("series_id", seriesId)
            .put("part", part)
            .put("start_seconds", startSeconds))

    companion object {
        fun fromJson(j: JSONObject): TaskStatus {
            val series = j.optJSONObject("series")
            val run = j.optJSONObject("run")
            return TaskStatus(
                jobId = j.optString("job_id"),
                state = j.optString("state", "queued"),
                message = j.optString("message", ""),
                createdAt = j.optLong("created_at_epoch"),
                updatedAt = j.optLong("updated_at_epoch"),
                releaseTag = j.optString("release_tag", ""),
                releaseUrl = j.optString("release_url", ""),
                runId = run?.optLong("workflow_run_id", 0) ?: 0,
                runUrl = run?.optString("workflow_run_url", "") ?: "",
                seriesEnabled = series?.optBoolean("enabled", false) ?: false,
                seriesId = series?.optString("series_id", "") ?: "",
                part = series?.optInt("part", 0) ?: 0,
                startSeconds = series?.optInt("start_seconds", 0) ?: 0,
            )
        }
    }
}

data class MusicTrack(val name: String, val path: String, val size: Long, val sha: String) {
    fun toJson() = JSONObject().put("name", name).put("path", path).put("size", size).put("sha", sha)
    companion object {
        fun fromJson(j: JSONObject) =
            MusicTrack(j.optString("name"), j.optString("path"), j.optLong("size"), j.optString("sha"))
    }
}

/** TTS voice catalogue — verbatim port of VOICES in bot/src/constants.js. */
object Voices {
    data class Voice(val id: String, val label: String, val gender: String, val style: String)

    val ALL: List<Voice> = listOf(
        Voice("en-US-AndrewNeural", "Andrew", "Male", "Warm, confident, conversational"),
        Voice("en-US-BrianNeural", "Brian", "Male", "Approachable, casual, sincere"),
        Voice("en-US-ChristopherNeural", "Christopher", "Male", "Reliable, authoritative narrator"),
        Voice("en-US-EricNeural", "Eric", "Male", "Rational, measured narrator"),
        Voice("en-US-GuyNeural", "Guy", "Male", "Energetic news-style narrator"),
        Voice("en-US-RogerNeural", "Roger", "Male", "Lively narrator"),
        Voice("en-US-AvaNeural", "Ava", "Female", "Expressive, caring, conversational"),
        Voice("en-US-AriaNeural", "Aria", "Female", "Positive, confident, conversational"),
        Voice("en-US-JennyNeural", "Jenny", "Female", "Friendly, considerate narrator"),
        Voice("en-US-MichelleNeural", "Michelle", "Female", "Friendly, polished narrator"),
        Voice("en-NG-AbeoNeural", "Abeo", "Male", "Nigerian English, friendly and positive"),
        Voice("en-NG-EzinneNeural", "Ezinne", "Female", "Nigerian English, friendly and positive"),
    )
    const val DEFAULT = "en-US-AndrewNeural"
}

/**
 * Client-side production.json validator — Kotlin port of
 * bot/src/plan.js parseAndValidateProductionPlan + schemas/production_plan.schema.json.
 */
object PlanValidator {
    fun validate(text: String): List<String> {
        val errors = mutableListOf<String>()
        val doc = try {
            JSONObject(text)
        } catch (e: Exception) {
            return listOf("production.json is not valid JSON: " + e.message)
        }

        fun posInt(key: String) {
            if (!doc.has(key)) { errors.add("`" + key + "` is required."); return }
            val v = doc.opt(key)
            if (v !is Int && v !is Long) errors.add("`" + key + "` must be an integer.")
            else if ((v as Number).toLong() < 1) errors.add("`" + key + "` must be a positive integer.")
        }

        posInt("video_duration_seconds")
        posInt("target_total_duration_seconds")
        val videoDuration = doc.optLong("video_duration_seconds", 0)

        // hashtags: 5..8 unique, #-prefixed, whitespace-free
        if (doc.has("hashtags")) {
            val arr = doc.optJSONArray("hashtags")
            if (arr == null) errors.add("`hashtags` must be an array.")
            else {
                if (arr.length() < 5 || arr.length() > 8)
                    errors.add("`hashtags` must contain 5 to 8 entries (got " + arr.length() + ").")
                val seen = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    val h = arr.optString(i, "")
                    if (!h.startsWith("#")) errors.add("hashtags[" + i + "] must start with '#'.")
                    if (h.contains(Regex("\\s"))) errors.add("hashtags[" + i + "] must not contain whitespace.")
                    if (!seen.add(h)) errors.add("hashtags[" + i + "] is a duplicate.")
                }
            }
        }

        // youtube_tags: 10..20, no #, no comma
        if (doc.has("youtube_tags")) {
            val arr = doc.optJSONArray("youtube_tags")
            if (arr == null) errors.add("`youtube_tags` must be an array.")
            else if (arr.length() < 10 || arr.length() > 20)
                errors.add("`youtube_tags` must contain 10 to 20 entries (got " + arr.length() + ").")
        }

        // series block (nested preferred, legacy flat accepted)
        var seriesStart = -1L
        var seriesEnd = -1L
        val series = doc.optJSONObject("series")
        if (series != null) {
            if (!series.has("is_final")) errors.add("`series.is_final` is required for a series plan.")
            seriesStart = series.optLong("start_seconds", -1)
            seriesEnd = series.optLong("end_seconds", -1)
            if (seriesStart < 0) errors.add("`series.start_seconds` must be a non-negative integer.")
            if (seriesEnd < 1) errors.add("`series.end_seconds` must be a positive integer.")
            if (seriesEnd in 1..seriesStart) errors.add("`series.end_seconds` must be greater than `series.start_seconds`.")
        } else if (doc.has("series_id")) {
            seriesStart = doc.optLong("series_start_seconds", -1)
            seriesEnd = doc.optLong("series_end_seconds", -1)
            if (seriesEnd in 1..seriesStart) errors.add("`series_end_seconds` must be greater than `series_start_seconds`.")
        }

        // cuts
        val cuts = doc.optJSONArray("cuts")
        if (cuts == null || cuts.length() < 1) {
            errors.add("`cuts` must contain at least one cut.")
        } else {
            var prevEnd = 0L
            for (i in 0 until cuts.length()) {
                val c = cuts.optJSONObject(i)
                val at = "cuts[" + i + "]"
                if (c == null) { errors.add(at + " must be an object."); continue }
                val start = c.optLong("start_seconds", -1)
                val end = c.optLong("end_seconds", -1)
                if (start < 0) errors.add(at + ".start_seconds must be a non-negative integer.")
                if (end < 1) errors.add(at + ".end_seconds must be an integer >= 1.")
                if (end in 1..start) errors.add(at + ".end_seconds must be greater than start_seconds.")
                if (videoDuration > 0 && end > videoDuration)
                    errors.add(at + ".end_seconds exceeds video_duration_seconds.")
                if (seriesEnd > 0 && end > seriesEnd) errors.add(at + ".end_seconds exceeds series_end_seconds.")
                if (seriesStart >= 0 && start in 0 until seriesStart)
                    errors.add(at + ".start_seconds precedes series_start_seconds.")
                if (i > 0 && start < prevEnd) errors.add(at + " overlaps or precedes the prior cut.")
                if (!c.has("voiceover_text") && !c.has("raw_narration"))
                    errors.add(at + " needs `voiceover_text` or `raw_narration`.")
                prevEnd = maxOf(prevEnd, end)
            }
        }
        return errors
    }
}

/** Series continuation — Kotlin port of bot/src/series.js (manualSeriesContinuation / nextPartJobId). */
object SeriesLogic {
    fun nextPartJobId(seriesId: String, part: Int): String = seriesId + "-p" + (part + 1)

    fun continuationFrom(status: TaskStatus, planText: String?): Pair<Int, Int>? {
        if (status.state != "complete" || !status.seriesEnabled) return null
        planText ?: return null
        val plan = runCatching { JSONObject(planText) }.getOrNull() ?: return null
        val nested = plan.optJSONObject("series")
        val isFinal = nested?.optBoolean("is_final") ?: plan.optBoolean("series_final", true)
        if (isFinal) return null
        val end = nested?.optLong("end_seconds", 0) ?: plan.optLong("series_end_seconds", 0)
        if (end <= 0) return null
        return (status.part + 1) to end.toInt()
    }
}

/** Pipeline state description mapping mirror from bot/src/constants.js */
object Pipeline {
    fun describe(state: String): String = when (state) {
        "queued" -> "Queued"
        "stage_a_running" -> "Analyzing video…"
        "awaiting_torrent_selection" -> "Waiting for torrent file selection"
        "awaiting_plan" -> "Awaiting production.json"
        "stage_b_queued" -> "Rendering queued"
        "stage_b_running" -> "Rendering clip…"
        "complete" -> "Complete"
        "error" -> "Failed"
        "cancelled" -> "Cancelled"
        else -> state
    }
}

/** Generates canonical agent prompt matching bot/src/index.js sendAgentPrompt */
object AgentPromptBuilder {
    fun build(status: TaskStatus, request: JSONObject?): String {
        val releaseUrl = if (status.releaseUrl.isNotBlank()) status.releaseUrl
        else "https://github.com/release/tag/" + (if (status.releaseTag.isNotBlank()) status.releaseTag else ("clipforge-" + status.jobId))

        val options = request?.optJSONObject("options")
        val target = options?.optInt("target_duration_seconds", 120) ?: 120
        val focus = options?.optString("focus", "").orEmpty().trim()
        val focusClause = if (focus.isNotBlank()) ", focused on: " + focus else ""

        val seriesClause = if (status.seriesEnabled) {
            "\n\nSERIES MODE — this is Part " + status.part + " of series \"" + status.seriesId + "\".\n" +
            "The production.json MUST include a nested \"series\" object whose series_id is EXACTLY \"" + status.seriesId + "\", part is " + status.part + ", start_seconds is " + status.startSeconds + ", plus end_seconds, is_final (boolean) and a concise summary of this part.\n" +
            "Copy those values verbatim — do not invent a new series id."
        } else ""

        val words = (target * 3.133).roundToInt()

        return "Open this GitHub release: " + releaseUrl + "\n" +
            "Download and read 00_READ_THIS_FIRST.txt FIRST, then inspect the evidence assets (transcript.json, scene_index.json, key_moments.json, and the screenshot composites as needed).\n" +
            "Produce exactly one production.json for a vertical clip" + focusClause + "." + seriesClause + "\n" +
            "The ~" + target + "s figure targets total SPOKEN NARRATION length only — it is NOT the video's length and must not influence how long any cut is, how many cuts you make, or where any end_seconds falls. The final video is exactly as long as the total narration, so keep TOTAL spoken words near " + target + " x 3.1 (about " + words + " words). Choose every cut's end_seconds at the full on-screen completion of its visual payoff (per the PICKING end_seconds rules in 00_READ_THIS_FIRST.txt), even when the footage then runs longer than the narration.\n" +
            "The file must match the production.json contract in 00_READ_THIS_FIRST.txt exactly.\n" +
            "Delivery: your ENTIRE reply must be ONLY the production.json. PREFER a .json file attachment; if you cannot attach files, reply with ONE ```json code block and nothing else. The JSON must be complete and valid: double quotes, no comments, no trailing commas, no truncation. No commentary, headings, or explanation outside the file or code block."
    }
}
