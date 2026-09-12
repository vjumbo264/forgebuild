package com.forgebuild.clipforgeandroid.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Super Series (feature-01) — Kotlin port of bot/src/super_series.js +
 * bot/src/plan.js validateProductionPlan, kept 1:1 with the bot and with
 * pipeline/plan/super_series.py: the SAME accept/reject decisions and the SAME
 * error strings for the same input (the bot/pipeline pin this equivalence in
 * pipeline/tests/test_super_series.py). Do NOT invent different validation.
 *
 * Ground truth re-read 2026-09-12 from motionssalt/clipforge @ main:
 *   - validateSuperPlan (bot/src/super_series.js)
 *   - validateProductionPlan + validateStringArray + extractSeries (bot/src/plan.js)
 *   - superQueueAdvance halt-and-resume decision order (bot/src/super_series.js)
 *   - buildSuperState durable record shape (bot/src/super_series.js)
 *   - settings path branding/super_series_settings.json (bot/src/settings_super.js)
 */
object SuperSeries {
    const val MAX_PARTS = 20
    const val SETTINGS_PATH = "branding/super_series_settings.json"

    // ---- JS type helpers (exact semantics: typeof number + Number.isFinite + floor) ----
    private fun isPlainObject(v: Any?): Boolean = v is JSONObject

    private fun isIntegerJs(v: Any?): Boolean =
        v is Number && v !is Boolean && v.toDouble().let { it.isFinite() && Math.floor(it) == it }

    private fun isNonemptyString(v: Any?): Boolean = v is String && v.trim().isNotEmpty()

    private fun intVal(v: Any?): Long = (v as Number).toLong()

    // ---------------------------------------------------------------------------
    // parse + validate (bot parseAndValidateSuperPlan)
    // ---------------------------------------------------------------------------

    data class ParseResult(val document: JSONObject?, val errors: List<String>)

    /** Mirrors parseAndValidateSuperPlan: JSON.parse errors reject before validation. */
    fun parseAndValidateSuperPlan(text: String): ParseResult {
        val value: Any? = try {
            JSONTokener(text).nextValue()
        } catch (e: Exception) {
            return ParseResult(null, listOf("Not valid JSON: ${e.message ?: "parse error"}."))
        }
        val errors = validateSuperPlan(value)
        return ParseResult(if (value is JSONObject) value else null, errors)
    }

    // ---------------------------------------------------------------------------
    // Super-plan validation (bot validateSuperPlan, exact error strings)
    // ---------------------------------------------------------------------------

    fun validateSuperPlan(document: Any?): List<String> {
        val errors = mutableListOf<String>()

        if (!isPlainObject(document)) return listOf("Top level must be a JSON object.")
        val doc = document as JSONObject

        // -- Shared series id --
        var seriesId: String? = doc.opt("series_id") as? String
        if (!isNonemptyString(doc.opt("series_id"))) {
            errors.add("`series_id` must be a non-empty string shared by every part.")
            seriesId = null
        }

        // -- Required positive-integer scalars --
        val videoDuration = doc.opt("video_duration_seconds")
        if (!isIntegerJs(videoDuration) || intVal(videoDuration) <= 0) {
            errors.add("`video_duration_seconds` must be a positive integer.")
        }
        val targetDuration = doc.opt("target_total_duration_seconds")
        if (!isIntegerJs(targetDuration) || intVal(targetDuration) <= 0) {
            errors.add("`target_total_duration_seconds` must be a positive integer.")
        }

        // -- parts array --
        val parts = doc.opt("parts")
        if (parts !is JSONArray) {
            errors.add("`parts` must be an array of per-part production plans.")
            return errors
        }
        if (parts.length() < 1) {
            errors.add("`parts` is empty — at least one part is required.")
            return errors
        }
        if (parts.length() > MAX_PARTS) {
            errors.add("`parts` must contain at most $MAX_PARTS entries.")
        }

        var finalCount = 0
        val titles = HashSet<String>()
        var previousEnd: Long? = null

        for (index in 0 until parts.length()) {
            val at = "parts[$index]"
            val part = parts.opt(index)
            if (part !is JSONObject) {
                errors.add("$at must be an object.")
                continue
            }

            // Every part must carry the same shared series_id.
            val partSeries = part.optJSONObject("series") ?: JSONObject()
            if (seriesId != null && partSeries.opt("series_id") != seriesId) {
                errors.add("$at.series.series_id must equal the shared top-level series_id.")
            }

            // Each part is itself an ordinary §7.3 series production plan; run the
            // existing single-part validator with the positional part number.
            val partErrors = validateProductionPlan(part, index + 1)
            for (message in partErrors) errors.add("$at: $message")

            if (partSeries.opt("is_final") == true) finalCount += 1

            val title = part.opt("title")
            if (isNonemptyString(title)) {
                val key = (title as String).trim().lowercase()
                if (!titles.add(key)) {
                    errors.add("$at.title duplicates an earlier part's title.")
                }
            }

            val startVal = partSeries.opt("start_seconds")
            val endVal = partSeries.opt("end_seconds")
            if (isIntegerJs(startVal) && isIntegerJs(endVal)) {
                val start = intVal(startVal)
                val end = intVal(endVal)
                if (index == 0 && start != 0L) {
                    errors.add("$at.series.start_seconds must be 0 for the first part.")
                }
                if (previousEnd != null && start != previousEnd) {
                    errors.add(
                        "$at.series.start_seconds must equal the previous part's series_end_seconds " +
                            "(parts must tile the source with no gaps or overlaps)."
                    )
                }
                previousEnd = end
            }
        }

        if (finalCount != 1) {
            errors.add("Exactly one part must be marked series.is_final = true.")
        } else {
            val last = parts.opt(parts.length() - 1)
            val lastSeries = if (last is JSONObject) last.optJSONObject("series") else null
            if (lastSeries?.opt("is_final") != true) {
                errors.add("The part marked series.is_final = true must be the last entry in `parts`.")
            }
        }

        return errors
    }

