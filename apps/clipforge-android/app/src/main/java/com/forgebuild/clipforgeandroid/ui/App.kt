@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.forgebuild.clipforgeandroid.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.forgebuild.clipforgeandroid.ClipForgeViewModel
import com.forgebuild.engine.ui.components.EngineLinearWavyProgress
import com.forgebuild.engine.ui.components.EngineLoadingIndicator
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.ElevationTokens
import com.forgebuild.engine.ui.theme.SpacingTokens

/**
 * App root + splash + onboarding (v22 task-123 rebuild on the engine's real M3
 * Expressive stack). The hand-rolled squiggle splash is gone: the splash is the
 * official morphing LoadingIndicator under a MaterialShapes decorative cookie,
 * and screen-to-screen transitions read their spring specs from the expressive
 * MotionScheme (never invented easings).
 */
@Composable
fun ClipForgeApp(vm: ClipForgeViewModel) {
    val ready by vm.ready.collectAsState()
    val login by vm.login.collectAsState()
    val lastCrash by vm.lastCrash.collectAsState()

    // Storage access is driven by the ACTUAL OS-level grant state (read live at
    // need time), never by a stale persisted flag.
    var needsStoragePermission by remember { mutableStateOf(false) }

    // Second-launch crash diagnosis: surface the captured trace once.
    lastCrash?.let { trace ->
        AlertDialog(
            onDismissRequest = { vm.dismissCrashReport() },
            title = { Text("Last launch crashed") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "The app crashed on its previous run. This is the captured stack trace — please share it when reporting the issue:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(SpacingTokens.Spacing.xs))
                    Text(
                        trace.take(3000),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = {
                TextActionButton(label = "Dismiss", onClick = { vm.dismissCrashReport() })
            },
        )
    }

    // Re-check the REAL grant state on every resume.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        needsStoragePermission = !vm.isStorageGrantedNow()
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                needsStoragePermission = !vm.isStorageGrantedNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        needsStoragePermission = !vm.isStorageGrantedNow()
    }

    if (needsStoragePermission) {
        AlertDialog(
            onDismissRequest = { needsStoragePermission = false },
            title = { Text("Storage Permission") },
            text = {
                Text(
                    "ClipForge saves generated vertical videos into your device's Movies/ClipForge directory.\n\n" +
                        "Grant storage access to enable automatic downloads."
                )
            },
            confirmButton = {
                ActionButton(label = "Grant Access", onClick = {
                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
                    } else {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                    storageLauncher.launch(perms)
                })
            },
            dismissButton = {
                TextActionButton(label = "Later", onClick = { needsStoragePermission = false })
            },
        )
    }

    MusicConfirmDialog(vm)

    if (!ready) {
        SplashScreen()
        return
    }

    if (login == null) {
        OnboardingScreen(vm)
    } else {
        MainScaffold(vm)
    }
}

/** v22 splash: official morphing LoadingIndicator + a MaterialShapes cookie. */
@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.lg),
        ) {
            Box(
                modifier = Modifier
                    .size(SpacingTokens.Spacing.xxxl + SpacingTokens.Spacing.xl)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialShapes.Cookie9Sided.toShape(),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "C",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                "ClipForge",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            EngineLoadingIndicator()
            Text(
                "Loading your studio…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun OnboardingScreen(vm: ClipForgeViewModel) {
    var mode by remember { mutableStateOf(0) } // 0 = connect, 1 = create
    var pat by remember { mutableStateOf("") }
    var repoSlug by remember { mutableStateOf("") }
    var newRepoName by remember { mutableStateOf("") }

    val cloneProgress by vm.cloneProgress.collectAsState()
    val cloneCopyProgress by vm.cloneCopyProgress.collectAsState()
    val cloneSuccess by vm.cloneSuccess.collectAsState()
    val cloneFailure by vm.cloneFailure.collectAsState()
    var autoName by remember { mutableStateOf(false) }
    val busyOps by vm.busyOps.collectAsState()
    val connectBusy = busyOps.contains("connect")
    val createBusy = busyOps.contains("create_clone")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ClipForge Setup") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ElevationTokens.tonalContainerColor(2)
                ),
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SpacingTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md),
        ) {
            Text(
                "Connect your ClipForge GitHub repository to manage video rendering tasks, inspect production plans, and preview audio.",
                style = MaterialTheme.typography.bodyMedium,
            )

            TabRow(selectedTabIndex = mode) {
                Tab(selected = mode == 0, onClick = { mode = 0 }, text = { Text("Connect Existing") })
                Tab(selected = mode == 1, onClick = { mode = 1 }, text = { Text("Create New Clone") })
            }

            OutlinedTextField(
                value = pat,
                onValueChange = { pat = it },
                label = { Text("GitHub Personal Access Token (PAT)") },
                placeholder = { Text("github_pat_... or ghp_...") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            if (mode == 0) {
                OutlinedTextField(
                    value = repoSlug,
                    onValueChange = { repoSlug = it },
                    label = { Text("Clone Repository Slug") },
                    placeholder = { Text("username/clipforge-clone") },
                    modifier = Modifier.fillMaxWidth(),
                )

                ActionButton(
                    label = if (connectBusy) "Connecting…" else "Connect Repository",
                    onClick = { vm.connectExisting(pat, repoSlug) },
                    busy = connectBusy,
                    enabled = pat.isNotBlank() && repoSlug.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(checked = autoName, onCheckedChange = { autoName = it })
                    Column {
                        Text("Auto-generate a name", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "The app picks clipforge-clone-<suffix> for you",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = newRepoName,
                    onValueChange = { newRepoName = it },
                    label = { Text("New Private Repo Name") },
                    placeholder = { Text(if (autoName) "(auto-generated)" else "my-clipforge-clone") },
                    enabled = !autoName,
                    modifier = Modifier.fillMaxWidth(),
                )

                ActionButton(
                    label = if (createBusy) "Working…" else "Create & Initialize Private Clone",
                    onClick = { vm.createClone(pat, if (autoName) "" else newRepoName) },
                    busy = createBusy,
                    enabled = pat.isNotBlank() && cloneProgress == null,
                    modifier = Modifier.fillMaxWidth(),
                )

                cloneProgress?.let { text ->
                    Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xxs)) {
                        val copy = cloneCopyProgress
                        if (copy != null && copy.stage == "copy" && copy.total > 0) {
                            EngineLinearWavyProgress(
                                progress = { (copy.done.toFloat() / copy.total).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            EngineLinearWavyProgress(Modifier.fillMaxWidth())
                        }
                        Text(text, style = MaterialTheme.typography.bodySmall)
                    }
                }

                cloneSuccess?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(SpacingTokens.Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                        ) {
                            Text("Clone created successfully", style = MaterialTheme.typography.titleMedium)
                            Text(msg, style = MaterialTheme.typography.bodyMedium)
                            TextActionButton(label = "Dismiss", onClick = { vm.dismissCloneOutcome() })
                        }
                    }
                }
                cloneFailure?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(SpacingTokens.Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                        ) {
                            Text(
                                "Clone creation failed",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(msg, style = MaterialTheme.typography.bodyMedium)
                            TextActionButton(label = "Dismiss", onClick = { vm.dismissCloneOutcome() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainScaffold(vm: ClipForgeViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    // Expressive spring physics for screen-to-screen transitions — the official
    // MotionScheme specs, never invented easings (v22 task-123).
    val motion = MaterialTheme.motionScheme

    fun go(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(EngineIcons.Add, contentDescription = "New Video") },
                    label = { Text("New Video") },
                    selected = currentRoute == "new",
                    onClick = { go("new") },
                )
                NavigationBarItem(
                    icon = { Icon(EngineIcons.Bolt, contentDescription = "Tasks") },
                    label = { Text("Tasks") },
                    selected = currentRoute == "tasks",
                    onClick = { go("tasks") },
                )
                NavigationBarItem(
                    icon = { Icon(EngineIcons.CheckCircle, contentDescription = "Completed") },
                    label = { Text("Completed") },
                    selected = currentRoute == "completed",
                    onClick = { go("completed") },
                )
                NavigationBarItem(
                    icon = { Icon(EngineIcons.Playlist, contentDescription = "Series") },
                    label = { Text("Series") },
                    selected = currentRoute == "series",
                    onClick = { go("series") },
                )
                NavigationBarItem(
                    icon = { Icon(EngineIcons.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = currentRoute == "settings",
                    onClick = { go("settings") },
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = "tasks",
                enterTransition = {
                    slideInHorizontally(motion.defaultSpatialSpec()) { it / 4 } +
                        fadeIn(motion.fastEffectsSpec())
                },
                exitTransition = {
                    slideOutHorizontally(motion.defaultSpatialSpec()) { -it / 6 } +
                        fadeOut(motion.fastEffectsSpec())
                },
                popEnterTransition = {
                    slideInHorizontally(motion.defaultSpatialSpec()) { -it / 4 } +
                        fadeIn(motion.fastEffectsSpec())
                },
                popExitTransition = {
                    slideOutHorizontally(motion.defaultSpatialSpec()) { it / 6 } +
                        fadeOut(motion.fastEffectsSpec())
                },
            ) {
                composable("new") {
                    NewTaskWizard(vm, onDone = {
                        navController.navigate("tasks") { popUpTo("tasks") { inclusive = true } }
                    })
                }
                composable("tasks") {
                    TasksScreen(vm, onSelectTask = { jobId -> navController.navigate("task/$jobId") })
                }
                composable("completed") {
                    CompletedScreen(vm, onSelectTask = { jobId -> navController.navigate("task/$jobId") })
                }
                composable("series") {
                    SeriesScreen(
                        vm,
                        onSelectSeries = { seriesId -> navController.navigate("series/$seriesId") },
                        onSelectTask = { jobId -> navController.navigate("task/$jobId") },
                    )
                }
                composable("settings") {
                    SettingsScreen(vm, onOpenMusic = { navController.navigate("music") })
                }
                composable("task/{jobId}") { backStackEntry ->
                    val jobId = backStackEntry.arguments?.getString("jobId") ?: return@composable
                    TaskDetailScreen(vm, jobId, onBack = { navController.popBackStack() })
                }
                composable("series/{seriesId}") { backStackEntry ->
                    val seriesId = backStackEntry.arguments?.getString("seriesId") ?: return@composable
                    SeriesDetailScreen(
                        vm = vm,
                        seriesId = seriesId,
                        onBack = { navController.popBackStack() },
                        onSelectTask = { jobId -> navController.navigate("task/$jobId") },
                    )
                }
                composable("music") {
                    MusicScreen(vm, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
