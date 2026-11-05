package com.motionsalt.ccstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgebuild.engine.ui.icons.EngineIcons

enum class NavDestination { PROJECTS, PACKS, LIBRARY, SETTINGS }
enum class ProjectStage { SOURCE, FIT, TUNE, EXPORT }

data class ProjectItem(
    val id: String,
    val name: String,
    val date: String,
    val pairCount: Int,
    val deltaE: Float,
    val status: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CcStudioApp() {
    var currentNav by remember { mutableStateOf(NavDestination.PROJECTS) }
    var activeProjectId by remember { mutableStateOf<String?>("demo-1") }
    var activeStage by remember { mutableStateOf(ProjectStage.SOURCE) }

    Scaffold(
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
                    icon = { Icon(EngineIcons.Search, contentDescription = "Library") },
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
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (currentNav) {
                NavDestination.PROJECTS -> {
                    if (activeProjectId != null) {
                        ProjectDetailView(
                            projectId = activeProjectId!!,
                            currentStage = activeStage,
                            onStageChange = { activeStage = it },
                            onBack = { activeProjectId = null }
                        )
                    } else {
                        ProjectsListView(onSelectProject = { activeProjectId = it })
                    }
                }
                NavDestination.PACKS -> PacksView()
                NavDestination.LIBRARY -> LibraryView()
                NavDestination.SETTINGS -> SettingsView()
            }
        }
    }
}

