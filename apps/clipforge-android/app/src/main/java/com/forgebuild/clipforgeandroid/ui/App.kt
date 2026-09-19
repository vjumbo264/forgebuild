@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.forgebuild.clipforgeandroid.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.forgebuild.clipforgeandroid.ClipForgeViewModel
import com.forgebuild.engine.ui.components.EngineLinearWavyProgress
import com.forgebuild.engine.ui.components.EngineLoadingIndicator
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.ElevationTokens
import com.forgebuild.engine.ui.theme.SpacingTokens
import kotlinx.coroutines.launch

/* ============================================================================
 *  V23 APP ROOT — complete remake.
 *
 *  Shell: FLoating bottom tab bar (tonal pill, detached from the screen edge)
 *  + HorizontalPager so the operator swipes horizontally between tabs and the
 *  bar stays in sync with the swipe (settledPage -> selected segment). Detail
 *  screens (task/series/music) navigate on top of the pager without the bar.
 * ============================================================================ */

@Composable
fun ClipForgeApp(vm: ClipForgeViewModel) {
    val ready by vm.ready.collectAsState()
    val login by vm.login.collectAsState()
    val lastCrash by vm.lastCrash.collectAsState()
    var needsStoragePermission by remember { mutableStateOf(false) }

    lastCrash?.let { trace ->
        AlertDialog(
            onDismissRequest = { vm.dismissCrashReport() },
            title = { Text("Last launch crashed") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "The app crashed on its previous run. This is the captured stack trace — share it when reporting the issue:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        trace.take(3000),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = SpacingTokens.Spacing.xs),
                    )
                }
            },
            confirmButton = { TextActionButton(label = "Dismiss", onClick = { vm.dismissCrashReport() }) },
        )
    }

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
    ) { needsStoragePermission = !vm.isStorageGrantedNow() }

    if (needsStoragePermission) {
        AlertDialog(
            onDismissRequest = { needsStoragePermission = false },
            title = { Text("Storage access") },
            text = { Text("ClipForge saves rendered videos to Movies/ClipForge on this device. Grant storage access to enable downloads.") },
            confirmButton = {
                ActionButton(label = "Grant access", onClick = {
                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
                    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    storageLauncher.launch(perms)
                })
            },
            dismissButton = { TextActionButton(label = "Later", onClick = { needsStoragePermission = false }) },
        )
    }

    MusicConfirmDialog(vm)

    when {
        !ready -> SplashScreen()
        login == null -> OnboardingScreen(vm)
        else -> MainHome(vm)
    }
}

