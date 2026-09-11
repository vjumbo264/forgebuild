package com.forgebuild.clipforge.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * GitHub REST client for the ClipForge clone repo.
 *
 * Mirrors the exact API surface the motionssalt/clipforge Telegram bot uses
 * (bot/src/github.js, bot/src/storage.js): all pipeline state lives as JSON
 * blobs in the clone repo on branch `main`; workflows are triggered with
 * workflow_dispatch; run logs come from the Actions runs API.
 */
class GitHubClient(val pat: String, val owner: String, val repo: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val api = "https://api.github.com"

    data class GhException(val code: Int, val body: String) : IOException("HTTP $code: $body")

    private fun base(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $pat")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")

    private suspend fun execute(req: Request.Builder, expected: IntRange = 200..299): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            client.newCall(req.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.code !in expected) throw GhException(resp.code, body.take(400))
                resp.code to body
            }
        }

    // ---- identity ----
    suspend fun whoami(): JSONObject = JSONObject(execute(base("$api/user").get()).second)

    suspend fun repoExists(): Boolean = withContext(Dispatchers.IO) {
        client.newCall(base("$api/repos/$owner/$repo").get().build()).execute().use { it.code == 200 }
    }

    suspend fun createRepo(name: String): JSONObject = JSONObject(
        execute(
            base("$api/user/repos").post(
                JSONObject().put("name", name).put("private", true)
                    .put("auto_init", true).put("description", "ClipForge clone")
                    .toString().toRequestBody("application/json".toMediaType())
            ), 200..299
        ).second
    )

    suspend fun defaultBranchSha(): String {
        val j = JSONObject(execute(base("$api/repos/$owner/$repo").get()).second)
        val branch = j.optString("default_branch", "main")
        val b = JSONObject(execute(base("$api/repos/$owner/$repo/branches/$branch").get()).second)
        return b.getJSONObject("commit").getString("sha")
    }

    // ---- contents API ----
    /** Read a file's decoded text + sha. Returns null if absent (404). */
    suspend fun readFile(path: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        client.newCall(base("$api/repos/$owner/$repo/contents/$path?ref=main").get().build()).execute().use { resp ->
            if (resp.code == 404) return@withContext null
            val body = resp.body?.string().orEmpty()
            if (resp.code != 200) throw GhException(resp.code, body.take(400))
            val j = JSONObject(body)
            val content = j.getString("content").replace("\n", "")
            String(Base64.decode(content, Base64.DEFAULT)) to j.getString("sha")
        }
    }

    /** Read raw bytes of a file. */
    suspend fun readBytes(path: String): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
        client.newCall(base("$api/repos/$owner/$repo/contents/$path?ref=main").get().build()).execute().use { resp ->
            if (resp.code == 404) return@withContext null
            val body = resp.body?.string().orEmpty()
            if (resp.code != 200) throw GhException(resp.code, body.take(400))
            val j = JSONObject(body)
            Base64.decode(j.getString("content").replace("\n", ""), Base64.DEFAULT) to j.getString("sha")
        }
    }

    /** List a directory: array of {name, path, type, sha}. Empty list if absent. */
    suspend fun listDir(path: String): JSONArray = withContext(Dispatchers.IO) {
        client.newCall(base("$api/repos/$owner/$repo/contents/$path?ref=main").get().build()).execute().use { resp ->
            if (resp.code == 404) return@withContext JSONArray()
            val body = resp.body?.string().orEmpty()
            if (resp.code != 200) throw GhException(resp.code, body.take(400))
            if (body.trimStart().startsWith("[")) JSONArray(body) else JSONArray().put(JSONObject(body))
        }
    }

    /** Write a file (create or update when sha supplied). */
    suspend fun putFile(path: String, bytes: ByteArray, message: String, sha: String? = null) {
        val payload = JSONObject()
            .put("message", message)
            .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .put("branch", "main")
        if (sha != null) payload.put("sha", sha)
        execute(
            base("$api/repos/$owner/$repo/contents/$path")
                .put(payload.toString().toRequestBody("application/json".toMediaType()))
        )
    }

    suspend fun deleteFile(path: String, sha: String, message: String) {
        val payload = JSONObject().put("message", message).put("sha", sha).put("branch", "main")
        execute(
            base("$api/repos/$owner/$repo/contents/$path")
                .method("DELETE", payload.toString().toRequestBody("application/json".toMediaType()))
        )
    }

    // ---- actions ----
    suspend fun dispatchWorkflow(file: String, inputs: Map<String, String>) {
        val payload = JSONObject().put("ref", "main")
        val inObj = JSONObject()
        inputs.forEach { (k, v) -> inObj.put(k, v) }
        payload.put("inputs", inObj)
        execute(
            base("$api/repos/$owner/$repo/actions/workflows/$file/dispatches")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
        )
    }

    suspend fun runInfo(runId: Long): JSONObject =
        JSONObject(execute(base("$api/repos/$owner/$repo/actions/runs/$runId").get()).second)

    suspend fun runJobs(runId: Long): JSONArray =
        JSONObject(execute(base("$api/repos/$owner/$repo/actions/runs/$runId/jobs?per_page=100").get()).second)
            .getJSONArray("jobs")

    suspend fun cancelRun(runId: Long) {
        execute(base("$api/repos/$owner/$repo/actions/runs/$runId/cancel").post("".toRequestBody(null)), 200..299)
    }

    /** Download the full (zip) log archive of a run for offline review. */
    suspend fun runLogsZip(runId: Long): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(base("$api/repos/$owner/$repo/actions/runs/$runId/logs").get().build())
            .execute().use { resp ->
                if (resp.code !in 200..299) throw GhException(resp.code, "")
                resp.body?.bytes() ?: ByteArray(0)
            }
    }

    // ---- releases ----
    suspend fun releaseByTag(tag: String): JSONObject? = withContext(Dispatchers.IO) {
        client.newCall(base("$api/repos/$owner/$repo/releases/tags/$tag").get().build()).execute().use { resp ->
            if (resp.code == 404) return@withContext null
            val body = resp.body?.string().orEmpty()
            if (resp.code != 200) throw GhException(resp.code, body.take(400))
            JSONObject(body)
        }
    }

    suspend fun deleteRelease(tag: String) = withContext(Dispatchers.IO) {
        val rel = releaseByTag(tag) ?: return@withContext
        val id = rel.getLong("id")
        client.newCall(base("$api/repos/$owner/$repo/releases/$id").delete().build()).execute().use { it.code }
        // remove the (usually orphan) tag ref as well
        client.newCall(base("$api/repos/$owner/$repo/git/refs/tags/$tag").delete().build()).execute().use { }
        Unit
    }

    /** Open a streaming connection to a release asset (Accept: octet-stream). Caller closes body. */
    fun openAssetStream(assetApiUrl: String): okhttp3.Response {
        val req = Request.Builder().url(assetApiUrl)
            .header("Authorization", "Bearer $pat")
            .header("Accept", "application/octet-stream")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get().build()
        val resp = client.newCall(req).execute()
        if (resp.code !in 200..299) { resp.close(); throw GhException(resp.code, "asset download failed") }
        return resp
    }
}
