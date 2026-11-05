package com.motionsalt.ccstudio.ui.export

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.MediaScannerConnection
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
import com.motionsalt.ccstudio.data.ProjectData
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
fun ExportStage(project: ProjectData, repository: ProjectRepository, effectRepository: EffectRepository? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(PackMode.CREATE_NEW) }
    var name by remember { mutableStateOf<String>(project.lookName.ifBlank { project.name.ifBlank { "MOTIONSALT Look" } }) }
    var suite by remember { mutableStateOf<String>("MOTIONSALT") }
    val available = remember { (effectRepository ?: EffectRepository(context)).getAvailableEffects() }
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
        val safeFilename = if (filename.endsWith(".kfpack")) filename else "$filename.kfpack"
        var writtenPath: String? = null

        // 1. Direct file write: saves both in Downloads/CC_Studio/ and root Downloads/
        try {
            val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (dlDir != null && (dlDir.exists() || dlDir.mkdirs())) {
                val subDir = File(dlDir, "CC_Studio").apply { mkdirs() }
                val targetFile = File(subDir, safeFilename)
                targetFile.writeBytes(bytes)
                writtenPath = "Downloads/CC_Studio/$safeFilename"

                val rootFile = File(dlDir, safeFilename)
                runCatching { rootFile.writeBytes(bytes) }

                runCatching {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetFile.absolutePath, rootFile.absolutePath),
                        arrayOf("application/octet-stream", "application/octet-stream"),
                        null
                    )
                }
            }
        } catch (_: Exception) {
            // Scoped storage fallback
        }

        // 2. Also register via MediaStore with exact .kfpack extension and octet-stream MIME
        try {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeFilename)
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
                if (writtenPath == null) {
                    writtenPath = "Downloads/CC_Studio/$safeFilename"
                }
            }
        } catch (_: Exception) {
        }

        return writtenPath ?: "Downloads/CC_Studio/$safeFilename"
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

                    // Export all stack effects so every effect added in Tune stage is present in the pack,
                    // with its exact tuned parameters and enabled/disabled state preserved.
                    val stack = project.tuneState.stack
                    require(stack.isNotEmpty()) { "Stack must not be empty" }

                    val usedIds = mutableMapOf<String, Int>()
                    val defs = stack.map { item ->
                        if (item.effectId == "colour_match") {
                            val ccId = "look_" + project.id.replace("-", "").take(10)
                            val ccName = name.ifBlank { project.lookName.ifBlank { project.name.ifBlank { "Color Correction" } } }
                            MatchEffect.definition(
                                model = model,
                                name = ccName,
                                id = ccId,
                                params = item.params,
                                isEnabled = item.enabled
                            )
                        } else {
                            val original = available.firstOrNull { it.id == item.effectId }
                                ?: error("Missing original effect: ${item.effectId}")

                            val tunedParams = original.parameters.map { p ->
                                val tunedVal = item.params[p.key] ?: p.defaultValue
                                val finalVal = if (p.key == "enabled") {
                                    if (item.enabled) PackBuilder.toEffectDefaultValue(tunedVal, ParamType.BOOL, 1.0) else 0.0
                                } else {
                                    PackBuilder.toEffectDefaultValue(tunedVal, p.type, p.defaultValue)
                                }
                                p.copy(defaultValue = finalVal)
                            }

                            val count = usedIds.getOrDefault(original.id, 0)
                            usedIds[original.id] = count + 1
                            val effId = if (count == 0) original.id else "${original.id}_${count + 1}"

                            PackBuilder.prepareEffect(
                                original.copy(
                                    id = effId,
                                    category = suite,
                                    parameters = tunedParams,
                                    passes = original.passes,
                                    passShaders = original.passShaders
                                ),
                                suiteCategory = suite,
                                isEnabled = item.enabled
                            )
                        }
                    }

                    require(defs.isNotEmpty()) { "At least one effect must be present in the pack" }

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
                savedPathDisplay = savedDlPath
                status = "Pack exported successfully to $savedDlPath (${bytes.size} bytes, ${project.tuneState.stack.size} effects wired)"

                if (launchPicker) {
                if (launchPicker) {
                    tree?.let { uri ->
                        val out = PackSave.createInTree(context, uri, "${suite}.kfpack", "application/octet-stream")
                            ?: error("Cannot create pack in selected folder")
                        write(out)
                    } ?: save.launch(PackSave.createDocumentIntent("${suite}.kfpack", "application/octet-stream"))
                }
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Export Keyflaw Pack (.kfpack)", style = MaterialTheme.typography.titleLarge)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pack Destination Mode", style = MaterialTheme.typography.labelLarge)
                SegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        onClick = { mode = PackMode.CREATE_NEW },
                        selected = mode == PackMode.CREATE_NEW
                    ) {
                        Text("New Pack", fontSize = 12.sp)
                    }
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        onClick = { mode = PackMode.ADD_TO_EXISTING },
                        selected = mode == PackMode.ADD_TO_EXISTING
                    ) {
                        Text("Add Existing", fontSize = 12.sp)
                    }
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        onClick = { mode = PackMode.MERGE_IMPORTED },
                        selected = mode == PackMode.MERGE_IMPORTED
                    ) {
                        Text("Merge Pack", fontSize = 12.sp)
                    }
                }
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
            label = { Text("Color correction look name") },
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
                Text("Effects & settings wired in exported pack:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                project.tuneState.stack.forEach { item ->
                    val isCC = item.effectId == "colour_match"
                    val def = if (isCC) MatchEffect.definition() else available.firstOrNull { it.id == item.effectId }
                    val defName = if (isCC) name.ifBlank { "Color Correction" } else (def?.name ?: item.effectId)
                    val statusPrefix = if (item.enabled) "✓" else "✗ (disabled by default)"
                    val tunedDetails = def?.parameters?.filter { it.key != "enabled" }?.map { p ->
                        val v = item.params[p.key] ?: p.defaultValue
                        val vStr = if (v is Number) String.format(java.util.Locale.US, "%.1f", v.toFloat()) else v.toString()
                        "${p.label}: $vStr"
                    }?.joinToString(", ") ?: ""
                    val suffix = if (tunedDetails.isNotBlank()) " [$tunedDetails]" else ""
                    Text("• $statusPrefix $defName$suffix", style = MaterialTheme.typography.bodySmall)
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
