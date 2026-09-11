package com.forgebuild.clipforge.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/** Per-clone credentials, encrypted at rest — Android mirror of the bot's crypto.js session model. */
class CredentialStore(context: Context) {
    data class CloneCredentials(val pat: String, val owner: String, val repo: String, val login: String)

    private val prefs = EncryptedSharedPreferences.create(
        context, "clipforge_creds",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): CloneCredentials? {
        val pat = prefs.getString("pat", null) ?: return null
        val owner = prefs.getString("owner", null) ?: return null
        val repo = prefs.getString("repo", null) ?: return null
        return CloneCredentials(pat, owner, repo, prefs.getString("login", "") ?: "")
    }

    fun save(c: CloneCredentials) {
        prefs.edit().putString("pat", c.pat).putString("owner", c.owner)
            .putString("repo", c.repo).putString("login", c.login).apply()
    }

    fun clear() = prefs.edit().clear().apply()
}

/** Canonical task state machine (bot/src/jobs.js). */
object Pipeline {
    val TERMINAL_STATES = setOf("complete", "error", "cancelled")
    private val ORDER = listOf(
        "queued", "stage_a_running", "awaiting_torrent_selection",
        "awaiting_plan", "stage_b_queued", "stage_b_running", "complete",
    )

    fun isTerminal(state: String) = state in TERMINAL_STATES
    fun progressFraction(state: String): Float =
        (ORDER.indexOf(state).takeIf { it >= 0 } ?: 0).toFloat() / (ORDER.size - 1)

    fun describe(state: String): String = when (state) {
        "queued" -> "Queued"
        "stage_a_running" -> "Analyzing source"
        "awaiting_torrent_selection" -> "Pick a torrent file"
        "awaiting_plan" -> "Awaiting production.json"
        "stage_b_queued" -> "Render queued"
        "stage_b_running" -> "Rendering video"
        "complete" -> "Complete"
        "error" -> "Error"
        "cancelled" -> "Cancelled"
        else -> state
    }

    const val JOB_TTL_SECONDS: Long = 48 * 3600
}

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
    val terminal get() = Pipeline.isTerminal(state)

    fun toJson(): JSONObject = JSONObject()
        .put("job_id", jobId).put("state", state).put("message", message)
        .put("created_at_epoch", createdAt).put("updated_at_epoch", updatedAt)
        .put("release_tag", releaseTag).put("release_url", releaseUrl)
        .put("run_id", runId).put("run_url", runUrl)
        .put("series_enabled", seriesEnabled).put("series_id", seriesId)
        .put("part", part).put("start_seconds", startSeconds)

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
    val ALL: List<Triple<String, String, String>> = listOf(
        Triple("en-US-AndrewNeural", "Andrew", "Warm, confident, conversational"),
        Triple("en-US-BrianNeural", "Brian", "Approachable, casual, sincere"),
        Triple("en-US-ChristopherNeural", "Christopher", "Reliable, authoritative narrator"),
        Triple("en-US-EricNeural", "Eric", "Rational, measured narrator"),
        Triple("en-US-GuyNeural", "Guy", "Energetic news-style narrator"),
        Triple("en-US-RogerNeural", "Roger", "Lively narrator"),
        Triple("en-US-AvaNeural", "Ava", "Expressive, caring, conversational"),
        Triple("en-US-AriaNeural", "Aria", "Positive, confident, conversational"),
        Triple("en-US-JennyNeural", "Jenny", "Friendly, considerate narrator"),
        Triple("en-US-MichelleNeural", "Michelle", "Friendly, polished narrator"),
        Triple("en-NG-AbeoNeural", "Abeo", "Nigerian English, friendly and positive"),
        Triple("en-NG-EzinneNeural", "Ezinne", "Nigerian English, friendly and positive"),
    )
    const val DEFAULT = "en-US-AndrewNeural"
}

/**
 * Client-side production.json validator — Kotlin port of
 * bot/src/plan.js parseAndValidateProductionPlan + schemas/production_plan.schema.json.
 * Returns a list of human-readable errors; empty list = valid.
 */
object PlanValidator {
    fun validate(text: String): List<String> {
        val errors = mutableListOf<String>()
        val doc = try {
            JSONObject(text)
        } catch (e: Exception) {
            return listOf("production.json is not valid JSON: ${e.message}")
        }
        fun posInt(key: String) {
            if (!doc.has(key)) { errors.add("`$key` is required."); return }
            val v = doc.opt(key)
            if (v !is Int && v !is Long) errors.add("`$key` must be an integer.")
            else if ((v as Number).toLong() < 1) errors.add("`$key` must be a positive integer.")
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
                    errors.add("`hashtags` must contain 5 to 8 entries (got ${arr.length()}).")
                val seen = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    val h = arr.optString(i, "")
                    if (!h.startsWith("#")) errors.add("hashtags[$i] must start with '#'.")
                    if (h.contains(Regex("\\s"))) errors.add("hashtags[$i] must not contain whitespace.")
                    if (!seen.add(h)) errors.add("hashtags[$i] is a duplicate.")
                }
            }
        }
        if (doc.has("youtube_tags")) {
            val arr = doc.optJSONArray("youtube_tags")
            if (arr == null) errors.add("`youtube_tags` must be an array.")
            else if (arr.length() < 10 || arr.length() > 20)
                errors.add("`youtube_tags` must contain 10 to 20 entries (got ${arr.length()}).")
        }

        // series block (nested preferred, legacy flat accepted)
        var seriesStart = -1L; var seriesEnd = -1L
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
                val at = "cuts[$i]"
                if (c == null) { errors.add("$at must be an object."); continue }
                val start = c.optLong("start_seconds", -1)
                val end = c.optLong("end_seconds", -1)
                if (start < 0) errors.add("$at.start_seconds must be a non-negative integer.")
                if (end < 1) errors.add("$at.end_seconds must be an integer >= 1.")
                if (end in 1..start) errors.add("$at.end_seconds must be greater than start_seconds.")
                if (videoDuration > 0 && end > videoDuration)
                    errors.add("$at.end_seconds exceeds video_duration_seconds.")
                if (seriesEnd > 0 && end > seriesEnd) errors.add("$at.end_seconds exceeds series_end_seconds.")
                if (seriesStart >= 0 && start in 0 until seriesStart)
                    errors.add("$at.start_seconds precedes series_start_seconds.")
                if (i > 0 && start < prevEnd) errors.add("$at overlaps or precedes the prior cut.")
                if (!c.has("voiceover_text") && !c.has("raw_narration"))
                    errors.add("$at needs `voiceover_text` or `raw_narration`.")
                prevEnd = maxOf(prevEnd, end)
            }
        }
        return errors
    }
}

/** Series continuation — Kotlin port of bot/src/series.js (manualSeriesContinuation / nextPartJobId). */
object SeriesLogic {
    fun nextPartJobId(seriesId: String, part: Int): String = "$seriesId-p${part + 1}"

    /** Next (part, startSeconds) when a completed part can be continued, else null. */
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
