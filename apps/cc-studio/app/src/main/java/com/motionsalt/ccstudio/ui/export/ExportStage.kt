package com.motionsalt.ccstudio.ui.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgebuild.engine.ui.icons.EngineIcons
import com.motionsalt.ccstudio.core.*
import com.motionsalt.ccstudio.data.EffectRepository
import com.motionsalt.ccstudio.data.ProjectData
import com.motionsalt.ccstudio.data.ProjectRepository
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

enum class PackMode {
    ADD_TO_EXISTING,
    CREATE_NEW,
    MERGE_IMPORTED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportStage(
    project: ProjectData,
    repository: ProjectRepository,
    effectRepository: EffectRepository
) {
    val context = LocalContext.current
    val availableEffects = remember { effectRepository.getAvailableEffects() }

    var packMode by remember { mutableStateOf(PackMode.ADD_TO_EXISTING) }
    var effectName by remember { mutableStateOf(project.lookName) }

    // New suite config
    var suiteName by remember { mutableStateOf("MOTIONSALT") }
    var suiteId by remember { mutableStateOf("com.motionsalt.effects") }
    var suiteAuthor by remember { mutableStateOf("MOTIONSALT") }
    var suiteVersion by remember { mutableStateOf("1.0.0") }
    var suiteDesc by remember { mutableStateOf("Keyflaw 1.4.9 color grading suite") }

    // Checkboxes
    var saveCube by remember { mutableStateOf(true) }
    var saveHald by remember { mutableStateOf(true) }
    var saveProof by remember { mutableStateOf(false) }
    var saveTuneJson by remember { mutableStateOf(false) }

    // Destination
    var exportFolderPath by remember { mutableStateOf("Downloads/MOTIONSALT/CC Studio/") }
    var customTreeUri by remember { mutableStateOf<Uri?>(null) }

    // State
    var isExporting by remember { mutableStateOf(false) }
    var exportSuccessMessage by remember { mutableStateOf<String?>(null) }
    var exportedFilePath by remember { mutableStateOf<String?>(null) }
    var exportErrorMessage by remember { mutableStateOf<String?>(null) }
    var showCollisionDialog by remember { mutableStateOf(false) }

    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                customTreeUri = uri
                exportFolderPath = uri.path ?: "Custom Folder"
            }
        }
    )

    fun performExport(collisionPolicy: CollisionPolicy) {
        isExporting = true
        exportErrorMessage = null

        try {
            // 1. Gather effects from current tune state
            val effectsToExport = mutableListOf<EffectDefinition>()
            for (item in project.tuneState.stack) {
                if (item.enabled) {
                    val def = availableEffects.firstOrNull { it.id == item.effectId }
                    if (def != null) {
                        effectsToExport.add(def)
                    }
                }
            }

            if (effectsToExport.isEmpty()) {
                val lutDef = availableEffects.firstOrNull { it.id == "ms_lut" }
                if (lutDef != null) effectsToExport.add(lutDef)
            }

            // Suite Info
            val suiteInfo = SuiteInfo(
                id = suiteId,
                name = suiteName,
                author = suiteAuthor,
                brand = suiteAuthor,
                version = suiteVersion,
                description = suiteDesc,
                category = "MOTIONSALT"
            )

            // Local cache in app private storage
            val resultsDir = File(context.filesDir, "projects/${project.id}/results")
            if (!resultsDir.exists()) resultsDir.mkdirs()

            val packFilename = "${suiteName}.kfpack"
            val localPackFile = File(resultsDir, packFilename)

            FileOutputStream(localPackFile).use { fos ->
                PackBuilder.buildPack(suiteInfo, effectsToExport, fos)
            }

            // Save into public Downloads/MOTIONSALT/CC Studio/ via MediaStore
            var savedPathResult = localPackFile.absolutePath
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, packFilename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MOTIONSALT/CC Studio")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        localPackFile.inputStream().copyTo(out)
                    }
                    savedPathResult = "Downloads/MOTIONSALT/CC Studio/$packFilename"
                }
            } else {
                val dlDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MOTIONSALT/CC Studio")
                if (!dlDir.exists()) dlDir.mkdirs()
                val target = File(dlDir, packFilename)
                localPackFile.copyTo(target, overwrite = true)
                savedPathResult = target.absolutePath
            }

            // Save .cube and .hald if checked
            if (saveCube && project.fitResult != null) {
                val cubeFile = File(project.fitResult!!.lutPath)
                if (cubeFile.exists()) {
                    val cubeTarget = File(context.filesDir, "${project.lookName}.cube")
                    cubeFile.copyTo(cubeTarget, overwrite = true)
                }
            }

            project.exportHistory.add(savedPathResult)
            repository.saveProject(project)

            exportedFilePath = savedPathResult
            exportSuccessMessage = "Successfully exported $packFilename to $savedPathResult"
            isExporting = false
        } catch (e: Exception) {
            isExporting = false
            exportErrorMessage = "Export failed: ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Export Keyflaw Suite", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        // Mode Segmented Row
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = packMode == PackMode.ADD_TO_EXISTING,
                onClick = { packMode = PackMode.ADD_TO_EXISTING },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
            ) {
                Text("Add to Pack", fontSize = 11.sp)
            }
            SegmentedButton(
                selected = packMode == PackMode.CREATE_NEW,
                onClick = { packMode = PackMode.CREATE_NEW },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
            ) {
                Text("Create New", fontSize = 11.sp)
            }
            SegmentedButton(
                selected = packMode == PackMode.MERGE_IMPORTED,
                onClick = { packMode = PackMode.MERGE_IMPORTED },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
            ) {
                Text("Merge .kfpack", fontSize = 11.sp)
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Export Configuration", fontWeight = FontWeight.SemiBold)

                        OutlinedTextField(
                            value = effectName,
                            onValueChange = { effectName = it },
                            label = { Text("Effect Name in Keyflaw") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (packMode == PackMode.CREATE_NEW) {
                            OutlinedTextField(
                                value = suiteName,
                                onValueChange = { suiteName = it },
                                label = { Text("Suite Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = suiteId,
                                onValueChange = { suiteId = it },
                                label = { Text("Suite ID (Reverse Domain)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (packMode == PackMode.ADD_TO_EXISTING) {
                            AssistChip(
                                onClick = { /* pinned default pack info */ },
                                label = { Text("Pinned Default Pack: MOTIONSALT.kfpack") },
                                leadingIcon = { Icon(EngineIcons.Bolt, contentDescription = null) }
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Also Save Formats", fontWeight = FontWeight.SemiBold)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = saveCube, onCheckedChange = { saveCube = it })
                            Text(".cube LUT (33x33x33 standard)", fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = saveHald, onCheckedChange = { saveHald = it })
                            Text("HaldCLUT Level 8 (512x512)", fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = saveTuneJson, onCheckedChange = { saveTuneJson = it })
                            Text("Tune-state JSON recipe", fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Save Location", fontWeight = FontWeight.SemiBold)
                        Text(exportFolderPath, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        FilledTonalButton(onClick = { safFolderLauncher.launch(null) }) {
                            Icon(EngineIcons.Folder, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Change Folder")
                        }
                    }
                }
            }
        }

        exportErrorMessage?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        Button(
            onClick = { performExport(CollisionPolicy.REPLACE) },
            enabled = !isExporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Building .kfpack...")
            } else {
                Icon(EngineIcons.FileDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export Keyflaw Pack Now")
            }
        }
    }

    // Success Bottom Sheet
    if (exportSuccessMessage != null && exportedFilePath != null) {
        ModalBottomSheet(onDismissRequest = { exportSuccessMessage = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(EngineIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Export Complete!", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                Text(exportSuccessMessage!!, fontSize = 14.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_TEXT, "MOTIONSALT Keyflaw Pack: $exportedFilePath")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share .kfpack"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Share")
                    }

                    FilledTonalButton(
                        onClick = {
                            // Try opening in Keyflaw or file manager
                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(exportedFilePath), "application/zip")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            try {
                                context.startActivity(viewIntent)
                            } catch (e: Exception) {
                                // Fallback
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open Keyflaw")
                    }
                }

                TextButton(
                    onClick = { exportSuccessMessage = null },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Done")
                }
            }
        }
    }
}