@Composable
fun ProjectsListView(onSelectProject: (String) -> Unit) {
    val projects = remember {
        mutableStateListOf(
            ProjectItem("demo-1", "Cyberpunk Teal & Orange", "2026-11-05", 2, 2.14f, "Tuned"),
            ProjectItem("proj-2", "Anime Sunset Glow", "2026-11-04", 1, 3.02f, "Fitted"),
            ProjectItem("proj-3", "Cinematic Fuji Bleach", "2026-11-03", 3, 1.89f, "Exported")
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("MOTIONSALT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("CC Studio Projects", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            FilledTonalButton(onClick = {
                val newId = "proj-" + (projects.size + 1)
                projects.add(0, ProjectItem(newId, "New Grade #" + (projects.size + 1), "Today", 0, 0f, "Draft"))
                onSelectProject(newId)
            }) {
                Icon(EngineIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("New")
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(projects) { p ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectProject(p.id) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                            SuggestionChip(
                                onClick = {},
                                label = { Text(p.status, fontSize = 12.sp) }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("${p.pairCount} pairs", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (p.deltaE > 0f) {
                                Text("ΔE: ${p.deltaE}", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(p.date, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailView(
    projectId: String,
    currentStage: ProjectStage,
    onStageChange: (ProjectStage) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Cyberpunk Teal & Orange", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Downloads/MOTIONSALT/CC Studio/", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(EngineIcons.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        // 4 Stages Tab Row
        TabRow(
            selectedTabIndex = currentStage.ordinal,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Tab(selected = currentStage == ProjectStage.SOURCE, onClick = { onStageChange(ProjectStage.SOURCE) }, text = { Text("1. Source") })
            Tab(selected = currentStage == ProjectStage.FIT, onClick = { onStageChange(ProjectStage.FIT) }, text = { Text("2. Fit") })
            Tab(selected = currentStage == ProjectStage.TUNE, onClick = { onStageChange(ProjectStage.TUNE) }, text = { Text("3. Tune") })
            Tab(selected = currentStage == ProjectStage.EXPORT, onClick = { onStageChange(ProjectStage.EXPORT) }, text = { Text("4. Export") })
        }

        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            when (currentStage) {
                ProjectStage.SOURCE -> SourceStageView()
                ProjectStage.FIT -> FitStageView()
                ProjectStage.TUNE -> TuneStageView()
                ProjectStage.EXPORT -> ExportStageView()
            }
        }
    }
}

@Composable
fun SourceStageView() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Frame Pairs (Base & Target)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Feed base frames and target look frames to extract the 3D color grade offline.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Pair #1: Scene 01 (City Night)", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f).height(100.dp).background(Color(0xFF2B3A42), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Text("Base Frame", color = Color.White, fontSize = 12.sp)
                    }
                    Box(modifier = Modifier.weight(1f).height(100.dp).background(Color(0xFF1E8284), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Text("Target Frame", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Icon(EngineIcons.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add Frame Pair")
        }
    }
}

@Composable
fun FitStageView() {
    var isFitting by remember { mutableStateOf(false) }
    var fitProgress by remember { mutableStateOf(100) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Solve 3D Color Grade", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("1D Tone curves + 3x3 chromatic matrix + 33x33x33 residual lattice with Laplacian smoothing.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fit Quality Metrics", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column {
                        Text("Mean ΔE 76", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("2.14", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Column {
                        Text("Peak ΔE 76", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("4.85", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Lattice Res", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("33x33x33", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Button(
            onClick = { isFitting = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Re-Run Offline Fit")
        }
    }
}

@Composable
fun TuneStageView() {
    var lookIntensity by remember { mutableStateOf(0.95f) }
    var glowIntensity by remember { mutableStateOf(0.35f) }
    var grainAmount by remember { mutableStateOf(0.12f) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Look Tuning & Adjustment Stack", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        // View Mode Selector
        ScrollableTabRow(selectedTabIndex = 0, edgePadding = 0.dp) {
            Tab(selected = true, onClick = {}, text = { Text("Result") })
            Tab(selected = false, onClick = {}, text = { Text("Wipe") })
            Tab(selected = false, onClick = {}, text = { Text("Side by Side") })
            Tab(selected = false, onClick = {}, text = { Text("Vs Target") })
            Tab(selected = false, onClick = {}, text = { Text("Scopes") })
        }

        Card(
            modifier = Modifier.fillMaxWidth().height(160.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("60 FPS OpenGL ES Preview Active", color = Color.White, fontSize = 14.sp)
            }
        }

        Text("Active Stack (3 Effects)", fontWeight = FontWeight.SemiBold)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            item {
                Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("3D LUT Grade (ms_lut)", fontWeight = FontWeight.SemiBold)
                        Slider(value = lookIntensity, onValueChange = { lookIntensity = it })
                        Text("Intensity: ${(lookIntensity * 100).toInt()}%", fontSize = 12.sp)
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Glow (ms_glow)", fontWeight = FontWeight.SemiBold)
                        Slider(value = glowIntensity, onValueChange = { glowIntensity = it })
                        Text("Intensity: ${(glowIntensity * 100).toInt()}%", fontSize = 12.sp)
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Film Grain (ms_grain)", fontWeight = FontWeight.SemiBold)
                        Slider(value = grainAmount, onValueChange = { grainAmount = it })
                        Text("Amount: ${(grainAmount * 100).toInt()}%", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ExportStageView() {
    var exportFormat by remember { mutableStateOf("kfpack") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Export Keyflaw Suite & LUT", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Exports directly to Downloads/MOTIONSALT/CC Studio/ offline on your phone.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select Output Format", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = exportFormat == "kfpack", onClick = { exportFormat = "kfpack" }, label = { Text("Keyflaw .kfpack") })
                    FilterChip(selected = exportFormat == "cube", onClick = { exportFormat = "cube" }, label = { Text(".cube LUT (33³)") })
                    FilterChip(selected = exportFormat == "hald", onClick = { exportFormat = "hald" }, label = { Text("Hald Level 8 PNG") })
                }
            }
        }

        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text("Export Now to Downloads")
        }
    }
}

@Composable
fun PacksView() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Keyflaw Shader Packs", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Manage installed .kfpack files and merge effect suites.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("MOTIONSALT Suite (Built-in)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("17 Effects • Category: MOTIONSALT • Keyflaw 1.4.9", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = {}) {
                    Text("Export MOTIONSALT.kfpack")
                }
            }
        }
    }
}

@Composable
fun LibraryView() {
    val effects = listOf(
        "ms_lut" to "3D LUT Grade",
        "ms_brightness_contrast" to "Brightness & Contrast",
        "ms_exposure_gamma" to "Exposure & Gamma",
        "ms_levels_curves" to "Levels & Curves",
        "ms_white_balance" to "White Balance",
        "ms_hue_saturation" to "Hue & Saturation",
        "ms_color_balance" to "Color Balance",
        "ms_selective_color" to "Selective Color",
        "ms_channel_mixer" to "Channel Mixer",
        "ms_sharpen" to "Sharpen",
        "ms_unsharp_mask" to "AE Unsharp Mask",
        "ms_clarity" to "Clarity",
        "ms_glow" to "Glow (expandBounds: 0.15)",
        "ms_tritone" to "Tritone (COLOR params)",
        "ms_haze_flare" to "Haze & Flare",
        "ms_vignette" to "Vignette",
        "ms_grain" to "Film Grain"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("17 In-Scope Effects", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Strict Keyflaw 1.4.9 GLSL ES 1.00 compliant effects.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(effects) { (id, name) ->
                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(name, fontWeight = FontWeight.SemiBold)
                            Text(id, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SuggestionChip(onClick = {}, label = { Text("MOTIONSALT") })
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsView() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings & System", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Offline Guarantee", fontWeight = FontWeight.SemiBold)
                Text("MOTIONSALT CC Studio has ZERO network permissions declared. No telemetry, no cloud dependencies, fully local.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Version: 1.0.0 (v1) • Keyflaw Target: 1.4.9", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }

        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Export Directory", fontWeight = FontWeight.SemiBold)
                Text("Downloads/MOTIONSALT/CC Studio/", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
