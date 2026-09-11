package com.forgebuild.clipforge.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forgebuild.clipforge.data.ClipSegment
import com.forgebuild.clipforge.data.ProductionPlan
import com.forgebuild.engine.ui.icons.EngineIcons

@OptIn(ExperimentalMaterial3Api::class)
/** New clip wizard — mirrors the bot's /new flow: URL → cloud pipeline, or local video → on-device Stage B. */
@Composable
fun NewJobScreen(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    var title by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    var startText by remember { mutableStateOf("0") }
    var endText by remember { mutableStateOf("30") }
    var caption by remember { mutableStateOf("") }
    var localUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var localMode by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { localUri = uri; localMode = true }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New clip") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(EngineIcons.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(value = title, onValueChange = { title = it },
                label = { Text("Clip title") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text("Source", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = sourceUrl,
                onValueChange = { sourceUrl = it; if (it.isNotBlank()) localMode = false },
                label = { Text("Direct https:// video URL (cloud pipeline)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                enabled = localUri == null
            )
            Text("or", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
                Icon(EngineIcons.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(localUri?.let { "Video selected" } ?: "Pick a local video (on-device render)")
            }

            if (localMode) {
                Text("Segment (seconds)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = startText, onValueChange = { startText = it },
                        label = { Text("Start") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = endText, onValueChange = { endText = it },
                        label = { Text("End") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = caption, onValueChange = { caption = it },
                    label = { Text("Caption (burned in)") }, modifier = Modifier.fillMaxWidth())
            }

            state.renderStatus?.let {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            state.renderOutput?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Clip rendered", style = MaterialTheme.typography.titleSmall)
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Button(
                onClick = {
                    if (localMode && localUri != null) {
                        val s = startText.toFloatOrNull() ?: 0f
                        val e = endText.toFloatOrNull() ?: (s + 30f)
                        val plan = ProductionPlan(
                            title = title.ifBlank { "ClipForge clip" },
                            segments = listOf(ClipSegment(s, e, caption)),
                            captionsEnabled = true,
                            watermarkText = state.settings.watermarkText,
                            vertical = state.settings.verticalReframe
                        )
                        vm.renderLocal(localUri!!, plan)
                    } else {
                        vm.createJob(title.ifBlank { "ClipForge clip" }, sourceUrl.trim())
                        onBack()
                    }
                },
                enabled = title.isNotBlank() && (sourceUrl.isNotBlank() || localUri != null) && state.renderStatus == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(if (localMode) EngineIcons.ContentCut else EngineIcons.Send,
                    contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (localMode) "Render on device" else "Queue on clone (Stage A)")
            }
        }
    }
}
