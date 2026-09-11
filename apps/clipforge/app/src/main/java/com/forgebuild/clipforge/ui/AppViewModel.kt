package com.forgebuild.clipforge.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forgebuild.clipforge.data.CloneConnection
import com.forgebuild.clipforge.data.ForgeSettings
import com.forgebuild.clipforge.data.Job
import com.forgebuild.clipforge.data.ProductionPlan
import com.forgebuild.clipforge.data.SettingsStore
import com.forgebuild.clipforge.github.GitHubApi
import com.forgebuild.clipforge.pipeline.ClipRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class UiState(
    val connection: CloneConnection? = null,
    val connecting: Boolean = false,
    val connectError: String? = null,
    val jobs: List<Job> = emptyList(),
    val jobsLoading: Boolean = false,
    val releases: List<Pair<String, String>> = emptyList(),
    val settings: ForgeSettings = ForgeSettings(),
    val renderStatus: String? = null,
    val renderOutput: String? = null,
    val message: String? = null
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val _state = MutableStateFlow(
        UiState(connection = store.loadConnection(), settings = store.loadSettings(), jobs = store.loadJobs())
    )
    val state: StateFlow<UiState> = _state

    private fun api(): GitHubApi? = _state.value.connection?.let { GitHubApi(it) }

    fun connect(owner: String, repo: String, pat: String) {
        val conn = CloneConnection(owner.trim(), repo.trim(), pat.trim())
        _state.value = _state.value.copy(connecting = true, connectError = null)
        viewModelScope.launch {
            try {
                GitHubApi(conn).verifyConnection()
                store.saveConnection(conn)
                _state.value = _state.value.copy(connection = conn, connecting = false)
                refreshJobs()
            } catch (e: Exception) {
                _state.value = _state.value.copy(connecting = false, connectError = e.message)
            }
        }
    }

    fun disconnect() {
        store.clearConnection()
        _state.value = UiState(settings = _state.value.settings)
    }

    fun refreshJobs() {
        val api = api() ?: return
        _state.value = _state.value.copy(jobsLoading = true)
        viewModelScope.launch {
            try {
                val jobs = api.listJobs()
                store.saveJobs(jobs)
                val releases = try { api.listReleases() } catch (e: Exception) { emptyList() }
                _state.value = _state.value.copy(jobs = jobs, releases = releases, jobsLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(jobsLoading = false, message = "Refresh failed: ${e.message}")
            }
        }
    }

    fun createJob(title: String, source: String) {
        val api = api() ?: return
        val now = System.currentTimeMillis() / 1000
        val job = Job(UUID.randomUUID().toString().take(8), title, source, "queued", now, now)
        viewModelScope.launch {
            try {
                api.createJob(job, null)
                _state.value = _state.value.copy(message = "Job ${job.id} queued — Stage A dispatched")
                refreshJobs()
            } catch (e: Exception) {
                _state.value = _state.value.copy(message = "Create failed: ${e.message}")
            }
        }
    }

    fun submitPlan(job: Job, plan: ProductionPlan) {
        val api = api() ?: return
        viewModelScope.launch {
            try {
                api.submitPlan(job, plan)
                _state.value = _state.value.copy(message = "Plan submitted — Stage B dispatched")
                refreshJobs()
            } catch (e: Exception) {
                _state.value = _state.value.copy(message = "Plan submit failed: ${e.message}")
            }
        }
    }

    fun cancelJob(job: Job) {
        val api = api() ?: return
        viewModelScope.launch {
            try {
                api.cancelJob(job); refreshJobs()
            } catch (e: Exception) {
                _state.value = _state.value.copy(message = "Cancel failed: ${e.message}")
            }
        }
    }

    /** On-device render (Stage B equivalent) from a picked local video. */
    fun renderLocal(uri: Uri, plan: ProductionPlan) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            _state.value = _state.value.copy(renderStatus = "Copying source…", renderOutput = null)
            try {
                val input = File(ctx.cacheDir, "source_${System.currentTimeMillis()}.mp4")
                ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    input.outputStream().use { ins.copyTo(it) }
                } ?: throw IllegalStateException("Cannot open selected video")
                val result = ClipRenderer.render(ctx, input, plan, _state.value.settings) { p ->
                    _state.value = _state.value.copy(renderStatus = p)
                }
                when (result) {
                    is ClipRenderer.Result.Success ->
                        _state.value = _state.value.copy(renderStatus = null, renderOutput = result.output.absolutePath)
                    is ClipRenderer.Result.Failure ->
                        _state.value = _state.value.copy(renderStatus = null, message = "Render failed: ${result.message}")
                }
                input.delete()
            } catch (e: Exception) {
                _state.value = _state.value.copy(renderStatus = null, message = "Render failed: ${e.message}")
            }
        }
    }

    fun saveSettings(s: ForgeSettings) {
        store.saveSettings(s)
        _state.value = _state.value.copy(settings = s)
    }

    fun clearMessage() { _state.value = _state.value.copy(message = null) }
}
