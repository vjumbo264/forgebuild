package com.forgebuild.clipforge.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.forgebuild.clipforge.ClipForgeViewModel
import com.forgebuild.clipforge.data.*
import com.forgebuild.engine.files.SafeSave
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.ForgeBuildTheme
import kotlinx.coroutines.launch

@Composable
fun ClipForgeApp(vm: ClipForgeViewModel = viewModel()) {
    ForgeBuildTheme {
        val login by vm.login.collectAsState()
        val ready by vm.ready.collectAsState()
        val nav = rememberNavController()
        val snack by vm.snack.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(snack) { snack?.let { snackbar.showSnackbar(it); vm.clearSnack() } }

        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
            Box(Modifier.padding(pad)) {
                if (!ready) return@Box
                if (login == null) OnboardingScreen(vm)
                else MainNav(nav, vm)
            }
        }
    }
}

/* ---------------- onboarding (connect existing / create clone) ---------------- */
@Composable
fun OnboardingScreen(vm: ClipForgeViewModel) {
    var pat by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("clipforge-clone") }
    var create by remember { mutableStateOf(false) }
    val progress by vm.cloneProgress.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(EngineIcons.Bolt, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("ClipForge", style = MaterialTheme.typography.headlineMedium)
        Text("Connect to your pipeline clone", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(pat, { pat = it }, label = { Text("GitHub PAT (repo + workflow)") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        if (!create) {
            OutlinedTextField(repo, { repo = it }, label = { Text("Clone repo (owner/repository)") },
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Button(onClick = { vm.connectExisting(pat, repo) }, Modifier.fillMaxWidth()) { Text("Connect existing clone") }
        } else {
            OutlinedTextField(newName, { newName = it }, label = { Text("New private repo name") },
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Button(onClick = { vm.createClone(pat, newName) }, Modifier.fillMaxWidth()) { Text("Create & start clone") }
        }
        TextButton(onClick = { create = !create }) {
            Text(if (create) "Have a clone already? Connect it" else "New here? Create a private clone")
        }
        progress?.let {
            Spacer(Modifier.height(16.dp)); LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/* ---------------- main nav shell ---------------- */
@Composable
fun MainNav(nav: NavHostController, vm: ClipForgeViewModel) {
    NavHost(nav, startDestination = "tasks") {
        composable("tasks") { TasksScreen(vm, onTask = { nav.navigate("task/$it") },
            onSeries = { nav.navigate("series/$it") }, onNew = { nav.navigate("new") },
            onMusic = { nav.navigate("music") }, onSettings = { nav.navigate("settings") }) }
        composable("task/{jobId}") { back ->
            val id = back.arguments?.getString("jobId") ?: return@composable
            TaskDetailScreen(vm, id, onBack = { nav.popBackStack() })
        }
        composable("series/{seriesId}") { back ->
            val id = back.arguments?.getString("seriesId") ?: return@composable
            SeriesScreen(vm, id, onBack = { nav.popBackStack() }, onTask = { nav.navigate("task/$it") })
        }
        composable("new") { NewTaskWizard(vm, onDone = { nav.popBackStack() }) }
        composable("music") { MusicScreen(vm, onBack = { nav.popBackStack() }) }
        composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
    }
}

/* ---------------- task list + series + multi-select delete ---------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    vm: ClipForgeViewModel, onTask: (String) -> Unit,
    onSeries: (String) -> Unit, onNew: () -> Unit, onMusic: () -> Unit, onSettings: () -> Unit,
) {
    LaunchedEffect(Unit) { vm.onTasksOpen() }
    val tasks by vm.tasks.collectAsState()
    val refreshing by vm.tasksRefreshing.collectAsState()
    val upload by vm.upload.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    val plain = tasks.filter { !it.seriesEnabled }
    val series = tasks.filter { it.seriesEnabled }.groupBy { it.seriesId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected.isEmpty()) "ClipForge" else "${selected.size} selected") },
                actions = {
                    if (selected.isNotEmpty()) {
                        TextButton(onClick = { vm.deleteTasks(selected); selected = emptySet() }) { Text("Delete") }
                    } else {
                        IconButton(onClick = onMusic) { Icon(EngineIcons.Home, "music") }
                        IconButton(onClick = onSettings) { Icon(EngineIcons.Settings, "settings") }
                    }
                },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = onNew) { Icon(EngineIcons.Add, "new task") } },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            upload?.let {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("Uploading ${it.label}", style = MaterialTheme.typography.labelSmall)
                    LinearProgressIndicator(progress = { it.fraction }, Modifier.fillMaxWidth())
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Tasks", style = MaterialTheme.typography.titleMedium) }
                items(plain) { t ->
                    TaskCard(t, selected.contains(t.jobId),
                        onLong = { selected = if (selected.contains(t.jobId)) selected - t.jobId else selected + t.jobId },
                        onClick = { if (selected.isNotEmpty()) selected = if (selected.contains(t.jobId)) selected - t.jobId else selected + t.jobId else onTask(t.jobId) })
                }
                item { Spacer(Modifier.height(8.dp)); Text("Series", style = MaterialTheme.typography.titleMedium) }
                items(series.entries.toList()) { (seriesId, parts) ->
                    Card(Modifier.fillMaxWidth().clickable { onSeries(seriesId) }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(seriesId, style = MaterialTheme.typography.titleSmall)
                            Text("${parts.size} part(s) — latest: ${Pipeline.describe(parts.maxByOrNull { it.part }?.state ?: "")}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskCard(t: TaskStatus, checked: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.jobId, style = MaterialTheme.typography.titleSmall)
                Text(Pipeline.describe(t.state) + if (t.message.isNotBlank()) " — ${t.message}" else "",
                    style = MaterialTheme.typography.bodySmall)
                if (!t.terminal) LinearProgressIndicator(
                    progress = { Pipeline.progressFraction(t.state) },
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
            if (checked) Icon(EngineIcons.Add, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/* ---------------- task detail: live polling log + save + production.json ---------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(vm: ClipForgeViewModel, jobId: String, onBack: () -> Unit) {
    val detail by vm.detail.collectAsState()
    val log by vm.detailLog.collectAsState()
    val steps by vm.steps.collectAsState()
    val save by vm.save.collectAsState()
    var planText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(jobId) { vm.pollTask(jobId) }

    // SafeSave: user picks where the finished video lands (Movies/ClipForge suggested by name).
    var pendingSave by remember { mutableStateOf<TaskStatus?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        if (uri != null) {
            SafeSave.takePersistablePermission(context, uri)
            pendingSave?.let { vm.saveVideo(it, uri) }
        }
        pendingSave = null
    }
    val planPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
            vm.uploadBytes("jobs/$jobId/production.json", bytes, "production.json for $jobId")
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(jobId) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(EngineIcons.ArrowBack, "back") } },
            actions = { if (detail?.terminal == false) TextButton(onClick = { vm.cancelTask() }) { Text("Cancel") } })
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            detail?.let { s ->
                Text(Pipeline.describe(s.state), style = MaterialTheme.typography.titleLarge)
                if (s.message.isNotBlank()) Text(s.message)
                if (!s.terminal) LinearProgressIndicator(
                    progress = { Pipeline.progressFraction(s.state) }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Spacer(Modifier.height(12.dp))

                if (s.state == "awaiting_plan") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("production.json", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(planText, { planText = it },
                                label = { Text("Paste raw production.json") },
                                modifier = Modifier.fillMaxWidth().height(160.dp))
                            Spacer(Modifier.height(8.dp))
                            Row {
                                Button(onClick = { vm.submitPlan(jobId, planText) }, enabled = planText.isNotBlank()) { Text("Submit pasted plan") }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = { planPicker.launch("application/json") }) { Text("Upload file") }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (s.state == "complete") {
                    Button(onClick = { pendingSave = s; saver.launch("${s.jobId}.mp4") }, Modifier.fillMaxWidth()) {
                        Text("Save video to Movies/ClipForge")
                    }
                    if (save.active) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { save.fraction }, Modifier.fillMaxWidth())
                        Text(save.label, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (steps.isNotEmpty()) {
                    Text("Pipeline steps", style = MaterialTheme.typography.titleSmall)
                    steps.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(8.dp))
                }
                Text("Live log", style = MaterialTheme.typography.titleSmall)
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Text(log.ifBlank { "…" }, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

/* ---------------- series view ---------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(vm: ClipForgeViewModel, seriesId: String, onBack: () -> Unit, onTask: (String) -> Unit) {
    val tasks by vm.tasks.collectAsState()
    val parts = tasks.filter { it.seriesId == seriesId }.sortedBy { it.part }
    val latest = parts.lastOrNull()
    Scaffold(topBar = {
        TopAppBar(title = { Text(seriesId) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(EngineIcons.ArrowBack, "back") } })
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(parts) { p -> TaskCard(p, false, onClick = { onTask(p.jobId) }, onLong = {}) }
            item {
                if (latest != null && latest.state == "complete") {
                    Button(onClick = { vm.dispatchNextPart(latest, latest.startSeconds + 60) }, Modifier.fillMaxWidth()) {
                        Text("Continue: dispatch part ${latest.part + 1}")
                    }
                }
            }
        }
    }
}

/* ---------------- new-task wizard (source → focus → length → music → confirm) ---------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskWizard(vm: ClipForgeViewModel, onDone: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    var series by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf("url") }
    var value by remember { mutableStateOf("") }
    var torrent by remember { mutableStateOf<ByteArray?>(null) }
    var focus by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("60") }
    var musicChoice by remember { mutableStateOf("default") }
    var musicRef by remember { mutableStateOf("") }
    val music by vm.music.collectAsState()
    val context = LocalContext.current
    val torrentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        torrent = context.contentResolver.openInputStream(uri)?.readBytes()
        if (torrent != null) kind = "torrent_file"
    }
    LaunchedEffect(Unit) { vm.onMusicOpen(); vm.loadDefaultMusic() }

    // series skips focus (wizard.js stepsFor)
    val steps = remember(series) { if (series) listOf("source", "length", "music", "confirm") else listOf("source", "focus", "length", "music", "confirm") }

    Scaffold(topBar = {
        TopAppBar(title = { Text("New task — ${steps[step]}") },
            navigationIcon = { IconButton(onClick = { if (step > 0) step-- else onDone() }) { Icon(EngineIcons.ArrowBack, "back") } })
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(20.dp)) {
            LinearProgressIndicator(progress = { (step + 1).toFloat() / steps.size }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            when (steps[step]) {
                "source" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(series, { series = it }); Text("Series (multi-part)")
                    }
                    Spacer(Modifier.height(12.dp))
                    listOf("url", "drive", "magnet", "torrent_file").forEach { k ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = kind == k, onClick = { kind = k })
                            Text(k.replace('_', ' '), Modifier.clickable { kind = k })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    when (kind) {
                        "torrent_file" -> OutlinedButton(onClick = { torrentPicker.launch("*/*") }) {
                            Text(if (torrent == null) "Pick .torrent file" else "Torrent ready (${torrent!!.size / 1024} KB)")
                        }
                        else -> OutlinedTextField(value, { value = it },
                            label = { Text(if (kind == "magnet") "Magnet URI" else "Source URL") },
                            modifier = Modifier.fillMaxWidth())
                    }
                }
                "focus" -> OutlinedTextField(focus, { focus = it },
                    label = { Text("Editorial focus (blank = whole video)") }, modifier = Modifier.fillMaxWidth())
                "length" -> OutlinedTextField(duration, { duration = it.filter(Char::isDigit) },
                    label = { Text("Target duration (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                "music" -> {
                    listOf("none", "default", "explicit_library").forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = musicChoice == m, onClick = { musicChoice = m })
                            Text(m.replace('_', ' '), Modifier.clickable { musicChoice = m })
                        }
                    }
                    if (musicChoice == "explicit_library") {
                        music.forEach { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = musicRef == t.path, onClick = { musicRef = t.path })
                                Text(t.name, Modifier.clickable { musicRef = t.path })
                            }
                        }
                    }
                }
                "confirm" -> {
                    Text("Source: $kind"); if (!series) Text("Focus: ${focus.ifBlank { "whole video" }}")
                    Text("Duration: ${duration}s"); Text("Music: $musicChoice")
                    Text(if (series) "Series: yes (part 1)" else "Series: no")
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                if (step < steps.size - 1) step++
                else {
                    vm.createTask(kind, value, torrent, focus, duration.toIntOrNull() ?: 60,
                        musicRef, musicChoice, series, Voices.DEFAULT)
                    onDone()
                }
            }, Modifier.fillMaxWidth()) { Text(if (step < steps.size - 1) "Next" else "Create task") }
        }
    }
}

