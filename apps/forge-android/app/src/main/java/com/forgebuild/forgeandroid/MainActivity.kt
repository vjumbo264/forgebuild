package com.forgebuild.forgeandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.forgebuild.engine.ui.theme.ForgeBuildTheme
import com.forgebuild.forgeandroid.data.AppsRepository
import com.forgebuild.forgeandroid.data.DetailRepository
import com.forgebuild.forgeandroid.data.GitHubApi
import com.forgebuild.forgeandroid.data.PatStore
import com.forgebuild.forgeandroid.ui.AppDetailScreen
import com.forgebuild.forgeandroid.ui.AppsListScreen
import com.forgebuild.forgeandroid.ui.NewAppScreen
import kotlinx.coroutines.launch

/** In-memory navigation state (single-activity, no navigation dependency — lean by default). */
sealed interface Screen {
    data object Home : Screen
    data class Detail(val slug: String) : Screen
    data object NewApp : Screen
}

/**
 * ForgeBuild for Android — a native client for the ForgeBuild dashboard
 * (vjumbo264/forgebuild): browse apps, releases/APKs, build state and prompt
 * history, and generate new-app build contracts. Built on the Engine's
 * Material 3 Expressive theme and CacheFirstStore data layer.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ForgeBuildTheme { ForgeBuildNav() }
        }
    }
}

@Composable
fun ForgeBuildNav() {
    val context = LocalContext.current
    val api = remember { GitHubApi { PatStore.get(context) } }
    val appsRepo = remember { AppsRepository(context, api) }
    val detailRepos = remember { mutableStateMapOf<String, DetailRepository>() }
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var patTick by remember { mutableIntStateOf(0) }

    val apps by appsRepo.store.state.collectAsState()
    val appsRefreshing by appsRepo.store.refreshing.collectAsState()
    val appsError by appsRepo.store.lastError.collectAsState()

    // Cache-first: render instantly from disk, then reconcile against the GitHub API.
    LaunchedEffect(Unit) {
        appsRepo.store.loadFromCache()
        appsRepo.store.refresh()
    }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (val s = screen) {
        Screen.Home -> AppsListScreen(
            apps = apps,
            refreshing = appsRefreshing,
            lastError = appsError,
            hasPat = remember(patTick) { PatStore.get(context) != null },
            onOpen = { screen = Screen.Detail(it) },
            onNewApp = { screen = Screen.NewApp },
            onRefresh = { scope.launch { appsRepo.store.refresh() } },
            onSavePat = {
                PatStore.set(context, it)
                patTick++
                scope.launch { appsRepo.store.refresh() }
            }
        )
        is Screen.Detail -> {
            val repo = detailRepos.getOrPut(s.slug) { DetailRepository(context, s.slug, api) }
            val details by repo.store.state.collectAsState()
            val refreshing by repo.store.refreshing.collectAsState()
            val error by repo.store.lastError.collectAsState()
            LaunchedEffect(s.slug, patTick) {
                repo.store.loadFromCache()
                repo.store.refresh()
            }
            AppDetailScreen(
                detail = details.firstOrNull(),
                refreshing = refreshing,
                lastError = error,
                onBack = { screen = Screen.Home },
                onRefresh = { scope.launch { repo.store.refresh() } }
            )
        }
        Screen.NewApp -> NewAppScreen(onBack = { screen = Screen.Home })
    }
}
