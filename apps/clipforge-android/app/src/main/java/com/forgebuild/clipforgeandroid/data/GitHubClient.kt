package com.forgebuild.clipforgeandroid.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * GitHub REST client for the ClipForge clone repo.
 * Mirrors the exact contract used by the motionssalt/clipforge pipeline & Telegram bot.
 */
class GitHubClient(val pat: String, val owner: String, val repo: String) {
    val client: OkHttpClient = OkHttpClient.Builder()
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

    fun isOriginalRepo(): Boolean {
        val norm = "$owner/$repo".trim().lowercase()
            .removePrefix("https://github.com/").removeSuffix(".git")
        return norm == "motionssalt/clipforge"
    }

    // ---- identity & repo management ----
    suspend fun whoami(): JSONObject = JSONObject(execute(base("$api/user").get()).second)

    suspend fun repoExists(): Boolean = withContext(Dispatchers.IO) {
        client.newCall(base("$api/repos/$owner/$repo").get().build()).execute().use { it.code == 200 }
    }

    suspend fun repoDetails(): JSONObject =
        JSONObject(execute(base("$api/repos/$owner/$repo").get()).second)

    suspend fun setVisibility(isPrivate: Boolean): JSONObject = JSONObject(
        execute(
            base("$api/repos/$owner/$repo")
                .patch(JSONObject().put("private", isPrivate).toString().toRequestBody("application/json".toMediaType()))
        ).second
    )

    suspend fun deleteRepo() = withContext(Dispatchers.IO) {
        execute(base("$api/repos/$owner/$repo").delete(), 200..204)
        Unit
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
    suspend fun readFile(path: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        client.newCall(base("$api/repos/$owner/$repo/contents/$path?ref=main").get().build()).execute().use { resp ->
            if (resp.code == 404) return@withContext null
            val body = resp.body?.string().orEmpty()
            if (resp.code != 200) throw GhException(resp.code, body.take(400))
            val j = JSONObject(body)
            val content = j.getString("content").replace("\n", "").replace("\r", "")
            String(Base64.decode(content, Base64.DEFAULT)) to j.getString("sha")
        }
    }

    suspend fun readBytes(path: String): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
        client.newCall(base("$api/repos/$owner/$repo/contents/$path?ref=main").get().build()).execute().use { resp ->
            if (resp.code == 404) return@withContext null
            val body = resp.body?.string().orEmpty()
            if (resp.code != 200) throw GhException(resp.code, body.take(400))
            val j = JSONObject(body)
            val content = j.getString("content").replace("\n", "").replace("\r", "")
            Base64.decode(content, Base64.DEFAULT) to j.getString("sha")
        }
    }

    suspend fun listDir(path: String): JSONArray = withContext(Dispatchers.IO) {
        client.newCall(base("$api/repos/$owner/$repo/contents/$path?ref=main").get().build()).execute().use { resp ->
            if (resp.code == 404) return@withContext JSONArray()
            val body = resp.body?.string().orEmpty()
            if (resp.code != 200) throw GhException(resp.code, body.take(400))
            if (body.trimStart().startsWith("[")) JSONArray(body) else JSONArray().put(JSONObject(body))
        }
    }

    /**
     * Upload / put a file into the repo with live progress tracking.
     */
    suspend fun putFile(
        path: String,
        bytes: ByteArray,
        message: String,
        sha: String? = null,
        onProgress: ((bytesSent: Long, totalBytes: Long) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val b64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val payload = JSONObject()
            .put("message", message)
            .put("content", b64Content)
            .put("branch", "main")
        if (sha != null) payload.put("sha", sha)

        val rawJson = payload.toString().toByteArray(Charsets.UTF_8)
        val totalLength = rawJson.size.toLong()

        val countingBody = object : RequestBody() {
            override fun contentType() = "application/json".toMediaType()
            override fun contentLength() = totalLength
            override fun writeTo(sink: BufferedSink) {
                var bytesWritten = 0L
                val chunkSize = 8192
                var offset = 0
                while (offset < rawJson.size) {
                    val count = Math.min(chunkSize, rawJson.size - offset)
                    sink.write(rawJson, offset, count)
                    offset += count
                    bytesWritten += count
                    onProgress?.invoke(bytesWritten, totalLength)
                }
            }
        }

        val req = base("$api/repos/$owner/$repo/contents/$path").put(countingBody)
        execute(req)
        onProgress?.invoke(totalLength, totalLength)
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

    suspend fun syncFromSource() {
        dispatchWorkflow("clone-sync.yml", emptyMap())
    }

    suspend fun pushUpdateToClones() {
        if (!isOriginalRepo()) throw IllegalStateException("Only main account can push updates")
        dispatchWorkflow("push-update.yml", emptyMap())
    }

    suspend fun pushNews(newsText: String) {
        if (!isOriginalRepo()) throw IllegalStateException("Only main account can broadcast news")
        val payload = JSONObject().put("message", newsText).put("updated_at", System.currentTimeMillis())
        putFile("branding/news.json", payload.toString(2).toByteArray(), "Broadcast news")
    }

    suspend fun runInfo(runId: Long): JSONObject =
        JSONObject(execute(base("$api/repos/$owner/$repo/actions/runs/$runId").get()).second)

    suspend fun runJobs(runId: Long): JSONArray =
        JSONObject(execute(base("$api/repos/$owner/$repo/actions/runs/$runId/jobs?per_page=100").get()).second)
            .getJSONArray("jobs")

    suspend fun cancelRun(runId: Long) {
        execute(base("$api/repos/$owner/$repo/actions/runs/$runId/cancel").post("".toRequestBody(null)), 200..299)
    }

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
        client.newCall(base("$api/repos/$owner/$repo/releases/$id").delete().build()).execute().use { }
        client.newCall(base("$api/repos/$owner/$repo/git/refs/tags/$tag").delete().build()).execute().use { }
        Unit
    }

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