/* ---------------- music library: multi-select, upload w/ progress, default ---------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(vm: ClipForgeViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { vm.onMusicOpen(); vm.loadDefaultMusic() }
    val music by vm.music.collectAsState()
    val refreshing by vm.musicRefreshing.collectAsState()
    val def by vm.defaultMusic.collectAsState()
    val upload by vm.upload.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "track.m4a"
        vm.uploadMusic(name, bytes)
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (selected.isEmpty()) "Music library" else "${selected.size} selected") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(EngineIcons.ArrowBack, "back") } },
            actions = {
                if (selected.isNotEmpty()) TextButton(onClick = {
                    vm.deleteMusic(music.filter { selected.contains(it.path) }.toSet()); selected = emptySet()
                }) { Text("Delete") }
            })
    }) { pad ->
        Column(Modifier.padding(pad)) {
            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            upload?.let {
                Column(Modifier.padding(16.dp)) {
                    Text("Uploading ${it.label}", style = MaterialTheme.typography.labelSmall)
                    LinearProgressIndicator(progress = { it.fraction }, Modifier.fillMaxWidth())
                }
            }
            Button(onClick = { picker.launch("audio/*") }, Modifier.padding(16.dp).fillMaxWidth()) { Text("Upload track") }
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(music) { t ->
                    Card(Modifier.fillMaxWidth().clickable {
                        selected = if (selected.contains(t.path)) selected - t.path else selected + t.path
                    }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = MaterialTheme.typography.titleSmall)
                                Text("${t.size / 1024} KB" + if (def == t.path) " — default" else "",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = {
                                vm.login.value?.let { AudioPreview.toggle(context, vm.audioPreviewUrl(t.path), it.pat) }
                            }) { Text("Play") }
                            TextButton(onClick = { vm.setDefaultMusic(if (def == t.path) null else t.path) }) {
                                Text(if (def == t.path) "Unset" else "Set default")
                            }
                            if (selected.contains(t.path)) Icon(EngineIcons.Add, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- settings ---------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: ClipForgeViewModel, onBack: () -> Unit) {
    val login by vm.login.collectAsState()
    var voice by remember { mutableStateOf(Voices.DEFAULT) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(EngineIcons.ArrowBack, "back") } })
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(20.dp)) {
            login?.let {
                Text("Clone", style = MaterialTheme.typography.titleSmall)
                Text("${it.owner}/${it.repo}", style = MaterialTheme.typography.bodyMedium)
                Text("Signed in as ${it.login} — PAT ${it.pat.take(8)}••••••••", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
            Text("Narration voice (TTS)", style = MaterialTheme.typography.titleSmall)
            Voices.ALL.forEach { (id, label, style) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = voice == id, onClick = { voice = id })
                    Column(Modifier.clickable { voice = id }) {
                        Text(label); Text(style, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { vm.signOut(); onBack() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Sign out (clear clone credentials)")
            }
        }
    }
}
