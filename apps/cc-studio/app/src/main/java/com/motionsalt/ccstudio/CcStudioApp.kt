package com.motionsalt.ccstudio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.icons.EngineIcons
import com.motionsalt.ccstudio.data.EffectRepository
import com.motionsalt.ccstudio.data.ProjectData
import com.motionsalt.ccstudio.data.ProjectRepository
import com.motionsalt.ccstudio.ui.export.ExportStage
import com.motionsalt.ccstudio.ui.fit.FitStage
import com.motionsalt.ccstudio.ui.library.LibraryScreen
import com.motionsalt.ccstudio.ui.packs.PacksScreen
import com.motionsalt.ccstudio.ui.projects.ProjectsScreen
import com.motionsalt.ccstudio.ui.settings.SettingsScreen
import com.motionsalt.ccstudio.ui.source.SourceStage
import com.motionsalt.ccstudio.ui.tune.TuneStage

enum class NavDestination {
    PROJECTS,
    PACKS,
    LIBRARY,
    SETTINGS
}

enum class ProjectStage(val title: String) {
    SOURCE("Source"),
    FIT("Fit"),
    TUNE("Tune"),
    EXPORT("Export")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CcStudioApp() {
    val context = LocalContext.current
    val projectRepository = remember { ProjectRepository(context) }
    val effectRepository = remember { EffectRepository(context) }

    var currentNav by remember { mutableStateOf(NavDestination.PROJECTS) }
    var activeProjectId by remember { mutableStateOf<String?>(null) }
    var activeStage by remember { mutableStateOf(ProjectStage.SOURCE) }

    val activeProject: ProjectData? = remember(activeProjectId) {
        activeProjectId?.let { projectRepository.getProject(it) }
    }

    Scaffold(
        topBar = {
            if (currentNav == NavDestination.PROJECTS && activeProject != null) {
                TopAppBar(
                    title = {
                        Column {
                            Text(activeProject.name, style = MaterialTheme.typography.titleMedium)
                            Text(activeProject.lookName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { activeProjectId = null }) {
                            Icon(EngineIcons.ArrowBack, contentDescription = "Close Project")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavigationBarItem(
                    selected = currentNav == NavDestination.PROJECTS,
                    onClick = { currentNav = NavDestination.PROJECTS },
                    icon = { Icon(EngineIcons.Home, contentDescription = "Projects") },
                    label = { Text("Projects") }
                )
                NavigationBarItem(
                    selected = currentNav == NavDestination.PACKS,
                    onClick = { currentNav = NavDestination.PACKS },
                    icon = { Icon(EngineIcons.Bolt, contentDescription = "Packs") },
                    label = { Text("Packs") }
                )
                NavigationBarItem(
                    selected = currentNav == NavDestination.LIBRARY,
                    onClick = { currentNav = NavDestination.LIBRARY },
                    icon = { Icon(EngineIcons.MenuBook, contentDescription = "Library") },
                    label = { Text("Library") }
                )
                NavigationBarItem(
                    selected = currentNav == NavDestination.SETTINGS,
                    onClick = { currentNav = NavDestination.SETTINGS },
                    icon = { Icon(EngineIcons.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentNav) {
                NavDestination.PROJECTS -> {
                    if (activeProject == null) {
                        ProjectsScreen(
                            repository = projectRepository,
                            onOpenProject = { id ->
                                activeProjectId = id
                                activeStage = ProjectStage.SOURCE
                            },
                            activeProjectId = activeProjectId,
                            onProjectDeleted = { deletedId ->
                                if (activeProjectId == deletedId) activeProjectId = null
                            }
                        )
                    } else {
                        // Project open: render Stage Bar + active Stage View
                        Column(modifier = Modifier.fillMaxSize()) {
                            val canFit = activeProject.pairs.any { it.isComplete }
                            val canTune = activeProject.fitResult != null
                            val canExport = canTune

                            PrimaryTabRow(
                                selectedTabIndex = activeStage.ordinal,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                ProjectStage.values().forEach { stage ->
                                    val isEnabled = when (stage) {
                                        ProjectStage.SOURCE -> true
                                        ProjectStage.FIT -> canFit
                                        ProjectStage.TUNE -> canTune
                                        ProjectStage.EXPORT -> canExport
                                    }
                                    Tab(
                                        selected = activeStage == stage,
                                        enabled = isEnabled,
                                        onClick = { if (isEnabled) activeStage = stage },
                                        text = {
                                            Text(
                                                stage.title,
                                                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            )
                                        }
                                    )
                                }
                            }

                            when (activeStage) {
                                ProjectStage.SOURCE -> {
                                    SourceStage(
                                        project = activeProject,
                                        repository = projectRepository,
                                        onContinueToFit = { activeStage = ProjectStage.FIT }
                                    )
                                }
                                ProjectStage.FIT -> {
                                    FitStage(
                                        project = activeProject,
                                        repository = projectRepository,
                                        onContinueToTune = { activeStage = ProjectStage.TUNE }
                                    )
                                }
                                ProjectStage.TUNE -> {
                                    TuneStage(
                                        project = activeProject,
                                        repository = projectRepository,
                                        effectRepository = effectRepository
                                    )
                                }
                                ProjectStage.EXPORT -> {
                                    ExportStage(
                                        project = activeProject,
                                        repository = projectRepository,
                                        effectRepository = effectRepository
                                    )
                                }
                            }
                        }
                    }
                }
                NavDestination.PACKS -> {
                    PacksScreen(effectRepository = effectRepository)
                }
                NavDestination.LIBRARY -> {
                    LibraryScreen(effectRepository = effectRepository)
                }
                NavDestination.SETTINGS -> {
                    SettingsScreen(
                        repository = projectRepository,
                        onOpenProject = { id ->
                            currentNav = NavDestination.PROJECTS
                            activeProjectId = id
                            activeStage = ProjectStage.SOURCE
                        },
                        onAllProjectsDeleted = {
                            activeProjectId = null
                        }
                    )
                }
            }
        }
    }
}
