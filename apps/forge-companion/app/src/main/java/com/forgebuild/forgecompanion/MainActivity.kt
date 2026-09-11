package com.forgebuild.forgecompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.forgebuild.engine.files.SafeSave
import com.forgebuild.engine.ui.theme.ForgeBuildTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object AppsList : Screen
    data class AppDetail(val slug: String) : Screen
    data class Version(val slug: String) : Screen
}

class MainActivity : ComponentActivity() {
    private lateinit var gitHubService: GitHubService
    private lateinit var appsStore: AppsStore
    private val snackbarHostState = SnackbarHostState()

    // SafeSave download state
    private var pendingDownload: Pair<String, String>? = null

    private val documentPickerLauncher = SafeSave.registerCreateDocument(this, "*/*") { uri ->
        if (uri == null) {
            lifecycleScope.launch {
                snackbarHostState.showSnackbar("Download cancelled")
            }
            return@registerCreateDocument
        }
        val (url, filename) = pendingDownload ?: return@registerCreateDocument
        pendingDownload = null

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    gitHubService.streamDownload(url, out)
                }
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar("Saved $filename")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar("Download failed: ${e.message}")
                }
            }
        }
    }

    private fun startDownload(url: String, filename: String) {
        pendingDownload = url to filename
        documentPickerLauncher.launch(filename)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        gitHubService = GitHubService(this)
        appsStore = AppsStore(this, gitHubService)

        setContent {
            ForgeBuildTheme {
                val coroutineScope = rememberCoroutineScope()
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

                fun showMessage(msg: String) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(msg)
                    }
                }

                // Handle back press
                when (val screen = currentScreen) {
                    is Screen.Home -> {
                        // Default back exits app
                    }
                    is Screen.AppsList -> {
                        BackHandler { currentScreen = Screen.Home }
                    }
                    is Screen.AppDetail -> {
                        BackHandler { currentScreen = Screen.AppsList }
                    }
                    is Screen.Version -> {
                        BackHandler { currentScreen = Screen.AppDetail(screen.slug) }
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize()
                ) { _ ->
                    when (val screen = currentScreen) {
                        is Screen.Home -> HomeScreen(
                            gitHubService = gitHubService,
                            onNavigateToApps = { currentScreen = Screen.AppsList },
                            onShowMessage = ::showMessage
                        )

                        is Screen.AppsList -> AppsListScreen(
                            appsStore = appsStore,
                            onSelectApp = { slug -> currentScreen = Screen.AppDetail(slug) },
                            onSelectInProgressBuild = { slug -> currentScreen = Screen.Version(slug) },
                            onNavigateBack = { currentScreen = Screen.Home },
                            onShowMessage = ::showMessage
                        )

                        is Screen.AppDetail -> AppDetailScreen(
                            slug = screen.slug,
                            gitHubService = gitHubService,
                            onNavigateBack = { currentScreen = Screen.AppsList },
                            onNavigateToVersion = { slug -> currentScreen = Screen.Version(slug) },
                            onDownloadFile = ::startDownload,
                            onShowMessage = ::showMessage
                        )

                        is Screen.Version -> VersionScreen(
                            slug = screen.slug,
                            gitHubService = gitHubService,
                            onNavigateBack = { currentScreen = Screen.AppDetail(screen.slug) },
                            onShowMessage = ::showMessage
                        )
                    }
                }
            }
        }
    }
}
