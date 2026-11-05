package com.motionsalt.ccstudio.ui.packs

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgebuild.engine.ui.icons.EngineIcons
import com.motionsalt.ccstudio.core.PackBuilder
import com.motionsalt.ccstudio.core.SuiteInfo
import com.motionsalt.ccstudio.data.EffectRepository
import java.io.File
import java.io.FileOutputStream

@Composable
fun PacksScreen(
    effectRepository: EffectRepository
) {
    val context = LocalContext.current
    val availableEffects = remember { effectRepository.getAvailableEffects() }
    var exportStatusMessage by remember { mutableStateOf<String?>(null) }
    var mergeStatusMessage by remember { mutableStateOf<String?>(null) }

    val packPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val result = PackBuilder.validatePack(stream)
                        if (result.isValid) {
                            mergeStatusMessage = "Validated pack: '${result.suiteInfo?.name}' with ${result.effectIds.size} effects (${result.effectIds.joinToString(", ")}). Ready to merge!"
                        } else {
                            mergeStatusMessage = "Pack validation issues: ${result.errors.joinToString("; ")}"
                        }
                    }
                } catch (e: Exception) {
                    mergeStatusMessage = "Failed to read pack: ${e.message}"
                }
            }
        }
    )

    fun exportBuiltinSuite() {
        try {
            val suiteInfo = SuiteInfo(
                id = "com.motionsalt.effects",
                name = "MOTIONSALT",
                author = "MOTIONSALT",
                brand = "MOTIONSALT",
                version = "1.4.9",
                description = "Complete 17-effect professional color suite for Keyflaw",
                category = "MOTIONSALT"
            )

            val cacheFile = File(context.cacheDir, "MOTIONSALT.kfpack")
            FileOutputStream(cacheFile).use { fos ->
                PackBuilder.buildPack(suiteInfo, availableEffects, fos)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "MOTIONSALT.kfpack")
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MOTIONSALT/CC Studio")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        cacheFile.inputStream().copyTo(out)
                    }
                }
            } else {
                val dlDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MOTIONSALT/CC Studio")
                if (!dlDir.exists()) dlDir.mkdirs()
                cacheFile.copyTo(File(dlDir, "MOTIONSALT.kfpack"), overwrite = true)
            }

            exportStatusMessage = "Saved MOTIONSALT.kfpack (17 effects) to Downloads/MOTIONSALT/CC Studio/!"
        } catch (e: Exception) {
            exportStatusMessage = "Export failed: ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Keyflaw Shader Packs", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Manage and export standalone .kfpack shader bundles for Keyflaw 1.4.9.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(EngineIcons.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Text("MOTIONSALT Suite (Built-in)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(
                            "17 Hand-Crafted Effects • GLSL ES 1.00 compliant • Ready for Keyflaw video timeline.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { exportBuiltinSuite() }
                        ) {
                            Icon(EngineIcons.FileDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Export MOTIONSALT.kfpack")
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Merge & Validate External Pack", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Pick an external .kfpack from Downloads or phone storage to inspect and merge into your MOTIONSALT suite.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { packPickerLauncher.launch("*/*") }
                        ) {
                            Icon(EngineIcons.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Pick .kfpack to Merge")
                        }
                    }
                }
            }
        }

        exportStatusMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { exportStatusMessage = null },
                title = { Text("Export Status") },
                text = { Text(msg) },
                confirmButton = {
                    Button(onClick = { exportStatusMessage = null }) { Text("OK") }
                }
            )
        }

        mergeStatusMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { mergeStatusMessage = null },
                title = { Text("Pack Inspector") },
                text = { Text(msg) },
                confirmButton = {
                    Button(onClick = { mergeStatusMessage = null }) { Text("Done") }
                }
            )
        }
    }
}
