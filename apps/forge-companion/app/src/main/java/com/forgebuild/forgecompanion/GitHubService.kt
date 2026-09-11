package com.forgebuild.forgecompanion

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GitHubService(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("forgebuild_prefs", Context.MODE_PRIVATE)

    companion object {
        const val OWNER = "vjumbo264"
        const val REPO = "forgebuild"
        private const val API_BASE = "https://api.github.com"
        private const val RAW_BASE = "https://raw.githubusercontent.com"
        private const val PREF_PAT = "github_pat"
    }

    fun getPat(): String? {
        return prefs.getString(PREF_PAT, null)?.takeIf { it.isNotBlank() }
    }

    fun setPat(pat: String) {
        prefs.edit().putString(PREF_PAT, pat.trim()).apply()
    }

    fun clearPat() {
        prefs.edit().remove(PREF_PAT).apply()
    }

    fun isConnected(): Boolean = !getPat().isNullOrBlank()

    private fun openConnection(urlStr: String, method: String = "GET"): HttpURLConnection {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        conn.setRequestProperty("User-Agent", "ForgeBuildCompanion-Android")
        val pat = getPat()
        if (!pat.isNullOrBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $pat")
        }
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        return conn
    }

    private suspend fun <T> executeRequest(
        path: String,
        method: String = "GET",
        bodyJson: String? = null,
        parser: (String) -> T
    ): T = withContext(Dispatchers.IO) {
        val fullUrl = if (path.startsWith("http")) path else "$API_BASE$path"
        val conn = openConnection(fullUrl, method)
        if (bodyJson != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { os ->
                os.write(bodyJson.toByteArray(Charsets.UTF_8))
                os.flush()
            }
        }
        val code = conn.responseCode
        if (code in 200..299) {
            if (code == 204) {
                return@withContext parser("")
            }
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parser(text)
        } else {
            val errorText = runCatching {
                conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }.getOrNull() ?: "HTTP $code"
            throw Exception("GitHub API $code: $errorText")
        }
    }

    suspend fun fetchAllReleases(): List<ReleaseInfo> = withContext(Dispatchers.IO) {
        executeRequest("/repos/$OWNER/$REPO/releases?per_page=100") { raw ->
            val arr = JSONArray(raw)
            val list = mutableListOf<ReleaseInfo>()
            for (i in 0 until arr.length()) {
                val rel = arr.getJSONObject(i)
                val tag = rel.optString("tag_name", "")
                val assetsArr = rel.optJSONArray("assets")
                val assetsList = mutableListOf<ReleaseAsset>()
                if (assetsArr != null) {
                    for (j in 0 until assetsArr.length()) {
                        val a = assetsArr.getJSONObject(j)
                        assetsList.add(
                            ReleaseAsset(
                                id = a.optLong("id", 0L),
                                name = a.optString("name", ""),
                                size = a.optLong("size", 0L),
                                downloadUrl = a.optString("browser_download_url", "")
                            )
                        )
                    }
                }
                list.add(
                    ReleaseInfo(
                        id = rel.optLong("id", 0L),
                        tagName = tag,
                        name = rel.optString("name", tag),
                        publishedAt = rel.optString("published_at", ""),
                        body = rel.optString("body", ""),
                        zipballUrl = rel.optString("zipball_url", ""),
                        assets = assetsList
                    )
                )
            }
            list
        }
    }

    suspend fun fetchApps(): List<AppSummary> = withContext(Dispatchers.IO) {
        val slugs = executeRequest("/repos/$OWNER/$REPO/contents/apps") { raw ->
            val arr = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                if (item.optString("type") == "dir") {
                    list.add(item.getString("name"))
                }
            }
            list
        }

        val allReleases = runCatching { fetchAllReleases() }.getOrDefault(emptyList())

        slugs.map { slug ->
            val prefix = "$slug-"
            val latest = allReleases.firstOrNull { it.tagName.startsWith(prefix) }
            AppSummary(
                slug = slug,
                latestReleaseTag = latest?.tagName,
                latestReleaseDate = latest?.publishedAt,
                inProgress = false
            )
        }
    }

    suspend fun fetchReleases(slug: String): List<ReleaseInfo> = withContext(Dispatchers.IO) {
        val allReleases = fetchAllReleases()
        val prefix = "$slug-"
        allReleases.filter { it.tagName.startsWith(prefix) }
    }

    suspend fun fetchBuildState(slug: String): BuildStateData? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$RAW_BASE/$OWNER/$REPO/main/apps/$slug/BUILD_STATE.json"
            val conn = openConnection(url)
            if (conn.responseCode in 200..299) {
                val raw = conn.inputStream.bufferedReader().use { it.readText() }
                BuildStateData.parse(raw)
            } else null
        }.getOrNull()
    }

    suspend fun fetchPromptHistory(slug: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$RAW_BASE/$OWNER/$REPO/main/PROMPT_HISTORY/$slug.md"
            val conn = openConnection(url)
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        }.getOrNull()
    }

    suspend fun deleteRelease(releaseId: Long): Boolean = withContext(Dispatchers.IO) {
        executeRequest("/repos/$OWNER/$REPO/releases/$releaseId", method = "DELETE") {
            true
        }
    }

    suspend fun deleteGitTag(tagName: String): Boolean = withContext(Dispatchers.IO) {
        val encodedTag = URLEncoder.encode(tagName, "UTF-8")
        runCatching {
            executeRequest("/repos/$OWNER/$REPO/git/refs/tags/$encodedTag", method = "DELETE") {
                true
            }
        }.getOrDefault(false)
    }

    suspend fun deleteAppTree(slug: String): Unit = withContext(Dispatchers.IO) {
        // 1. Delete all releases matching <slug>-*
        val releases = fetchReleases(slug)
        for (r in releases) {
            runCatching { deleteRelease(r.id) }
            runCatching { deleteGitTag(r.tagName) }
        }

        // 2. Delete PROMPT_HISTORY/<slug>.md
        val phPath = "PROMPT_HISTORY/$slug.md"
        val ph = runCatching {
            executeRequest("/repos/$OWNER/$REPO/contents/$phPath") { raw ->
                JSONObject(raw).optString("sha")
            }
        }.getOrNull()
        if (!ph.isNullOrBlank()) {
            val delBody = JSONObject().apply {
                put("message", "Delete app $slug: remove prompt history")
                put("sha", ph)
                put("branch", "main")
            }.toString()
            runCatching {
                executeRequest("/repos/$OWNER/$REPO/contents/$phPath", method = "DELETE", bodyJson = delBody) {}
            }
        }

        // 3. Remove the entire apps/<slug>/ directory via Git Trees API
        val ref = executeRequest("/repos/$OWNER/$REPO/git/ref/heads/main") { raw ->
            JSONObject(raw).getJSONObject("object").getString("sha")
        }
        val commit = executeRequest("/repos/$OWNER/$REPO/git/commits/$ref") { raw ->
            JSONObject(raw).getJSONObject("tree").getString("sha")
        }
        val tree = executeRequest("/repos/$OWNER/$REPO/git/trees/$commit?recursive=1") { raw ->
            JSONObject(raw).optJSONArray("tree") ?: JSONArray()
        }

        val prefix = "apps/$slug/"
        val deletions = JSONArray()
        for (i in 0 until tree.length()) {
            val item = tree.getJSONObject(i)
            val path = item.optString("path")
            if (path.startsWith(prefix)) {
                deletions.put(JSONObject().apply {
                    put("path", path)
                    put("mode", item.optString("mode"))
                    put("type", item.optString("type"))
                    put("sha", JSONObject.NULL)
                })
            }
        }

        if (deletions.length() > 0) {
            val newTreeBody = JSONObject().apply {
                put("base_tree", commit)
                put("tree", deletions)
            }.toString()
            val newTreeSha = executeRequest("/repos/$OWNER/$REPO/git/trees", method = "POST", bodyJson = newTreeBody) { raw ->
                JSONObject(raw).getString("sha")
            }

            val newCommitBody = JSONObject().apply {
                put("message", "Delete app $slug (apps/$slug/ removed from ForgeBuild Companion)")
                put("tree", newTreeSha)
                put("parents", JSONArray().put(ref))
            }.toString()
            val newCommitSha = executeRequest("/repos/$OWNER/$REPO/git/commits", method = "POST", bodyJson = newCommitBody) { raw ->
                JSONObject(raw).getString("sha")
            }

            var success = false
            for (attempt in 0..3) {
                try {
                    val patchBody = JSONObject().apply { put("sha", newCommitSha) }.toString()
                    executeRequest("/repos/$OWNER/$REPO/git/refs/heads/main", method = "PATCH", bodyJson = patchBody) {}
                    success = true
                    break
                } catch (e: Exception) {
                    if (attempt == 3) throw e
                    kotlinx.coroutines.delay(1200L * (attempt + 1))
                }
            }
        }
    }

    suspend fun streamDownload(urlStr: String, outputStream: OutputStream): Unit = withContext(Dispatchers.IO) {
        val conn = openConnection(urlStr)
        conn.instanceFollowRedirects = true
        var targetConn = conn
        var redirectCount = 0
        while (targetConn.responseCode in listOf(301, 302, 303, 307, 308) && redirectCount < 5) {
            val newLocation = targetConn.getHeaderField("Location") ?: break
            targetConn.disconnect()
            targetConn = URL(newLocation).openConnection() as HttpURLConnection
            targetConn.setRequestProperty("User-Agent", "ForgeBuildCompanion-Android")
            redirectCount++
        }

        targetConn.inputStream.use { input ->
            input.copyTo(outputStream, bufferSize = 64 * 1024)
        }
        outputStream.flush()
    }
}
