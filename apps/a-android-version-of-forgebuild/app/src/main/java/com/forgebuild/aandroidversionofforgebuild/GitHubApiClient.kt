package com.forgebuild.aandroidversionofforgebuild

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat

object GitHubApiClient {
    const val OWNER = "vjumbo264"
    const val REPO = "forgebuild"
    private const val API_BASE = "https://api.github.com"
    private const val RAW_BASE = "https://raw.githubusercontent.com"

    private fun openConnection(urlString: String, pat: String? = null, method: String = "GET"): HttpURLConnection {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "ForgeBuild-Android-App")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        if (!pat.isNullOrBlank()) {
            conn.setRequestProperty("Authorization", "Bearer ${pat.trim()}")
        }
        return conn
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val isSuccess = code in 200..299
        val stream = if (isSuccess) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        val reader = BufferedReader(InputStreamReader(stream))
        val sb = StringBuilder()
        var line = reader.readLine()
        while (line != null) {
            sb.append(line).append("\n")
            line = reader.readLine()
        }
        reader.close()
        if (!isSuccess) {
            throw Exception("HTTP $code: ${sb.toString().take(200)}")
        }
        return sb.toString()
    }

    suspend fun verifyPat(pat: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("$API_BASE/user", pat)
            val resp = readResponse(conn)
            val json = JSONObject(resp)
            val login = json.optString("login", "authenticated-user")
            Result.success(login)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAppSlugs(pat: String?): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("$API_BASE/repos/$OWNER/$REPO/contents/apps", pat)
            val resp = readResponse(conn)
            val arr = JSONArray(resp)
            val slugs = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                if (item.optString("type") == "dir") {
                    val name = item.optString("name")
                    if (name.isNotEmpty()) slugs.add(name)
                }
            }
            slugs.sort()
            Result.success(slugs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchBuildState(slug: String, pat: String?): Result<BuildState?> = withContext(Dispatchers.IO) {
        try {
            val url = "$RAW_BASE/$OWNER/$REPO/main/apps/$slug/BUILD_STATE.json"
            val conn = openConnection(url, pat)
            val resp = readResponse(conn)
            val json = JSONObject(resp)

            val tasksArray = json.optJSONArray("tasks") ?: JSONArray()
            val tasks = mutableListOf<BuildTask>()
            for (i in 0 until tasksArray.length()) {
                val t = tasksArray.getJSONObject(i)
                tasks.add(
                    BuildTask(
                        id = t.optString("id"),
                        title = t.optString("title"),
                        status = t.optString("status", "pending"),
                        notes = t.optString("notes")
                    )
                )
            }

            val histArray = json.optJSONArray("history") ?: JSONArray()
            val history = mutableListOf<BuildHistoryEntry>()
            for (i in 0 until histArray.length()) {
                val h = histArray.getJSONObject(i)
                history.add(
                    BuildHistoryEntry(
                        session = h.optString("session"),
                        event = h.optString("event")
                    )
                )
            }

            val state = BuildState(
                project = json.optString("project"),
                buildComplete = json.optBoolean("build_complete", false),
                updatedBy = json.optString("updated_by"),
                currentTask = json.optString("current_task"),
                tasks = tasks,
                history = history
            )
            Result.success(state)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchPromptHistory(slug: String, pat: String?): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val url = "$RAW_BASE/$OWNER/$REPO/main/PROMPT_HISTORY/$slug.md"
            val conn = openConnection(url, pat)
            val resp = readResponse(conn)
            Result.success(resp.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchReleases(pat: String?): Result<List<AppRelease>> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/repos/$OWNER/$REPO/releases?per_page=100"
            val conn = openConnection(url, pat)
            val resp = readResponse(conn)
            val arr = JSONArray(resp)
            val releases = mutableListOf<AppRelease>()

            val df = DecimalFormat("0.0")
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                val tagName = r.optString("tag_name")
                val name = r.optString("name", tagName)
                val body = r.optString("body")
                val publishedAt = r.optString("published_at")

                var apkDownloadUrl: String? = null
                var apkFileName: String? = null
                var apkSizeFormatted: String? = null
                var manifestUrl: String? = null

                val assets = r.optJSONArray("assets") ?: JSONArray()
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    val aName = a.optString("name")
                    val dlUrl = a.optString("browser_download_url")
                    if (aName.endsWith(".apk", ignoreCase = true)) {
                        apkDownloadUrl = dlUrl
                        apkFileName = aName
                        val sizeBytes = a.optLong("size", 0)
                        if (sizeBytes > 0) {
                            val mb = sizeBytes / (1024.0 * 1024.0)
                            apkSizeFormatted = "${df.format(mb)} MB"
                        }
                    } else if (aName == "forgebuild-manifest.json") {
                        manifestUrl = dlUrl
                    }
                }

                val version = tagName.substringAfterLast("-v", "v1")

                releases.add(
                    AppRelease(
                        tagName = tagName,
                        version = if (version.startsWith("v")) version else "v$version",
                        name = name,
                        body = body,
                        publishedAt = publishedAt,
                        apkDownloadUrl = apkDownloadUrl,
                        apkSizeFormatted = apkSizeFormatted,
                        apkFileName = apkFileName,
                        manifestUrl = manifestUrl
                    )
                )
            }
            Result.success(releases)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchWorkflowRuns(pat: String?): Result<List<WorkflowRun>> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/repos/$OWNER/$REPO/actions/runs?per_page=30"
            val conn = openConnection(url, pat)
            val resp = readResponse(conn)
            val root = JSONObject(resp)
            val arr = root.optJSONArray("workflow_runs") ?: JSONArray()
            val runs = mutableListOf<WorkflowRun>()

            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                val headCommit = r.optJSONObject("head_commit")
                val msg = headCommit?.optString("message") ?: ""

                runs.add(
                    WorkflowRun(
                        id = r.optLong("id"),
                        name = r.optString("name"),
                        status = r.optString("status"),
                        conclusion = if (r.isNull("conclusion")) null else r.optString("conclusion"),
                        htmlUrl = r.optString("html_url"),
                        createdAt = r.optString("created_at"),
                        updatedAt = r.optString("updated_at"),
                        runNumber = r.optInt("run_number"),
                        event = r.optString("event"),
                        headCommitMessage = msg
                    )
                )
            }
            Result.success(runs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dispatchReleaseWorkflow(slug: String, releaseNotes: String, pat: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/repos/$OWNER/$REPO/actions/workflows/release.yml/dispatches"
            val conn = openConnection(url, pat, "POST")
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject()
            body.put("ref", "main")
            val inputs = JSONObject()
            inputs.put("app_path", "apps/$slug")
            if (releaseNotes.isNotBlank()) {
                inputs.put("release_notes", releaseNotes)
            }
            body.put("inputs", inputs)

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(body.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            if (code == 204 || code == 200 || code == 201) {
                Result.success(true)
            } else {
                val err = readResponse(conn)
                Result.failure(Exception("Workflow dispatch failed ($code): $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
