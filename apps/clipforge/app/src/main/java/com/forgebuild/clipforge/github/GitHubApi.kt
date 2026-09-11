package com.forgebuild.clipforge.github

import com.forgebuild.clipforge.data.CloneConnection
import com.forgebuild.clipforge.data.Job
import com.forgebuild.clipforge.data.ProductionPlan
import com.forgebuild.clipforge.data.jobFromJson
import com.forgebuild.clipforge.data.planToJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GitHub API client — the Android counterpart of the ClipForge Telegram bot.
 * The bot drives a private clone repo of motionssalt/clipforge via the GitHub API;
 * this client does exactly the same: dispatch workflows, commit job/plan files,
 * and list jobs + releases.
 */
class GitHubApi(private val conn: CloneConnection) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val base = "https://api.github.com"
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun requestBuilder(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer ${conn.pat}")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")

    class ApiException(val code: Int, message: String) : Exception("HTTP $code: $message")

    /** Verify the PAT and repo access (equivalent of the bot's "Connect existing clone"). */
    suspend fun verifyConnection(): String = withContext(Dispatchers.IO) {
        val resp = client.newCall(requestBuilder("$base/repos/${conn.fullName}").build()).execute()
        resp.use {
            if (it.code == 404) throw ApiException(404, "Repository not found or token lacks access")
            if (it.code == 401) throw ApiException(401, "Invalid GitHub token")
            if (!it.isSuccessful) throw ApiException(it.code, it.message)
            JSONObject(it.body!!.string()).optString("full_name", conn.fullName)
        }
    }

    /** Get a repo file's content+sha (null if absent). */
    private suspend fun getFile(path: String): Pair<String, String?>? = withContext(Dispatchers.IO) {
        val resp = client.newCall(
            requestBuilder("$base/repos/${conn.fullName}/contents/$path").build()
        ).execute()
        resp.use {
            if (it.code == 404) return@withContext null
            if (!it.isSuccessful) throw ApiException(it.code, it.message)
            val o = JSONObject(it.body!!.string())
            val content = android.util.Base64.decode(
                o.optString("content").replace("\n", ""), android.util.Base64.DEFAULT
            ).toString(Charsets.UTF_8)
            content to o.optString("sha", null as String?)
        }
    }

    /** Create or update a repo file. */
    private suspend fun putFile(path: String, content: String, message: String) = withContext(Dispatchers.IO) {
        val existing = getFile(path)
        val body = JSONObject()
            .put("message", message)
            .put("content", android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP))
        existing?.second?.let { body.put("sha", it) }
        val resp = client.newCall(
            requestBuilder("$base/repos/${conn.fullName}/contents/$path")
                .put(body.toString().toRequestBody(json)).build()
        ).execute()
        resp.use { if (!it.isSuccessful) throw ApiException(it.code, it.body?.string() ?: it.message) }
    }

    /** Dispatch a workflow on the clone (equivalent of the bot dispatching stage-a / stage-b). */
    suspend fun dispatchWorkflow(workflowFile: String, inputs: Map<String, String>) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("ref", "main")
        if (inputs.isNotEmpty()) body.put("inputs", JSONObject(inputs))
        val resp = client.newCall(
            requestBuilder("$base/repos/${conn.fullName}/actions/workflows/$workflowFile/dispatches")
                .post(body.toString().toRequestBody(json)).build()
        ).execute()
        resp.use {
            if (it.code == 404) throw ApiException(404, "Workflow $workflowFile not found on ${conn.fullName}")
            if (!it.isSuccessful && it.code != 204) throw ApiException(it.code, it.body?.string() ?: it.message)
        }
    }

    /** List jobs by scanning jobs/<id>/status.json via the git trees API. */
    suspend fun listJobs(): List<Job> = withContext(Dispatchers.IO) {
        val treeResp = client.newCall(
            requestBuilder("$base/repos/${conn.fullName}/git/trees/main?recursive=1").build()
        ).execute()
        treeResp.use {
            if (!it.isSuccessful) throw ApiException(it.code, it.message)
            val tree = JSONObject(it.body!!.string()).optJSONArray("tree") ?: JSONArray()
            val paths = mutableListOf<String>()
            for (i in 0 until tree.length()) {
                val p = tree.getJSONObject(i).optString("path")
                if (p.startsWith("jobs/") && p.endsWith("/status.json")) paths.add(p)
            }
            val jobs = mutableListOf<Job>()
            paths.take(25).forEach { p ->
                try {
                    getFile(p)?.first?.let { c -> jobs.add(jobFromJson(JSONObject(c))) }
                } catch (_: Exception) { }
            }
            jobs.sortedByDescending { j -> j.updatedAt }
        }
    }

    /** Create a new job: commit jobs/<id>/stage-a-request.json + status.json and dispatch Stage A. */
    suspend fun createJob(job: Job, plan: ProductionPlan?) {
        val req = JSONObject()
            .put("job_id", job.id).put("title", job.title).put("source", job.source)
            .put("created_at", job.createdAt).put("mode", "manual")
        if (plan != null) req.put("plan", planToJson(plan))
        putFile("jobs/${job.id}/stage-a-request.json", req.toString(2), "clipforge-android: new job ${job.id}")
        val status = JSONObject()
            .put("id", job.id).put("job_id", job.id).put("title", job.title)
            .put("source", job.source).put("state", "queued")
            .put("created_at", job.createdAt).put("updated_at", job.updatedAt)
        putFile("jobs/${job.id}/status.json", status.toString(2), "clipforge-android: queue job ${job.id}")
        dispatchWorkflow("stage-a.yml", mapOf("job_id" to job.id))
    }

    /** Provide a production plan for a job awaiting one, then dispatch Stage B. */
    suspend fun submitPlan(job: Job, plan: ProductionPlan) {
        putFile("jobs/${job.id}/production.json", planToJson(plan).toString(2), "clipforge-android: production plan ${job.id}")
        dispatchWorkflow("stage-b.yml", mapOf("job_id" to job.id))
    }

    /** Cancel a running job by updating its status. */
    suspend fun cancelJob(job: Job) {
        val existing = getFile("jobs/${job.id}/status.json")?.first ?: return
        val o = JSONObject(existing)
        o.put("state", "cancelled").put("updated_at", System.currentTimeMillis() / 1000)
        putFile("jobs/${job.id}/status.json", o.toString(2), "clipforge-android: cancel job ${job.id}")
    }

    /** List releases of the clone (finished clips land here, per the pipeline). */
    suspend fun listReleases(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val resp = client.newCall(
            requestBuilder("$base/repos/${conn.fullName}/releases?per_page=20").build()
        ).execute()
        resp.use {
            if (!it.isSuccessful) throw ApiException(it.code, it.message)
            val arr = JSONArray(it.body!!.string())
            (0 until arr.length()).map { i ->
                val r = arr.getJSONObject(i)
                val asset = r.optJSONArray("assets")?.takeIf { a -> a.length() > 0 }?.getJSONObject(0)
                (r.optString("tag_name", r.optString("name", "release"))) to
                    (asset?.optString("browser_download_url") ?: r.optString("html_url", ""))
            }
        }
    }
}
