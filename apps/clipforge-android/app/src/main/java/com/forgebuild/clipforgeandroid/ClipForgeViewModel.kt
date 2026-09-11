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

    fun markStoragePrompted() {
        creds.setPromptedStorage(true)
        _hasPromptedStorage.value = true
    }

    init {
        _hasPromptedStorage.value = creds.hasPromptedStorage()
        creds.load()?.let {
            _login.value = it
            api = GitHubClient(it.pat, it.owner, it.repo)
        }
        _ready.value = true
        if (api != null) {
            refreshAll()
        }
    }

    // ---------------- auth / clones ----------------
    fun connectExisting(pat: String, repoSlug: String) = viewModelScope.launch {
        val parts = repoSlug.trim().trim('/').split("/")
        if (parts.size != 2) {
            toast("Repo must be in format owner/repository")
            return@launch
        }
        val c = GitHubClient(pat.trim(), parts[0], parts[1])
        try {
            if (!c.repoExists()) {
                toast("Repo not found — check PAT scopes and repo name")
                return@launch
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

    private val _cloneProgress = MutableStateFlow<String?>(null)
    val cloneProgress: StateFlow<String?> = _cloneProgress

    fun createClone(pat: String, repoName: String) = viewModelScope.launch {
        val name = repoName.trim().ifBlank { "clipforge-clone" }
        _cloneProgress.value = "Creating private repo…"
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
        try {
            c.deleteRepo()
            signOut()
            toast("Repository deleted")
            onSuccess()
        } catch (e: Exception) {
            toast("Failed to delete repository: ${e.message}")
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
        val jobId = "cf-" + System.currentTimeMillis()
        _upload.value = UploadProgress("Setting up task…", 0.1f)
        try {
            var sourceObject = JSONObject().put("kind", sourceKind)

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
                sourceObject.put("path", torrentPath)
            } else {
                sourceObject.put("url", sourceValue.trim())
            }

            val options = JSONObject()
                .put("target_duration_seconds", targetDurationSeconds)
                .put("focus", focus.trim())

            if (!selectedMusicPath.isNullOrBlank()) {
                options.put("music_track", selectedMusicPath)
            }

            val requestJson = JSONObject()
                .put("job_id", jobId)
                .put("source", sourceObject)
                .put("options", options)
                .put("created_at_epoch", System.currentTimeMillis())

            if (isSeries) {
                val sid = if (seriesId.isNullOrBlank()) "series-" + (System.currentTimeMillis() / 1000) else seriesId.trim()
                requestJson.put("series", JSONObject()
                    .put("enabled", true)
                    .put("series_id", sid)
                    .put("part", 1)
                    .put("start_seconds", 0)
                )
            }

            val statusJson = JSONObject()
                .put("job_id", jobId)
                .put("state", "queued")
                .put("message", "Task queued")
                .put("created_at_epoch", System.currentTimeMillis())
                .put("updated_at_epoch", System.currentTimeMillis())

            if (isSeries) {
                statusJson.put("series", requestJson.getJSONObject("series"))
            }

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

    // Append-only log stream: each unique step line appears exactly once, in first-seen order.
    // The UI is expected to autoscroll to the bottom whenever this list grows.
    private val _detailLogs = MutableStateFlow<List<String>>(emptyList())
    val detailLogs: StateFlow<List<String>> = _detailLogs

    // Track already-emitted log lines for the CURRENT job so we only append truly-new lines.
    private val seenLogLines: LinkedHashSet<String> = LinkedHashSet()
    private var currentPollJobId: String? = null

    private var pollJob: Job? = null

    fun startPollingTask(jobId: String) {
        pollJob?.cancel()
        // Fresh log buffer for a fresh task view.
        currentPollJobId = jobId
        seenLogLines.clear()
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
        currentPollJobId = null
        seenLogLines.clear()
        _detailStatus.value = null
        _detailLogs.value = emptyList()
    }

    /** Append a fresh action line (de-duplicated) to the streaming log. */
    private fun appendLogLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        if (seenLogLines.add(trimmed)) {
            _detailLogs.value = _detailLogs.value + trimmed
        }
    }

    suspend fun loadTaskDetail(jobId: String) {
        val c = api ?: return
        try {
            val stFile = c.readFile("jobs/$jobId/status.json")
            if (stFile != null) {
                val j = JSONObject(stFile.first)
                val status = TaskStatus.fromJson(j)
                _detailStatus.value = status

                if (status.runId > 0) {
                    try {
                        val jobs = c.runJobs(status.runId)
                        for (i in 0 until jobs.length()) {
                            val jobObj = jobs.getJSONObject(i)
                            val jobName = jobObj.optString("name")
                            val steps = jobObj.optJSONArray("steps") ?: continue
                            // Emit each step exactly once when it first appears — the log then
                            // reads as an auto-scrolling per-action timeline instead of a full dump.
                            for (s in 0 until steps.length()) {
                                val step = steps.getJSONObject(s)
                                val sName = step.optString("name")
                                val sStatus = step.optString("status", "")
                                val sConclusion = step.optString("conclusion", "")
                                // Only surface a step once it has actually started (or finished).
                                if (sStatus == "queued" && sConclusion.isBlank()) continue
                                val marker = when {
                                    sConclusion == "success" -> "✓"
                                    sConclusion == "failure" -> "✗"
                                    sConclusion == "cancelled" -> "⏹"
                                    sConclusion == "skipped" -> "↷"
                                    sStatus == "in_progress" -> "…"
                                    else -> "•"
                                }
                                val line = "$marker  [$jobName] $sName"
                                appendLogLine(line)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            val reqFile = c.readFile("jobs/$jobId/stage-a-request.json")
            if (reqFile != null) {
                _detailRequest.value = JSONObject(reqFile.first)
            }

            val planFile = c.readFile("jobs/$jobId/production.json")
            _detailPlan.value = planFile?.first
        } catch (_: Exception) {}
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

    fun loadSettings() = viewModelScope.launch {
        val c = api ?: return@launch
        try {
            val details = c.repoDetails()
            val isPriv = details.optBoolean("private", true)

            var voice = Voices.DEFAULT
            c.readFile("branding/tts_settings.json")?.let {
                voice = JSONObject(it.first).optString("voice", Voices.DEFAULT)
            }

            var seriesDef = false
            c.readFile("branding/series_settings.json")?.let {
                seriesDef = JSONObject(it.first).optBoolean("enabled", false)
            }

            var wm = ""
            c.readFile("branding/creator_watermark.json")?.let {
                wm = JSONObject(it.first).optString("watermark", "")
            }

            var zEnabled = false
            var zKey = ""
            var zAccounts = listOf<String>()
            c.readFile("branding/zernio_settings.json")?.let {
                val j = JSONObject(it.first)
                zEnabled = j.optBoolean("enabled", false)
                zKey = j.optString("api_key", "")
                val accs = j.optJSONArray("accounts")
                if (accs != null) {
                    zAccounts = (0 until accs.length()).map { i -> accs.getString(i) }
                }
            }

            _settings.value = AppSettings(
                isPrivate = isPriv,
                narratorVoice = voice,
                seriesDefault = seriesDef,
                watermarkText = wm,
                zernioEnabled = zEnabled,
                zernioApiKey = zKey,
                zernioAccounts = zAccounts
            )
        } catch (_: Exception) {}
    }

    fun toggleRepoVisibility() = viewModelScope.launch {
        val c = api ?: return@launch
        val current = _settings.value.isPrivate
        try {
            c.setVisibility(!current)
            _settings.value = _settings.value.copy(isPrivate = !current)
            toast("Repository visibility set to ${if (!current) "Private" else "Public"}")
        } catch (e: Exception) {
            toast("Failed to change visibility: ${e.message}")
        }
    }

    fun syncFromSource() = viewModelScope.launch {
        val c = api ?: return@launch
        try {
            c.syncFromSource()
            toast("Dispatched sync from motionssalt/clipforge")
        } catch (e: Exception) {
            toast("Sync dispatch error: ${e.message}")
        }
    }

    fun pushUpdateToClones() = viewModelScope.launch {
        val c = api ?: return@launch
        try {
            c.pushUpdateToClones()
            toast("Broadcasted update to all clones")
        } catch (e: Exception) {
            toast("Push update error: ${e.message}")
        }
    }

    fun pushNews(newsText: String) = viewModelScope.launch {
        val c = api ?: return@launch
        try {
            c.pushNews(newsText)
            toast("Broadcasted news message")
        } catch (e: Exception) {
            toast("Broadcast error: ${e.message}")
        }
    }

    fun setNarratorVoice(voiceId: String) = viewModelScope.launch {
        val c = api ?: return@launch
        try {
            val payload = JSONObject().put("voice", voiceId)
            c.putFile("branding/tts_settings.json", payload.toString(2).toByteArray(), "Update TTS voice")
            _settings.value = _settings.value.copy(narratorVoice = voiceId)
            toast("Narrator voice updated")
        } catch (e: Exception) {
            toast("Voice update error: ${e.message}")
        }
    }

    fun setSeriesDefault(enabled: Boolean) = viewModelScope.launch {
        val c = api ?: return@launch
        try {
            val payload = JSONObject().put("enabled", enabled)
            c.putFile("branding/series_settings.json", payload.toString(2).toByteArray(), "Update series default")
            _settings.value = _settings.value.copy(seriesDefault = enabled)
            toast("Series mode default: ${if (enabled) "ON" else "OFF"}")
        } catch (e: Exception) {
            toast("Series default update error: ${e.message}")
        }
    }

    fun setWatermark(text: String) = viewModelScope.launch {
        val c = api ?: return@launch
        try {
            val payload = JSONObject().put("watermark", text.trim())
            c.putFile("branding/creator_watermark.json", payload.toString(2).toByteArray(), "Update watermark")
            _settings.value = _settings.value.copy(watermarkText = text.trim())
            toast("Watermark saved")
        } catch (e: Exception) {
            toast("Watermark error: ${e.message}")
        }
    }

    fun clearWatermark() = setWatermark("")

    fun saveZernioSettings(apiKey: String, enabled: Boolean) = viewModelScope.launch {
        val c = api ?: return@launch
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

    fun clearZernioKey() = viewModelScope.launch {
        val c = api ?: return@launch
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