    // ---------------------------------------------------------------------------
    // Single-part production-plan validation (bot plan.js validateProductionPlan,
    // exact error strings incl. the partNumber re-stamp used for super-plan parts)
    // ---------------------------------------------------------------------------

    /** Nested-wins-per-field series extraction (bot plan.js extractSeries). */
    private fun extractSeries(document: JSONObject): JSONObject? {
        val mapping = mapOf(
            "series_id" to "series_id",
            "part" to "series_part",
            "start_seconds" to "series_start_seconds",
            "end_seconds" to "series_end_seconds",
            "is_final" to "series_final",
            "summary" to "series_summary"
        )
        val nested = document.optJSONObject("series")
        val values = JSONObject()
        var hasFlat = false
        for ((nestedKey, flatKey) in mapping) {
            if (document.has(flatKey)) {
                hasFlat = true
                values.put(nestedKey, document.opt(flatKey) ?: JSONObject.NULL)
            }
            if (nested != null && nested.has(nestedKey)) {
                values.put(nestedKey, nested.opt(nestedKey) ?: JSONObject.NULL)
            }
        }
        return if (hasFlat || nested != null) values else null
    }

    /** Bot plan.js validateStringArray — same messages, same order. */
    private fun validateStringArray(
        document: JSONObject,
        name: String,
        minimum: Int,
        maximum: Int,
        requirePrefix: String?,
        forbidWhitespace: Boolean,
        forbidPrefix: String?,
        forbidSubstring: String?,
        errors: MutableList<String>
    ) {
        if (!document.has(name)) return
        val value = document.opt(name)
        if (value !is JSONArray) {
            errors.add("`$name` must be an array of strings when present.")
            return
        }
        if (value.length() < minimum || value.length() > maximum) {
            errors.add("`$name` must contain between $minimum and $maximum entries.")
        }
        val seen = HashSet<String>()
        for (index in 0 until value.length()) {
            val entry = value.opt(index)
            val at = "$name[$index]"
            if (!isNonemptyString(entry)) {
                errors.add("$at must be a non-empty string.")
                continue
            }
            val cleaned = (entry as String).trim()
            if (requirePrefix != null && !cleaned.startsWith(requirePrefix)) {
                errors.add("$at must start with $requirePrefix.")
            }
            if (forbidWhitespace && Regex("\\s").containsMatchIn(cleaned)) {
                errors.add("$at must not contain whitespace.")
            }
            if (forbidPrefix != null && cleaned.startsWith(forbidPrefix)) {
                errors.add("$at must not start with $forbidPrefix.")
            }
            if (forbidSubstring != null && cleaned.contains(forbidSubstring)) {
                errors.add("$at must not contain $forbidSubstring.")
            }
            if (!seen.add(cleaned.lowercase())) {
                errors.add("$at duplicates an earlier entry.")
            }
        }
    }

