package com.forgebuild.forgecompanion

import org.json.JSONArray
import org.json.JSONObject

data class AppSummary(
    val slug: String,
    val latestReleaseTag: String? = null,
    val latestReleaseDate: String? = null,
    val inProgress: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("slug", slug)
        put("latestReleaseTag", latestReleaseTag ?: "")
        put("latestReleaseDate", latestReleaseDate ?: "")
        put("inProgress", inProgress)
    }

    companion object {
        fun fromJson(json: JSONObject): AppSummary = AppSummary(
            slug = json.optString("slug", ""),
            latestReleaseTag = json.optString("latestReleaseTag").takeIf { it.isNotEmpty() },
            latestReleaseDate = json.optString("latestReleaseDate").takeIf { it.isNotEmpty() },
            inProgress = json.optBoolean("inProgress", false)
        )
    }
}

data class ReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long,
    val downloadUrl: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("size", size)
        put("downloadUrl", downloadUrl)
    }

    companion object {
        fun fromJson(json: JSONObject): ReleaseAsset = ReleaseAsset(
            id = json.optLong("id", 0L),
            name = json.optString("name", ""),
            size = json.optLong("size", 0L),
            downloadUrl = json.optString("downloadUrl", "")
        )
    }
}

data class ReleaseInfo(
    val id: Long,
    val tagName: String,
    val name: String,
    val publishedAt: String,
    val body: String,
    val zipballUrl: String,
    val assets: List<ReleaseAsset>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("id_long", id)
        put("tagName", tagName)
        put("name", name)
        put("publishedAt", publishedAt)
        put("body", body)
        put("zipballUrl", zipballUrl)
        val arr = JSONArray()
        assets.forEach { arr.put(it.toJson()) }
        put("assets", arr)
    }

    companion object {
        fun fromJson(json: JSONObject): ReleaseInfo {
            val assetsList = mutableListOf<ReleaseAsset>()
            val arr = json.optJSONArray("assets")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    assetsList.add(ReleaseAsset.fromJson(arr.getJSONObject(i)))
                }
            }
            return ReleaseInfo(
                id = json.optLong("id", 0L),
                tagName = json.optString("tagName", ""),
                name = json.optString("name", ""),
                publishedAt = json.optString("publishedAt", ""),
                body = json.optString("body", ""),
                zipballUrl = json.optString("zipballUrl", ""),
                assets = assetsList
            )
        }
    }
}

data class BuildTask(
    val id: String,
    val title: String,
    val status: String,
    val notes: String
)

data class BuildStateData(
    val slug: String,
    val project: String,
    val buildComplete: Boolean,
    val currentTask: String?,
    val tasks: List<BuildTask>,
    val rawJson: String
) {
    companion object {
        fun parse(raw: String): BuildStateData {
            val root = JSONObject(raw)
            val slug = root.optString("slug", "")
            val project = root.optString("project", "")
            val buildComplete = root.optBoolean("build_complete", false)
            val currentTask = root.optString("current_task").takeIf { it.isNotEmpty() }
            val tasks = mutableListOf<BuildTask>()
            val arr = root.optJSONArray("tasks")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val t = arr.getJSONObject(i)
                    tasks.add(
                        BuildTask(
                            id = t.optString("id", ""),
                            title = t.optString("title", ""),
                            status = t.optString("status", "pending"),
                            notes = t.optString("notes", "")
                        )
                    )
                }
            }
            return BuildStateData(slug, project, buildComplete, currentTask, tasks, raw)
        }
    }
}