/** v23 splash — official morphing loader on a decorative cookie shape. */
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
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie9Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Text("C", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text("ClipForge", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            EngineLoadingIndicator()
            Text("Loading your studio…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** v23 onboarding — connect an existing clone or create a new one. */
@Composable
fun OnboardingScreen(vm: ClipForgeViewModel) {
    var mode by remember { mutableStateOf(0) } // 0 = connect, 1 = create
    var pat by remember { mutableStateOf("") }
    var repoSlug by remember { mutableStateOf("") }
    var newRepoName by remember { mutableStateOf("") }
    var autoName by remember { mutableStateOf(false) }

    val cloneProgress by vm.cloneProgress.collectAsState()
    val cloneCopyProgress by vm.cloneCopyProgress.collectAsState()
    val cloneSuccess by vm.cloneSuccess.collectAsState()
    val cloneFailure by vm.cloneFailure.collectAsState()
    val connectBusy = busyOf(vm, "connect")
    val createBusy = busyOf(vm, "create_clone")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ClipForge setup") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ElevationTokens.tonalContainerColor(2)),
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SpacingTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md),
        ) {
            Text(
                "Connect your ClipForge GitHub clone to create video tasks, watch them render live, and publish the results.",
                style = MaterialTheme.typography.bodyMedium,
            )

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == 0, onClick = { mode = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Connect existing") }
                SegmentedButton(
                    selected = mode == 1, onClick = { mode = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Create new clone") }
            }

            CfSection(title = "GitHub access token", subtitle = "A personal access token with repo + actions access. Stored on this device only.") {
                OutlinedTextField(
                    value = pat, onValueChange = { pat = it },
                    label = { Text("Personal access token") },
                    placeholder = { Text("github_pat_… or ghp_…") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (mode == 0) {
                CfSection(title = "Your clone repository") {
                    OutlinedTextField(
                        value = repoSlug, onValueChange = { repoSlug = it },
                        label = { Text("Repository") },
                        placeholder = { Text("username/clipforge-clone") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ActionButton(
                        label = if (connectBusy) "Connecting…" else "Connect repository",
                        onClick = { vm.connectExisting(pat, repoSlug) },
                        busy = connectBusy,
                        enabled = pat.isNotBlank() && repoSlug.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                CfSection(title = "New private clone", subtitle = "The full ClipForge source is copied into a fresh private repo, then you are signed in automatically.") {
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoName, onCheckedChange = { autoName = it })
                        Column {
                            Text("Auto-generate a name", style = MaterialTheme.typography.bodyMedium)
                            Text("clipforge-clone-<suffix>", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedTextField(
                        value = newRepoName, onValueChange = { newRepoName = it },
                        label = { Text("Repository name") },
                        placeholder = { Text(if (autoName) "(auto-generated)" else "my-clipforge-clone") },
                        enabled = !autoName,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ActionButton(
                        label = if (createBusy) "Working…" else "Create clone & sign in",
                        onClick = { vm.createClone(pat, if (autoName) "" else newRepoName) },
                        busy = createBusy,
                        enabled = pat.isNotBlank() && cloneProgress == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    cloneProgress?.let { text ->
                        val copy = cloneCopyProgress
                        if (copy != null && copy.stage == "copy" && copy.total > 0) {
                            CfProgress(label = text, fraction = (copy.done.toFloat() / copy.total).coerceIn(0f, 1f))
                        } else {
                            CfProgress(label = text, fraction = null)
                        }
                    }
                    cloneSuccess?.let { msg ->
                        CfCard(tonalLevel = 2) {
                            Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                                Text("Clone created", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Text(msg, style = MaterialTheme.typography.bodyMedium)
                                TextActionButton(label = "Dismiss", onClick = { vm.dismissCloneOutcome() })
                            }
                        }
                    }
                    cloneFailure?.let { msg ->
                        CfCard(tonalLevel = 2) {
                            Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                                Text("Clone creation failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                                Text(msg, style = MaterialTheme.typography.bodyMedium)
                                TextActionButton(label = "Dismiss", onClick = { vm.dismissCloneOutcome() })
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------------------
 * Main shell: floating tab bar + horizontal swipe pager.
 * ------------------------------------------------------------------------- */

private data class MainTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun MainHome(vm: ClipForgeViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainTabs(
                vm = vm,
                onOpenTask = { navController.navigate("task/$it") },
                onOpenSeries = { navController.navigate("series/$it") },
                onOpenMusic = { navController.navigate("music") },
            )
        }
        composable("task/{jobId}") { entry ->
            val jobId = entry.arguments?.getString("jobId") ?: return@composable
            TaskDetailScreen(vm = vm, jobId = jobId, onBack = { navController.popBackStack() })
        }
        composable("series/{seriesId}") { entry ->
            val seriesId = entry.arguments?.getString("seriesId") ?: return@composable
            SeriesDetailScreen(
                vm = vm, seriesId = seriesId,
                onBack = { navController.popBackStack() },
                onSelectTask = { navController.navigate("task/$it") },
            )
        }
        composable("music") { MusicScreen(vm = vm, onBack = { navController.popBackStack() }) }
    }
}

@Composable
private fun MainTabs(
    vm: ClipForgeViewModel,
    onOpenTask: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenMusic: () -> Unit,
) {
    val tabs = remember {
        listOf(
            MainTab("New", EngineIcons.Add),
            MainTab("Tasks", EngineIcons.Bolt),
            MainTab("Done", EngineIcons.CheckCircle),
            MainTab("Series", EngineIcons.Playlist),
            MainTab("Settings", EngineIcons.Settings),
        )
    }
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        // Content flows FULL SCREEN behind the floating bar.
        // No black strip, no cut-off, no leftover bar area, and seamless content scrolling.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> NewTaskWizard(vm = vm, onDone = { scope.launch { pagerState.animateScrollToPage(1) } })
                1 -> TasksScreen(vm = vm, onSelectTask = onOpenTask)
                2 -> CompletedScreen(vm = vm, onSelectTask = onOpenTask)
                3 -> SeriesScreen(vm = vm, onSelectSeries = onOpenSeries, onSelectTask = onOpenTask)
                4 -> SettingsScreen(vm = vm, onOpenMusic = onOpenMusic)
            }
        }

        // Truly FLOATING expressive navigation pill dock
        FloatingExpressiveNavigationBar(
            tabs = tabs,
            selectedTab = pagerState.currentPage,
            onTabSelected = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .padding(bottom = (navBottom + 12.dp).coerceAtLeast(16.dp)),
        )
    }
}

@Composable
private fun FloatingExpressiveNavigationBar(
    tabs: List<MainTab>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = selectedTab == index
                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .expressiveBounce(interactionSource, pressedScale = 0.88f)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onTabSelected(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .width(if (isSelected) 52.dp else 36.dp)
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                    else Color.Transparent,
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(20.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                fontSize = 11.sp,
                            ),
                            maxLines = 1,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }
    }
}

/** v23 deterministic-music abort dialog (data layer contract, fresh styling). */
@Composable
fun MusicConfirmDialog(vm: ClipForgeViewModel) {
    val confirm by vm.musicConfirm.collectAsState()
    confirm?.let { mc ->
        AlertDialog(
            onDismissRequest = { vm.dismissMusicConfirm() },
            title = { Text("Couldn't confirm your background music") },
            text = {
                Text(
                    mc.reason + "\n\nNothing was dispatched — the video will NOT silently render without music.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                ActionButton(label = "Retry", onClick = { vm.dismissMusicConfirm(); mc.retry() })
            },
            dismissButton = {
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                    TextActionButton(label = "Continue without music", onClick = { vm.dismissMusicConfirm(); mc.continueWithoutMusic() })
                    TextActionButton(label = "Cancel", onClick = { vm.dismissMusicConfirm() })
                }
            },
        )
    }
}