    /**
     * Bot validateProductionPlan(document, { partNumber }): when [partNumber] is set
     * the plan's own series.part is re-stamped to the positional number first.
     */
    fun validateProductionPlan(input: Any?, partNumber: Int? = null): List<String> {
        // feature-01: options.partNumber overrides the series part a sliced
        // super-plan part is expected to carry (positional).
        if (partNumber != null && isPlainObject(input)) {
            val series = (input as JSONObject).optJSONObject("series")
            if (series != null) series.put("part", partNumber)
        }

        if (!isPlainObject(input)) return listOf("Top level must be a JSON object.")
        val doc = input as JSONObject
        val errors = mutableListOf<String>()

        // -- Optional title --
        if (doc.has("title") && !isNonemptyString(doc.opt("title"))) {
            errors.add("`title` must be a non-empty string when present (or omit it entirely).")
        }

        // -- Required positive-integer scalars --
        for (key in listOf("video_duration_seconds", "target_total_duration_seconds")) {
            val value = doc.opt(key)
            if (!isIntegerJs(value) || intVal(value) <= 0) {
                errors.add("`$key` must be a positive integer.")
            }
        }

        // -- Optional tag arrays --
        validateStringArray(doc, "hashtags", 5, 8,
            requirePrefix = "#", forbidWhitespace = true, forbidPrefix = null, forbidSubstring = null, errors)
        validateStringArray(doc, "youtube_tags", 10, 20,
            requirePrefix = null, forbidWhitespace = false, forbidPrefix = "#", forbidSubstring = ",", errors)

        // -- Series (optional) --
        val seriesValues = extractSeries(doc)
        var seriesStart: Long? = null
        var seriesEnd: Long? = null

        if (seriesValues != null) {
            if (!isNonemptyString(seriesValues.opt("series_id"))) {
                errors.add("`series_id` must be a non-empty string for a series production plan.")
            }
            val part = seriesValues.opt("part")
            if (!isIntegerJs(part) || intVal(part) <= 0) {
                errors.add("`series_part` must be a positive integer for a series production plan.")
            }
            val startVal = seriesValues.opt("start_seconds")
            if (!isIntegerJs(startVal) || intVal(startVal) < 0) {
                errors.add("`series_start_seconds` must be a non-negative integer for a series production plan.")
            } else {
                seriesStart = intVal(startVal)
            }
            val endVal = seriesValues.opt("end_seconds")
            if (!isIntegerJs(endVal) || intVal(endVal) < 0) {
                errors.add("`series_end_seconds` must be a non-negative integer for a series production plan.")
            } else {
                seriesEnd = intVal(endVal)
            }
            if (seriesStart != null && seriesEnd != null && seriesEnd <= seriesStart) {
                errors.add("`series_end_seconds` must be greater than `series_start_seconds`.")
            }
            if (seriesValues.opt("is_final") !is Boolean) {
                errors.add("`series_final` must be boolean for a series production plan.")
            }
            val summary = seriesValues.opt("summary")
            if (!isNonemptyString(summary)) {
                errors.add("`series_summary` must be a non-empty string for a series production plan.")
            } else if ((summary as String).trim().length > 1200) {
                errors.add("`series_summary` exceeds the maximum allowed length.")
            }
        }

        // -- Cuts --
        val cuts = doc.opt("cuts")
        if (cuts !is JSONArray) {
            errors.add("`cuts` must be an array.")
            return errors
        }
        if (cuts.length() < 1) {
            errors.add("`cuts` is empty — at least one cut is required.")
            return errors
        }

        val duration = doc.opt("video_duration_seconds")
        val validDuration: Long? = if (isIntegerJs(duration)) intVal(duration) else null
        var previousEnd: Long? = null

        for (index in 0 until cuts.length()) {
            val cut = cuts.opt(index)
            val at = "cuts[$index]"
            if (cut !is JSONObject) {
                errors.add("$at must be an object.")
                continue
            }
            val start = cut.opt("start_seconds")
            val end = cut.opt("end_seconds")
            if (!isIntegerJs(start)) errors.add("$at.start_seconds must be an integer.")
            if (!isIntegerJs(end)) errors.add("$at.end_seconds must be an integer.")

            var narration = cut.opt("voiceover_text")
            if (!isNonemptyString(narration)) narration = cut.opt("raw_narration")
            if (!isNonemptyString(narration)) {
                errors.add("$at.voiceover_text must be a non-empty string (legacy raw_narration accepted).")
            }

            if (!isIntegerJs(start) || !isIntegerJs(end)) continue
            val s = intVal(start)
            val e = intVal(end)

            if (s < 0) errors.add("$at.start_seconds must be at least 0.")
            if (seriesStart != null && s < seriesStart) {
                errors.add("$at.start_seconds precedes series_start_seconds.")
            }
            if (seriesEnd != null && e > seriesEnd) {
                errors.add("$at.end_seconds exceeds series_end_seconds.")
            }
            if (e <= s) errors.add("$at.end_seconds must be greater than start_seconds.")
            if (validDuration != null && e > validDuration) {
                errors.add("$at.end_seconds exceeds video_duration_seconds.")
            }
            if (previousEnd != null && s < previousEnd) {
                errors.add("$at overlaps or precedes the prior cut.")
            }
            previousEnd = e
        }

        return errors
    }

