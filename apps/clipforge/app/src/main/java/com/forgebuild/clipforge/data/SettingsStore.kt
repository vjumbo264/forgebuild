package com.forgebuild.clipforge.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local persistence (SharedPreferences) for connection, settings and job history. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("clipforge", Context.MODE_PRIVATE)

    fun saveConnection(c: CloneConnection) {
        prefs.edit()
            .putString("owner", c.owner).putString("repo", c.repo).putString("pat", c.pat)
            .apply()
    }

    fun loadConnection(): CloneConnection? {
        val o = prefs.getString("owner", null) ?: return null
        val r = prefs.getString("repo", null) ?: return null
        val p = prefs.getString("pat", null) ?: return null
        return CloneConnection(o, r, p)
    }

    fun clearConnection() = prefs.edit().remove("owner").remove("repo").remove("pat").apply()

    fun saveSettings(s: ForgeSettings) {
        prefs.edit()
            .putString("voice", s.narratorVoice)
            .putString("watermark", s.watermarkText)
            .putBoolean("captions", s.captionsEnabled)
            .putBoolean("vertical", s.verticalReframe)
            .putBoolean("music", s.musicEnabled)
            .apply()
    }

    fun loadSettings(): ForgeSettings = ForgeSettings(
        narratorVoice = prefs.getString("voice", "en-US-AriaNeural") ?: "en-US-AriaNeural",
        watermarkText = prefs.getString("watermark", "") ?: "",
        captionsEnabled = prefs.getBoolean("captions", true),
        verticalReframe = prefs.getBoolean("vertical", true),
        musicEnabled = prefs.getBoolean("music", false)
    )

    fun saveJobs(jobs: List<Job>) {
        val arr = JSONArray()
        jobs.forEach { j ->
            arr.put(JSONObject()
                .put("id", j.id).put("title", j.title).put("source", j.source)
                .put("state", j.state).put("created_at", j.createdAt)
                .put("updated_at", j.updatedAt).put("error", j.error ?: JSONObject.NULL))
        }
        prefs.edit().putString("jobs", arr.toString()).apply()
    }

    fun loadJobs(): List<Job> {
        val raw = prefs.getString("jobs", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { jobFromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }
}
