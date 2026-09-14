package com.forgebuild.clipforgeandroid.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Super Series (feature-01) — freshly rebuilt against the REAL current backend
 * (post session-22 task-92/93). Kept 1:1 with motionssalt/clipforge:
 *   - site/js/super.js     validateSuperPlan / parseAndValidateSuperPlan / sliceSuperPart
 *   - site/js/plan.js      validateProductionPlan / validateStringArray / extractSeries
 *   - site/js/supertick.js submitSuperPlan / superQueueAdvance / buildSuperState
 *   - branding/super_series_settings.json  {version, enabled, updated_at_epoch}
 * The app produces the SAME accept/reject decisions and the SAME error strings
 * for the same input. Do NOT invent different validation.
 *
 * Part 1 dispatches immediately on super-plan submission (one superQueueTick).
 * Part 2..N dispatch automatically, chained by completion from a success-only
 * final step of stage-b.yml — immune to the workflow_run default-branch
 * restriction. The app NEVER dispatches them and has NO "Start Next Part" for
 * Super Series parts.
 */
object SuperSeries {
    const val MAX_PARTS = 20
    const val SETTINGS_PATH = "branding/super_series_settings.json"

    // ---- JS type helpers (typeof number + Number.isFinite + Math.floor) ----
    private fun isIntegerJs(v: Any?): Boolean =
        v is Number && v !is Boolean && v.toDouble().let { it.isFinite() && Math.floor(it) == it }

    private fun isNonemptyString(v: Any?): Boolean = v is String && v.trim().isNotEmpty()

    private fun intVal(v: Any?): Long = (v as Number).toLong()

    // ---------------------------------------------------------------------------
    // parse + validate (backend parseAndValidateSuperPlan)
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
    // Super-plan validation (backend validateSuperPlan, exact error strings)
    // ---------------------------------------------------------------------------

