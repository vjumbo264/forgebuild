package com.forgebuild.forgecompanion

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.icons.EngineIcons
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    slug: String,
    gitHubService: GitHubService,
    onNavigateBack: () -> Unit,
    onNavigateToVersion: (String) -> Unit,
    onDownloadFile: (url: String, filename: String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val releasesStore = remember(slug) { ReleasesStore(context, slug, gitHubService) }
    val releases by releasesStore.state.collectAsState()
    val isRefreshingReleases by releasesStore.refreshing.collectAsState()
    val releasesLastError by releasesStore.lastError.collectAsState()

    var buildState by remember { mutableStateOf<BuildStateData?>(null) }
    var promptHistory by remember { mutableStateOf<String?>(null) }
    var isLoadingBuildState by remember { mutableStateOf(true) }

    // Selected tab: 0 = Releases, 1 = Extend Prompt, 2 = Prompt History
    var selectedTab by remember { mutableStateOf(0) }

    // Extend prompt state
    var extendInstruction by remember { mutableStateOf("") }
    var generatedExtendPrompt by remember { mutableStateOf<String?>(null) }

    // Delete version dialog state
    var versionToDelete by remember { mutableStateOf<ReleaseInfo?>(null) }
    var isDeletingVersion by remember { mutableStateOf(false) }

    // Delete entire app state
    var showDeleteAppDialog by remember { mutableStateOf(false) }
    var confirmSlugInput by remember { mutableStateOf("") }
    var isDeletingApp by remember { mutableStateOf(false) }

    // Load initial cached data + background refresh
    LaunchedEffect(slug) {
        releasesStore.loadFromCache()
        releasesStore.refresh()
        isLoadingBuildState = true
        buildState = gitHubService.fetchBuildState(slug)
        promptHistory = gitHubService.fetchPromptHistory(slug)
        isLoadingBuildState = false
    }

    // Confirmation dialog for deleting a single release
    if (versionToDelete != null) {
        val ver = versionToDelete!!
        AlertDialog(
            onDismissRequest = { if (!isDeletingVersion) versionToDelete = null },
            icon = { Icon(EngineIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${ver.tagName}?") },
            text = {
                Text("This will delete the release and git tag ${ver.tagName} from vjumbo264/forgebuild. The source tree will remain.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeletingVersion = true
                        coroutineScope.launch {
                            try {
                                gitHubService.deleteRelease(ver.id)
                                gitHubService.deleteGitTag(ver.tagName)
                                releasesStore.refresh()
                                onShowMessage("Deleted ${ver.tagName}")
                            } catch (e: Exception) {
                                onShowMessage("Delete failed: ${e.message}")
                            } finally {
                                isDeletingVersion = false
                                versionToDelete = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !isDeletingVersion
                ) {
                    Text(if (isDeletingVersion) "Deleting..." else "Delete Version")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { versionToDelete = null },
                    enabled = !isDeletingVersion
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation dialog for deleting the entire app
    if (showDeleteAppDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingApp) showDeleteAppDialog = false },
            icon = { Icon(EngineIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Entire App \"$slug\"?") },
            text = {
                Column {
                    Text(
                        "This will permanently delete:\n" +
                        "• All releases tagged $slug-*\n" +
                        "• PROMPT_HISTORY/$slug.md\n" +
                        "• The entire apps/$slug/ directory\n\n" +
                        "To confirm, type \"$slug\" below:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmSlugInput,
                        onValueChange = { confirmSlugInput = it },
                        placeholder = { Text(slug) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeletingApp = true
                        coroutineScope.launch {
                            try {
                                gitHubService.deleteAppTree(slug)
                                onShowMessage("App \"$slug\" was completely deleted from repo")
                                showDeleteAppDialog = false
                                onNavigateBack()
                            } catch (e: Exception) {
                                onShowMessage("Failed to delete app: ${e.message}")
                            } finally {
                                isDeletingApp = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = confirmSlugInput.trim() == slug && !isDeletingApp
                ) {
                    Text(if (isDeletingApp) "Deleting everything..." else "Delete App Permanently")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAppDialog = false },
                    enabled = !isDeletingApp
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(slug, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(EngineIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                releasesStore.refresh()
                                buildState = gitHubService.fetchBuildState(slug)
                                promptHistory = gitHubService.fetchPromptHistory(slug)
                                onShowMessage("Refreshed app details")
                            }
                        }
                    ) {
                        Icon(EngineIcons.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isRefreshingReleases) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (releasesLastError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Releases refresh notice: $releasesLastError",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // In-progress banner if active build
            val activeBuild = buildState
            if (activeBuild != null && !activeBuild.buildComplete) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Build in progress (${activeBuild.currentTask ?: "active"})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Tap to view BUILD_STATE and resume prompt",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Button(
                            onClick = { onNavigateToVersion(slug) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Resume →")
                        }
                    }
                }
            }

            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Releases (${releases.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Extend (Type 2)") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("History") }
                )
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    0 -> ReleasesTab(
                        releases = releases,
                        slug = slug,
                        onDownload = onDownloadFile,
                        onDeleteVersion = { versionToDelete = it },
                        onShowDeleteApp = {
                            confirmSlugInput = ""
                            showDeleteAppDialog = true
                        }
                    )
                    1 -> ExtendTab(
                        slug = slug,
                        latestRelease = releases.firstOrNull()?.tagName,
                        instruction = extendInstruction,
                        onInstructionChange = { extendInstruction = it },
                        generatedPrompt = generatedExtendPrompt,
                        onGenerate = {
                            if (extendInstruction.isNotBlank()) {
                                generatedExtendPrompt = PromptGenerator.promptExtend(
                                    slug = slug,
                                    instruction = extendInstruction.trim(),
                                    latest = releases.firstOrNull()?.tagName
                                )
                                onShowMessage("Extend prompt generated!")
                            }
                        },
                        onCopyPrompt = { prompt ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Extend Prompt", prompt))
                            onShowMessage("Prompt copied to clipboard!")
                        }
                    )
                    2 -> HistoryTab(
                        history = promptHistory
                    )
                }
            }
        }
    }
}

@Composable
private fun ReleasesTab(
    releases: List<ReleaseInfo>,
    slug: String,
    onDownload: (url: String, filename: String) -> Unit,
    onDeleteVersion: (ReleaseInfo) -> Unit,
    onShowDeleteApp: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        if (releases.isEmpty()) {
            Text(
                text = "No releases found tagged $slug-v* yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Once an AI session dispatches release.yml, signed APKs and manifests will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            releases.forEach { rel ->
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rel.tagName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = rel.publishedAt.take(10),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        if (rel.body.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = rel.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))

                        // Download actions
                        Text(
                            text = "Downloads (SafeSave):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))

                        // Look for APK asset
                        val apkAsset = rel.assets.find { it.name.endsWith(".apk") }
                        val manifestAsset = rel.assets.find { it.name.contains("manifest") }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (apkAsset != null) {
                                val mb = String.format("%.1f MB", apkAsset.size / (1024f * 1024f))
                                FilledTonalButton(
                                    onClick = { onDownload(apkAsset.downloadUrl, apkAsset.name) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(EngineIcons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("APK ($mb)")
                                }
                            }

                            if (manifestAsset != null) {
                                OutlinedButton(
                                    onClick = { onDownload(manifestAsset.downloadUrl, manifestAsset.name) }
                                ) {
                                    Text("Manifest")
                                }
                            }

                            if (rel.zipballUrl.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { onDownload(rel.zipballUrl, "$slug-${rel.tagName}-source.zip") }
                                ) {
                                    Text("Source (zip)")
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Delete version button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { onDeleteVersion(rel) },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(EngineIcons.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Delete ${rel.tagName}")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Danger Zone: Delete entire app
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Danger Zone",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Permanently remove this app ($slug), all releases, git tags, and its code tree from the repo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onShowDeleteApp,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(EngineIcons.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Entire App")
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ExtendTab(
    slug: String,
    latestRelease: String?,
    instruction: String,
    onInstructionChange: (String) -> Unit,
    generatedPrompt: String?,
    onGenerate: () -> Unit,
    onCopyPrompt: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Extend or Update $slug",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Enter your update instructions. This generates the Type-2 prompt for a new AI session to iterate on version ${latestRelease ?: "v1"}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = instruction,
            onValueChange = onInstructionChange,
            label = { Text("New instruction from operator") },
            placeholder = { Text("e.g. Add dark mode toggle, persist settings to SharedPreferences, and add audio notifications...") },
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onGenerate,
            enabled = instruction.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(EngineIcons.Bolt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Generate Type-2 Extend Prompt")
        }

        if (generatedPrompt != null) {
            Spacer(Modifier.height(20.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Type-2 Extend Prompt",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(onClick = { onCopyPrompt(generatedPrompt) }) {
                            Icon(EngineIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = generatedPrompt,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun HistoryTab(history: String?) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Prompt History",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Appended record of operator instructions stored in PROMPT_HISTORY/",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        if (history.isNullOrBlank()) {
            Text(
                text = "No prompt history found for this app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = history,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
