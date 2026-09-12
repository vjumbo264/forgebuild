package com.forgebuild.clipforgeandroid.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.forgebuild.clipforgeandroid.ClipForgeViewModel
import com.forgebuild.engine.ui.icons.EngineIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipForgeApp(vm: ClipForgeViewModel) {
    val ready by vm.ready.collectAsState()
    val login by vm.login.collectAsState()
    val lastCrash by vm.lastCrash.collectAsState()

    // Session-10 fix #7: storage access is driven by the ACTUAL OS-level grant state
    // (ContextCompat.checkSelfPermission read live at need time), never by the stale
    // persisted "did we ask before" flag that caused re-prompts after the grant.
    var needsStoragePermission by remember { mutableStateOf(false) }

    // Second-launch crash diagnosis: if the PREVIOUS launch died, the startup
    // crash-catcher saved the real stack trace — surface it once so the actual
    // cause is visible instead of being guessed at again.
    lastCrash?.let { trace ->
        AlertDialog(
            onDismissRequest = { vm.dismissCrashReport() },
            title = { Text("Last launch crashed") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "The app crashed on its previous run. This is the captured stack trace — please share it when reporting the issue:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        trace.take(3000),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissCrashReport() }) { Text("Dismiss") }
            }
        )
    }

    // Re-check the REAL grant state on every resume — returning to the app can
    // never re-prompt when the permission is already granted.
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
                TextButton(onClick = {
                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
                    } else {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                    storageLauncher.launch(perms)
                }) { Text("Grant Access") }
            },
            dismissButton = {
                TextButton(onClick = { needsStoragePermission = false }) { Text("Later") }
            }
        )
    }

    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (login == null) {
        OnboardingScreen(vm)
    } else {
        MainScaffold(vm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    // Session-10 fix #4: auto-generate the clone name (bot bug-45 blank-name option).
    var autoName by remember { mutableStateOf(false) }
    val busyOps by vm.busyOps.collectAsState()
    val connectBusy = busyOps.contains("connect")
    val createBusy = busyOps.contains("create_clone")

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("ClipForge Setup") })
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Connect your ClipForge GitHub repository to manage video rendering tasks, inspect production plans, and preview audio.",
                style = MaterialTheme.typography.bodyMedium
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
                modifier = Modifier.fillMaxWidth()
            )

            if (mode == 0) {
                OutlinedTextField(
                    value = repoSlug,
                    onValueChange = { repoSlug = it },
                    label = { Text("Clone Repository Slug") },
                    placeholder = { Text("username/clipforge-clone") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { vm.connectExisting(pat, repoSlug) },
                    enabled = pat.isNotBlank() && repoSlug.isNotBlank() && !connectBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (connectBusy) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                            color = LocalContentColor.current
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Connecting…")
                    } else {
                        Text("Connect Repository")
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = autoName, onCheckedChange = { autoName = it })
                    Column {
                        Text("Auto-generate a name", style = MaterialTheme.typography.bodyMedium)
                        Text("The app picks clipforge-clone-<suffix> for you", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    value = newRepoName,
                    onValueChange = { newRepoName = it },
                    label = { Text("New Private Repo Name") },
                    placeholder = { Text(if (autoName) "(auto-generated)" else "my-clipforge-clone") },
                    enabled = !autoName,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { vm.createClone(pat, if (autoName) "" else newRepoName) },
                    enabled = pat.isNotBlank() && cloneProgress == null && !createBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (createBusy) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                            color = LocalContentColor.current
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Working…")
                    } else {
                        Text("Create & Initialize Private Clone")
                    }
                }

                cloneProgress?.let { text ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Determinate bar while the one-time copy workflow reports
                        // done/total (fix #2: the copy takes minutes on a runner — the
                        // UI must show it advancing instead of an indeterminate hum).
                        val copy = cloneCopyProgress
                        if (copy != null && copy.stage == "copy" && copy.total > 0) {
                            LinearProgressIndicator(
                                progress = { (copy.done.toFloat() / copy.total).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        Text(text, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Session-10 fix #3: an unmissable confirmation once creation GENUINELY
                // completes (the copy poll finished and finalize verified the tree).
                cloneSuccess?.let { msg ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Clone created successfully", style = MaterialTheme.typography.titleMedium)
                            Text(msg, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { vm.dismissCloneOutcome() }) { Text("Dismiss") }
                        }
                    }
                }
                cloneFailure?.let { msg ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Clone creation failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                            Text(msg, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { vm.dismissCloneOutcome() }) { Text("Dismiss") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(vm: ClipForgeViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(EngineIcons.Add, contentDescription = "New Video") },
                    label = { Text("New Video") },
                    selected = currentRoute == "new",
                    onClick = {
                        navController.navigate("new") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(EngineIcons.Bolt, contentDescription = "Tasks") },
                    label = { Text("Tasks") },
                    selected = currentRoute == "tasks",
                    onClick = {
                        navController.navigate("tasks") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(EngineIcons.CheckCircle, contentDescription = "Completed") },
                    label = { Text("Completed") },
                    selected = currentRoute == "completed",
                    onClick = {
                        navController.navigate("completed") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(EngineIcons.Playlist, contentDescription = "Series") },
                    label = { Text("Series") },
                    selected = currentRoute == "series",
                    onClick = {
                        navController.navigate("series") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(EngineIcons.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = currentRoute == "settings",
                    onClick = {
                        navController.navigate("settings") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            NavHost(navController = navController, startDestination = "tasks") {
                composable("new") {
                    NewTaskWizard(vm, onDone = {
                        navController.navigate("tasks") {
                            popUpTo("tasks") { inclusive = true }
                        }
                    })
                }
                composable("tasks") {
                    TasksScreen(vm, onSelectTask = { jobId ->
                        navController.navigate("task/$jobId")
                    })
                }
                composable("completed") {
                    CompletedScreen(vm, onSelectTask = { jobId ->
                        navController.navigate("task/$jobId")
                    })
                }
                composable("series") {
                    SeriesScreen(vm, onSelectSeries = { seriesId ->
                        navController.navigate("series/$seriesId")
                    })
                }
                composable("settings") {
                    SettingsScreen(vm, onOpenMusic = {
                        navController.navigate("music")
                    })
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
                        onSelectTask = { jobId -> navController.navigate("task/$jobId") }
                    )
                }
                composable("music") {
                    MusicScreen(vm, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
