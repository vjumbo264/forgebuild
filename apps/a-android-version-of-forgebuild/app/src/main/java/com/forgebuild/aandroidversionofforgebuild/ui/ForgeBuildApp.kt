package com.forgebuild.aandroidversionofforgebuild.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.aandroidversionofforgebuild.ForgeBuildViewModel
import com.forgebuild.aandroidversionofforgebuild.ForgeTab
import com.forgebuild.engine.ui.icons.EngineIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgeBuildApp(viewModel: ForgeBuildViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    // If an app is selected for detail view
    val selectedApp = state.apps.find { it.slug == state.selectedAppSlug }
    if (selectedApp != null) {
        AppDetailScreen(
            app = selectedApp,
            viewModel = viewModel,
            onBack = { viewModel.selectApp(null) }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (state.currentTab) {
                            ForgeTab.APPS -> "ForgeBuild"
                            ForgeTab.NEW_APP -> "New App Contract"
                            ForgeTab.WORKFLOWS -> "CI / Workflows"
                            ForgeTab.SETTINGS -> "Settings"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(EngineIcons.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = state.currentTab == ForgeTab.APPS,
                    onClick = { viewModel.setTab(ForgeTab.APPS) },
                    icon = { Icon(EngineIcons.Apps, contentDescription = "Apps") },
                    label = { Text("Apps") }
                )
                NavigationBarItem(
                    selected = state.currentTab == ForgeTab.NEW_APP,
                    onClick = { viewModel.setTab(ForgeTab.NEW_APP) },
                    icon = { Icon(EngineIcons.Add, contentDescription = "New App") },
                    label = { Text("New App") }
                )
                NavigationBarItem(
                    selected = state.currentTab == ForgeTab.WORKFLOWS,
                    onClick = { viewModel.setTab(ForgeTab.WORKFLOWS) },
                    icon = { Icon(EngineIcons.Bolt, contentDescription = "Workflows") },
                    label = { Text("Workflows") }
                )
                NavigationBarItem(
                    selected = state.currentTab == ForgeTab.SETTINGS,
                    onClick = { viewModel.setTab(ForgeTab.SETTINGS) },
                    icon = { Icon(EngineIcons.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state.currentTab) {
                ForgeTab.APPS -> AppsScreen(
                    viewModel = viewModel,
                    apps = state.filteredApps,
                    searchQuery = state.searchQuery,
                    isLoading = state.isLoading,
                    onSelectApp = { viewModel.selectApp(it) }
                )
                ForgeTab.NEW_APP -> NewAppScreen(
                    viewModel = viewModel,
                    description = state.newAppDescription,
                    slug = state.newAppSlug,
                    generatedContract = state.generatedContract
                )
                ForgeTab.WORKFLOWS -> WorkflowsScreen(
                    viewModel = viewModel,
                    apps = state.apps,
                    workflowRuns = state.workflowRuns,
                    isLoading = state.isLoading,
                    isDispatching = state.isDispatching
                )
                ForgeTab.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    pat = state.pat,
                    isPatConnected = state.isPatConnected,
                    patUser = state.patUser,
                    isLoading = state.isLoading
                )
            }
        }
    }
}