    // ---------------------------------------------------------------------------
    // Durable queue state (bot buildSuperState — jobs/<anchor>/super-plan.json)
    // ---------------------------------------------------------------------------

    fun buildSuperState(anchorJobId: String, seriesId: String, document: JSONObject): JSONObject =
        JSONObject()
            .put("version", 1)
            .put("anchor_job_id", anchorJobId)
            .put("series_id", seriesId)
            .put("total_parts", document.getJSONArray("parts").length())
            .put("video_duration_seconds", document.optLong("video_duration_seconds"))
            .put("plan", document)
            .put("spawned", JSONArray())

    // ---------------------------------------------------------------------------
    // Queue decision (bot superQueueAdvance — same ordering, same halt messages)
    // ---------------------------------------------------------------------------

    data class SpawnedPart(val part: Int, val jobId: String, val state: String)

    data class QueueView(
        val totalParts: Int,
        val spawned: List<SpawnedPart>,
        val queueStatus: Status,
        /** The part the queue is currently stopped/moving on (halted part or next to queue). */
        val focusPart: Int,
        val focusJobId: String,
        /** The bot's exact halt message when paused; null otherwise. */
        val message: String?
    ) {
        enum class Status { DONE, WAITING, HALTED, QUEUING }
    }

    /**
     * Port of superQueueAdvance(state, statusFor): walk the spawned parts in spawn
     * order; the FIRST not-complete part decides. error/cancelled -> HALTED (bot's
     * exact message); anything else non-terminal -> WAITING; everything complete ->
     * done when every part landed, otherwise the controller will QUEUE the next part.
     */
    fun evaluateQueue(state: JSONObject, statusFor: (String) -> String?): QueueView {
        val plan = state.optJSONObject("plan")
        val planParts = plan?.optJSONArray("parts")
        val totalParts = state.optInt("total_parts", 0)
        val spawned = mutableListOf<SpawnedPart>()
        val arr = state.optJSONArray("spawned")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val entry = arr.optJSONObject(i) ?: continue
                val jobId = entry.optString("job_id")
                spawned.add(SpawnedPart(entry.optInt("part"), jobId, statusFor(jobId) ?: ""))
            }
        }
        if (plan == null || planParts == null || totalParts < 1) {
            return QueueView(totalParts, spawned, QueueView.Status.DONE, 0, "", null)
        }
        for (entry in spawned) {
            if (entry.state == "complete") continue
            if (entry.state == "error") {
                return QueueView(
                    totalParts, spawned, QueueView.Status.HALTED, entry.part, entry.jobId,
                    "Super Series part ${entry.part} of $totalParts failed (task ${entry.jobId} is in state error). " +
                        "The queue is PAUSED — no further parts will be queued. Open that task and use Restart Stage B; " +
                        "the moment it reaches complete, the queue resumes automatically."
                )
            }
            if (entry.state == "cancelled") {
                return QueueView(
                    totalParts, spawned, QueueView.Status.HALTED, entry.part, entry.jobId,
                    "Super Series part ${entry.part} of $totalParts was cancelled (task ${entry.jobId}). " +
                        "The queue is PAUSED — no further parts will be queued. Open that task and use Restart Stage B; " +
                        "the moment it reaches complete, the queue resumes automatically."
                )
            }
            // queued / stage_b_queued / stage_b_running / anything else non-terminal:
            // the pipeline is working on it — the queue waits for it.
            return QueueView(totalParts, spawned, QueueView.Status.WAITING, entry.part, entry.jobId, null)
        }
        if (spawned.size >= totalParts) {
            return QueueView(totalParts, spawned, QueueView.Status.DONE, 0, "", null)
        }
        // Everything spawned so far is complete — the controller queues the next part.
        return QueueView(totalParts, spawned, QueueView.Status.QUEUING, spawned.size + 1, "", null)
    }
}
