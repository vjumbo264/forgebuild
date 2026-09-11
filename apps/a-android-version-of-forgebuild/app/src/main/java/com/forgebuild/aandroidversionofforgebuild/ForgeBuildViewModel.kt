package com.forgebuild.aandroidversionofforgebuild

import android.app.Application
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ForgeTab {
    APPS, NEW_APP, WORKFLOWS, SETTINGS
}

data class ForgeUiState(
    val currentTab: ForgeTab = ForgeTab.APPS,
    val selectedAppSlug: String? = null,
    val apps: List<ForgeApp> = emptyList(),
    val filteredApps: List<ForgeApp> = emptyList(),
    val workflowRuns: List<WorkflowRun> = emptyList(),
    val isLoading: Boolean = false,
    val isDispatching: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val pat: String = "",
    val isPatConnected: Boolean = false,
    val patUser: String? = null,
    val newAppDescription: String = "",
    val newAppSlug: String = "",
    val generatedContract: String = "",
    val statusMessage: String? = null
)

class ForgeBuildViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("forgebuild_prefs", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(ForgeUiState())
    val uiState: StateFlow<ForgeUiState> = _uiState.asStateFlow()

    init {
        val savedPat = prefs.getString("github_pat", "") ?: ""
        val isConnected = savedPat.isNotBlank()
        _uiState.value = _uiState.value.copy(
            pat = savedPat,
            isPatConnected = isConnected
        )
        loadData()
    }

    fun setTab(tab: ForgeTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab, statusMessage = null)
    }

    fun selectApp(slug: String?) {
        _uiState.value = _uiState.value.copy(selectedAppSlug = slug)
    }

    fun setSearchQuery(query: String) {
        val q = query.trim().lowercase()
        val all = _uiState.value.apps
        val filtered = if (q.isEmpty()) all else all.filter {
            it.slug.lowercase().contains(q) || it.name.lowercase().contains(q)
        }
        _uiState.value = _uiState.value.copy(searchQuery = query, filteredApps = filtered)
    }

    fun setNewAppDescription(desc: String) {
        val currentSlug = _uiState.value.newAppSlug
        val autoSlug = ContractGenerator.slugify(desc)
        val finalSlug = if (currentSlug.isEmpty() || currentSlug == ContractGenerator.slugify(_uiState.value.newAppDescription)) {
            autoSlug
        } else {
            currentSlug
        }
        val contract = if (desc.isNotBlank()) {
            ContractGenerator.promptNewApp(desc, finalSlug)
        } else ""

        _uiState.value = _uiState.value.copy(
            newAppDescription = desc,
            newAppSlug = finalSlug,
            generatedContract = contract
        )
    }

    fun setNewAppSlug(slug: String) {
        val cleanSlug = ContractGenerator.slugify(slug)
        val contract = if (_uiState.value.newAppDescription.isNotBlank()) {
            ContractGenerator.promptNewApp(_uiState.value.newAppDescription, cleanSlug)
        } else ""
        _uiState.value = _uiState.value.copy(newAppSlug = cleanSlug, generatedContract = contract)
    }

    fun savePat(newPat: String) {
        val trimmed = newPat.trim()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val verify = GitHubApiClient.verifyPat(trimmed)
            if (verify.isSuccess) {
                prefs.edit().putString("github_pat", trimmed).apply()
                _uiState.value = _uiState.value.copy(
                    pat = trimmed,
                    isPatConnected = true,
                    patUser = verify.getOrNull(),
                    isLoading = false,
                    statusMessage = "PAT connected as @${verify.getOrNull()}"
                )
                loadData()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "PAT verification failed: ${verify.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun disconnectPat() {
        prefs.edit().remove("github_pat").apply()
        _uiState.value = _uiState.value.copy(
            pat = "",
            isPatConnected = false,
            patUser = null,
            statusMessage = "PAT removed. Browsing public repository data."
        )
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val pat = _uiState.value.pat.ifBlank { null }

            // 1. Fetch app slugs
            val slugsRes = GitHubApiClient.fetchAppSlugs(pat)
            val slugs = slugsRes.getOrDefault(listOf("calcom", "tapcounter", "a-android-version-of-forgebuild"))

            // 2. Fetch all releases
            val releasesRes = GitHubApiClient.fetchReleases(pat)
            val allReleases = releasesRes.getOrDefault(emptyList())

            // 3. Fetch workflows
            val workflowsRes = GitHubApiClient.fetchWorkflowRuns(pat)
            val workflows = workflowsRes.getOrDefault(emptyList())

            // 4. Build ForgeApp models
            val appsList = mutableListOf<ForgeApp>()
            for (slug in slugs) {
                val stateRes = GitHubApiClient.fetchBuildState(slug, pat)
                val buildState = stateRes.getOrNull()

                val promptRes = GitHubApiClient.fetchPromptHistory(slug, pat)
                val promptHistory = promptRes.getOrNull()

                // Filter releases for this slug: prefix is "<slug>-"
                val appReleases = allReleases.filter { it.tagName.startsWith("$slug-") }
                val latest = appReleases.firstOrNull()

                val derivedName = if (buildState?.project != null) {
                    buildState.project.removePrefix("forgebuild-").replace("-", " ")
                        .split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
                } else {
                    slug.replace("-", " ").split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
                }

                appsList.add(
                    ForgeApp(
                        slug = slug,
                        name = derivedName,
                        buildState = buildState,
                        latestRelease = latest,
                        releases = appReleases,
                        promptHistory = promptHistory
                    )
                )
            }

            // Sort: apps with builds/releases or active first
            appsList.sortBy { it.slug }

            val query = _uiState.value.searchQuery.trim().lowercase()
            val filtered = if (query.isEmpty()) appsList else appsList.filter {
                it.slug.lowercase().contains(query) || it.name.lowercase().contains(query)
            }

            _uiState.value = _uiState.value.copy(
                apps = appsList,
                filteredApps = filtered,
                workflowRuns = workflows,
                isLoading = false
            )
        }
    }

    fun dispatchRelease(slug: String, notes: String) {
        val pat = _uiState.value.pat
        if (pat.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "A GitHub PAT with Actions:write is required to trigger builds.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDispatching = true, errorMessage = null)
            val res = GitHubApiClient.dispatchReleaseWorkflow(slug, notes, pat)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isDispatching = false,
                    statusMessage = "Release workflow dispatched for $slug! Monitoring CI runs..."
                )
                loadData()
            } else {
                _uiState.value = _uiState.value.copy(
                    isDispatching = false,
                    errorMessage = "Failed to dispatch: ${res.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun copyToClipboard(context: Context, text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun downloadApk(context: Context, release: AppRelease) {
        val url = release.apkDownloadUrl
        if (url.isNullOrBlank()) {
            Toast.makeText(context, "No APK asset available for this release", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val fileName = release.apkFileName ?: "${release.tagName}.apk"
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("ForgeBuild: $fileName")
                .setDescription("Downloading signed release APK")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "Downloading $fileName to Downloads folder...", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // Fallback: open in browser
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun openInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open URL", Toast.LENGTH_SHORT).show()
        }
    }
}
