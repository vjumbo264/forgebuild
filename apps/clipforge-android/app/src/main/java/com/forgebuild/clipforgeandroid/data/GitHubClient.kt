package com.forgebuild.clipforgeandroid.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Dns
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * GitHub REST client for the ClipForge clone repo.
 * Mirrors the exact contract used by the motionssalt/clipforge pipeline & Telegram bot.
 */
class GitHubClient(val pat: String, val owner: String, val repo: String, private val appCtx: Context? = null) {

    /** Session-10 fix #8: exact-request diagnostics — every non-2xx response is
     *  logged with method, path, HTTP code and a response excerpt, so a failing
     *  write on one specific device/instance is diagnosable from evidence. */
    private fun requestLog(method: String, path: String, code: Int, body: String) {
        DiagLog.log(appCtx, "GitHubAPI", "$method $path -> HTTP $code :: ${body.take(280)}")
    }
    /** IPv4-first DNS with an IPv6 Happy-Eyeballs fallback (operator fix #6 —
     *  ETIMEDOUT on mobile data). Mobile networks frequently have a broken/slow
     *  IPv6 route to api.github.com:443; ordering IPv4 first avoids the stall
     *  while still returning every address so OkHttp can race the rest. */
    private val ipv4FirstDns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val all = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
            return all.sortedBy { if (it is java.net.Inet4Address) 0 else 1 }
        }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        // fix #6: the operator's clone-creation failure was a 20s CONNECT timeout on
        // mobile data, not auth/logic. Raise connect to 45s and let OkHttp retry
        // silently-failed connections.
        .connectTimeout(45, TimeUnit.SECONDS)
        // Slow-network fix (2026-09-20, operator report: starting a job from a
        // .torrent upload failed with a 4xx on a slow link): the body is base64
        // (~1.33x inflation); at 60s write/read caps a trickling mobile link gets
        // cut mid-upload, and GitHub can then answer 408 Request Timeout (a 4xx).
        // Give writes/reads 5 min and drop the overall call cap — the per-request
        // progress UI is the real bound for the operator.
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .dns(ipv4FirstDns)
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
            val built = req.build()
            client.newCall(built).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.code !in expected) {
                    // fix #8: record the EXACT failing request (method + path + code) at
                    // the moment it fails — never guess after the fact.
                    requestLog(built.method, built.url.encodedPath, resp.code, body)
                    if (resp.code == 403) throw GhException(403,
                        "403 FORBIDDEN on ${built.method} ${built.url.encodedPath} — the exact request is in the on-device diagnostic log (Settings → About). If this device runs a build older than v7, install the latest APK first. Body: " + body.take(300))
                    throw GhException(resp.code, body.take(400))
                }
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

    /** Bot parity (github.js beginShadowCloneCreation): private, auto_init FALSE —
     *  the Contents-API bootstrap PUT creates the first commit + ref (bug-46: the
     *  Git Data API 409s on a repo with zero refs, the Contents API does not). */
    suspend fun createRepo(name: String): JSONObject = JSONObject(
        execute(
            base("$api/user/repos").post(
                JSONObject().put("name", name).put("private", true)
                    .put("auto_init", false).put("description", "Private ClipForge Shadow Clone")
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

    // ---- git data / refs (Shadow Clone port, bot github.js parity) ----

    /** GET /git/ref/heads/<branch> — returns the ref's commit sha, throws on missing ref. */
    suspend fun branchRefSha(branch: String): String {
        val j = JSONObject(execute(base("$api/repos/$owner/$repo/git/ref/heads/$branch").get()).second)
        return j.getJSONObject("object").getString("sha")
    }

    /** branchRefSha for ref-settling loops: null when the ref does not exist yet. */
    suspend fun branchRefShaOrNull(branch: String): String? = try {
        branchRefSha(branch)
    } catch (e: GhException) {
        if (e.code == 404) null else throw e
    }

    /** GET /git/commits/<sha> -> the commit's tree sha. */
    suspend fun gitCommitTreeSha(commitSha: String): String {
        val j = JSONObject(execute(base("$api/repos/$owner/$repo/git/commits/$commitSha").get()).second)
        return j.getJSONObject("tree").getString("sha")
    }

    /** GET /git/trees/<sha>?recursive=1 — the full (possibly large) tree object. */
    suspend fun gitTreeRecursive(treeSha: String): JSONObject = JSONObject(
        execute(base("$api/repos/$owner/$repo/git/trees/$treeSha?recursive=1").get()).second
    )

    /** GET /git/blobs/<sha> — {content: base64, encoding: base64} (for workflow-file copies). */
    suspend fun gitBlobBase64(blobSha: String): JSONObject = JSONObject(
        execute(base("$api/repos/$owner/$repo/git/blobs/$blobSha").get()).second
    )

    /** POST /git/refs — create refs/heads/<branch> at [sha] (branch normalization). */
    suspend fun createRef(ref: String, sha: String) {
        execute(
            base("$api/repos/$owner/$repo/git/refs").post(
                JSONObject().put("ref", ref).put("sha", sha)
                    .toString().toRequestBody("application/json".toMediaType())
            )
        )
    }

    /** PATCH /repos/{repo} — set the default branch (branch normalization). */
    suspend fun patchDefaultBranch(branch: String): JSONObject = JSONObject(
        execute(
            base("$api/repos/$owner/$repo")
                .patch(JSONObject().put("default_branch", branch)
                    .toString().toRequestBody("application/json".toMediaType()))
        ).second
    )

    /** DELETE /git/refs/heads/<branch> — remove the pre-normalization branch. */
    suspend fun deleteBranchRef(branch: String) {
        execute(base("$api/repos/$owner/$repo/git/ref/heads/$branch").delete(), 200..204)
    }

    // ---- contents API ----
    suspend fun readFile(path: String, ref: String = "main"): Pair<String, String>? = withContext(Dispatchers.IO) {
        client.newCall(base("$api/repos/$owner/$repo/contents/$path?ref=$ref").get().build()).execute().use { resp ->
            if (resp.code == 404) return@withContext null
            val body = resp.body?.string().orEmpty()
            if (resp.code != 200) {
                requestLog("GET", "/repos/$owner/$repo/contents/$path?ref=$ref", resp.code, body)
                throw GhException(resp.code, body.take(400))
            }
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
     *
     * 422 fix (bot parity with bot/src/github.js putTextFile): the GitHub
     * Contents create-or-update endpoint REQUIRES the existing file's `sha`
     * on every UPDATE, and only tolerates its absence on first CREATE. The
     * old code included `sha` only when the caller happened to pass one —
     * every overwrite of an already-existing file (e.g. rewriting
     * jobs/<id>/stage-a-request.json in the torrent-selection path, which
     * createStageATask already created) failed with HTTP 422
     * "Invalid request. \"sha\" wasn't supplied". We now ALWAYS GET the file
     * first (404 tolerated = brand-new file) and include its sha on update.
     */
    suspend fun putFile(
        path: String,
        bytes: ByteArray,
        message: String,
        sha: String? = null,
        onProgress: ((bytesSent: Long, totalBytes: Long) -> Unit)? = null,
        branch: String = "main"
    ) = withContext(Dispatchers.IO) {
        // Resolve the current sha unless the caller supplied one explicitly.
        val effectiveSha: String? = sha ?: run {
            try {
                readFile(path, branch)?.second
            } catch (e: GhException) {
                if (e.code == 404) null else throw e
            }
        }
        // fix #8: log create-vs-update intent (sha present = UPDATE, absent = CREATE).
        // A stale or wrongly-absent sha against the Contents API is the exact failure
        // class behind the torrent-selection 422 and the suspected second-device 403.
        DiagLog.log(appCtx, "GitHubAPI", "PUT contents/$path (${if (effectiveSha != null) "update sha=${effectiveSha.take(8)}" else "create (no sha)"})")
        val b64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val payload = JSONObject()
            .put("message", message)
            .put("content", b64Content)
            .put("branch", branch)
        if (effectiveSha != null) payload.put("sha", effectiveSha)

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
        // Return the response BODY (the Contents-API create/update response carries
        // content + commit — the Shadow Clone bootstrap needs commit.sha). Every
        // pre-existing caller ignores the return value, so this is additive.
        //
        // Slow-network resilience (operator report 2026-09-20): retry the transient
        // slow-link failure modes — GitHub 408 (upload trickled too slowly) and
        // mid-upload socket stalls — up to 2 extra attempts with backoff. The PUT
        // is idempotent for identical content+sha, so repeating a write that had
        // silently succeeded server-side is a harmless no-op update.
        var attempt = 0
        var body = ""
        while (true) {
            try {
                val (_, b) = execute(req)
                body = b
                break
            } catch (e: GhException) {
                if (e.code == 408 && attempt < 2) {
                    attempt++
                    DiagLog.log(appCtx, "GitHubAPI", "PUT contents/$path -> HTTP 408 (slow upload), retry $attempt/2")
                    kotlinx.coroutines.delay(2000L * attempt)
                    continue
                }
                throw e
            } catch (e: SocketTimeoutException) {
                if (attempt < 2) {
                    attempt++
                    DiagLog.log(appCtx, "GitHubAPI", "PUT contents/$path timed out mid-upload, retry $attempt/2")
                    kotlinx.coroutines.delay(2000L * attempt)
                    continue
                }
                throw e
            }
        }
        onProgress?.invoke(totalLength, totalLength)
        body
    }

    suspend fun deleteFile(path: String, sha: String, message: String, branch: String = "main") {
        val payload = JSONObject().put("message", message).put("sha", sha).put("branch", branch)
        execute(
            base("$api/repos/$owner/$repo/contents/$path")
                .method("DELETE", payload.toString().toRequestBody("application/json".toMediaType()))
        )
    }

    // ---- actions ----
    suspend fun dispatchWorkflow(file: String, inputs: Map<String, String>, ref: String = "main") {
        val payload = JSONObject().put("ref", ref)
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

    /**
     * Bot parity (bot/src/index.js pushNews): the announcement lives at
     * docs/news.json with {version, message, interval_hours, published_at(ISO)}
     * — the app's previous branding/news.json {message, updated_at} shape was
     * invented and never matched the bot.
     */
    suspend fun pushNews(newsText: String) {
        if (!isOriginalRepo()) throw IllegalStateException("Only main account can broadcast news")
        val publishedAt = java.time.Instant.ofEpochMilli(System.currentTimeMillis()).toString()
        val payload = JSONObject()
            .put("version", 1)
            .put("message", newsText)
            .put("interval_hours", 0)
            .put("published_at", publishedAt)
        putFile("docs/news.json", payload.toString(2).toByteArray(), "clipforge: broadcast news")
    }

    // ---- GitHub Actions secrets (bot parity: the Zernio API key is stored as
    // the sealed repo Actions secret ZERNIO_API_KEY, NEVER in a settings json) ----

    /** True when the named Actions secret exists on this repo (404 = absent). */
    suspend fun actionsSecretExists(secretName: String): Boolean = withContext(Dispatchers.IO) {
        client.newCall(base("$api/repos/$owner/$repo/actions/secrets/$secretName").get().build()).execute().use { resp ->
            when (resp.code) {
                200 -> true
                404 -> false
                else -> throw GhException(resp.code, resp.body?.string().orEmpty().take(400))
            }
        }
    }

    /** Delete the named Actions secret (bot: deleteZernioSecret). 404 tolerated. */
    suspend fun deleteActionsSecret(secretName: String) = withContext(Dispatchers.IO) {
        client.newCall(base("$api/repos/$owner/$repo/actions/secrets/$secretName").delete().build()).execute().use { resp ->
            if (resp.code !in 200..299 && resp.code != 404) throw GhException(resp.code, resp.body?.string().orEmpty().take(400))
        }
        Unit
    }

    /**
     * Create/update the sealed Zernio API key secret (bot: updateZernioSecret ->
     * updateActionsSecret). Requires a PAT with Actions:write; the sealed value
     * is a libsodium box over the repo's Actions public key.
     */
    suspend fun setZernioSecret(plaintext: String) = withContext(Dispatchers.IO) {
        val keyResp = execute(base("$api/repos/$owner/$repo/actions/secrets/public-key").get())
        val keyJson = JSONObject(keyResp.second)
        val keyId = keyJson.getString("key_id")
        // SodiumSeal returns base64(sealed_bytes) directly, ready for the PUT body.
        val sealedB64 = SodiumSeal.sealToBase64(plaintext, keyJson.getString("key"))
        val payload = JSONObject()
            .put("encrypted_value", sealedB64)
            .put("key_id", keyId)
        execute(
            base("$api/repos/$owner/$repo/actions/secrets/ZERNIO_API_KEY")
                .put(payload.toString().toRequestBody("application/json".toMediaType()))
        )
    }

    suspend fun runInfo(runId: Long): JSONObject =
        JSONObject(execute(base("$api/repos/$owner/$repo/actions/runs/$runId").get()).second)

    /** GET /actions/runs?per_page=N — repo-wide run list (clone-copy run discovery). */
    suspend fun actionsRuns(perPage: Int = 10): JSONObject = JSONObject(
        execute(base("$api/repos/$owner/$repo/actions/runs?per_page=$perPage").get()).second
    )

    /** GET /actions/workflows/<file>/runs?per_page=N — per-workflow run list. */
    suspend fun workflowRuns(workflowFile: String, perPage: Int = 10): JSONObject = JSONObject(
        execute(base("$api/repos/$owner/$repo/actions/workflows/$workflowFile/runs?per_page=$perPage").get()).second
    )

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

    // ---- network-resilience helpers (operator fix #6) -----------------------------

    /** True for the transient network faults worth an automatic retry. */
    fun isTransientNetworkError(t: Throwable): Boolean = when (t) {
        is SocketTimeoutException -> true
        is UnknownHostException -> true
        else -> {
            val m = (t.message ?: "").lowercase()
            m.contains("etimedout") || m.contains("connection reset") ||
                m.contains("failed to connect") || m.contains("timeout") ||
                m.contains("network is unreachable") || m.contains("econnreset")
        }
    }

    /**
     * Retry [block] up to [attempts] times with exponential backoff (2s,4s,8s,16s)
     * on transient network faults only. [onRetry] gets (nextAttempt, waitSeconds, cause).
     */
    suspend fun <T> withNetworkRetry(
        attempts: Int = 4,
        onRetry: (Int, Int, Throwable) -> Unit = { _, _, _ -> },
        block: suspend () -> T
    ): T {
        var last: Throwable? = null
        var wait = 2
        for (attempt in 1..attempts) {
            try {
                return block()
            } catch (t: Throwable) {
                last = t
                if (attempt >= attempts || !isTransientNetworkError(t)) throw t
                onRetry(attempt + 1, wait, t)
                kotlinx.coroutines.delay(wait * 1000L)
                wait = (wait * 2).coerceAtMost(16)
            }
        }
        throw last ?: IOException("network retry exhausted")
    }

    /** Lightweight connectivity pre-check: HEAD api.github.com, true when reachable. */
    suspend fun connectivityOk(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(api).head().build()).execute().use { it.code in 200..599 }
        }.getOrDefault(false)
    }

    // ---- Actions: paginated jobs + per-job logs (logger rebuild, instruction #3) ----

    /**
     * Fetch EVERY job of a run across pages (per_page=100). Returns (jobArray,
     * jobIdByIndex) — the caller needs each job's numeric id to fetch its log.
     */
    suspend fun runJobsAll(runId: Long): Pair<JSONArray, List<Long>> = withContext(Dispatchers.IO) {
        val merged = JSONArray()
        val ids = mutableListOf<Long>()
        var page = 1
        while (true) {
            val body = execute(
                base("$api/repos/$owner/$repo/actions/runs/$runId/jobs?per_page=100&page=$page").get()
            ).second
            val jobs = JSONObject(body).optJSONArray("jobs") ?: break
            if (jobs.length() == 0) break
            for (i in 0 until jobs.length()) {
                val j = jobs.getJSONObject(i)
                merged.put(j)
                ids.add(j.optLong("id", -1L))
            }
            if (jobs.length() < 100) break
            page++
        }
        merged to ids
    }

    /**
     * Raw text of a single job's log (GET /actions/jobs/{job_id}/logs).
     * GitHub returns an HTTP 302 redirect to Azure Blob Storage
     * (pipelines.actions.githubusercontent.com). If OkHttp follows this redirect
     * automatically with the Authorization header attached, Azure rejects the
     * request with HTTP 401 AuthenticationFailed.
     *
     * Fix: use an OkHttpClient that does NOT follow redirects automatically, capture
     * the Location header, and make a clean GET request to Azure with NO Authorization
     * and NO custom GitHub headers.
     */
    suspend fun jobLog(jobId: Long): String = withContext(Dispatchers.IO) {
        val noRedirectClient = client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val initialReq = base("$api/repos/$owner/$repo/actions/jobs/$jobId/logs").get().build()
        val location = noRedirectClient.newCall(initialReq).execute().use { resp ->
            when (resp.code) {
                in 200..299 -> return@withContext resp.body?.string().orEmpty()
                301, 302, 303, 307, 308 -> resp.header("Location")
                else -> {
                    val err = resp.body?.string().orEmpty()
                    requestLog("GET", "/actions/jobs/$jobId/logs", resp.code, err)
                    throw GhException(resp.code, "Failed to get job $jobId log redirect: $err".take(300))
                }
            }
        } ?: throw GhException(404, "No redirect location for job $jobId log")

        // Clean GET without Authorization or GitHub headers
        val cleanReq = Request.Builder()
            .url(location)
            .header("User-Agent", "ClipForge-Android")
            .get()
            .build()
        client.newCall(cleanReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code in 200..299) {
                body
            } else {
                requestLog("GET", location.take(60), resp.code, body)
                throw GhException(resp.code, "Blob download failed (${resp.code}): ${body.take(200)}")
            }
        }
    }

    /**
     * Parsed Actions job log structure providing both sequential step blocks
     * and name/command lookup, preserving all step lines.
     */
    data class ParsedJobLogs(
        val rawLog: String,
        val totalLines: Int,
        val stepBlocks: List<StepBlock>,
        val stepsByName: Map<String, List<String>>,
    ) {
        data class StepBlock(val name: String, val lines: List<String>)

        fun linesForStep(index: Int, name: String): List<String> {
            val byName = stepsByName.entries.firstOrNull { (k, _) ->
                k.equals(name, ignoreCase = true) ||
                name.contains(k, ignoreCase = true) ||
                k.contains(name, ignoreCase = true)
            }?.value
            if (!byName.isNullOrEmpty()) return byName

            if (index in stepBlocks.indices) {
                val block = stepBlocks[index]
                if (block.lines.isNotEmpty()) return block.lines
            }
            return emptyList()
        }
    }

    /**
     * Parse raw GitHub Actions job log into structured per-step blocks.
     * Handles ##[group] delimiters, Run command prefixes, ANSI escapes,
     * and ISO timestamps.
     */
    fun parseJobLogs(raw: String): ParsedJobLogs {
        val ansi = Regex("\\u001B\\[[;\\d]*m")
        val ts = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z\s*""")
        fun clean(line: String): String = ansi.replace(ts.replace(line, ""), "").trimEnd()

        val stepBlocks = mutableListOf<ParsedJobLogs.StepBlock>()
        val byName = linkedMapOf<String, MutableList<String>>()

        var currentBlockName = "Set up job"
        val currentBlockLines = mutableListOf<String>()

        for (line in raw.lines()) {
            val c = clean(line)
            val isStepBoundary = c.startsWith("##[group]Run ") ||
                (c.startsWith("##[group]") && (
                    c.contains("Post ") ||
                    c.contains("Stopping Gradle") ||
                    c.contains("Complete job") ||
                    c.contains("Cleaning up orphan")
                ))

            if (isStepBoundary) {
                if (currentBlockLines.isNotEmpty()) {
                    stepBlocks.add(ParsedJobLogs.StepBlock(currentBlockName, currentBlockLines.toList()))
                    currentBlockLines.clear()
                }
                currentBlockName = c.removePrefix("##[group]")
                    .removePrefix("Run ")
                    .trim()
                    .ifBlank { "Step" }
                byName.getOrPut(currentBlockName) { mutableListOf() }
            } else {
                if (c.isNotBlank() && !c.startsWith("##[endgroup]")) {
                    currentBlockLines.add(c)
                    byName.getOrPut(currentBlockName) { mutableListOf() }.add(c)
                }
            }
        }
        if (currentBlockLines.isNotEmpty()) {
            stepBlocks.add(ParsedJobLogs.StepBlock(currentBlockName, currentBlockLines.toList()))
        }

        return ParsedJobLogs(
            rawLog = raw,
            totalLines = raw.lines().size,
            stepBlocks = stepBlocks,
            stepsByName = byName
        )
    }

    /**
     * Split a raw Actions job log into per-step content. Kept for backward compatibility.
     */
    fun splitLogByStep(raw: String): Map<String, List<String>> = parseJobLogs(raw).stepsByName
}
