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

    // ---------------- startup crash catcher (second-launch bug, round 3) ----------------
    // Installs a default UncaughtExceptionHandler that writes the REAL stack trace to
    // filesDir/last_crash.txt before delegating to the previous handler. On the next
    // successful launch the trace is surfaced to the operator so the actual cause is
    // reported instead of being guessed at again.
    private val crashFile = java.io.File(app.filesDir, "last_crash.txt")

    private val _lastCrash = MutableStateFlow<String?>(null)
    val lastCrash: StateFlow<String?> = _lastCrash

    init {
        // Read & clear any crash recorded by the PREVIOUS launch (this is the only
        // reliable way to see why the app died on second open).
        try {
            if (crashFile.exists()) {
                val trace = crashFile.readText()
                crashFile.delete()
                if (trace.isNotBlank()) _lastCrash.value = trace
            }
        } catch (_: Exception) {}

        try {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    val sw = java.io.StringWriter()
                    throwable.printStackTrace(java.io.PrintWriter(sw))
                    crashFile.writeText("CRASH ${java.time.Instant.now()} on ${thread.name}\n$sw")
                } catch (_: Exception) {}
                previous?.uncaughtException(thread, throwable)
                    ?: kotlin.system.exitProcess(2)
            }
        } catch (_: Exception) {}
    }

    fun dismissCrashReport() { _lastCrash.value = null }

    private val _login = MutableStateFlow<CredentialStore.CloneCredentials?>(null)
    val login: StateFlow<CredentialStore.CloneCredentials?> = _login

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    private val _snack = MutableStateFlow<String?>(null)
    val snack: StateFlow<String?> = _snack

    private val _hasPromptedStorage = MutableStateFlow(false)
    val hasPromptedStorage: StateFlow<Boolean> = _hasPromptedStorage

    /**
     * Session-10 fix #6: every notification uses the phone's NATIVE Android toast —
     * no custom in-app toast/snackbar UI. _snack/clearSnack are kept only so any
     * leftover Compose snackbar collector neutralizes instead of re-rendering in-app.
     */
    fun toast(msg: String) {
        _snack.value = null
        try {
            android.widget.Toast.makeText(app.applicationContext, msg, android.widget.Toast.LENGTH_LONG).show()
        } catch (_: Exception) {}
    }
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
                api = GitHubClient(it.pat, it.owner, it.repo, app.applicationContext)
            }
        } catch (_: Exception) {
            _login.value = null
            api = null
        }
        _ready.value = true
        // SECOND-OPEN CRASH FIX (session-06/task-41): NO network or cache work here.
        // The recovered last-working ForgeBuild app (apps/clipforge @ 65891f6) never
        // refreshed in init — its launch path is purely: load persisted credentials
        // (guarded) -> set ready. Every data load is cache-first from the UI layer
        // (onTasksOpen/onMusicOpen), so a second open with saved state never touches
        // the network or does any throwing work before the UI is composed.
    }

    // ---------------- auth / clones ----------------
    fun connectExisting(pat: String, repoSlug: String) = viewModelScope.launch {
        val parts = repoSlug.trim().trim('/').split("/")
        if (parts.size != 2) {
            toast("Repo must be in format owner/repository")
            return@launch
        }
        withBusy("connect") {
            val c = GitHubClient(pat.trim(), parts[0], parts[1], app.applicationContext)
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

    /** Live copy progress for the onboarding "Create New Clone" flow (fix #2):
     *  stage + done/total straight from the one-time copy workflow's status file. */
    data class CloneCopyProgress(val stage: String, val done: Int, val total: Int)

    private val _cloneCopyProgress = MutableStateFlow<CloneCopyProgress?>(null)
    val cloneCopyProgress: StateFlow<CloneCopyProgress?> = _cloneCopyProgress

    // Session-10 fix #3: explicit success/failure confirmation for clone creation.
    private val _cloneSuccess = MutableStateFlow<String?>(null)
    val cloneSuccess: StateFlow<String?> = _cloneSuccess
    private val _cloneFailure = MutableStateFlow<String?>(null)
    val cloneFailure: StateFlow<String?> = _cloneFailure
    fun dismissCloneOutcome() { _cloneSuccess.value = null; _cloneFailure.value = null }

    /**
     * Fix #2: faithful port of the bot's real clone-creation flow
     * (beginShadowCloneCreation -> pollShadowCloneJob -> finalizeShadowClone in
     * bot/src/github.js — see data/ShadowClone.kt for the bug-45/46/47/51/63 notes).
     * The old implementation here was a simplified guess (empty auto_init repo +
     * 4 branding seeds, no source copy, no polling) — replaced wholesale.
     *
     * "Create it and you're in": on success the app logs into the new clone
     * immediately (same credentials object the connect flow stores) — there is
     * no separate manual login step afterwards.
     */
    /**
     * Create a new shadow clone. Session-10 fix #4: an EMPTY [repoName] is the
     * bot-sanctioned "auto-generate a name" option (bot bug-45: blank requestedName
     * -> clipforge-clone-<suffix>) — ShadowClone.begin already implements it.
     * Fix #3: ShadowClone.begin is the faithful beginShadowCloneCreation ->
     * pollShadowCloneJob -> finalizeShadowClone port, so this call returns ONLY after
     * the copy poll reached a finished state and finalize verified the tree —
     * success is never assumed from the initial create call. On genuine completion a
     * clear success confirmation is shown and the session logs in automatically.
     */
    fun createClone(pat: String, repoName: String) = viewModelScope.launch {
        val token = pat.trim()
        if (token.isBlank()) { toast("Enter a GitHub PAT first"); return@launch }
        _cloneSuccess.value = null
        _cloneFailure.value = null
        _cloneProgress.value = "Creating private repo…"
        withBusy("create_clone") {
            try {
                val result = ShadowClone.begin(
                    pat = token,
                    requestedName = repoName,
                    onProgress = { stage, done, total ->
                        // Same stage names the bot reports: source -> copy -> finalize.
                        _cloneProgress.value = when (stage) {
                            ShadowClone.STAGE_SOURCE -> "Reading ClipForge source tree…"
                            ShadowClone.STAGE_COPY ->
                                if (total > 0) "Copying source files… $done/$total"
                                else "Starting the copy workflow…"
                            ShadowClone.STAGE_FINALIZE ->
                                if (total > 0) "Finalizing clone… $done/$total"
                                else "Finalizing clone…"
                            else -> "Working…"
                        }
                        _cloneCopyProgress.value = CloneCopyProgress(stage, done, total)
                    }
                )
                // Auto-login into the freshly created clone ("create it and you're in").
                // ShadowClone verified the default branch holds the copied tree, so the
                // client is ready for normal use immediately.
                val credentials = CredentialStore.CloneCredentials(
                    token, result.login, result.name, result.login
                )
                creds.save(credentials)
                api = GitHubClient(token, result.login, result.name, app.applicationContext)
                _login.value = credentials
                _cloneSuccess.value = "Clone ${result.repo} created and verified (${result.copiedFiles} files copied). You are logged in — no further setup needed."
                toast("✅ Clone ${result.repo} created — logged in automatically.")
                refreshAll()
            } catch (e: ShadowClone.CloneException) {
                _cloneFailure.value = e.message ?: "Clone creation failed"
                toast(e.message ?: "Clone creation failed")
            } catch (e: Exception) {
                _cloneFailure.value = "Clone creation failed: ${e.message}"
                toast("Clone creation failed: ${e.message}")
            } finally {
                _cloneProgress.value = null
                _cloneCopyProgress.value = null
            }
        }
    }

    fun signOut() {
        creds.clear()
        api = null
        _login.value = null
        // Wipe the on-disk cache-first stores so a different account never sees
        // stale data, then reload (empty) state from the now-absent cache files.
        try { java.io.File(app.filesDir, "tasks.json").delete() } catch (_: Exception) {}
        try { java.io.File(app.filesDir, "music.json").delete() } catch (_: Exception) {}
        taskStore.loadFromCache()
        musicStore.loadFromCache()
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

    // ---------------- tasks & state (cache-first, recovered working architecture) ----------------
    // Recovered from the last-working ForgeBuild app (apps/clipforge @ 65891f6 — the
    // build the operator confirms did NOT crash on second open): the task list is a
    // CacheFirstStore<TaskStatus> backed by filesDir/tasks.json. Opening the screen
    // renders INSTANTLY from the on-disk cache (loadFromCache is synchronous and
    // network-free), then a background refresh reconciles. The from-scratch
    // network-only reload on every open (refreshAll-in-init) is gone for good.
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
        val entries = c.listDir("jobs")
        val list = mutableListOf<TaskStatus>()
        for (i in 0 until entries.length()) {
            val item = entries.getJSONObject(i)
            if (item.optString("type") != "dir") continue
            val jobId = item.getString("name")
            val statusFile = c.readFile("jobs/$jobId/status.json") ?: continue
            runCatching { list.add(TaskStatus.fromJson(JSONObject(statusFile.first))) }
        }
        return list.sortedByDescending { it.createdAt }
    }

    val activeTasks: StateFlow<List<TaskStatus>> = tasks.map { list ->
        list.filter { !it.isComplete }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val completedTasks: StateFlow<List<TaskStatus>> = tasks.map { list ->
        list.filter { it.isComplete }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Fix #1 — the New Video screen loads the music library AND the current default
     *  track directly when it opens. It never relies on the Music settings screen having
     *  been visited first (the cache files persist across opens, so a previously-cached
     *  library still renders instantly, then both refresh in the background). */
    fun onNewTaskOpen() {
        musicStore.loadFromCache()
        refreshMusic()
        loadSettings()
    }

    /** Fix #4 — computed next-part state for the currently-open completed series part. */
    data class NextPartInfo(
        val continuation: SeriesLogic.Continuation,
        val nextId: String,
        val exists: Boolean
    )
    private val _nextPart = MutableStateFlow<NextPartInfo?>(null)
    val nextPart: StateFlow<NextPartInfo?> = _nextPart

    /** GitHub Actions-style log expansion (fix #3): null = follow the running step. */
    private var expandedStepKey: String? = null
    private var autoCollapseDone = false

    fun isStepExpanded(step: LogStep): Boolean =
        expandedStepKey?.let { it == step.key }
            ?: (!autoCollapseDone && step.level == LogLevel.RUNNING)

    fun toggleStepExpanded(key: String) {
        if (expandedStepKey == key) {
            expandedStepKey = null // collapse: return to auto-follow of the running step
        } else {
            expandedStepKey = key
            autoCollapseDone = true // manual pick wins; the running step no longer auto-expands
        }
    }

    /** Fix #2 — stream the bot's pre-rendered sample assets/tts-previews/<voiceId>.mp3
     *  (same file the bot's set:voice:prev handler serves via getRepositoryFileBytes).
     *  Uses the shared AudioPreview cache/pipeline — never re-synthesizes a sample. */
    fun previewVoice(voiceId: String) {
        val ctx = app.applicationContext
        val c = api ?: run { toast("Not connected"); return }
        com.forgebuild.clipforgeandroid.ui.AudioPreview.toggle(ctx, audioPreviewUrl("assets/tts-previews/$voiceId.mp3"), c.pat)
    }

    /** Fix #4 — compute the next-part state for a completed series part exactly like the
     *  bot's startNextSeriesPart prologue: manualSeriesContinuation + nextPartJobId +
     *  the duplicate-dispatch guard (a stage-a-request.json OR status.json existing for
     *  the next id means "Part N already exists", which is exactly why deleting that
     *  next part makes the button reappear in the bot). */
    fun refreshNextPart(jobId: String) = viewModelScope.launch {
        val c = api ?: return@launch
        _nextPart.value = null
        try {
            val status = c.readFile("jobs/$jobId/status.json")?.let { runCatching { JSONObject(it.first) }.getOrNull() }
            val request = c.readFile("jobs/$jobId/stage-a-request.json")?.let { runCatching { JSONObject(it.first) }.getOrNull() }
            val plan = c.readFile("jobs/$jobId/production.json")?.let { runCatching { JSONObject(it.first) }.getOrNull() }
            val cont = SeriesLogic.manualSeriesContinuation(status, request, plan) ?: return@launch
            val nextId = runCatching { SeriesLogic.nextPartJobId(cont.seriesId, cont.part) }.getOrNull() ?: return@launch
            val exists = (c.readFile("jobs/$nextId/stage-a-request.json") != null) ||
                (c.readFile("jobs/$nextId/status.json") != null)
            _nextPart.value = NextPartInfo(cont, nextId, exists)
        } catch (_: Exception) {}
    }

    /** Fix #4 — full port of bot startNextSeriesPart: duplicate guard again at tap
     *  time, buildSeriesContext over prior parts' summaries, nextPartRequestBody
     *  (mode manual, bug-64 source_job_id fallback), schema-complete queued status,
     *  then dispatch stage-a.yml with a fresh code_ref. */
    fun startNextSeriesPart(jobId: String) = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("start_next") {
            try {
                val status = c.readFile("jobs/$jobId/status.json")?.let { JSONObject(it.first) }
                val request = c.readFile("jobs/$jobId/stage-a-request.json")?.let { JSONObject(it.first) }
                val plan = c.readFile("jobs/$jobId/production.json")?.let { JSONObject(it.first) }
                val cont = SeriesLogic.manualSeriesContinuation(status, request, plan)
                if (cont == null) {
                    toast("That completed task has no next Series Mode part to start (not a manual series part, or it was the final part).")
                    return@withBusy
                }
                val nextId = SeriesLogic.nextPartJobId(cont.seriesId, cont.part)
                val exists = (c.readFile("jobs/$nextId/stage-a-request.json") != null) ||
                    (c.readFile("jobs/$nextId/status.json") != null)
                if (exists) {
                    _nextPart.value = NextPartInfo(cont, nextId, true)
                    toast("Part ${cont.part} of this series already exists as task $nextId.")
                    return@withBusy
                }

                // §11 continuity context: "Prior events (Part N): <summary>" over this series.
                val summaries = mutableListOf<Pair<Int, String>>()
                val jobs = c.listDir("jobs")
                for (i in 0 until jobs.length()) {
                    val item = jobs.getJSONObject(i)
                    if (item.optString("type") != "dir") continue
                    val otherId = item.getString("name")
                    val req = c.readFile("jobs/$otherId/stage-a-request.json")?.let {
                        runCatching { JSONObject(it.first) }.getOrNull()
                    } ?: continue
                    val rs = req.optJSONObject("series") ?: continue
                    if (!rs.optBoolean("enabled", false) || rs.optString("series_id") != cont.seriesId) continue
                    val otherPlan = c.readFile("jobs/$otherId/production.json")?.let {
                        runCatching { JSONObject(it.first) }.getOrNull()
                    } ?: continue
                    val values = SeriesLogic.extractPlanSeries(otherPlan)
                    val part = values.optInt("part", -1)
                    val summary = values.optString("summary").trim()
                    if (part >= 1 && summary.isNotEmpty()) summaries.add(part to summary)
                }
                val context = SeriesLogic.buildSeriesContext(summaries)

                val nowSec = System.currentTimeMillis() / 1000
                val body = SeriesLogic.nextPartRequestBody(request!!, cont, context, jobId)
                val requestJson = JSONObject(body.toString())
                    .put("version", 2)
                    .put("job_id", nextId)
                    .put("saved_at_epoch", nowSec)

                val statusJson = JSONObject()
                    .put("version", 2)
                    .put("job_id", nextId)
                    .put("mode", "manual")
                    .put("series", JSONObject()
                        .put("enabled", true)
                        .put("series_id", cont.seriesId)
                        .put("part", cont.part)
                        .put("start_seconds", cont.startSeconds)
                        .put("is_final", false))
                    .put("state", "queued")
                    .put("message", "Series part ${cont.part} queued — Stage A dispatched.")
                    .put("created_at_epoch", nowSec)
                    .put("updated_at_epoch", nowSec)
                    .put("expires_at_epoch", nowSec + 172800)
                    .put("release_tag", "clipforge-$nextId")
                    .put("release_url", "https://github.com/${c.owner}/${c.repo}/releases/tag/clipforge-$nextId")
                    .put("assets", JSONObject())
                    .put("run", JSONObject()
                        .put("workflow_run_id", 0)
                        .put("workflow_run_url", "")
                        .put("code_ref", ""))
                    .put("publishing", JSONObject()
                        .put("status", "not_requested")
                        .put("posts", JSONArray())
                        .put("idempotency_key", ""))

                c.putFile("jobs/$nextId/stage-a-request.json", requestJson.toString(2).toByteArray(Charsets.UTF_8), "clipforge: stage-a request for series part ${cont.part} ($nextId)")
                c.putFile("jobs/$nextId/status.json", statusJson.toString(2).toByteArray(Charsets.UTF_8), "clipforge: queue series part ${cont.part} ($nextId)")
                val codeRef = c.defaultBranchSha()
                c.dispatchWorkflow("stage-a.yml", mapOf("job_id" to nextId, "code_ref" to codeRef))

                toast("Series Part ${cont.part} dispatched as $nextId")
                refreshTasks()
                refreshNextPart(jobId)
            } catch (e: Exception) {
                toast("Could not start next part: ${e.message}")
            }
        }
    }

    /** Called when the Tasks screen opens: instant cached render, then background refresh. */
    fun onTasksOpen() {
        taskStore.loadFromCache()
        viewModelScope.launch { taskStore.refresh() }
    }

    /** Manual refresh (pull-to-refresh / refresh button). Never throws. */
    fun refreshTasks() = viewModelScope.launch { taskStore.refresh() }

    /**
     * Hold-to-delete an entire series (operator fix #3, session-13): removes every
     * repo job directory identifiable as belonging to [seriesId] (every task the
     * Series tab grouped under it, plus the Super Series anchor job when present),
     * then refreshes so the series card disappears. Uses the same repo deleteFile
     * primitive the app already uses; errors are surfaced via the native toast.
     */
    fun deleteSeries(seriesId: String) = viewModelScope.launch {
        val c = api ?: run { toast("Not connected"); return@launch }
        val partJobIds = tasks.value
            .filter { it.seriesEnabled && it.seriesId == seriesId }
            .map { it.jobId }
            .toMutableSet()
        // Super Series anchor job: not a "part" task, so it isn't in partJobIds.
        // Find it by scanning jobs/*/super-plan.json for this series' queue record.
        try {
            val jobsDir = c.listDir("jobs")
            for (i in 0 until jobsDir.length()) {
                val d = jobsDir.getJSONObject(i)
                if (d.optString("type") != "dir") continue
                val jid = d.optString("name")
                val sp = c.readFile("jobs/$jid/super-plan.json") ?: continue
                if (sp.first.contains("\"series_id\": \"$seriesId\"")) partJobIds.add(jid)
            }
        } catch (_: Exception) {}
        var failed = 0
        for (jobId in partJobIds) {
            try { deleteJobDir(c, jobId) } catch (e: Exception) { failed++ }
        }
        taskStore.refresh()
        if (failed == 0) toast("Series \"$seriesId\" deleted") else toast("Deleted with $failed error(s)")
    }

    /** Best-effort removal of every file under jobs/<jobId>/ via the git tree. */
    private suspend fun deleteJobDir(c: com.forgebuild.clipforgeandroid.data.GitHubClient, jobId: String) {
        val entries = c.listDir("jobs/$jobId")
        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            if (e.optString("type") == "file") {
                c.deleteFile("jobs/$jobId/" + e.optString("name"), e.optString("sha"), "clipforge: delete series part ($jobId)")
            }
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
            toast("Deleted ${jobIds.size} task(s)")
            taskStore.refresh()
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
        isSuperSeries: Boolean = false,
        torrentBytes: ByteArray?,
        onCreated: (String) -> Unit
    ) = viewModelScope.launch {
        val c = api ?: run { toast("Not connected"); return@launch }
        // Fix #1 — bot wizard guard (index.js wz:series handler): a series id may only
        // start at Part 1; continuing an EXISTING series is exclusively the
        // "Start Next Part" flow (startNextSeriesPart), which derives the part number
        // and start_seconds from the previous part's production.json.
        if (isSeries && !seriesId.isNullOrBlank()) {
            val existing = taskStore.state.value.firstOrNull { it.seriesId == seriesId.trim() }
            if (existing != null) {
                toast("Series '${seriesId.trim()}' already exists (Part ${existing.part}) — open its latest completed part and tap 'Start Next Part' to continue it.")
                return@launch
            }
        }
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

            // Fix #1 — bot wizard parity (wizard.js musicKeyboard / describeMusic):
            // three real choices: an explicit library track, "no music", and "saved
            // default". "Default / None" selected WITH a default configured in Settings
            // means music.source = "default": Stage B / startNextSeriesPart resolve it
            // to branding/music_default.json's library_track_path at render time
            // (resolveMusicRef / pipeline music.py). No default configured means the
            // operator genuinely picked silence -> "none".
            val musicJson = when {
                !selectedMusicPath.isNullOrBlank() ->
                    JSONObject().put("ref", selectedMusicPath).put("source", "explicit_library")
                _defaultMusic.value != null ->
                    JSONObject().put("ref", "").put("source", "default")
                else ->
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
            // Session-10 fix #9: super_series rides the series block exactly like the
            // bot's wizard (series.super_series) — the bot's upload handler branches
            // on it and its prompt template switches to SUPER_SERIES_DIRECTIVE.
            if (isSuperSeries && isSeries) seriesJson.put("super_series", true)

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
            toast(if (isSuperSeries && isSeries) "Super Series task $jobId created — the agent will return ONE super-plan for the whole series." else "Task $jobId created")
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

    /** Color-coding level for a pipeline step (maps to status colors in the UI). */
    enum class LogLevel { PENDING, RUNNING, SUCCESS, FAILURE, SKIPPED, CANCELLED, INFO }

    /** One rendered log row. [key] is stable across polls so Compose can animate updates. */
    data class LogLine(val key: String, val text: String, val level: LogLevel)

    /** One pipeline step's status lines (its "detail lines") for the expanded view. */
    data class LogDetail(val text: String, val level: LogLevel)

    /**
     * GitHub Actions-style collapsible step (operator fix #3): header (name + status
     * + duration) with its detail lines collapsed by default. [key] is stable across
     * polls so expansion state survives the 2.5 s refresh cycle.
     */
    data class LogStep(
        val key: String,
        val name: String,
        val level: LogLevel,
        val durationText: String,
        val details: List<LogDetail>
    )

    /** Null-safe JSON string: returns "" for missing or JSON null (never the literal "null"). */
    private fun strOr(o: JSONObject, key: String): String =
        if (o.isNull(key)) "" else o.optString(key, "")

    private val _detailLogs = MutableStateFlow<List<LogStep>>(emptyList())
    val detailLogs: StateFlow<List<LogStep>> = _detailLogs

    /** A single video candidate discovered inside a torrent by Stage A. */
    data class TorrentFileOption(val index: Int, val name: String, val sizeBytes: Long)

    private val _torrentFiles = MutableStateFlow<List<TorrentFileOption>>(emptyList())
    val torrentFiles: StateFlow<List<TorrentFileOption>> = _torrentFiles

    private val _torrentSubmitting = MutableStateFlow(false)
    val torrentSubmitting: StateFlow<Boolean> = _torrentSubmitting

    private var pollJob: Job? = null

    fun startPollingTask(jobId: String) {
        pollJob?.cancel()
        // Fresh log buffer + expansion state for a fresh task view.
        _detailLogs.value = emptyList()
        expandedStepKey = null
        autoCollapseDone = false
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
        expandedStepKey = null
        autoCollapseDone = false
        _nextPart.value = null
        _downloadedVideoFor.value = null
        _playVideoUri.value = null
    }

    /** Human-readable step duration, GitHub Actions style ("34s", "1m 12s"). */
    private fun fmtStepDuration(startedAt: String, completedAt: String): String {
        if (startedAt.isBlank()) return ""
        val startMs = runCatching { java.time.Instant.parse(startedAt).toEpochMilli() }.getOrNull() ?: return ""
        val endMs = if (completedAt.isNotBlank())
            runCatching { java.time.Instant.parse(completedAt).toEpochMilli() }.getOrNull()
        else System.currentTimeMillis()
        endMs ?: return ""
        val secs = ((endMs - startMs) / 1000).coerceAtLeast(0)
        return if (secs >= 60) "${secs / 60}m ${secs % 60}s" else "${secs}s"
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

                // Rebuild the GitHub Actions-style collapsible step list on every poll
                // (fix #3): one LogStep per workflow step — header (name + status +
                // duration), detail lines collapsed by default; the currently-running
                // step is auto-expanded by the UI.
                val newSteps = mutableListOf<LogStep>()
                val statusLevel = when (status.state) {
                    "error" -> LogLevel.FAILURE
                    "complete" -> LogLevel.SUCCESS
                    "cancelled" -> LogLevel.CANCELLED
                    "queued" -> LogLevel.PENDING
                    "awaiting_torrent_selection", "awaiting_plan" -> LogLevel.SKIPPED
                    else -> LogLevel.RUNNING
                }
                newSteps.add(
                    LogStep(
                        key = "pipeline-status",
                        name = "Pipeline status",
                        level = statusLevel,
                        durationText = "",
                        details = listOf(
                            LogDetail("state: ${status.state}", statusLevel),
                            LogDetail("message: ${status.message.ifBlank { "—" }}", LogLevel.INFO)
                        )
                    )
                )

                if (status.runId > 0) {
                    try {
                        val jobs = c.runJobs(status.runId)
                        for (i in 0 until jobs.length()) {
                            val jobObj = jobs.getJSONObject(i)
                            val jobName = strOr(jobObj, "name").ifBlank { "job" }
                            val jStatus = strOr(jobObj, "status")
                            val jConclusion = strOr(jobObj, "conclusion")
                            val jobLevel = logLevelFor(jStatus, jConclusion)
                            newSteps.add(
                                LogStep(
                                    key = "job-$i",
                                    name = "Job: $jobName",
                                    level = jobLevel,
                                    durationText = fmtStepDuration(
                                        strOr(jobObj, "started_at"),
                                        strOr(jobObj, "completed_at")
                                    ),
                                    details = listOf(
                                        LogDetail("status: $jStatus", LogLevel.INFO),
                                        LogDetail("conclusion: ${jConclusion.ifBlank { "—" }}", LogLevel.INFO)
                                    )
                                )
                            )
                            val steps = jobObj.optJSONArray("steps") ?: continue
                            for (s in 0 until steps.length()) {
                                val step = steps.getJSONObject(s)
                                val sName = strOr(step, "name").ifBlank { "step" }
                                val sStatus = strOr(step, "status")
                                val sConclusion = strOr(step, "conclusion")
                                val level = logLevelFor(sStatus, sConclusion)
                                newSteps.add(
                                    LogStep(
                                        key = "job-$i-step-$s",
                                        name = sName,
                                        level = level,
                                        durationText = fmtStepDuration(
                                            strOr(step, "started_at"),
                                            strOr(step, "completed_at")
                                        ),
                                        details = listOf(
                                            LogDetail("status: $sStatus", LogLevel.INFO),
                                            LogDetail("conclusion: ${sConclusion.ifBlank { "—" }}", LogLevel.INFO)
                                        )
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }
                _detailLogs.value = newSteps
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

    // ---------------- restart / cancel stage controls (fix #4, bot index.js parity) ----

    /** Port of bot resolveMusicRef (index.js — mirrors pipeline/plan/music.py for
     *  manual Stage B dispatches): none -> ""; explicit_library/job_upload ->
     *  path:<ref>; default -> branding/music_default.json's library_track_path. */
    private suspend fun resolveMusicRef(c: GitHubClient, jobId: String): String {
        val request = try {
            c.readFile("jobs/$jobId/stage-a-request.json")?.let { JSONObject(it.first) }
        } catch (_: Exception) { null }
        val music = request?.optJSONObject("music") ?: JSONObject()
        return when (music.optString("source", "none")) {
            "explicit_library", "job_upload" ->
                music.optString("ref").takeIf { it.isNotBlank() }?.let { "path:$it" } ?: ""
            "default" -> try {
                c.readFile("branding/music_default.json")?.let {
                    JSONObject(it.first).optString("library_track_path")
                        .takeIf { p -> p.isNotBlank() }?.let { p -> "path:$p" } ?: ""
                } ?: ""
            } catch (_: Exception) { "" }
            else -> "" // 'none' and anything unrecognized
        }
    }

    /** Bot restartStageA (index.js): dispatch stage-a.yml with a FRESH code_ref —
     *  §8.5 restart-correctness, a failed job never re-runs stale code. */
    fun restartStageA(jobId: String) = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("restart_a") {
            try {
                val codeRef = c.defaultBranchSha()
                c.dispatchWorkflow("stage-a.yml", mapOf("job_id" to jobId, "code_ref" to codeRef))
                toast("Restarting Stage A for $jobId…")
                refreshTasks()
                loadTaskDetail(jobId)
            } catch (e: Exception) {
                toast("Failed to restart Stage A: ${e.message}")
            }
        }
    }

    /** Bot restartStageB (index.js): production.json guard FIRST — the exact bot
     *  block message when absent — then dispatch stage-b.yml with production_ref
     *  path:jobs/<id>/production.json, music_ref (resolveMusicRef) and a fresh code_ref. */
    fun restartStageB(jobId: String) = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("restart_b") {
            try {
                val plan = try { c.readFile("jobs/$jobId/production.json") } catch (_: Exception) { null }
                if (plan == null) {
                    toast("Task $jobId has no production.json yet — upload one (or restart Stage A) before Stage B can run.")
                    return@withBusy
                }
                val musicRef = resolveMusicRef(c, jobId)
                val codeRef = c.defaultBranchSha()
                c.dispatchWorkflow(
                    "stage-b.yml",
                    mapOf(
                        "job_id" to jobId,
                        "production_ref" to "path:jobs/$jobId/production.json",
                        "music_ref" to musicRef,
                        "code_ref" to codeRef
                    )
                )
                toast("Restarting Stage B for $jobId…")
                refreshTasks()
                loadTaskDetail(jobId)
            } catch (e: Exception) {
                toast("Failed to restart Stage B: ${e.message}")
            }
        }
    }

    /**
     * Bot cancelStageB (index.js), BOTH branches reproduced:
     *  - a recorded workflow run id -> cancel via the GitHub Actions API only
     *    (the pipeline records the cancelled state itself);
     *  - no run id yet (queued) -> cancel LOCALLY by merging state:'cancelled' into
     *    the EXISTING status.json — never a minimal rewrite: version, series, TTL,
     *    assets and publishing fields must survive, exactly like the bot's
     *    mergeStatus. Terminal states are never re-cancelled.
     * Replaces the old cancelTask, which rewrote status.json as a 4-field object
     * (destroying every other field) and cancelled + wrote locally in ALL cases.
     */
    fun cancelRunningStage(jobId: String) = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("cancel_stage") {
            try {
                // Fresh read exactly like the bot's cancelStageB (readStatus at action
                // time): the polled in-memory status can be up to one poll (2.5s) stale —
                // a run id that just landed must route this to the Actions API branch,
                // and a just-recorded terminal state must not be re-cancelled.
                val freshFile = try { c.readFile("jobs/$jobId/status.json") } catch (_: Exception) { null }
                val freshObj = freshFile?.let { try { JSONObject(it.first) } catch (_: Exception) { null } }
                val runId = freshObj?.optJSONObject("run")?.optLong("workflow_run_id", 0)
                    ?: (_detailStatus.value?.runId ?: 0L)
                if (runId > 0) {
                    // Actively running (or queued-with-run) workflow: Actions API cancel.
                    c.cancelRun(runId)
                    toast("Cancelling the running workflow…")
                } else {
                    if (freshObj != null) {
                        val currentState = freshObj.optString("state")
                        if (currentState !in setOf("complete", "error", "cancelled")) {
                            freshObj.put("state", "cancelled")
                            freshObj.put(
                                "message",
                                if (currentState == "stage_b_queued")
                                    "Cancelled before the Stage B run started." // bot verbatim
                                else
                                    "Cancelled before the run started."
                            )
                            freshObj.put("updated_at_epoch", nowEpoch())
                            c.putFile(
                                "jobs/$jobId/status.json",
                                freshObj.toString(2).toByteArray(Charsets.UTF_8),
                                "clipforge: cancel job $jobId"
                            )
                        }
                    }
                    toast("Task cancelled")
                }
                refreshTasks()
                loadTaskDetail(jobId)
            } catch (e: Exception) {
                toast("Failed to cancel: ${e.message}")
            }
        }
    }

    // ---------------- video download with progress, total size & speed ----------------
    data class DownloadState(
        val isDownloading: Boolean = false,
        val progress: Float = 0f,
        val speedText: String = "",
        val downloadedText: String = "",
        val totalText: String = "",
        val error: String? = null
    )
    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState: StateFlow<DownloadState> = _downloadState

    /** Fix #5 — the video downloaded for [jobId] earlier in this session/app install. */
    private val _downloadedVideoFor = MutableStateFlow<org.json.JSONObject?>(null)
    val downloadedVideoFor: StateFlow<org.json.JSONObject?> = _downloadedVideoFor

    /** Fix #5 — non-null while the in-app player dialog is open. */
    private val _playVideoUri = MutableStateFlow<Pair<String, String>?>(null)
    val playVideoUri: StateFlow<Pair<String, String>?> = _playVideoUri

    fun downloadedVideoFor(jobId: String): org.json.JSONObject? =
        DownloadsRegistry.forJob(app.applicationContext, jobId)

    fun playDownloaded(jobId: String, title: String) {
        val rec = DownloadsRegistry.forJob(app.applicationContext, jobId) ?: return
        _playVideoUri.value = rec.optString("uri") to title
    }

    fun playVideoFromRegistry(rec: org.json.JSONObject) {
        _playVideoUri.value = rec.optString("uri") to rec.optString("name")
    }

    fun dismissVideoPlayer() { _playVideoUri.value = null }

    fun seriesDownloads(seriesId: String): List<org.json.JSONObject> =
        DownloadsRegistry.forSeries(app.applicationContext, seriesId)

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1L shl 20 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    fun saveVideoToMovies(tag: String, fileName: String = "clipforge-video.mp4", jobId: String = "", seriesId: String = "") = viewModelScope.launch {
        val c = api ?: run { toast("Not connected"); return@launch }
        _downloadState.value = DownloadState(isDownloading = true, progress = 0.05f, speedText = "Locating asset…")
        withContext(Dispatchers.IO) {
            try {
                val rel = c.releaseByTag(tag) ?: throw Exception("Release tag $tag not found")
                val assets = rel.getJSONArray("assets")
                var assetApiUrl: String? = null
                var assetName = fileName
                var assetSizeBytes = 0L
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.getString("name")
                    if (name.endsWith(".mp4", ignoreCase = true)) {
                        assetApiUrl = a.getString("url")
                        assetName = name
                        assetSizeBytes = a.optLong("size", 0L)
                        break
                    }
                }
                if (assetApiUrl == null) throw Exception("No .mp4 asset found in release $tag")

                val resp = c.openAssetStream(assetApiUrl)
                val body = resp.body ?: throw Exception("Empty response body from asset stream")
                // Fix #6 — total size is known up front: prefer the release asset's
                // recorded size (GitHub Release assets always carry it), fall back to
                // the response's Content-Length.
                val totalBytes = if (assetSizeBytes > 0) assetSizeBytes else body.contentLength()

                val context = app.applicationContext
                var savedUriString = ""
                val outputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, assetName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ClipForge")
                    }
                    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                        ?: throw Exception("Could not create MediaStore entry")
                    savedUriString = uri.toString()
                    resolver.openOutputStream(uri) ?: throw Exception("Could not open MediaStore output stream")
                } else {
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ClipForge")
                    if (!dir.exists()) dir.mkdirs()
                    val targetFile = File(dir, assetName)
                    savedUriString = targetFile.absolutePath
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
                                // Fix #6 — always show downloaded + total alongside speed.
                                _downloadState.value = DownloadState(
                                    isDownloading = true,
                                    progress = frac,
                                    speedText = speedStr,
                                    downloadedText = formatBytes(totalRead),
                                    totalText = if (totalBytes > 0) formatBytes(totalBytes) else "unknown size"
                                )
                                lastTime = now
                                lastBytes = totalRead
                            }
                        }
                    }
                }

                _downloadState.value = DownloadState(
                    isDownloading = false,
                    progress = 1f,
                    speedText = "Saved to Movies/ClipForge/$assetName",
                    downloadedText = formatBytes(totalBytes),
                    totalText = if (totalBytes > 0) formatBytes(totalBytes) else "unknown size"
                )
                // Fix #5 — register the download against the job AND its series id so
                // revisiting the task (or the series view) offers Play instead of
                // Download, and the file is findable later.
                if (jobId.isNotBlank()) {
                    DownloadsRegistry.register(
                        context, jobId, seriesId,
                        savedUriString, assetName,
                        if (totalBytes > 0) totalBytes else 0L
                    )
                    _downloadedVideoFor.value = DownloadsRegistry.forJob(context, jobId)
                }
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

    // ---------------- music library (cache-first, recovered working architecture) ----------------
    private val musicStore = CacheFirstStore<MusicTrack>(
        app, "music.json",
        toJson = { it.toJson() },
        fromJson = { MusicTrack.fromJson(it) },
        fetchRemote = {
            val c = api ?: return@CacheFirstStore emptyList()
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
            tracks
        },
        keyOf = { it.path },
    )
    val music: StateFlow<List<MusicTrack>> = musicStore.state
    val musicRefreshing: StateFlow<Boolean> = musicStore.refreshing

    private val _defaultMusic = MutableStateFlow<String?>(null)
    val defaultMusic: StateFlow<String?> = _defaultMusic

    // ---------------- storage permission (session-10 fix #7) ----------------
    /** The ACTUAL current storage-permission grant state, read live from the OS via
     *  ContextCompat.checkSelfPermission at the moment it is needed — never the
     *  persisted "did we ask before" flag that desyncs from the real grant and caused
     *  repeat prompts (including just returning to the app). */
    fun isStorageGrantedNow(): Boolean {
        val ctx = app.applicationContext
        val perm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
            android.Manifest.permission.READ_MEDIA_VIDEO
        else
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        return androidx.core.content.ContextCompat.checkSelfPermission(ctx, perm) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // ---------------- diagnostic log (session-10 fix #8, session-11 task-75) ----------------
    // Export now goes through the Storage Access Framework picker in SettingsScreen
    // (SafeSave / ACTION_CREATE_DOCUMENT) — the operator picks a reachable destination
    // such as Documents/ClipForge. The old app-private filesDir export was removed.
    fun clearDiagLog() { DiagLog.clear(app.applicationContext); toast("Diagnostic log cleared") }

    // ---------------- Super Series (feature-01, session-10 fix #9) ----------------

    /** Bot settings_super.js readSuperSeriesSettings:
     *  branding/super_series_settings.json {version,enabled,updated_at_epoch}, or null. */
    suspend fun readSuperSeriesSettings(): JSONObject? = try {
        api?.readFile(SuperSeries.SETTINGS_PATH)?.let { JSONObject(it.first) }
    } catch (_: Exception) { null }

    /** Bot settings_super.js saveSuperSeriesSettings — same document shape. */
    fun setSuperSeriesDefault(enabled: Boolean) = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("save_super_series_default") {
            try {
                val payload = JSONObject()
                    .put("version", 1)
                    .put("enabled", enabled == true)
                    .put("updated_at_epoch", nowEpoch())
                c.putFile(SuperSeries.SETTINGS_PATH, payload.toString(2).toByteArray(), "clipforge: update Super Series setting")
                _settings.value = _settings.value.copy(superSeriesDefault = enabled)
                toast("Super Series default: ${if (enabled) "ON" else "OFF"}")
            } catch (e: Exception) {
                toast("Super Series default update error: ${e.message}")
            }
        }
    }

    /** Bot toggle dependency (wizard.js: the two toggles cannot desync) — turning
     *  Series Mode OFF also force-writes Super Series OFF. */
    fun setSeriesDefaultWithDependency(enabled: Boolean) {
        setSeriesDefault(enabled)
        if (!enabled && _settings.value.superSeriesDefault) setSuperSeriesDefault(false)
    }

    /**
     * Submit a Super Series super-plan for the anchor job — app-side port of bot
     * handleSuperPlanUploadMessage: validate the WHOLE document with the bot's
     * validator (identical accept/reject + error strings), verify the job is still
     * awaiting_plan, verify the super-plan's series_id matches the anchor task's,
     * write jobs/<anchor>/super-plan.json FIRST (the durable record the bot's
     * per-minute queue sweep resumes from), then merge the status to stage_b_queued
     * with the bot's acceptance message. Parts are then queued and dispatched
     * SEQUENTIALLY by the bot's queue controller — no manual per-part button exists
     * or is needed for Super Series parts.
     */
    fun submitSuperPlan(jobId: String, text: String, onDone: () -> Unit) = viewModelScope.launch {
        val c = api ?: return@launch
        val parsed = SuperSeries.parseAndValidateSuperPlan(text)
        if (parsed.errors.isNotEmpty()) {
            toast("That super-plan is not valid (${parsed.errors.size} problem${if (parsed.errors.size == 1) "" else "s"}): ${parsed.errors.first()}")
            return@launch
        }
        val document = parsed.document ?: return@launch
        withBusy("submit_super_plan") {
            try {
                _upload.value = UploadProgress("Validating super-plan…", 0.2f)
                val st = try { c.readFile("jobs/$jobId/status.json")?.let { JSONObject(it.first) } } catch (_: Exception) { null }
                if (st != null && st.optString("state") != "awaiting_plan") {
                    toast("Task $jobId is in state ${st.optString("state")}, not awaiting_plan. Refresh the task screen first.")
                    return@withBusy
                }
                val reqFile = try { c.readFile("jobs/$jobId/stage-a-request.json") } catch (_: Exception) { null }
                val reqSeries = reqFile?.let { runCatching { JSONObject(it.first).optJSONObject("series") }.getOrNull() } ?: JSONObject()
                val seriesId = reqSeries.optString("series_id", "")
                if (seriesId.isBlank() || document.optString("series_id") != seriesId) {
                    toast("The super-plan's series_id is ${document.optString("series_id").ifBlank { "(missing)" }} but this task expects $seriesId. Copy the exact id from the agent prompt and try again.")
                    return@withBusy
                }
                _upload.value = UploadProgress("Writing super-plan…", 0.5f)
                c.putFile(
                    "jobs/$jobId/super-plan.json",
                    (SuperSeries.buildSuperState(jobId, seriesId, document).toString(2) + "\n").toByteArray(Charsets.UTF_8),
                    "clipforge: accept super series plan ($jobId)"
                )
                val next = (st ?: JSONObject())
                    .put("state", "stage_b_queued")
                    .put("message", "Super Series plan accepted — ${document.getJSONArray("parts").length()} parts queued; part 1 dispatched.")
                    .put("updated_at_epoch", nowEpoch())
                c.putFile("jobs/$jobId/status.json", (next.toString(2) + "\n").toByteArray(Charsets.UTF_8), "clipforge: super plan accepted for job $jobId")
                _upload.value = UploadProgress("Super Series plan accepted", 1f)
                toast("⚡ Super Series plan accepted — ${document.getJSONArray("parts").length()} parts validated. Parts dispatch automatically, one at a time.")
                refreshTasks()
                loadTaskDetail(jobId)
                onDone()
            } catch (e: Exception) {
                toast("Super-plan submission failed: ${e.message}")
            } finally {
                _upload.value = null
            }
        }
    }

    // ---- Super Series queue views (bot superQueueAdvance — series overview) ----
    private val _superQueues = MutableStateFlow<Map<String, SuperSeries.QueueView>>(emptyMap())
    val superQueues: StateFlow<Map<String, SuperSeries.QueueView>> = _superQueues

    private val _superQueue = MutableStateFlow<SuperSeries.QueueView?>(null)
    val superQueue: StateFlow<SuperSeries.QueueView?> = _superQueue

    /** Read every anchor's durable super-plan record and evaluate each queue exactly
     *  like the bot's sweep (first not-complete spawned part decides). */
    fun refreshSuperQueues() = viewModelScope.launch {
        val c = api ?: return@launch
        val out = mutableMapOf<String, SuperSeries.QueueView>()
        for (task in taskStore.state.value) {
            if (!task.seriesEnabled || task.seriesId.isBlank()) continue
            val stateFile = try { c.readFile("jobs/${task.jobId}/super-plan.json") } catch (_: Exception) { null } ?: continue
            val state = try { JSONObject(stateFile.first) } catch (_: Exception) { continue }
            val statusMap = mutableMapOf<String, String>()
            state.optJSONArray("spawned")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val id = arr.optJSONObject(i)?.optString("job_id") ?: continue
                    statusMap[id] = try { c.readFile("jobs/$id/status.json")?.let { JSONObject(it.first).optString("state") } ?: "" } catch (_: Exception) { "" }
                }
            }
            out[task.seriesId] = SuperSeries.evaluateQueue(state) { id -> statusMap[id] }
        }
        _superQueues.value = out
    }

    fun loadSuperQueue(seriesId: String) = viewModelScope.launch {
        val c = api ?: return@launch
        _superQueue.value = null
        val anchor = taskStore.state.value.firstOrNull { it.seriesEnabled && it.seriesId == seriesId } ?: return@launch
        val stateFile = try { c.readFile("jobs/${anchor.jobId}/super-plan.json") } catch (_: Exception) { null } ?: return@launch
        val state = try { JSONObject(stateFile.first) } catch (_: Exception) { return@launch }
        val statusMap = mutableMapOf<String, String>()
        state.optJSONArray("spawned")?.let { arr ->
            for (i in 0 until arr.length()) {
                val id = arr.optJSONObject(i)?.optString("job_id") ?: continue
                statusMap[id] = try { c.readFile("jobs/$id/status.json")?.let { JSONObject(it.first).optString("state") } ?: "" } catch (_: Exception) { "" }
            }
        }
        _superQueue.value = SuperSeries.evaluateQueue(state) { id -> statusMap[id] }
    }

    /** Called when the Music screen opens: instant cached render, then background refresh. */
    fun onMusicOpen() {
        musicStore.loadFromCache()
        refreshMusic()
    }

    fun refreshMusic() = viewModelScope.launch {
        musicStore.refresh()
        // Default-track marker is a tiny separate document, not part of the cached list.
        val c = api ?: return@launch
        try {
            val def = c.readFile("branding/music_default.json")
            if (def != null) {
                val j = JSONObject(def.first)
                _defaultMusic.value = j.optString("library_track_path").ifBlank { j.optString("path", "") }.ifBlank { null }
            }
        } catch (_: Exception) {}
    }

    /**
     * Set/clear the default music track. Bot parity: bot/src/github.js
     * saveMusicDefault writes {version, library_track_path, updated_at_epoch, note};
     * clearing deletes the document entirely (clearMusicDefaultIfTrack).
     */
    fun setDefaultMusic(path: String?) = viewModelScope.launch {
        val c = api ?: return@launch
        try {
            if (path == null) {
                val existing = c.readFile("branding/music_default.json")
                if (existing != null) c.deleteFile("branding/music_default.json", existing.second, "clipforge: clear default background music")
                _defaultMusic.value = null
                toast("Default track cleared")
            } else {
                val payload = JSONObject()
                    .put("version", 1)
                    .put("library_track_path", path)
                    .put("updated_at_epoch", nowEpoch())
                    .put("note", "Last explicitly selected audio-library track. One-off job uploads are never stored as the default.")
                c.putFile("branding/music_default.json", payload.toString(2).toByteArray(), "clipforge: save default background music")
                _defaultMusic.value = path
                toast("Default track updated")
            }
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
            toast("Deleted ${tracks.size} track(s)")
            musicStore.refresh()
        } catch (e: Exception) {
            toast("Failed to delete music: ${e.message}")
        }
    }

    fun audioPreviewUrl(path: String): String {
        val c = api ?: return ""
        return "https://api.github.com/repos/${c.owner}/${c.repo}/contents/$path?ref=main"
    }

    // ---------------- full settings (bot-parity shapes) ----------------
    /**
     * One connected Zernio social account — mirrors an entry in
     * branding/zernio_accounts.json {accounts:[{id,platform,username,
     * displayName,isActive,enabled,needsReconnection}]}. A separate store from
     * zernio_settings.json; the bot reads it independently.
     */
    data class ZernioAccount(
        val id: String,
        val platform: String,
        val username: String,
        val displayName: String,
        val isActive: Boolean,
        val enabled: Boolean,
        val needsReconnection: Boolean
    ) {
        val available: Boolean get() = isActive && enabled && !needsReconnection
        val label: String get() = displayName.ifBlank { username.ifBlank { id } }
    }

    data class AppSettings(
        val isPrivate: Boolean = true,
        val narratorVoice: String = Voices.DEFAULT,
        val seriesDefault: Boolean = false,
        val watermarkText: String = "",           // bot creator_watermark.json {creator_name}
        // Zernio — three separate stores (bot: loadZernioConfig):
        //   branding/zernio_settings.json {version,enabled,auto_publish,automatic_mode,
        //     target_accounts{platform->[ids]},smart_schedule{...}}
        //   branding/zernio_accounts.json {accounts:[...]}
        //   sealed Actions secret ZERNIO_API_KEY (existence only — value never readable)
        val zernioEnabled: Boolean = false,
        val zernioAutoPublish: Boolean = false,
        val zernioAutomaticMode: String = "smart_schedule",
        val zernioKeyConfigured: Boolean = false,
        val zernioAccounts: List<ZernioAccount> = emptyList(),
        // Session-10 fix #9: branding/super_series_settings.json {enabled}
        val superSeriesDefault: Boolean = false
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

    /** Epoch seconds — bot writes updated_at_epoch on every settings document. */
    private fun nowEpoch(): Long = System.currentTimeMillis() / 1000

    /**
     * Load every settings section from the clone in the exact shapes the bot
     * persists (verified against the real stored data in the operator's repo):
     *   narrator  = branding/tts_settings.json {voice}
     *   series    = branding/series_settings.json {enabled}
     *   watermark = branding/creator_watermark.json {creator_name}   (NOT 'watermark')
     *   music     = branding/music_default.json {library_track_path} (NOT 'path')
     *   zernio    = zernio_settings.json {enabled,auto_publish,automatic_mode,...}
     *             + zernio_accounts.json {accounts:[...]}
     *             + sealed secret ZERNIO_API_KEY existence check
     */
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

            // Session-10 fix #9: Super Series default — its own stored setting at
            // branding/super_series_settings.json (bot settings_super.js), read with
            // the same pattern as the Series Mode setting right above.
            var superDef = false
            try {
                c.readFile(SuperSeries.SETTINGS_PATH)?.let {
                    superDef = JSONObject(it.first).optBoolean("enabled", false)
                }
            } catch (_: Exception) {}

            // bot reads creator_watermark.json {creator_name}; tolerate the app's
            // legacy {watermark} so an older clone file still renders.
            var wm = ""
            try {
                c.readFile("branding/creator_watermark.json")?.let {
                    val j = JSONObject(it.first)
                    wm = j.optString("creator_name").ifBlank { j.optString("watermark", "") }
                }
            } catch (_: Exception) {}

            // Music default — bot reads library_track_path; tolerate legacy 'path'.
            try {
                c.readFile("branding/music_default.json")?.let {
                    val j = JSONObject(it.first)
                    _defaultMusic.value = j.optString("library_track_path")
                        .ifBlank { j.optString("path", "") }.ifBlank { null }
                }
            } catch (_: Exception) {}

            // Zernio — three separate stores, read independently like loadZernioConfig.
            var zEnabled = false
            var zAutoPublish = false
            var zMode = "smart_schedule"
            try {
                c.readFile("branding/zernio_settings.json")?.let {
                    val j = JSONObject(it.first)
                    zEnabled = j.optBoolean("enabled", false)
                    zAutoPublish = j.optBoolean("auto_publish", false)
                    zMode = j.optString("automatic_mode", "smart_schedule")
                }
            } catch (_: Exception) {}

            var zAccounts = listOf<ZernioAccount>()
            try {
                c.readFile("branding/zernio_accounts.json")?.let {
                    zAccounts = parseZernioAccounts(JSONObject(it.first))
                }
            } catch (_: Exception) {}

            // The API key is a sealed Actions secret — we can only check existence,
            // never read the value back (GitHub never returns plaintext).
            var zKeyConfigured = false
            try {
                zKeyConfigured = c.actionsSecretExists("ZERNIO_API_KEY")
            } catch (_: Exception) {}

            _settings.value = AppSettings(
                isPrivate = isPriv,
                narratorVoice = voice,
                seriesDefault = seriesDef,
                watermarkText = wm,
                zernioEnabled = zEnabled,
                zernioAutoPublish = zAutoPublish,
                zernioAutomaticMode = zMode,
                zernioKeyConfigured = zKeyConfigured,
                zernioAccounts = zAccounts,
                superSeriesDefault = superDef
            )
            _settingsLoaded.value = true
        } catch (e: Exception) {
            toast("Failed to load settings: ${e.message}")
        } finally {
            _settingsLoading.value = false
        }
    }

    /** Parse branding/zernio_accounts.json {accounts:[{...}]} into typed accounts. */
    private fun parseZernioAccounts(j: JSONObject): List<ZernioAccount> {
        val arr = j.optJSONArray("accounts") ?: return emptyList()
        val out = mutableListOf<ZernioAccount>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                ZernioAccount(
                    id = o.optString("id"),
                    platform = o.optString("platform"),
                    username = o.optString("username"),
                    displayName = o.optString("displayName"),
                    isActive = o.optBoolean("isActive", true),
                    enabled = o.optBoolean("enabled", true),
                    needsReconnection = o.optBoolean("needsReconnection", false)
                )
            )
        }
        return out
    }

    /**
     * Refresh the Zernio connected-accounts list from the clone repo. Bot parity:
     * accounts live in branding/zernio_accounts.json (a SEPARATE store from
     * zernio_settings.json — the app previously read a non-existent 'accounts'
     * array out of zernio_settings.json, which is why nothing ever showed up).
     */
    fun refreshZernioAccounts() = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("zernio_refresh") {
            try {
                var list = listOf<ZernioAccount>()
                c.readFile("branding/zernio_accounts.json")?.let {
                    list = parseZernioAccounts(JSONObject(it.first))
                }
                // Also re-check the sealed key + settings so the whole section is fresh.
                var enabled = _settings.value.zernioEnabled
                var keyConfigured = _settings.value.zernioKeyConfigured
                try {
                    c.readFile("branding/zernio_settings.json")?.let {
                        enabled = JSONObject(it.first).optBoolean("enabled", enabled)
                    }
                } catch (_: Exception) {}
                try { keyConfigured = c.actionsSecretExists("ZERNIO_API_KEY") } catch (_: Exception) {}
                _settings.value = _settings.value.copy(
                    zernioAccounts = list,
                    zernioEnabled = enabled,
                    zernioKeyConfigured = keyConfigured
                )
                toast(if (list.isEmpty()) "No connected accounts found" else "Loaded ${list.size} connected account(s)")
            } catch (e: Exception) {
                toast("Failed to refresh Zernio accounts: ${e.message}")
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

    // Bot parity: bot/src/github.js saveNarrator writes the full Edge-TTS doc.
    fun setNarratorVoice(voiceId: String) = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("save_narrator") {
            try {
                val label = Voices.ALL.firstOrNull { it.id == voiceId }?.label ?: voiceId
                val payload = JSONObject()
                    .put("version", 1)
                    .put("engine", "edge-tts")
                    .put("voice", voiceId)
                    .put("voice_label", label)
                    .put("rate", "+20%")
                    .put("volume", "+0%")
                    .put("pitch", "+0Hz")
                    .put("updated_at_epoch", nowEpoch())
                c.putFile("branding/tts_settings.json", payload.toString(2).toByteArray(), "clipforge: save Edge TTS narrator")
                _settings.value = _settings.value.copy(narratorVoice = voiceId)
                toast("Narrator voice updated")
            } catch (e: Exception) {
                toast("Voice update error: ${e.message}")
            }
        }
    }

    // Bot parity: bot/src/github.js saveSeriesSettings {version,enabled,updated_at_epoch}.
    fun setSeriesDefault(enabled: Boolean) = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("save_series_default") {
            try {
                val payload = JSONObject()
                    .put("version", 1)
                    .put("enabled", enabled)
                    .put("updated_at_epoch", nowEpoch())
                c.putFile("branding/series_settings.json", payload.toString(2).toByteArray(), "clipforge: update Series Mode setting")
                _settings.value = _settings.value.copy(seriesDefault = enabled)
                toast("Series mode default: ${if (enabled) "ON" else "OFF"}")
            } catch (e: Exception) {
                toast("Series default update error: ${e.message}")
            }
        }
    }

    // Bot parity: bot/src/github.js saveWatermark {version, creator_name, updated_at_epoch}.
    fun setWatermark(text: String) = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("save_watermark") {
            try {
                val payload = JSONObject()
                    .put("version", 1)
                    .put("creator_name", text.trim())
                    .put("updated_at_epoch", nowEpoch())
                c.putFile("branding/creator_watermark.json", payload.toString(2).toByteArray(), "clipforge: update creator watermark")
                _settings.value = _settings.value.copy(watermarkText = text.trim())
                toast(if (text.isBlank()) "Watermark cleared" else "Watermark saved")
            } catch (e: Exception) {
                toast("Watermark error: ${e.message}")
            }
        }
    }

    fun clearWatermark() = setWatermark("")

    /**
     * Save Zernio settings. Bot parity: the enabled/auto_publish/mode flags live in
     * branding/zernio_settings.json (full canonical doc via zernioSettingsOrDefault),
     * while the API key — when the caller supplies a new one — is sealed and stored as
     * the GitHub Actions secret ZERNIO_API_KEY, NEVER as plaintext in the settings file.
     */
    fun saveZernioSettings(apiKey: String, enabled: Boolean) = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("save_zernio") {
            try {
                // Read-modify-write the existing settings doc so schedule/targets survive.
                val existing = try {
                    c.readFile("branding/zernio_settings.json")?.let { JSONObject(it.first) }
                } catch (_: Exception) { null }
                val doc = existing ?: defaultZernioSettings()
                doc.put("version", 1)
                    .put("enabled", enabled)
                    .put("updated_at_epoch", nowEpoch())
                c.putFile("branding/zernio_settings.json", doc.toString(2).toByteArray(), "clipforge: update Zernio publishing settings")

                // Only seal + store a key when the user typed a new one (non-blank).
                val newKey = apiKey.trim()
                var keyConfigured = _settings.value.zernioKeyConfigured
                if (newKey.isNotEmpty()) {
                    c.setZernioSecret(newKey)
                    keyConfigured = true
                }
                _settings.value = _settings.value.copy(zernioEnabled = enabled, zernioKeyConfigured = keyConfigured)
                toast("Zernio settings saved")
            } catch (e: Exception) {
                toast("Zernio save error: ${e.message}")
            }
        }
    }

    /** Canonical empty Zernio settings doc — matches bot zernioSettingsOrDefault(). */
    private fun defaultZernioSettings(): JSONObject = JSONObject()
        .put("version", 1)
        .put("enabled", false)
        .put("auto_publish", false)
        .put("automatic_mode", "smart_schedule")
        .put("target_accounts", JSONObject()
            .put("tiktok", JSONArray())
            .put("youtube", JSONArray())
            .put("instagram", JSONArray()))
        .put("smart_schedule", JSONObject()
            .put("timezone", "UTC")
            .put("interval_hours", 24)
            .put("preferred_time", "19:30")
            .put("queue_depth", 4)
            .put("start_mode", "next_available")
            .put("custom_start", ""))

    // Bot parity: clear = delete the sealed ZERNIO_API_KEY secret + disable settings.
    fun clearZernioKey() = viewModelScope.launch {
        val c = api ?: return@launch
        withBusy("clear_zernio") {
            try {
                try { c.deleteActionsSecret("ZERNIO_API_KEY") } catch (_: Exception) {}
                val existing = try {
                    c.readFile("branding/zernio_settings.json")?.let { JSONObject(it.first) }
                } catch (_: Exception) { null }
                val doc = (existing ?: defaultZernioSettings())
                    .put("enabled", false)
                    .put("updated_at_epoch", nowEpoch())
                c.putFile("branding/zernio_settings.json", doc.toString(2).toByteArray(), "clipforge: disable Zernio publishing")
                _settings.value = _settings.value.copy(
                    zernioEnabled = false,
                    zernioKeyConfigured = false
                )
                toast("Zernio API key removed")
            } catch (e: Exception) {
                toast("Failed to clear Zernio key: ${e.message}")
            }
        }
    }
}
