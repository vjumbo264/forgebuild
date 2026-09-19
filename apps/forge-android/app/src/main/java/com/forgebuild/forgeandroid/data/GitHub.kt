package com.forgebuild.forgeandroid.data

import android.content.Context
import com.forgebuild.engine.data.CacheFirstStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Device-local storage for the optional operator PAT (mirrors the dashboard's one-time-PAT model). */
object PatStore {
    private const val PREFS = "forgebuild_prefs"
    private const val KEY = "github_pat"
    fun get(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)?.takeIf { it.isNotBlank() }
    fun set(context: Context, pat: String?) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().apply { if (pat.isNullOrBlank()) remove(KEY) else putString(KEY, pat.trim()) }.apply()
    }
}

data class ReleaseAsset(val name: String, val downloadUrl: String, val sizeBytes: Long)
data class Release(val tag: String, val name: String, val notes: String, val publishedAt: String, val assets: List<ReleaseAsset>)
data class AppDetail(val slug: String, val buildStateJson: String?, val promptHistoryMd: String?, val releases: List<Release>)

/** Minimal GitHub REST client for the single ForgeBuild repo (platform HttpURLConnection + org.json, no new deps). */
class GitHubApi(private val patProvider: () -> String?) {
    companion object {
        const val OWNER = "vjumbo264"
        const val REPO = "forgebuild"
        const val API = "https://api.github.com/repos/$OWNER/$REPO"
        const val WEB = "https://github.com/$OWNER/$REPO"
    }

    private suspend fun get(url: String, raw: Boolean = false): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", if (raw) "application/vnd.github.raw+json" else "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "forgebuild-android")
            patProvider()?.let { setRequestProperty("Authorization", "Bearer $it") }
            connectTimeout = 15000
            readTimeout = 15000
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.readText() ?: ""
        conn.disconnect()
        if (code !in 200..299) throw Exception("GitHub API HTTP $code: ${body.take(200)}")
        body
    }

    /** App slugs = directory names under apps/ in the repo. */
    suspend fun listApps(): List<String> {
        val arr = JSONArray(get("$API/contents/apps"))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("type") == "dir" }
            .map { it.getString("name") }
            .sorted()
    }

    private suspend fun rawOrNull(path: String): String? =
        runCatching { get("$API/contents/$path", raw = true) }.getOrNull()

    suspend fun fetchDetail(slug: String): AppDetail {
        val buildState = rawOrNull("apps/$slug/BUILD_STATE.json")
        val history = rawOrNull("PROMPT_HISTORY/$slug.md")
        val arr = JSONArray(get("$API/releases?per_page=100"))
        val releases = mutableListOf<Release>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val tag = o.getString("tag_name")
            if (!tag.startsWith("$slug-v")) continue
            if (o.optBoolean("draft") || o.optBoolean("prerelease")) continue
            val assetsArr = o.optJSONArray("assets") ?: JSONArray()
            val assets = (0 until assetsArr.length()).map { j ->
                val a = assetsArr.getJSONObject(j)
                ReleaseAsset(a.getString("name"), a.getString("browser_download_url"), a.optLong("size"))
            }
            releases.add(
                Release(tag, o.optString("name"), o.optString("body"), o.optString("published_at"), assets)
            )
        }
        return AppDetail(slug, buildState, history, releases)
    }
}

/** Apps list — Engine cache-first store: instant cached render, background reconcile. */
class AppsRepository(context: Context, api: GitHubApi) {
    val store = CacheFirstStore(
        context = context.applicationContext,
        cacheFileName = "apps.json",
        toJson = { JSONObject().put("slug", it) },
        fromJson = { it.getString("slug") },
        fetchRemote = { api.listApps() },
        keyOf = { it }
    )
}

/** Per-app detail — same Engine cache-first contract; the list holds zero/one AppDetail. */
class DetailRepository(context: Context, slug: String, api: GitHubApi) {
    val store = CacheFirstStore(
        context = context.applicationContext,
        cacheFileName = "detail_$slug.json",
        toJson = { d ->
            JSONObject().apply {
                put("slug", d.slug)
                put("buildState", d.buildStateJson ?: "")
                put("history", d.promptHistoryMd ?: "")
                val arr = JSONArray()
                d.releases.forEach { r ->
                    arr.put(JSONObject().apply {
                        put("tag", r.tag); put("name", r.name); put("notes", r.notes); put("publishedAt", r.publishedAt)
                        val aa = JSONArray()
                        r.assets.forEach { a -> aa.put(JSONObject().apply { put("name", a.name); put("url", a.downloadUrl); put("size", a.sizeBytes) }) }
                        put("assets", aa)
                    })
                }
                put("releases", arr)
            }
        },
        fromJson = { o ->
            val ra = o.optJSONArray("releases") ?: JSONArray()
            val rels = (0 until ra.length()).map { i ->
                val r = ra.getJSONObject(i)
                val aa = r.optJSONArray("assets") ?: JSONArray()
                val assets = (0 until aa.length()).map { j ->
                    val a = aa.getJSONObject(j)
                    ReleaseAsset(a.getString("name"), a.getString("url"), a.optLong("size"))
                }
                Release(r.getString("tag"), r.optString("name"), r.optString("notes"), r.optString("publishedAt"), assets)
            }
            AppDetail(
                o.getString("slug"),
                o.optString("buildState").ifBlank { null },
                o.optString("history").ifBlank { null },
                rels
            )
        },
        fetchRemote = { listOf(api.fetchDetail(slug)) },
        keyOf = { it.slug }
    )
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576f)
    bytes >= 1_024 -> "%.0f KB".format(bytes / 1_024f)
    else -> "$bytes B"
}
