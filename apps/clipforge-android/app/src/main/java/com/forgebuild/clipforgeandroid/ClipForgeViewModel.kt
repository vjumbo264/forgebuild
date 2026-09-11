package com.forgebuild.clipforgeandroid

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forgebuild.clipforgeandroid.data.*
import com.forgebuild.engine.data.CacheFirstStore
import com.forgebuild.engine.files.SafeSave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ClipForgeViewModel(val app: Application) : AndroidViewModel(app) {
    private val creds = CredentialStore(app)
    var api: GitHubClient? = null
        private set

    private val _login = MutableStateFlow<CredentialStore.CloneCredentials?>(null)
    val login: StateFlow<CredentialStore.CloneCredentials?> = _login

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    private val _snack = MutableStateFlow<String?>(null)
    val snack: StateFlow<String?> = _snack

    private val _hasPromptedStorage = MutableStateFlow(false)
    val hasPromptedStorage: StateFlow<Boolean> = _hasPromptedStorage

    fun toast(msg: String) { _snack.value = msg }
    fun clearSnack() { _snack.value = null }

    // ---------------- per-operation busy indicators ----------------
    // Buttons that call the network mark themselves busy so the UI can disable
    // them and render an inline spinner while the coroutine is in flight.
    private val _busyOps = MutableStateFlow<Set<String>>(emptySet())
    val busyOps: StateFlow<Set<String>> = _busyOps

    fun isBusy(op: String): Boolean = _busyOps.value.contains(op)

    private fun setBusy(op: String, busy: Boolean) {
        val cur = _busyOps.value
        _busyOps.value = if (busy) cur + op else cur - op
    }

    /** Convenience wrapper: mark [op] busy for the duration of [block]. */
    private suspend fun <T> withBusy(op: String, block: suspend () -> T): T {
        setBusy(op, true)
        return try { block() } finally { setBusy(op, false) }
    }

    fun markStoragePrompted() {
        creds.setPromptedStorage(true)
        _hasPromptedStorage.value = true
    }

    init {
        // Startup must NEVER throw — a crash here bricks every subsequent launch
        // (the "app doesn't launch again" bug). Credential storage is already
        // crash-proof, but we belt-and-suspend the whole init path anyway.
        try {
            _hasPromptedStorage.value = creds.hasPromptedStorage()
            creds.load()?.let {
                _login.value = it
                api = GitHubClient(it.pat, it.owner, it.repo)
            }
        } catch (_: Exception) {
            _login.value = null
            api = null
        }
        _ready.value = true
        // Kick off background refresh only after the UI is guaranteed to render.
        // refreshAll() launches coroutines (non-blocking); any network failure is
        // caught inside the individual refresh calls.
        if (api != null) {
            try {
                refreshAll()
            } catch (_: Exception) {}
        }
    }

    // ---------------- auth / clones ----------------
    fun connectExisting(pat: String, repoSlug: String) = viewModelScope.launch {
        val parts = repoSlug.trim().trim('/').split("/")
        if (parts.size != 2) {
            toast("Repo must be in format owner/repository")
            return@launch
        }
        withBusy("connect") {
            val c = GitHubClient(pat.trim(), parts[0], parts[1])
            try {
                if (!c.repoExists()) {
                    toast("Repo not found — check PAT scopes and repo name")
                    return@withBusy
                }
                val me = c.whoami().optString("login")
                val credentials = CredentialStore.CloneCredentials(pat.trim(), parts[0], parts[1], me)
                creds.save(credentials)
                api = c
                _login.value = credentials
                toast("Connected to $repoSlug")
                refreshAll()
            } catch (e: Exception) {
                toast("Login failed: ${e.message}")
            }
        }
    }

    private val _cloneProgress = MutableStateFlow<String?>(null)
    val cloneProgress: StateFlow<String?> = _cloneProgress

    fun createClone(pat: String, repoName: String) = viewModelScope.launch {
        val name = repoName.trim().ifBlank { "clipforge-clone" }
        _cloneProgress.value = "Creating private repo…"
        withBusy("create_clone") {
            try {
                val temp = GitHubClient(pat.trim(), "", "")
                val user = temp.whoami().optString("login")
                val created = temp.createRepo(name)
                val owner = created.getJSONObject("owner").getString("login")
                val c = GitHubClient(pat.trim(), owner, name)
                _cloneProgress.value = "Seeding branding and settings…"
                c.putFile("branding/tts_settings.json", JSONObject().put("voice", Voices.DEFAULT).toString(2).toByteArray(), "init tts")
                c.putFile("branding/series_settings.json", JSONObject().put("enabled", false).toString(2).toByteArray(), "init series")
                c.putFile("branding/creator_watermark.json", JSONObject().put("watermark", "").toString(2).toByteArray(), "init watermark")
                c.putFile("branding/zernio_settings.json", JSONObject().put("enabled", false).put("api_key", "").toString(2).toByteArray(), "init zernio")

                val credentials = CredentialStore.CloneCredentials(pat.trim(), owner, name, user)
                creds.save(credentials)
                api = c
                _login.value = credentials
                toast("Created and connected $owner/$name")
                refreshAll()
            } catch (e: Exception) {
                toast("Clone creation failed: ${e.message}")
            } finally {
                _cloneProgress.value = null
            }
        }
    }

    fun signOut() {
        creds.clear()
        api = null
        _login.value = null
        _tasks.value = emptyList()
        _music.value = emptyList()
        toast("Signed out")
    }

    fun deleteClone(onSuccess: () -> Unit) = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("delete_clone") {
            try {
                c.deleteRepo()
                signOut()
                toast("Repository deleted")
                onSuccess()
            } catch (e: Exception) {
                toast("Failed to delete repository: ${e.message}")
            }
        }
    }

    fun refreshAll() {
        refreshTasks()
        refreshMusic()
        loadSettings()
    }

    // ---------------- tasks & state ----------------
    private val _tasks = MutableStateFlow<List<TaskStatus>>(emptyList())
    val tasks: StateFlow<List<TaskStatus>> = _tasks

    val activeTasks: StateFlow<List<TaskStatus>> = _tasks.map { list ->
        list.filter { !it.isComplete }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val completedTasks: StateFlow<List<TaskStatus>> = _tasks.map { list ->
        list.filter { it.isComplete }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _tasksRefreshing = MutableStateFlow(false)
    val tasksRefreshing: StateFlow<Boolean> = _tasksRefreshing

    fun refreshTasks() = viewModelScope.launch {
        val c = api ?: return@launch
        _tasksRefreshing.value = true
        try {
            val entries = c.listDir("jobs")
            val list = mutableListOf<TaskStatus>()
            for (i in 0 until entries.length()) {
                val item = entries.getJSONObject(i)
                if (item.optString("type") != "dir") continue
                val jobId = item.getString("name")
                val statusFile = c.readFile("jobs/$jobId/status.json") ?: continue
                try {
                    val j = JSONObject(statusFile.first)
                    list.add(TaskStatus.fromJson(j))
                } catch (_: Exception) {}
            }
            _tasks.value = list.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            // keep existing tasks on transient error
        } finally {
            _tasksRefreshing.value = false
        }
    }

    fun deleteTasks(jobIds: Set<String>) = viewModelScope.launch {
        val c = api ?: return@launch
        try {
            jobIds.forEach { id ->
                val files = c.listDir("jobs/$id")
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    c.deleteFile(f.getString("path"), f.getString("sha"), "clean job $id")
                }
                c.deleteRelease("clipforge-$id")
                c.deleteRelease("clipforge-relay-input-$id")
            }
            _tasks.value = _tasks.value.filter { !jobIds.contains(it.jobId) }
            toast("Deleted ${jobIds.size} task(s)")
        } catch (e: Exception) {
            toast("Delete error: ${e.message}")
        }
    }

    // ---------------- upload progress ----------------
    data class UploadProgress(val label: String, val fraction: Float)
    private val _upload = MutableStateFlow<UploadProgress?>(null)
    val upload: StateFlow<UploadProgress?> = _upload

    // ---------------- task creation ----------------
    fun createStageATask(
        sourceKind: String,
        sourceValue: String,
        focus: String,
        targetDurationSeconds: Int,
        selectedMusicPath: String?,
        isSeries: Boolean,
        seriesId: String?,
        torrentBytes: ByteArray?,
        onCreated: (String) -> Unit
    ) = viewModelScope.launch {
        val c = api ?: run { toast("Not connected"); return@launch }
        // Bot parity (motionssalt/clipforge bot/src/github.js makeJobId): job ids are
        // manual-<epochMillis>. The stage-a-request MUST match schemas/stage_a_request.schema.json
        // exactly — ingest.py's load_request() hard-fails when source.value is absent, which is
        // why every app-created task used to die at ingestion (app wrote source.url/source.path).
        val jobId = "manual-" + System.currentTimeMillis()
        val nowSec = System.currentTimeMillis() / 1000
        _upload.value = UploadProgress("Setting up task…", 0.1f)
        try {
            val sourceObject = JSONObject().put("kind", sourceKind)

            if (sourceKind == "torrent_file") {
                if (torrentBytes == null || torrentBytes.isEmpty()) {
                    toast("Torrent file is required")
                    _upload.value = null
                    return@launch
                }
                if (torrentBytes.size > 1024 * 1024) {
                    toast("Torrent file must be under 1 MB")
                    _upload.value = null
                    return@launch
                }
                val torrentPath = "jobs/$jobId/source.torrent"
                _upload.value = UploadProgress("Uploading torrent file…", 0.2f)
                c.putFile(torrentPath, torrentBytes, "Add torrent for $jobId", onProgress = { sent, total ->
                    val fraction = if (total > 0) 0.2f + (sent.toFloat() / total * 0.5f) else 0.4f
                    _upload.value = UploadProgress("Uploading torrent file…", fraction)
                })
                // ingest.py expects the job-local manifest path in source.value ("path:" prefix tolerated)
                sourceObject.put("value", "path:$torrentPath")
            } else {
                sourceObject.put("value", sourceValue.trim())
            }

            val options = JSONObject()
                .put("whisper_model", "base")
                .put("language", "auto")
                .put("task", "translate_to_english")
                .put("target_duration_seconds", targetDurationSeconds)
                .put("focus", if (isSeries) "" else focus.trim())
                .put("enable_vision_assist", true)

            // Series block is REQUIRED by the schema even when disabled. For a new series,
            // Part 1's own Stage A release is its evidence source: source_job_id = this job id
            // (bug-64 in the reference repo — never seed series_id here).
            val sid = if (seriesId.isNullOrBlank()) "series-" + System.currentTimeMillis() else seriesId.trim()
            val seriesJson = JSONObject()
                .put("enabled", isSeries)
                .put("series_id", if (isSeries) sid else "")
                .put("source_job_id", if (isSeries) jobId else "")
                .put("part", if (isSeries) 1 else 0)
                .put("start_seconds", 0)
                .put("context", "")

            val musicJson = if (!selectedMusicPath.isNullOrBlank()) {
                JSONObject().put("ref", selectedMusicPath).put("source", "explicit_library")
            } else {
                JSONObject().put("ref", "").put("source", "none")
            }

            val requestJson = JSONObject()
                .put("version", 2)
                .put("job_id", jobId)
                .put("source", sourceObject)
                .put("options", options)
                .put("mode", "manual")
                .put("series", seriesJson)
                .put("music", musicJson)
                .put("saved_at_epoch", nowSec)

            // Status record must satisfy schemas/job_status.schema.json. Field-for-field
            // parity with the bot's newStatus() (bot/src/jobs.js): version 2, status-series
            // block {enabled, series_id, part, start_seconds, is_final} (NOTE: unlike the
            // request schema, the STATUS series block has is_final and no source_job_id/
            // context), assets is a map, run carries code_ref, publishing is fully seeded.
            val statusSeries = JSONObject()
                .put("enabled", isSeries)
                .put("series_id", if (isSeries) sid else "")
                .put("part", if (isSeries) 1 else 0)
                .put("start_seconds", 0)
                .put("is_final", false)
            val statusJson = JSONObject()
                .put("version", 2)
                .put("job_id", jobId)
                .put("mode", "manual")
                .put("series", statusSeries)
                .put("state", "queued")
                .put("message", "Task queued")
                .put("created_at_epoch", nowSec)
                .put("updated_at_epoch", nowSec)
                .put("expires_at_epoch", nowSec + 172800) // 48h TTL, matches CLIPFORGE_TTL_SECONDS
                .put("release_tag", "")
                .put("release_url", "")
                .put("assets", JSONObject())
                .put("run", JSONObject()
                    .put("workflow_run_id", 0)
                    .put("workflow_run_url", "")
                    .put("code_ref", ""))
                .put("publishing", JSONObject()
                    .put("status", "not_requested")
                    .put("posts", JSONArray())
                    .put("idempotency_key", ""))

            _upload.value = UploadProgress("Dispatching Stage A…", 0.8f)
            c.putFile("jobs/$jobId/stage-a-request.json", requestJson.toString(2).toByteArray(), "Stage A request")
            c.putFile("jobs/$jobId/status.json", statusJson.toString(2).toByteArray(), "Init status")

            val sha = c.defaultBranchSha()
            c.dispatchWorkflow("stage-a.yml", mapOf("job_id" to jobId, "code_ref" to sha))

            _upload.value = UploadProgress("Task started!", 1f)
            toast("Task $jobId created")
            refreshTasks()
            onCreated(jobId)
        } catch (e: Exception) {
            toast("Task creation failed: ${e.message}")
        } finally {
            _upload.value = null
        }
    }

    // ---------------- task detail & logs ----------------
    private val _detailStatus = MutableStateFlow<TaskStatus?>(null)
    val detailStatus: StateFlow<TaskStatus?> = _detailStatus

    private val _detailRequest = MutableStateFlow<JSONObject?>(null)
    val detailRequest: StateFlow<JSONObject?> = _detailRequest

    private val _detailPlan = MutableStateFlow<String?>(null)
    val detailPlan: StateFlow<String?> = _detailPlan

    /** Color-coding level for a single log line (maps to theme colors in the UI). */
    enum class LogLevel { PENDING, RUNNING, SUCCESS, FAILURE, SKIPPED, CANCELLED, INFO }

    /** One rendered log row. [key] is stable across polls so Compose can animate updates. */
    data class LogLine(val key: String, val text: String, val level: LogLevel)

    // Per-step log stream, rebuilt in canonical order on every poll: queued steps render
    // as gray pending rows, the running step pulses in primary, and finished steps keep
    // their conclusion color (success/failure/skipped/cancelled).
    private val _detailLogs = MutableStateFlow<List<LogLine>>(emptyList())
    val detailLogs: StateFlow<List<LogLine>> = _detailLogs

    /** A single video candidate discovered inside a torrent by Stage A. */
    data class TorrentFileOption(val index: Int, val name: String, val sizeBytes: Long)

    private val _torrentFiles = MutableStateFlow<List<TorrentFileOption>>(emptyList())
    val torrentFiles: StateFlow<List<TorrentFileOption>> = _torrentFiles

    private val _torrentSubmitting = MutableStateFlow(false)
    val torrentSubmitting: StateFlow<Boolean> = _torrentSubmitting

    private var pollJob: Job? = null

    fun startPollingTask(jobId: String) {
        pollJob?.cancel()
        // Fresh log buffer for a fresh task view.
        _detailLogs.value = emptyList()
        pollJob = viewModelScope.launch {
            while (isActive) {
                loadTaskDetail(jobId)
                delay(2500)
            }
        }
    }

    fun stopPollingTask() {
        pollJob?.cancel()
        pollJob = null
        _detailStatus.value = null
        _detailLogs.value = emptyList()
    }

    suspend fun loadTaskDetail(jobId: String) {
        val c = api ?: return
        try {
            val stFile = c.readFile("jobs/$jobId/status.json")
            if (stFile != null) {
                val j = JSONObject(stFile.first)
                val status = TaskStatus.fromJson(j)
                _detailStatus.value = status

                if (status.state == "awaiting_torrent_selection") {
                    loadTorrentFiles(jobId)
                }

                // Rebuild the full ordered log on every poll. Stable keys mean existing
                // rows simply change color as steps transition pending -> running -> done.
                val newLogs = mutableListOf<LogLine>()
                newLogs.add(
                    LogLine(
                        key = "status",
                        text = "Status: ${status.state} — ${status.message}",
                        level = when (status.state) {
                            "error" -> LogLevel.FAILURE
                            "complete" -> LogLevel.SUCCESS
                            "cancelled" -> LogLevel.CANCELLED
                            "queued" -> LogLevel.PENDING
                            "awaiting_torrent_selection", "awaiting_plan" -> LogLevel.SKIPPED // attention-grabbing waiting state
                            else -> LogLevel.RUNNING
                        }
                    )
                )

                if (status.runId > 0) {
                    try {
                        val jobs = c.runJobs(status.runId)
                        for (i in 0 until jobs.length()) {
                            val jobObj = jobs.getJSONObject(i)
                            val jobName = jobObj.optString("name")
                            val jStatus = jobObj.optString("status", "")
                            val jConclusion = jobObj.optString("conclusion", "")
                            newLogs.add(
                                LogLine(
                                    key = "job-$i",
                                    text = "Job: $jobName",
                                    level = logLevelFor(jStatus, jConclusion)
                                )
                            )
                            val steps = jobObj.optJSONArray("steps") ?: continue
                            for (s in 0 until steps.length()) {
                                val step = steps.getJSONObject(s)
                                val sName = step.optString("name")
                                val sStatus = step.optString("status", "")
                                val sConclusion = step.optString("conclusion", "")
                                val level = logLevelFor(sStatus, sConclusion)
                                val marker = when (level) {
                                    LogLevel.SUCCESS -> "✓"
                                    LogLevel.FAILURE -> "✗"
                                    LogLevel.CANCELLED -> "⏹"
                                    LogLevel.SKIPPED -> "↷"
                                    LogLevel.RUNNING -> "…"
                                    else -> "•"
                                }
                                newLogs.add(
                                    LogLine(
                                        key = "job-$i-step-$s",
                                        text = "$marker  $sName",
                                        level = level
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }
                _detailLogs.value = newLogs
            }

            val reqFile = c.readFile("jobs/$jobId/stage-a-request.json")
            if (reqFile != null) {
                _detailRequest.value = JSONObject(reqFile.first)
            }

            val planFile = c.readFile("jobs/$jobId/production.json")
            _detailPlan.value = planFile?.first
        } catch (_: Exception) {}
    }

    /** Map a GitHub Actions job/step (status, conclusion) pair onto a log level. */
    private fun logLevelFor(status: String, conclusion: String): LogLevel = when {
        conclusion == "success" -> LogLevel.SUCCESS
        conclusion == "failure" || conclusion == "timed_out" || conclusion == "action_required" -> LogLevel.FAILURE
        conclusion == "cancelled" -> LogLevel.CANCELLED
        conclusion == "skipped" || conclusion == "neutral" -> LogLevel.SKIPPED
        status == "in_progress" -> LogLevel.RUNNING
        status == "completed" -> LogLevel.INFO
        else -> LogLevel.PENDING // queued / waiting / requested / pending
    }

    /**
     * Load the video candidates discovered inside the torrent for [jobId].
     * Pipeline parity: ingest.py (write_torrent_selection) writes
     * jobs/<id>/torrent-selection.json with {video_candidates: [{index, path, name?, size}]}.
     * Candidate display name comes from `path` (fallback `name`), size from `size`
     * (bytes; tolerates size_bytes / length like the bot's candidateSize()).
     */
    private suspend fun loadTorrentFiles(jobId: String) {
        val c = api ?: return
        val parsed = mutableListOf<TorrentFileOption>()

        fun parseArray(arr: JSONArray) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("path").ifBlank { o.optString("name", "file #" + o.optInt("index", i)) }
                val size = o.optLong("size", o.optLong("size_bytes", o.optLong("length", 0L)))
                parsed.add(
                    TorrentFileOption(
                        index = o.optInt("index", i),
                        name = name,
                        sizeBytes = size
                    )
                )
            }
        }

        try {
            val tfFile = c.readFile("jobs/$jobId/torrent-selection.json")
            if (tfFile != null) {
                val tj = JSONObject(tfFile.first)
                tj.optJSONArray("video_candidates")?.let { parseArray(it) }
            }
        } catch (_: Exception) {}

        _torrentFiles.value = parsed
    }

    /**
     * Submit the user's torrent video selection — bot parity with
     * bot/src/index.js pickTorrentFile:
     *   1. read jobs/<id>/stage-a-request.json, set source.torrent_file_index = "<index>",
     *      rewrite the request (ingest.py reads ONLY this field; there is no
     *      torrent_selected workflow input in stage-a.yml)
     * 2. re-dispatch stage-a.yml with exactly {job_id, code_ref} — no extra inputs exist
     *   3. merge the status record (preserve every existing field) to stage_a_running
     */
    fun submitTorrentSelection(jobId: String, index: Int, onDone: () -> Unit = {}) = viewModelScope.launch {
        val c = api ?: return@launch
        if (_torrentSubmitting.value) return@launch
        _torrentSubmitting.value = true
        try {
            _upload.value = UploadProgress("Submitting torrent selection…", 0.3f)

            val reqFile = c.readFile("jobs/$jobId/stage-a-request.json")
                ?: throw Exception("stage-a-request.json not found for $jobId")
            val request = JSONObject(reqFile.first)
            val src = request.optJSONObject("source") ?: JSONObject().also { request.put("source", it) }
            src.put("torrent_file_index", index.toString())
            request.put("saved_at_epoch", System.currentTimeMillis() / 1000)
            c.putFile(
                "jobs/$jobId/stage-a-request.json",
                request.toString(2).toByteArray(Charsets.UTF_8),
                "clipforge: torrent file selected for job $jobId"
            )

            val sha = c.defaultBranchSha()
            _upload.value = UploadProgress("Resuming pipeline…", 0.7f)
            c.dispatchWorkflow("stage-a.yml", mapOf("job_id" to jobId, "code_ref" to sha))

            // Merge (not rewrite) the status record so TTL, series and run fields survive.
            try {
                val stFile = c.readFile("jobs/$jobId/status.json")
                if (stFile != null) {
                    val stObj = JSONObject(stFile.first)
                    stObj.put("state", "stage_a_running")
                    stObj.put("message", "Video file #$index selected — resuming ingest.")
                    stObj.put("updated_at_epoch", System.currentTimeMillis() / 1000)
                    c.putFile("jobs/$jobId/status.json", stObj.toString(2).toByteArray(), "clipforge: resume Stage A after torrent selection for $jobId")
                }
            } catch (_: Exception) {}

            toast("Selection submitted — resuming task.")
            _torrentFiles.value = emptyList()
            loadTaskDetail(jobId)
            onDone()
        } catch (e: Exception) {
            toast("Selection failed: ${e.message}")
        } finally {
            _torrentSubmitting.value = false
            _upload.value = null
        }
    }

    fun submitProductionPlan(jobId: String, planText: String, onDone: () -> Unit) = viewModelScope.launch {
        val c = api ?: return@launch
        val errors = PlanValidator.validate(planText)
        if (errors.isNotEmpty()) {
            toast("Plan validation failed: ${errors.first()}")
            return@launch
        }
        _upload.value = UploadProgress("Submitting production plan…", 0.3f)
        try {
            c.putFile("jobs/$jobId/production.json", planText.toByteArray(Charsets.UTF_8), "Submit production.json", onProgress = { s, t ->
                val f = if (t > 0) 0.3f + (s.toFloat() / t * 0.4f) else 0.5f
                _upload.value = UploadProgress("Uploading production plan…", f)
            })

            val sha = c.defaultBranchSha()
            _upload.value = UploadProgress("Dispatching Stage B…", 0.8f)
            c.dispatchWorkflow("stage-b.yml", mapOf("job_id" to jobId, "code_ref" to sha))
            toast("Production plan accepted! Rendering started.")
            refreshTasks()
            onDone()
        } catch (e: Exception) {
            toast("Submission error: ${e.message}")
        } finally {
            _upload.value = null
        }
    }

    fun cancelTask(jobId: String) = viewModelScope.launch {
        val c = api ?: return@launch
        val st = _detailStatus.value ?: return@launch
        try {
            if (st.runId > 0) {
                c.cancelRun(st.runId)
            }
            val stObj = JSONObject()
                .put("job_id", jobId)
                .put("state", "cancelled")
                .put("message", "Cancelled by user")
                .put("updated_at_epoch", System.currentTimeMillis())
            c.putFile("jobs/$jobId/status.json", stObj.toString(2).toByteArray(), "Cancel task")
            toast("Task cancelled")
            refreshTasks()
        } catch (e: Exception) {
            toast("Failed to cancel: ${e.message}")
        }
    }

    // ---------------- video download with progress & speed ----------------
    data class DownloadState(val isDownloading: Boolean = false, val progress: Float = 0f, val speedText: String = "", val error: String? = null)
    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState: StateFlow<DownloadState> = _downloadState

    fun saveVideoToMovies(tag: String, fileName: String = "clipforge-video.mp4") = viewModelScope.launch {
        val c = api ?: run { toast("Not connected"); return@launch }
        _downloadState.value = DownloadState(isDownloading = true, progress = 0.05f, speedText = "Locating asset…")
        withContext(Dispatchers.IO) {
            try {
                val rel = c.releaseByTag(tag) ?: throw Exception("Release tag $tag not found")
                val assets = rel.getJSONArray("assets")
                var assetApiUrl: String? = null
                var assetName = fileName
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.getString("name")
                    if (name.endsWith(".mp4", ignoreCase = true)) {
                        assetApiUrl = a.getString("url")
                        assetName = name
                        break
                    }
                }
                if (assetApiUrl == null) throw Exception("No .mp4 asset found in release $tag")

                val resp = c.openAssetStream(assetApiUrl)
                val body = resp.body ?: throw Exception("Empty response body from asset stream")
                val totalBytes = body.contentLength()

                val context = app.applicationContext
                val outputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, assetName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ClipForge")
                    }
                    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                        ?: throw Exception("Could not create MediaStore entry")
                    resolver.openOutputStream(uri) ?: throw Exception("Could not open MediaStore output stream")
                } else {
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ClipForge")
                    if (!dir.exists()) dir.mkdirs()
                    val targetFile = File(dir, assetName)
                    FileOutputStream(targetFile)
                }

                outputStream.use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(32768)
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastTime = System.currentTimeMillis()
                        var lastBytes = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            val now = System.currentTimeMillis()
                            if (now - lastTime >= 500) {
                                val elapsedSec = (now - lastTime) / 1000.0
                                val speed = ((totalRead - lastBytes) / elapsedSec) / (1024.0 * 1024.0)
                                val speedStr = String.format("%.2f MB/s", speed)
                                val frac = if (totalBytes > 0) totalRead.toFloat() / totalBytes else 0.5f
                                _downloadState.value = DownloadState(isDownloading = true, progress = frac, speedText = speedStr)
                                lastTime = now
                                lastBytes = totalRead
                            }
                        }
                    }
                }

                _downloadState.value = DownloadState(isDownloading = false, progress = 1f, speedText = "Saved to Movies/ClipForge/$assetName")
                withContext(Dispatchers.Main) {
                    toast("Video saved to Movies/ClipForge/$assetName")
                }
            } catch (e: Exception) {
                _downloadState.value = DownloadState(isDownloading = false, error = e.message)
                withContext(Dispatchers.Main) {
                    toast("Download failed: ${e.message}")
                }
            }
        }
    }

    // ---------------- music library ----------------
    private val _music = MutableStateFlow<List<MusicTrack>>(emptyList())
    val music: StateFlow<List<MusicTrack>> = _music

    private val _musicRefreshing = MutableStateFlow(false)
    val musicRefreshing: StateFlow<Boolean> = _musicRefreshing

    private val _defaultMusic = MutableStateFlow<String?>(null)
    val defaultMusic: StateFlow<String?> = _defaultMusic

    fun refreshMusic() = viewModelScope.launch {
        val c = api ?: return@launch
        _musicRefreshing.value = true
        try {
            val list = c.listDir("audio-library")
            val tracks = mutableListOf<MusicTrack>()
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                if (item.optString("type") == "file") {
                    val name = item.getString("name")
                    if (name.endsWith(".m4a") || name.endsWith(".mp3") || name.endsWith(".aac")) {
                        tracks.add(MusicTrack(name, item.getString("path"), item.optLong("size", 0), item.getString("sha")))
                    }
                }
            }
            _music.value = tracks

            val def = c.readFile("branding/music_default.json")
            if (def != null) {
                _defaultMusic.value = JSONObject(def.first).optString("path").ifBlank { null }
            }
        } catch (_: Exception) {}
        finally { _musicRefreshing.value = false }
    }

    fun setDefaultMusic(path: String?) = viewModelScope.launch {
        val c = api ?: return@launch
        try {
            val payload = JSONObject().put("path", path ?: "")
            c.putFile("branding/music_default.json", payload.toString(2).toByteArray(), "Update default music")
            _defaultMusic.value = path
            toast(if (path != null) "Default track updated" else "Default track cleared")
        } catch (e: Exception) {
            toast("Failed to update default music: ${e.message}")
        }
    }

    fun uploadMusic(name: String, bytes: ByteArray) = viewModelScope.launch {
        val c = api ?: return@launch
        val cleanName = name.replace(" ", "_")
        _upload.value = UploadProgress("Uploading $cleanName…", 0.1f)
        try {
            c.putFile("audio-library/$cleanName", bytes, "Add $cleanName", onProgress = { sent, total ->
                val f = if (total > 0) sent.toFloat() / total else 0.5f
                _upload.value = UploadProgress("Uploading $cleanName…", f)
            })
            toast("Track $cleanName uploaded")
            refreshMusic()
        } catch (e: Exception) {
            toast("Upload failed: ${e.message}")
        } finally {
            _upload.value = null
        }
    }

    fun deleteMusic(tracks: Set<MusicTrack>) = viewModelScope.launch {
        val c = api ?: return@launch
        try {
            tracks.forEach { t ->
                c.deleteFile(t.path, t.sha, "Remove audio track ${t.name}")
            }
            _music.value = _music.value.filter { !tracks.contains(it) }
            toast("Deleted ${tracks.size} track(s)")
        } catch (e: Exception) {
            toast("Failed to delete music: ${e.message}")
        }
    }

    fun audioPreviewUrl(path: String): String {
        val c = api ?: return ""
        return "https://api.github.com/repos/${c.owner}/${c.repo}/contents/$path?ref=main"
    }

    // ---------------- full settings ----------------
    data class AppSettings(
        val isPrivate: Boolean = true,
        val narratorVoice: String = Voices.DEFAULT,
        val seriesDefault: Boolean = false,
        val watermarkText: String = "",
        val zernioEnabled: Boolean = false,
        val zernioApiKey: String = "",
        val zernioAccounts: List<String> = emptyList()
    )

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    // True while loadSettings() is in flight — lets the Settings screen show a skeleton
    // instead of empty defaults that read as "nothing was ever saved".
    private val _settingsLoading = MutableStateFlow(false)
    val settingsLoading: StateFlow<Boolean> = _settingsLoading

    // True after loadSettings() has completed at least once for this session.
    private val _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded

    fun loadSettings() = viewModelScope.launch {
        val c = api ?: return@launch
        _settingsLoading.value = true
        try {
            val details = try { c.repoDetails() } catch (_: Exception) { JSONObject() }
            val isPriv = details.optBoolean("private", true)

            var voice = Voices.DEFAULT
            try {
                c.readFile("branding/tts_settings.json")?.let {
                    voice = JSONObject(it.first).optString("voice", Voices.DEFAULT)
                }
            } catch (_: Exception) {}

            var seriesDef = false
            try {
                c.readFile("branding/series_settings.json")?.let {
                    seriesDef = JSONObject(it.first).optBoolean("enabled", false)
                }
            } catch (_: Exception) {}

            var wm = ""
            try {
                c.readFile("branding/creator_watermark.json")?.let {
                    wm = JSONObject(it.first).optString("watermark", "")
                }
            } catch (_: Exception) {}

            var zEnabled = false
            var zKey = ""
            var zAccounts = listOf<String>()
            try {
                c.readFile("branding/zernio_settings.json")?.let {
                    val j = JSONObject(it.first)
                    zEnabled = j.optBoolean("enabled", false)
                    zKey = j.optString("api_key", "")
                    val accs = j.optJSONArray("accounts")
                    if (accs != null) {
                        zAccounts = (0 until accs.length()).map { i -> accs.getString(i) }
                    }
                }
            } catch (_: Exception) {}

            _settings.value = AppSettings(
                isPrivate = isPriv,
                narratorVoice = voice,
                seriesDefault = seriesDef,
                watermarkText = wm,
                zernioEnabled = zEnabled,
                zernioApiKey = zKey,
                zernioAccounts = zAccounts
            )
            _settingsLoaded.value = true
        } catch (e: Exception) {
            toast("Failed to load settings: ${e.message}")
        } finally {
            _settingsLoading.value = false
        }
    }

    /** Refresh Zernio connected-channels list from the clone repo. */
    fun refreshZernioAccounts() = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("zernio_refresh") {
            try {
                val f = c.readFile("branding/zernio_settings.json") ?: return@withBusy
                val j = JSONObject(f.first)
                val accs = j.optJSONArray("accounts")
                val list = if (accs != null) (0 until accs.length()).map { i -> accs.getString(i) } else emptyList()
                _settings.value = _settings.value.copy(zernioAccounts = list)
                toast(if (list.isEmpty()) "No connected channels yet" else "Loaded ${list.size} channel(s)")
            } catch (e: Exception) {
                toast("Failed to refresh Zernio channels: ${e.message}")
            }
        }
    }

    fun toggleRepoVisibility() = viewModelScope.launch {
        val c = api ?: return@launch
        val current = _settings.value.isPrivate
        withBusy("toggle_visibility") {
            try {
                c.setVisibility(!current)
                _settings.value = _settings.value.copy(isPrivate = !current)
                toast("Repository visibility set to ${if (!current) "Public" else "Private"}")
            } catch (e: Exception) {
                toast("Failed to change visibility: ${e.message}")
            }
        }
    }

    fun syncFromSource() = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("sync_from_source") {
            try {
                c.syncFromSource()
                toast("Dispatched sync from motionssalt/clipforge")
            } catch (e: Exception) {
                toast("Sync dispatch error: ${e.message}")
            }
        }
    }

    fun pushUpdateToClones() = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("push_update") {
            try {
                c.pushUpdateToClones()
                toast("Broadcasted update to all clones")
            } catch (e: Exception) {
                toast("Push update error: ${e.message}")
            }
        }
    }

    fun pushNews(newsText: String) = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("push_news") {
            try {
                c.pushNews(newsText)
                toast("Broadcasted news message")
            } catch (e: Exception) {
                toast("Broadcast error: ${e.message}")
            }
        }
    }

    fun setNarratorVoice(voiceId: String) = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("save_narrator") {
            try {
                val payload = JSONObject().put("voice", voiceId)
                c.putFile("branding/tts_settings.json", payload.toString(2).toByteArray(), "Update TTS voice")
                _settings.value = _settings.value.copy(narratorVoice = voiceId)
                toast("Narrator voice updated")
            } catch (e: Exception) {
                toast("Voice update error: ${e.message}")
            }
        }
    }

    fun setSeriesDefault(enabled: Boolean) = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("save_series_default") {
            try {
                val payload = JSONObject().put("enabled", enabled)
                c.putFile("branding/series_settings.json", payload.toString(2).toByteArray(), "Update series default")
                _settings.value = _settings.value.copy(seriesDefault = enabled)
                toast("Series mode default: ${if (enabled) "ON" else "OFF"}")
            } catch (e: Exception) {
                toast("Series default update error: ${e.message}")
            }
        }
    }

    fun setWatermark(text: String) = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("save_watermark") {
            try {
                val payload = JSONObject().put("watermark", text.trim())
                c.putFile("branding/creator_watermark.json", payload.toString(2).toByteArray(), "Update watermark")
                _settings.value = _settings.value.copy(watermarkText = text.trim())
                toast(if (text.isBlank()) "Watermark cleared" else "Watermark saved")
            } catch (e: Exception) {
                toast("Watermark error: ${e.message}")
            }
        }
    }

    fun clearWatermark() = setWatermark("")

    fun saveZernioSettings(apiKey: String, enabled: Boolean) = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("save_zernio") {
            try {
                val payload = JSONObject()
                    .put("api_key", apiKey.trim())
                    .put("enabled", enabled)
                c.putFile("branding/zernio_settings.json", payload.toString(2).toByteArray(), "Update Zernio settings")
                _settings.value = _settings.value.copy(zernioApiKey = apiKey.trim(), zernioEnabled = enabled)
                toast("Zernio settings saved")
            } catch (e: Exception) {
                toast("Zernio save error: ${e.message}")
            }
        }
    }

    fun clearZernioKey() = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("clear_zernio") {
            try {
                val payload = JSONObject().put("api_key", "").put("enabled", false)
                c.putFile("branding/zernio_settings.json", payload.toString(2).toByteArray(), "Clear Zernio key")
                _settings.value = _settings.value.copy(zernioApiKey = "", zernioEnabled = false, zernioAccounts = emptyList())
                toast("Zernio credentials cleared")
            } catch (e: Exception) {
                toast("Failed to clear Zernio key: ${e.message}")
            }
        }
    }
}
