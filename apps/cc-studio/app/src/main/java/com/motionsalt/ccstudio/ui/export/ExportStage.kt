package com.motionsalt.ccstudio.ui.export

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.forgebuild.engine.files.SafeSave
import com.motionsalt.ccstudio.core.*
import com.motionsalt.ccstudio.data.EffectRepository
import com.motionsalt.ccstudio.data.PackSave
import com.motionsalt.ccstudio.data.Project
import com.motionsalt.ccstudio.data.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

enum class PackMode {
    CREATE_NEW,
    ADD_TO_EXISTING,
    MERGE_IMPORTED
}

@Composable
fun ExportStage(project: Project, repository: ProjectRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(PackMode.CREATE_NEW) }
    var name by remember { mutableStateOf(project.title.ifBlank { "MOTIONSALT Look" }) }
    var suite by remember { mutableStateOf("MOTIONSALT") }
    val available = remember { EffectRepository(context).getAvailableEffects() }
    var imported by remember { mutableStateOf<ByteArray?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var collision by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var policy by remember { mutableStateOf(CollisionPolicy.REPLACE) }
    var saveProof by remember { mutableStateOf(false) }
    var saveJson by remember { mutableStateOf(false) }
    var tree by remember { mutableStateOf<Uri?>(null) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var savedPathDisplay by remember { mutableStateOf<String?>(null) }

    val local = File(context.filesDir, "packs/${suite}.kfpack")

    fun displayName(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else ""
        } ?: ""

    fun saveToPublicDownloads(bytes: ByteArray, filename: String): String? {
        return try {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CC_Studio")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cv.clear()
                    cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, cv, null, null)
                }
                "Downloads/CC_Studio/$filename"
            } else {
                val dlDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CC_Studio")
                dlDir.mkdirs()
                val f = File(dlDir, filename)
                f.writeBytes(bytes)
                f.absolutePath
            }
        } catch (e: Exception) {
            try {
                val dlDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CC_Studio")
                dlDir.mkdirs()
                val f = File(dlDir, filename)
                f.writeBytes(bytes)
                f.absolutePath
            } catch (_: Exception) {
                null
            }
        }
    }

    fun write(uri: Uri) {
        val bytes = pending ?: return
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    check(PackSave.writeToUri(context, uri, bytes)) { "Could not save pack to selected URI" }
                    local.parentFile?.mkdirs()
                    local.writeBytes(bytes)
                    tree?.let { folder ->
                        if (saveJson) {
                            val out = PackSave.createInTree(context, folder, "${name}_tune.json", "application/json")
                            if (out != null) PackSave.writeToUri(context, out, project.tuneState.toJsonString().toByteArray())
                        }
                        if (saveProof) {
                            val pair = project.pairs.firstOrNull { it.isComplete }
                            val proof = File(context.filesDir, "projects/${project.id}/results/recreated_${pair?.id}.png")
                            if (proof.exists()) {
                                val out = PackSave.createInTree(context, folder, "${name}_match_proof.png", "image/png")
                                if (out != null) PackSave.writeToUri(context, out, proof.readBytes())
                            }
                        }
                    }
                }
                savedUri = uri
                val nameDisp = displayName(uri).ifBlank { "${suite}.kfpack" }
                project.exportHistory.add(uri.toString())
                repository.saveProject(project)
                status = "Saved to: $nameDisp"
            } catch (e: Exception) {
                status = "Write error: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) r.data?.data?.let { write(it) }
    }

    val folder = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) r.data?.data?.let { uri ->
            tree = uri
            PackSave.persistGrant(context, uri)
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                }
                val v = PackBuilder.validatePack(ByteArrayInputStream(bytes))
                require(v.isValid) { v.errors.joinToString() }
                imported = bytes
                status = "Imported ${v.suiteInfo?.name}: ${v.effectIds.size} effects"
            } catch (e: Exception) {
                status = e.message
            }
        }
    }

    fun export(chosen: CollisionPolicy, launchPicker: Boolean = false) {
        scope.launch {
            busy = true
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val fit = project.fitResult
                    val model = if (fit != null && File(fit.modelPath).exists()) {
                        runCatching {
                            ParametricMatch.fromJson(JSONObject(File(fit.modelPath).readText()))
                        }.getOrDefault(ParametricMatch())
                    } else {
                        ParametricMatch()
                    }

                    val enabledItems = project.tuneState.stack.filter { it.enabled }
                    val defs = enabledItems.mapIndexed { i, item ->
                        if (item.effectId == "colour_match") {
                            MatchEffect.definition(
                                model = model,
                                name = name.ifBlank { "Colour Match" },
                                id = "look_" + project.id.replace("-", "").take(10),
                                params = item.params
                            )
                        } else {
                            val original = available.firstOrNull { it.id == item.effectId }
                                ?: error("Missing original effect: ${item.effectId}")
                            val tunedParams = original.parameters.map { p ->
                                val v = item.params[p.key] ?: p.defaultValue
                                p.copy(defaultValue = v)
                            }
                            val effId = if (i == 0) original.id else "${original.id}_$i"
                            PackBuilder.prepareEffect(
                                original.copy(
                                    id = effId,
                                    category = "MOTIONSALT",
                                    parameters = tunedParams
                                )
                            )
                        }
                    }
                    require(defs.isNotEmpty()) { "Enable at least one effect in your stack before exporting" }

                    val existing = when (mode) {
                        PackMode.CREATE_NEW -> null
                        PackMode.ADD_TO_EXISTING -> local.takeIf { it.exists() }?.readBytes()
                        PackMode.MERGE_IMPORTED -> imported ?: error("Import a pack first")
                    }

                    val out = ByteArrayOutputStream()
                    if (existing == null) {
                        PackBuilder.buildPack(SuiteInfo(name = suite), defs, out)
                    } else {
                        PackBuilder.mergePacks(ByteArrayInputStream(existing), defs, chosen, out)
                    }
                    val result = out.toByteArray()
                    val validation = PackBuilder.validatePack(ByteArrayInputStream(result))
                    require(validation.isValid) { validation.errors.joinToString("\n") }
                    result
                }

                pending = bytes

                // 1. Always save directly to public Downloads folder so it is immediately accessible
                val savedDlPath = withContext(Dispatchers.IO) {
                    saveToPublicDownloads(bytes, "${suite}.kfpack")
                }

                // 2. Cache file for sharing via FileProvider
                val shareUri = withContext(Dispatchers.IO) {
                    val cacheFile = File(context.cacheDir, "${suite}.kfpack")
                    cacheFile.writeBytes(bytes)
                    local.parentFile?.mkdirs()
                    local.writeBytes(bytes)
                    runCatching {
                        FileProvider.getUriForFile(context, "com.motionsalt.ccstudio.fileprovider", cacheFile)
                    }.getOrNull()
                }

                savedUri = shareUri
                savedPathDisplay = savedDlPath ?: "Downloads/CC_Studio/${suite}.kfpack"
                status = "Pack exported! Saved to $savedPathDisplay (${bytes.size / 1024} KB)"

                // 3. If user selected a custom SAF tree or requested picker, handle that
                if (launchPicker) {
                    tree?.let { uri ->
                        val out = PackSave.createInTree(context, uri, "${suite}.kfpack", "application/octet-stream")
                            ?: error("Cannot create pack in selected folder")
                        write(out)
                    } ?: save.launch(PackSave.createDocumentIntent("${suite}.kfpack", "application/octet-stream"))
                }
            } catch (e: Exception) {
                status = "Export error: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Export Keyflaw Pack (.kfpack)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Exports ready-to-use Keyflower effects with your tuned settings. Each effect includes an on/off toggle and live controls.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Pack Mode Selector
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PackMode.entries.forEach { p ->
                FilterChip(
                    selected = mode == p,
                    onClick = { mode = p },
                    label = {
                        Text(
                            when (p) {
                                PackMode.ADD_TO_EXISTING -> "Add to MOTIONSALT"
                                PackMode.CREATE_NEW -> "Create new pack"
                                PackMode.MERGE_IMPORTED -> "Merge imported"
                            }
                        )
                    }
                )
            }
        }

        if (mode == PackMode.MERGE_IMPORTED) {
            OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) {
                Text("Select .kfpack to Merge")
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Effect look name") },
            modifier = Modifier.fillMaxWidth()
        )

        if (mode == PackMode.CREATE_NEW) {
            OutlinedTextField(
                value = suite,
                onValueChange = { suite = it },
                label = { Text("Pack suite name") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Settings in Pack Info Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Settings in exported pack:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("• Enable Color Correction toggle + Strength, Matrix, Curves, Hue controls", style = MaterialTheme.typography.bodySmall)
                val otherItems = project.tuneState.stack.filter { it.enabled && it.effectId != "colour_match" }
                if (otherItems.isNotEmpty()) {
                    otherItems.forEach { item ->
                        val defName = available.firstOrNull { it.id == item.effectId }?.name ?: item.effectId
                        Text("• Enable $defName toggle + tuned parameters", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text("• Additional effects added in Tune stage will also get individual enable toggles and controls", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Export Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                enabled = !busy && name.isNotBlank(),
                onClick = {
                    if (mode != PackMode.CREATE_NEW && (imported != null || local.exists())) {
                        collision = true
                    } else {
                        export(policy, launchPicker = false)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (busy) "Exporting…" else "Export to Downloads")
            }

            OutlinedButton(
                enabled = !busy && name.isNotBlank(),
                onClick = {
                    if (mode != PackMode.CREATE_NEW && (imported != null || local.exists())) {
                        collision = true
                    } else {
                        export(policy, launchPicker = true)
                    }
                }
            ) {
                Text("Custom Folder (SAF)")
            }
        }

        // Success / Status display
        status?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (msg.startsWith("Export error") || msg.startsWith("Write error"))
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (msg.startsWith("Export error") || msg.startsWith("Write error"))
                            MaterialTheme.colorScheme.onErrorContainer
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    savedPathDisplay?.let { p ->
                        Text(
                            text = "Find in: $p",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Share & Open in Keyflaw buttons
        savedUri?.let { uri ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Use your pack:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                runCatching {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share .kfpack"))
                                }.onFailure { status = "Share error: ${it.message}" }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Share Pack")
                        }

                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/octet-stream")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(viewIntent)
                                }.onFailure {
                                    status = "Keyflaw app not registered for direct open; import the file from Keyflaw: $savedPathDisplay"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Open in Keyflaw")
                        }
                    }
                }
            }
        }

        // Folder selection details
        OutlinedButton(
            onClick = { folder.launch(PackSave.openTreeIntent()) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (tree == null) "Pick Optional SAF Target Folder" else "Selected Folder: ${tree?.lastPathSegment}")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = saveProof, onCheckedChange = { saveProof = it })
            Text("Save colour-match proof PNG with export", fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = saveJson, onCheckedChange = { saveJson = it })
            Text("Save tune-state JSON with export", fontSize = 13.sp)
        }
    }

    if (collision) {
        AlertDialog(
            onDismissRequest = { collision = false },
            title = { Text("Effect ID collision policy") },
            text = {
                Column {
                    Text("Apply to duplicate IDs only; other existing effects are preserved.")
                    TextButton(
                        onClick = {
                            collision = false
                            policy = CollisionPolicy.KEEP_BOTH_NEW_ID
                            export(policy)
                        }
                    ) {
                        Text("Keep both with new ID")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        collision = false
                        policy = CollisionPolicy.REPLACE
                        export(policy)
                    }
                ) {
                    Text("Replace matching IDs")
                }
            },
            dismissButton = {
                TextButton(onClick = { collision = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
