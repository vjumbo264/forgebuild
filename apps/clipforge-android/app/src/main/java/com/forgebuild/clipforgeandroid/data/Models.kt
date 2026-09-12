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

/**
 * Series continuation — faithful Kotlin port of bot/src/series.js (operator fix #4):
 * extractPlanSeries / manualSeriesContinuation / buildSeriesContext /
 * nextPartRequestBody / nextPartJobId.
 */
object SeriesLogic {
    const val MAX_CONTEXT_CHARS = 8000
    const val MAX_JOB_ID_LENGTH = 120

    /** bot/src/jobs.js isValidJobId — safe-charset port (letters, digits, dot, dash, underscore). */
    private val JOB_ID_RE = Regex("^[A-Za-z0-9._-]+$")
    fun isValidJobId(id: String): Boolean = id.isNotBlank() && JOB_ID_RE.matches(id)

    /** §6.3 identity rule for the derived next-part job id (bot nextPartJobId). */
    fun nextPartJobId(seriesId: String, nextPart: Int): String {
        val nextId = seriesId + "-p" + nextPart
        require(isValidJobId(nextId) && nextId.length <= MAX_JOB_ID_LENGTH) {
            "The next series part job id would be unsafe."
        }
        return nextId
    }

    /** Next-part coordinates for a continuable manual series part. */
    data class Continuation(val seriesId: String, val part: Int, val startSeconds: Int)

    /**
     * Normalize series metadata from a production.json document, accepting the nested
     * §7.3 object and the legacy flat series_* siblings. Nested wins per-field — the
     * shared closest-analog rule, same as bot extractPlanSeries / plan.js / schema.py.
     */
    fun extractPlanSeries(plan: JSONObject?): JSONObject {
        val out = JSONObject()
        if (plan == null) return out
        val nested = plan.optJSONObject("series")
        val mapping = mapOf(
            "series_id" to "series_id",
            "part" to "series_part",
            "start_seconds" to "series_start_seconds",
            "end_seconds" to "series_end_seconds",
            "is_final" to "series_final",
            "summary" to "series_summary"
        )
        for ((nestedKey, flatKey) in mapping) {
            if (plan.has(flatKey)) out.put(nestedKey, plan.opt(flatKey))
            if (nested != null && nested.has(nestedKey)) out.put(nestedKey, nested.opt(nestedKey))
        }
        return out
    }

    /**
     * Port of bot manualSeriesContinuation(status, request, plan): decide whether a
     * COMPLETED job is a continuable manual series part and, if so, return the next
     * part's coordinates (seriesId, part + 1, plan end_seconds).
     */
    fun manualSeriesContinuation(status: JSONObject?, request: JSONObject?, plan: JSONObject?): Continuation? {
        if (status == null || status.optString("state") != "complete") return null
        if (request == null) return null
        val reqSeries = request.optJSONObject("series") ?: JSONObject()
        if (!reqSeries.optBoolean("enabled", false)) return null
        val seriesId = reqSeries.optString("series_id").trim()
        val part = reqSeries.optInt("part", 0)
        if (seriesId.isEmpty() || part < 1) return null
        if (plan == null) return null
        val planSeries = extractPlanSeries(plan)
        if (planSeries.optBoolean("is_final", false)) return null
        val end = planSeries.optLong("end_seconds", -1)
        if (end < 0) return null
        return Continuation(seriesId, part + 1, end.toInt())
    }

    /**
     * Port of bot buildSeriesContext: "Prior events (Part N): <summary>" lines sorted
     * by part, capped at MAX_CONTEXT_CHARS (bug-56 'Prior events' prefix kept verbatim).
     */
    fun buildSeriesContext(entries: List<Pair<Int, String>>): String {
        val text = entries
            .filter { it.second.isNotBlank() }
            .sortedBy { it.first }
            .joinToString("\n") { (part, summary) -> "Prior events (Part $part): ${summary.trim()}" }
        return text.ifBlank { "(No prior summaries.)" }.take(MAX_CONTEXT_CHARS)
    }

    /**
     * Port of bot nextPartRequestBody: the wizardToRequest-shaped body for the next
     * manual series part (mode forced to manual, focus cleared, source_job_id carried
     * forward with the bug-64 fallback to the COMPLETING part's job id — never the
     * series_id, which is not a job id).
     */
    fun nextPartRequestBody(request: JSONObject, cont: Continuation, context: String, currentJobId: String): JSONObject {
        val source = request.optJSONObject("source") ?: JSONObject()
        val options = request.optJSONObject("options") ?: JSONObject()
        val music = request.optJSONObject("music") ?: JSONObject()
        val reqSeries = request.optJSONObject("series") ?: JSONObject()

        val src = JSONObject()
            .put("kind", source.optString("kind"))
            .put("value", source.optString("value"))
        source.optJSONObject("relay")?.let { src.put("relay", it) }
        val tfi = source.opt("torrent_file_index")
        if (tfi != null && tfi.toString().isNotEmpty()) src.put("torrent_file_index", tfi.toString())

        val opts = JSONObject()
            .put("whisper_model", options.optString("whisper_model"))
            .put("language", options.optString("language"))
            .put("task", options.optString("task"))
            .put("target_duration_seconds", options.optInt("target_duration_seconds"))
            .put("focus", "")
            .put("enable_vision_assist", options.optBoolean("enable_vision_assist", true))

        val sourceJobId = reqSeries.optString("source_job_id").ifBlank {
            currentJobId.ifBlank { cont.seriesId }
        }
        val series = JSONObject()
            .put("enabled", true)
            .put("series_id", cont.seriesId)
            .put("source_job_id", sourceJobId)
            .put("part", cont.part)
            .put("start_seconds", cont.startSeconds)
            .put("context", context.take(MAX_CONTEXT_CHARS))

        val musicOut = JSONObject()
            .put("ref", music.optString("ref"))
            .put("source", music.optString("source", "none"))

        return JSONObject()
            .put("source", src)
            .put("options", opts)
            .put("mode", "manual")
            .put("series", series)
            .put("music", musicOut)
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
