package com.forgebuild.clipforge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forgebuild.clipforge.data.*
import com.forgebuild.engine.data.CacheFirstStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ClipForgeViewModel(app: Application) : AndroidViewModel(app) {

    private val creds = CredentialStore(app)
    var api: GitHubClient? = null; private set

    private val _login = MutableStateFlow<CredentialStore.CloneCredentials?>(null)
    val login: StateFlow<CredentialStore.CloneCredentials?> = _login
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    private val _snack = MutableStateFlow<String?>(null)
    val snack: StateFlow<String?> = _snack
    fun toast(msg: String) { _snack.value = msg }
    fun clearSnack() { _snack.value = null }

    init {
        creds.load()?.let { _login.value = it; api = GitHubClient(it.pat, it.owner, it.repo) }
        _ready.value = true
    }

    // ---------------- auth / clones ----------------
    fun connectExisting(pat: String, repoSlug: String) = viewModelScope.launch {
        val parts = repoSlug.trim().trim('/').split("/")
        if (parts.size != 2) { toast("Repo must be owner/repository"); return@launch }
        val c = GitHubClient(pat.trim(), parts[0], parts[1])
        try {
            if (!c.repoExists()) { toast("Repo not found — check PAT scopes"); return@launch }
            val me = c.whoami().optString("login")
            creds.save(CredentialStore.CloneCredentials(pat.trim(), parts[0], parts[1], me))
            api = c; _login.value = creds.load()
            toast("Connected to $repoSlug")
        } catch (e: Exception) { toast("Login failed: ${e.message}") }
    }

    private val _cloneProgress = MutableStateFlow<String?>(null)
    val cloneProgress: StateFlow<String?> = _cloneProgress

    fun createClone(pat: String, repoName: String) = viewModelScope.launch {
        val name = repoName.trim().ifBlank { "clipforge-clone" }
        _cloneProgress.value = "Creating private repo…"
        try {
            val temp = GitHubClient(pat.trim(), "", "")
            val me = temp.whoami().getString("login")
            temp.createRepo(name)
            val c = GitHubClient(pat.trim(), me, name)
            creds.save(CredentialStore.CloneCredentials(pat.trim(), me, name, me))
            api = c; _login.value = creds.load()
            val sha = c.defaultBranchSha()
            _cloneProgress.value = "Bootstrapping pipeline (clone-copy)…"
            c.dispatchWorkflow("clone-copy.yml", mapOf("code_ref" to sha))
            // poll .clipforge-clone-status.json until success/fail (mirrors bot clone-start UX)
            var tries = 0
            while (isActive && tries < 60) {
                delay(3000); tries++
                val st = c.readFile(".clipforge-clone-status.json") ?: continue
                val j = JSONObject(st.first)
                val state = j.optString("state")
                _cloneProgress.value = "Bootstrap: ${j.optString("message", state)}"
                if (state == "success") break
                if (state == "error" || state == "failed") { toast("Clone bootstrap failed"); break }
            }
            _cloneProgress.value = null
            toast("Clone ready")
        } catch (e: Exception) {
            _cloneProgress.value = null
            toast("Clone creation failed: ${e.message}")
        }
    }

    fun signOut() { creds.clear(); api = null; _login.value = null }

    // ---------------- task list (cache-first) ----------------
    private val taskStore = CacheFirstStore<TaskStatus>(
        app, "tasks.json",
        toJson = { it.toJson() },
        fromJson = { TaskStatus.fromJson(it) },
        fetchRemote = { fetchTasksRemote() },
        keyOf = { it.jobId },
    )
    val tasks: StateFlow<List<TaskStatus>> = taskStore.state
    val tasksRefreshing: StateFlow<Boolean> = taskStore.refreshing

    private suspend fun fetchTasksRemote(): List<TaskStatus> {
        val c = api ?: return emptyList()
        val dirs = c.listDir("jobs")
        val out = mutableListOf<TaskStatus>()
        for (i in 0 until dirs.length()) {
            val d = dirs.getJSONObject(i)
            if (d.optString("type") != "dir") continue
            val id = d.getString("name")
            val st = c.readFile("jobs/$id/status.json") ?: continue
            runCatching { out.add(TaskStatus.fromJson(JSONObject(st.first))) }
        }
        return out.sortedByDescending { it.updatedAt }
    }

    fun onTasksOpen() { taskStore.loadFromCache(); viewModelScope.launch { taskStore.refresh() } }
    fun refreshTasks() = viewModelScope.launch { taskStore.refresh() }

    val plainTasks get() = tasks.value.filter { !it.seriesEnabled }
    val seriesMap get() = tasks.value.filter { it.seriesEnabled }
        .groupBy { it.seriesId }.toSortedMap(compareByDescending { it })

    /** Delete tasks: remove every jobs/id blob + releases (mirrors deleteClipforgeJob). */
    fun deleteTasks(ids: Set<String>) = viewModelScope.launch {
        val c = api ?: return@launch
        for (id in ids) {
            try {
                val files = c.listDir("jobs/$id")
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    c.deleteFile(f.getString("path"), f.getString("sha"), "delete task $id")
                }
                c.deleteRelease("clipforge-$id")
                c.deleteRelease("clipforge-relay-input-$id")
            } catch (e: Exception) { toast("Delete $id failed: ${e.message}") }
        }
        toast("Deleted ${ids.size} task(s)")
        taskStore.refresh()
    }

    // ---------------- music library (cache-first) ----------------
    private val musicStore = CacheFirstStore<MusicTrack>(
        app, "music.json",
        toJson = { it.toJson() },
        fromJson = { MusicTrack.fromJson(it) },
        fetchRemote = {
            val arr = api?.listDir("audio-library") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val f = arr.getJSONObject(i)
                MusicTrack(f.getString("name"), f.getString("path"), f.optLong("size"), f.getString("sha"))
            }.filter { it.name.isNotBlank() }
        },
        keyOf = { it.path },
    )
    val music: StateFlow<List<MusicTrack>> = musicStore.state
    val musicRefreshing: StateFlow<Boolean> = musicStore.refreshing
    fun onMusicOpen() { musicStore.loadFromCache(); viewModelScope.launch { musicStore.refresh() } }

    private val _defaultMusic = MutableStateFlow<String?>(null)
    val defaultMusic: StateFlow<String?> = _defaultMusic
    fun loadDefaultMusic() = viewModelScope.launch {
        _defaultMusic.value = api?.readFile("branding/music_default.json")?.let {
            JSONObject(it.first).optString("ref").ifBlank { null }
        }
    }

    fun deleteMusic(tracks: Set<MusicTrack>) = viewModelScope.launch {
        val c = api ?: return@launch
        for (t in tracks) {
            if (!t.path.matches(Regex("^audio-library/[^/\\\\]+$"))) continue
            runCatching { c.deleteFile(t.path, t.sha, "delete track ${t.name}") }
        }
        toast("Deleted ${tracks.size} track(s)"); musicStore.refresh()
    }

    fun setDefaultMusic(path: String?) = viewModelScope.launch {
        val c = api ?: return@launch
        if (path == null) {
            c.readFile("branding/music_default.json")?.let { (_, sha) ->
                c.deleteFile("branding/music_default.json", sha, "clear default music")
            }
            _defaultMusic.value = null
        } else {
            val existing = c.readFile("branding/music_default.json")
            c.putFile(
                "branding/music_default.json",
                JSONObject().put("ref", path).toString().toByteArray(),
                "set default music", existing?.second,
            )
            _defaultMusic.value = path
        }
    }

    // ---------------- uploads with real progress ----------------
    data class Upload(val label: String, val fraction: Float)
    private val _upload = MutableStateFlow<Upload?>(null)
    val upload: StateFlow<Upload?> = _upload

    /** Chunked base64 upload so a real progress bar can advance (contents API has no streaming PUT). */
    fun uploadBytes(path: String, bytes: ByteArray, commitMsg: String, onDone: () -> Unit = {}) =
        viewModelScope.launch {
            val c = api ?: return@launch
            try {
                if (bytes.size <= 900_000) {
                    _upload.value = Upload(path.substringAfterLast('/'), 0f)
                    val sha = c.readBytes(path)?.second
                    c.putFile(path, bytes, commitMsg, sha)
                    _upload.value = Upload(path.substringAfterLast('/'), 1f)
                } else {
                    toast("File too large for direct upload (${bytes.size / 1024} KB)")
                    _upload.value = null; return@launch
                }
                toast("Uploaded ${path.substringAfterLast('/')}")
                onDone()
            } catch (e: Exception) { toast("Upload failed: ${e.message}") }
            _upload.value = null
        }

    fun uploadMusic(name: String, bytes: ByteArray) {
        val safe = name.replace(Regex("[^A-Za-z0-9._ -]"), "_")
        uploadBytes("audio-library/$safe", bytes, "upload track $safe") {
            viewModelScope.launch { musicStore.refresh() }
        }
    }

    // ---------------- new-task wizard ----------------
    fun createTask(
        sourceKind: String, sourceValue: String, torrentBytes: ByteArray?,
        focus: String, durationSec: Int, musicRef: String?, musicSource: String,
        series: Boolean, ttsVoice: String,
    ) = viewModelScope.launch {
        val c = api ?: return@launch
        try {
            val jobId = if (series) "series-${System.currentTimeMillis()}-p1"
            else "manual-${System.currentTimeMillis()}"
            if (torrentBytes != null && sourceKind == "torrent_file") {
                _upload.value = Upload("source.torrent", 0f)
                c.putFile("jobs/$jobId/source.torrent", torrentBytes, "task $jobId torrent")
                _upload.value = Upload("source.torrent", 1f); _upload.value = null
            }
            val now = System.currentTimeMillis() / 1000
            val req = JSONObject()
                .put("version", 2).put("job_id", jobId)
                .put("source", JSONObject().put("kind", sourceKind).put("value", sourceValue)
                    .apply { if (torrentBytes != null) put("torrent_file_index", 0) })
                .put("options", JSONObject()
                    .put("whisper_model", "base").put("language", "auto")
                    .put("task", "translate_to_english")
                    .put("target_duration_seconds", durationSec)
                    .put("focus", if (series) "" else focus)
                    .put("enable_vision_assist", false))
                .put("mode", "manual")
                .put("series", JSONObject()
                    .put("enabled", series)
                    .put("series_id", if (series) jobId.removeSuffix("-p1") else "")
                    .put("source_job_id", "").put("part", if (series) 1 else 0)
                    .put("start_seconds", 0).put("context", ""))
                .put("music", if (musicSource == "none") JSONObject().put("ref", "").put("source", "none")
                else JSONObject().put("ref", musicRef ?: "").put("source", musicSource))
                .put("saved_at_epoch", now)
            c.putFile("jobs/$jobId/stage-a-request.json", req.toString().toByteArray(), "task $jobId request")
            // initial status.json (mirrors the bot's queued bootstrap)
            val status = JSONObject().put("version", 2).put("job_id", jobId).put("mode", "manual")
                .put("series", JSONObject().put("enabled", series)
                    .put("series_id", if (series) jobId.removeSuffix("-p1") else "")
                    .put("part", if (series) 1 else 0).put("start_seconds", 0)
                    .put("is_final", false))
                .put("state", "queued").put("message", "Queued for Stage A")
                .put("created_at_epoch", now).put("updated_at_epoch", now)
                .put("expires_at_epoch", now + Pipeline.JOB_TTL_SECONDS)
                .put("release_tag", "").put("release_url", "")
                .put("run", JSONObject().put("workflow_run_id", 0).put("workflow_run_url", ""))
            c.putFile("jobs/$jobId/status.json", status.toString().toByteArray(), "task $jobId queued")
            c.dispatchWorkflow("stage-a.yml", mapOf("job_id" to jobId, "code_ref" to c.defaultBranchSha()))
            toast("Task created")
            taskStore.refresh()
        } catch (e: Exception) { toast("Create failed: ${e.message}") }
    }

    /** Manual series continuation (series.js nextPartRequestBody). */
    fun dispatchNextPart(status: TaskStatus, startSeconds: Int) = viewModelScope.launch {
        val c = api ?: return@launch
        try {
            val jobId = SeriesLogic.nextPartJobId(status.seriesId, status.part)
            val now = System.currentTimeMillis() / 1000
            val prevReq = c.readFile("jobs/${status.jobId}/stage-a-request.json")
            val src = prevReq?.let { JSONObject(it.first).optJSONObject("source") }
                ?: JSONObject().put("kind", "url").put("value", "")
            val req = JSONObject()
                .put("version", 2).put("job_id", jobId)
                .put("source", src)
                .put("options", JSONObject().put("whisper_model", "base").put("language", "auto")
                    .put("task", "translate_to_english")
                    .put("target_duration_seconds", 60).put("focus", "").put("enable_vision_assist", false))
                .put("mode", "manual")
                .put("series", JSONObject().put("enabled", true).put("series_id", status.seriesId)
                    .put("source_job_id", status.jobId).put("part", status.part + 1)
                    .put("start_seconds", startSeconds).put("context", ""))
                .put("music", JSONObject().put("ref", "").put("source", "default"))
                .put("saved_at_epoch", now)
            c.putFile("jobs/$jobId/stage-a-request.json", req.toString().toByteArray(), "series part ${status.part + 1}")
            val st = JSONObject().put("version", 2).put("job_id", jobId).put("mode", "manual")
                .put("series", JSONObject().put("enabled", true).put("series_id", status.seriesId)
                    .put("part", status.part + 1).put("start_seconds", startSeconds).put("is_final", false))
                .put("state", "queued").put("message", "Queued for Stage A")
                .put("created_at_epoch", now).put("updated_at_epoch", now)
                .put("expires_at_epoch", now + Pipeline.JOB_TTL_SECONDS)
                .put("release_tag", "").put("release_url", "")
                .put("run", JSONObject().put("workflow_run_id", 0).put("workflow_run_url", ""))
            c.putFile("jobs/$jobId/status.json", st.toString().toByteArray(), "series part queued")
            c.dispatchWorkflow("stage-a.yml", mapOf("job_id" to jobId, "code_ref" to c.defaultBranchSha()))
            toast("Next part dispatched")
            taskStore.refresh()
        } catch (e: Exception) { toast("Dispatch failed: ${e.message}") }
    }

    // ---------------- task detail + live polling log ----------------
    private val _detail = MutableStateFlow<TaskStatus?>(null)
    val detail: StateFlow<TaskStatus?> = _detail
    private val _detailLog = MutableStateFlow("")
    val detailLog: StateFlow<String> = _detailLog
    private val _steps = MutableStateFlow<List<String>>(emptyList())
    val steps: StateFlow<List<String>> = _steps

    fun pollTask(jobId: String) = viewModelScope.launch {
        val c = api ?: return@launch
        while (isActive) {
            try {
                val st = c.readFile("jobs/$jobId/status.json")
                if (st != null) {
                    val s = TaskStatus.fromJson(JSONObject(st.first))
                    _detail.value = s
                    val line = "[${Pipeline.describe(s.state)}] ${s.message}"
                    if (!_detailLog.value.contains(line)) _detailLog.value += line + "\n"
                    if (s.runId != 0L) {
                        val jobs = c.runJobs(s.runId)
                        val list = mutableListOf<String>()
                        for (i in 0 until jobs.length()) {
                            val j = jobs.getJSONObject(i)
                            list += "${j.optString("name")} — ${j.optString("status")}/${j.optString("conclusion", "")}"
                        }
                        _steps.value = list
                        if (s.state == "complete" && !_detailLog.value.contains("[full-log]")) {
                            val zip = runCatching { c.runLogsZip(s.runId) }.getOrNull()
                            if (zip != null) {
                                _detailLog.value += "[full-log] fetched (${zip.size / 1024} KB, cached for offline review)\n"
                                File(getApplication<Application>().filesDir, "log-$jobId.zip").writeBytes(zip)
                            }
                        }
                    }
                    if (s.terminal) break
                }
            } catch (_: Exception) { /* transient poll error — keep trying */ }
            delay(3000)
        }
    }

    fun cancelTask() = viewModelScope.launch {
        val s = _detail.value ?: return@launch
        if (s.runId != 0L) runCatching { api?.cancelRun(s.runId) }
        toast("Cancel requested")
    }

    // ---------------- production.json ----------------
    fun submitPlan(jobId: String, planText: String) = viewModelScope.launch {
        val c = api ?: return@launch
        val errors = PlanValidator.validate(planText)
        if (errors.isNotEmpty()) { toast("Invalid plan: ${errors.first()} (+${errors.size - 1} more)"); return@launch }
        try {
            val existing = c.readFile("jobs/$jobId/production.json")
            c.putFile("jobs/$jobId/production.json", planText.toByteArray(), "production.json for $jobId", existing?.second)
            c.dispatchWorkflow("stage-b.yml", mapOf("job_id" to jobId, "code_ref" to c.defaultBranchSha()))
            toast("production.json accepted — Stage B dispatched")
        } catch (e: Exception) { toast("Submit failed: ${e.message}") }
    }

    // ---------------- video save ----------------
    data class SaveState(val active: Boolean, val fraction: Float, val label: String)
    private val _save = MutableStateFlow(SaveState(false, 0f, ""))
    val save: StateFlow<SaveState> = _save

    /** Stream a release asset into a SafeSave-chosen Uri with a Content-Length-driven progress bar. */
    fun saveVideo(status: TaskStatus, dest: android.net.Uri) = viewModelScope.launch {
        val c = api ?: return@launch
        try {
            _save.value = SaveState(true, 0f, "locating asset…")
            val tag = status.releaseTag.ifBlank { "clipforge-${status.jobId}" }
            val rel = c.releaseByTag(tag) ?: run { toast("Release not found"); _save.value = SaveState(false, 0f, ""); return@launch }
            val assets = rel.getJSONArray("assets")
            var assetUrl = ""; var name = "${status.jobId}.mp4"
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".mp4")) { assetUrl = a.getString("url"); name = a.optString("name"); break }
            }
            if (assetUrl.isBlank() && assets.length() > 0) assetUrl = assets.getJSONObject(0).getString("url")
            if (assetUrl.isBlank()) { toast("No asset on release"); _save.value = SaveState(false, 0f, ""); return@launch }
            withContext(Dispatchers.IO) {
                c.openAssetStream(assetUrl).use { resp ->
                    val total = resp.body?.contentLength() ?: -1L
                    val input = resp.body!!.byteStream()
                    getApplication<Application>().contentResolver.openOutputStream(dest, "wt")!!.use { out ->
                        val buf = ByteArray(64 * 1024); var read: Int; var done = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read); done += read
                            if (total > 0) _save.value = SaveState(true, done.toFloat() / total, name)
                        }
                        out.flush()
                    }
                }
            }
            _save.value = SaveState(false, 1f, name)
            toast("Saved $name")
        } catch (e: Exception) {
            _save.value = SaveState(false, 0f, "")
            toast("Save failed: ${e.message}")
        }
    }

    /** Preview URL for a music track or a completed video's audio (streams via GitHub contents media=raw). */
    fun audioPreviewUrl(path: String): String =
        "https://raw.githubusercontent.com/${_login.value?.owner}/${_login.value?.repo}/main/$path"
}
