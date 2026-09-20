package com.forgebuild.clipforgeandroid.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-task Zernio publishing — parity port of motionssalt/clipforge
 * site/js/features/tasks.js (zernioTaskPublishButton, dispatchZernioPublish,
 * dispatchZernioPostAction). Reads the task's publishing status from
 * jobs/<id>/publish_state.json (or publish.json) and dispatches the SAME
 * publish.yml workflow with the SAME inputs the site uses.
 */
object ZernioPublish {

    const val WORKFLOW = "publish.yml"

    data class Post(
        val postId: String,
        val platform: String,
        val status: String,          // pending | publishing | scheduled | published | failed | cancelled
        val scheduledFor: String = "",
        val message: String = ""
    )

    data class TaskPublishState(
        val status: String,          // not_requested | publishing | scheduled | published | partial | failed
        val targets: List<String> = emptyList(),
        val posts: List<Post> = emptyList(),
        val mode: String = ""
    )

    /** Read the task's publish state from the clone (tolerant of both file names/shapes). */
    fun parse(text: String?): TaskPublishState {
        if (text.isNullOrBlank()) return TaskPublishState(status = "not_requested")
        val j = try { JSONObject(text) } catch (_: Exception) { return TaskPublishState("not_requested") }
        val posts = mutableListOf<Post>()
        val arr = j.optJSONArray("posts")
        if (arr != null) for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            posts.add(
                Post(
                    postId = p.optString("post_id", p.optString("id", "")),
                    platform = p.optString("platform", ""),
                    status = p.optString("status", "pending"),
                    scheduledFor = p.optString("scheduled_for", ""),
                    message = p.optString("message", p.optString("error", ""))
                )
            )
        }
        val targets = mutableListOf<String>()
        j.optJSONArray("targets")?.let { for (i in 0 until it.length()) targets.add(it.optString(i)) }
        return TaskPublishState(
            status = j.optString("status", if (posts.isEmpty()) "not_requested" else "scheduled"),
            targets = targets.ifEmpty { posts.map { it.platform }.distinct() },
            posts = posts,
            mode = j.optString("mode", "")
        )
    }

    /** publish.yml inputs for a whole-task dispatch (site dispatchZernioPublish parity). */
    fun taskDispatchInputs(
        mode: String,                 // "" (auto) | publish_now | smart_schedule | manual_schedule
        jobId: String,
        scheduledFor: String,         // YYYY-MM-DDTHH:MM (manual_schedule only)
        timezone: String,
        targetsJson: String,
        requestId: String
    ): Map<String, String> {
        // publish.yml declares the idempotency input as `request_id`; sending the
        // undeclared `idempotency_key` made the dispatches API reject every publish
        // from the app with HTTP 422 "Unexpected inputs provided".
        val m = linkedMapOf(
            "job_id" to jobId,
            "mode" to mode,
            "scheduled_for" to scheduledFor,
            "timezone" to timezone,
            "targets_json" to targetsJson,
            "request_id" to requestId
        )
        if (scheduledFor.isNotBlank()) m["scheduled_at"] = scheduledFor
        return m
    }

    /** publish.yml inputs for a single-post action (site dispatchZernioPostAction parity). */
    fun postActionInputs(
        action: String,               // retry | publish_now | reschedule | cancel | update
        jobId: String,
        postId: String,
        mode: String,
        scheduledFor: String,
        timezone: String
    ): Map<String, String> = linkedMapOf(
        "action" to action,
        "job_id" to jobId,
        "post_id" to postId,
        "mode" to mode,
        "scheduled_for" to scheduledFor,
        "timezone" to timezone
    )

    /** validate YYYY-MM-DDTHH:MM like the site (validZernioDateTime). */
    fun validDateTime(v: String): Boolean =
        Regex("^\\d{4}-\\d{2}-\\d{2}T([01]\\d|2[0-3]):[0-5]\\d$").matches(v.trim())

    fun validPostId(v: String): Boolean =
        Regex("^[A-Za-z0-9._:\\-]{1,200}$").matches(v)

    fun requestId(jobId: String, action: String): String =
        "android-$jobId-$action-${System.currentTimeMillis()}".take(200)

    /** Friendly label for a publishing status chip. */
    fun statusLabel(status: String): String = when (status) {
        "not_requested" -> "Not requested"
        "publishing" -> "Publishing…"
        "scheduled" -> "Scheduled"
        "published" -> "Published"
        "partial" -> "Partially published"
        "failed" -> "Publish failed"
        else -> status
    }
}