    fun validateSuperPlan(document: Any?): List<String> {
        val errors = mutableListOf<String>()

        if (document !is JSONObject) return listOf("Top level must be a JSON object.")
        val doc = document

        var seriesId: String? = null
        val sidRaw = doc.opt("series_id")
        if (!isNonemptyString(sidRaw)) {
            errors.add("`series_id` must be a non-empty string shared by every part.")
        } else {
            seriesId = sidRaw as String
        }

        val videoDuration = doc.opt("video_duration_seconds")
        if (!isIntegerJs(videoDuration) || intVal(videoDuration) <= 0) {
            errors.add("`video_duration_seconds` must be a positive integer.")
        }
        val targetDuration = doc.opt("target_total_duration_seconds")
        if (!isIntegerJs(targetDuration) || intVal(targetDuration) <= 0) {
            errors.add("`target_total_duration_seconds` must be a positive integer.")
        }

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

            val partSeries = part.optJSONObject("series") ?: JSONObject()
            if (seriesId != null && partSeries.opt("series_id") != seriesId) {
                errors.add("$at.series.series_id must equal the shared top-level series_id.")
            }

            // Each part is itself an ordinary series production plan; run the
            // single-part validator with the positional part number.
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
    // Single-part production-plan validation (backend plan.js validateProductionPlan)
    // ---------------------------------------------------------------------------

    /** Nested-wins-per-field series extraction (backend plan.js extractSeries). */
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

    /** Backend plan.js validateStringArray — same messages, same order. */
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
     * Backend validateProductionPlan(document, { partNumber }): when [partNumber]
     * is set the plan's own series.part is re-stamped to the positional number
     * first — the super-plan's authored part number is never trusted.
     */
    fun validateProductionPlan(input: Any?, partNumber: Int? = null): List<String> {
        val errors = mutableListOf<String>()
        if (input !is JSONObject) return listOf("Plan must be a JSON object.")
        val document = input

        if (partNumber != null) {
            val series = document.optJSONObject("series") ?: JSONObject()
            series.put("part", partNumber)
            document.put("series", series)
        }

        val vd = document.opt("video_duration_seconds")
        if (!isIntegerJs(vd) || intVal(vd) <= 0) {
            errors.add("`video_duration_seconds` must be a positive integer.")
        }
        val td = document.opt("target_total_duration_seconds")
        if (!isIntegerJs(td) || intVal(td) <= 0) {
            errors.add("`target_total_duration_seconds` must be a positive integer.")
        }

        val segments = document.opt("segments")
        if (segments !is JSONArray) {
            errors.add("`segments` must be an array.")
        } else if (segments.length() < 1) {
            errors.add("`segments` must contain at least one segment.")
        } else {
            for (index in 0 until segments.length()) {
                val at = "segments[$index]"
                val seg = segments.opt(index)
                if (seg !is JSONObject) {
                    errors.add("$at must be an object.")
                    continue
                }
                val start = seg.opt("start_seconds")
                val end = seg.opt("end_seconds")
                if (!isIntegerJs(start) || intVal(start) < 0) {
                    errors.add("$at.start_seconds must be a non-negative integer.")
                }
                if (!isIntegerJs(end) || (isIntegerJs(start) && intVal(end) <= intVal(start))) {
                    errors.add("$at.end_seconds must be an integer greater than start_seconds.")
                }
                if (!isNonemptyString(seg.opt("voiceover_text"))) {
                    errors.add("$at.voiceover_text must be a non-empty string.")
                }
                if (seg.has("caption_text") && !seg.isNull("caption_text") && seg.opt("caption_text") !is String) {
                    errors.add("$at.caption_text must be a string when present.")
                }
            }
        }

        val series = extractSeries(document)
        if (series != null) {
            if (!isNonemptyString(series.opt("series_id"))) {
                errors.add("`series_id` must be a non-empty string when series is used.")
            }
            val partVal = series.opt("part")
            if (!isIntegerJs(partVal) || intVal(partVal) < 1) {
                errors.add("`series_part` must be an integer ≥ 1.")
            }
            val startVal = series.opt("start_seconds")
            val endVal = series.opt("end_seconds")
            if (!isIntegerJs(startVal) || intVal(startVal) < 0) {
                errors.add("`series_start_seconds` must be a non-negative integer.")
            }
            if (!isIntegerJs(endVal) || (isIntegerJs(startVal) && intVal(endVal) <= intVal(startVal))) {
                errors.add("`series_end_seconds` must be an integer greater than series_start_seconds.")
            }
            if (!isNonemptyString(series.opt("summary"))) {
                errors.add("`series_summary` must be a non-empty string.")
            }
        }

        validateStringArray(document, "hashtags", 1, 30, "#", true, null, null, errors)
        validateStringArray(document, "hooks", 1, 10, null, false, null, null, errors)

        return errors
    }

    // ---------------------------------------------------------------------------
    // Slicing (backend sliceSuperPart — includes the task-92 "Part N" guarantee)
    // ---------------------------------------------------------------------------

    /**
     * Return the ordinary single-part production.json for [partIndex] (0-based)
     * of a VALID super-plan. [jobId] is the real spawned job id. Every spawned
     * part's title reliably carries "Part N" — appended when the authored title
     * lacks it; titles already naming that part are left verbatim.
     */
    fun sliceSuperPart(document: JSONObject, partIndex: Int, jobId: String): JSONObject {
        val source = document.getJSONArray("parts").getJSONObject(partIndex)
        val part = JSONObject(source.toString()) // deep copy
        val series = part.optJSONObject("series") ?: JSONObject()
        series.put("part", partIndex + 1)
        part.put("series", series)
        part.put("job_id", jobId)
        val partNumber = partIndex + 1
        val titleRaw = part.opt("title")
        val title = if (isNonemptyString(titleRaw)) (titleRaw as String).trim() else ""
        if (title.isNotEmpty() &&
            !Regex("\\bpart\\s+$partNumber\\b", RegexOption.IGNORE_CASE).containsMatchIn(title)
        ) {
            part.put("title", "$title — Part $partNumber")
        }
        return part
    }

    // ---------------------------------------------------------------------------
    // Durable queue state (stored at jobs/<anchor>/super-plan.json)
    // ---------------------------------------------------------------------------

    /** buildSuperState: the whole validated super-plan travels with the record. */
    fun buildSuperState(anchorJobId: String, seriesId: String, document: JSONObject): JSONObject {
        return JSONObject()
            .put("version", 1)
            .put("anchor_job_id", anchorJobId)
            .put("series_id", seriesId)
            .put("total_parts", document.getJSONArray("parts").length())
            .put("video_duration_seconds", document.opt("video_duration_seconds"))
            .put("plan", document)
            .put("spawned", JSONArray())
    }

    // ---------------------------------------------------------------------------
    // Pure queue decision (backend superQueueAdvance) — halt-and-resume order
    // ---------------------------------------------------------------------------

    /**
     * Port of scripts/super_chain/super.js superPartRequestBody: synthesize the
     * ordinary §7.1 stage-a-request body for a spawned Super Series part by reusing
     * SeriesLogic.nextPartRequestBody (the SAME series-continuation helper ordinary
     * series parts use), so the result is byte-for-byte what a manual continuation
     * would have written. Carries the anchor's REAL source reference forward (never a
     * placeholder) so Stage B's >2GiB source re-fetch has something to re-fetch from.
     */
    fun superPartRequestBody(anchorRequest: JSONObject, state: JSONObject, partNumber: Int, summaries: List<Pair<Int, String>>): JSONObject {
        val context = SeriesLogic.buildSeriesContext(summaries)
        return SeriesLogic.nextPartRequestBody(
            anchorRequest,
            SeriesLogic.Continuation(state.getString("series_id"), partNumber, planStartSeconds(state, partNumber)),
            context,
            anchorRequest.optString("job_id")
        )
    }

    private fun planStartSeconds(state: JSONObject, partNumber: Int): Int {
        val parts = state.optJSONObject("plan")?.optJSONArray("parts")
        val part = if (parts != null && partNumber >= 1 && partNumber <= parts.length()) parts.optJSONObject(partNumber - 1) else null
        val series = part?.optJSONObject("series")
        return if (series != null && series.has("start_seconds")) series.optInt("start_seconds", 0) else 0
    }

    sealed class Advance {
        data class Queue(val part: Int, val jobId: String, val plan: JSONObject) : Advance()
        data class Halted(val part: Int, val jobId: String, val message: String) : Advance()
        data class Waiting(val part: Int, val jobId: String) : Advance()
        object Done : Advance()
    }

    /**
     * The FIRST spawned part that is not yet "complete" decides everything:
     * error → halt, non-terminal → wait, complete → look at the next part, none
     * remain → done. [statusOf] returns a spawned job's current status.json state.
     */
    fun superQueueAdvance(state: JSONObject, statusOf: (String) -> String?): Advance {
        val plan = state.getJSONObject("plan")
        val totalParts = state.optInt("total_parts", plan.getJSONArray("parts").length())
        val spawned = state.optJSONArray("spawned") ?: JSONArray()
        for (i in 0 until spawned.length()) {
            val entry = spawned.getJSONObject(i)
            val jobId = entry.getString("job_id")
            val partNo = entry.optInt("part", i + 1)
            val st = statusOf(jobId)
            if (st != "complete") {
                return if (st == "error") {
                    Advance.Halted(
                        partNo, jobId,
                        "Super Series part $partNo failed — restart its Stage B to resume the queue."
                    )
                } else {
                    Advance.Waiting(partNo, jobId)
                }
            }
        }
        if (spawned.length() >= totalParts) return Advance.Done
        val nextPart = spawned.length() + 1
        val jobId = SeriesLogic.nextPartJobId(state.getString("series_id"), nextPart)
        val sliced = sliceSuperPart(plan, nextPart - 1, jobId)
        return Advance.Queue(nextPart, jobId, sliced)
    }

    // ---------------------------------------------------------------------------
    // Queue/halt display view (backend describeSuperQueue)
    // ---------------------------------------------------------------------------

    data class QueueView(
        val anchorJobId: String,
        val seriesId: String,
        val totalParts: Int,
        val spawned: List<Pair<Int, String>>, // part -> jobId, in spawn order
        val haltedPart: Int,                  // >0 when a part's error halted the chain
        val waitingPart: Int,                 // >0 when a part is still running
        val done: Boolean
    ) {
        val halted: Boolean get() = haltedPart > 0
        val label: String
            get() = when {
                done -> "Super Series complete ($totalParts parts)"
                halted -> "Super Series halted at part $haltedPart — restart that part to resume"
                waitingPart > 0 -> "Super Series: part $waitingPart rendering — the next part dispatches automatically"
                else -> "Super Series queued (${spawned.size}/$totalParts parts dispatched)"
            }
    }
}
