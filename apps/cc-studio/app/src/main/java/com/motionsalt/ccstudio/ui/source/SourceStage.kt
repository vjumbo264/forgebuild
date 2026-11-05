package com.motionsalt.ccstudio.ui.source

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.forgebuild.engine.ui.icons.EngineIcons
import com.motionsalt.ccstudio.data.*
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceStage(
    project: ProjectData,
    repository: ProjectRepository,
    onContinueToFit: () -> Unit
) {
    val context = LocalContext.current

    // Observe pairs directly via snapshot state list for instant reactivity
    val pairsList = remember(project.id) {
        mutableStateListOf<FramePair>().apply { addAll(project.pairs) }
    }

    var showSourceSheet by remember { mutableStateOf(false) }
    var activeTargetPairIndex by remember { mutableStateOf<Int?>(null) }
    var isPickingTargetFrame by remember { mutableStateOf(false) } // false = base, true = target
    var cropEditorPair by remember { mutableStateOf<Pair<Int, Boolean>?>(null) } // pairIndex, isTarget
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var permissionExplanationDialog by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    fun syncAndSave() {
        project.pairs.clear()
        project.pairs.addAll(pairsList)
        repository.saveProject(project)
    }

    fun handleImageUri(uri: Uri?) {
        if (uri == null) return
        val targetIdx = activeTargetPairIndex ?: return
        if (targetIdx !in pairsList.indices) return
        try {
            val destDir = File(context.filesDir, "projects/${project.id}/images")
            val copiedPath = ImageUtils.importImageToProject(
                context = context,
                uri = uri,
                destDir = destDir,
                baseName = if (isPickingTargetFrame) "target" else "base"
            )
            val frame = ImageFrame(copiedPath)
            val currentPair = pairsList[targetIdx]
            val updatedPair = if (isPickingTargetFrame) {
                currentPair.copy(target = frame)
            } else {
                currentPair.copy(base = frame)
            }
            pairsList[targetIdx] = updatedPair
            syncAndSave()
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = "Failed to import image: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    // Photo Picker launcher (Gallery)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> handleImageUri(uri) }
    )

    // Document/File picker launcher (SAF)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> handleImageUri(uri) }
    )

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
        onResult = { bitmap ->
            if (bitmap != null) {
                val targetIdx = activeTargetPairIndex ?: return@rememberLauncherForActivityResult
                if (targetIdx in pairsList.indices) {
                    val pDir = File(context.filesDir, "projects/${project.id}/images")
                    if (!pDir.exists()) pDir.mkdirs()
                    val destFile = File(pDir, "cam_${System.currentTimeMillis()}.jpg")
                    java.io.FileOutputStream(destFile).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    val frame = ImageFrame(destFile.absolutePath)
                    val currentPair = pairsList[targetIdx]
                    val updatedPair = if (isPickingTargetFrame) currentPair.copy(target = frame) else currentPair.copy(base = frame)
                    pairsList[targetIdx] = updatedPair
                    syncAndSave()
                }
            }
        }
    )

    // Permission launchers
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            // Open picker either way (graceful fallback)
            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    )

    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                cameraLauncher.launch(null)
            } else {
                errorMessage = "Camera permission denied. You can pick photos from Gallery or Files."
            }
        }
    )

    val completePairCount = pairsList.count { it.isComplete }
    val canFit = completePairCount >= 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Frame Pairs (${pairsList.size})", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Pick base (ungraded) and target (graded) frame pairs.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = {
                    val newPair = FramePair(id = UUID.randomUUID().toString())
                    pairsList.add(newPair)
                    syncAndSave()
                    // Immediately trigger base picking for the newly created pair
                    activeTargetPairIndex = pairsList.size - 1
                    isPickingTargetFrame = false
                    showSourceSheet = true
                }
            ) {
                Icon(EngineIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Pair")
            }
        }

        Spacer(Modifier.height(12.dp))

        if (pairsList.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No Frame Pairs Added", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Tap 'Add Pair' above to pick your source frames from Gallery, Camera, Files, or Video, or generate synthetic test frames.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(pairsList, key = { _, pair -> pair.id }) { index, pair ->
                    PairCard(
                        pair = pair,
                        index = index,
                        onPickBase = {
                            activeTargetPairIndex = index
                            isPickingTargetFrame = false
                            showSourceSheet = true
                        },
                        onPickTarget = {
                            activeTargetPairIndex = index
                            isPickingTargetFrame = true
                            showSourceSheet = true
                        },
                        onSwap = {
                            val temp = pair.base
                            val updated = pair.copy(base = pair.target, target = temp)
                            pairsList[index] = updated
                            syncAndSave()
                        },
                        onToggleMode = {
                            val nextMode = if (pair.mode == PairMode.SAME_FRAME) PairMode.DIFFERENT_SCENES else PairMode.SAME_FRAME
                            pairsList[index] = pair.copy(mode = nextMode)
                            syncAndSave()
                        },
                        onEditCrop = { isTarget ->
                            cropEditorPair = index to isTarget
                        },
                        onRemove = {
                            pairsList.removeAt(index)
                            syncAndSave()
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            if (!canFit) {
                Text(
                    "Add both Base and Target frames to at least one pair to fit a look.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Button(
                onClick = onContinueToFit,
                enabled = canFit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Fit Look ($completePairCount Ready)")
            }
        }
    }

    if (showSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSourceSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val label = if (isPickingTargetFrame) "Target (Graded)" else "Base (Ungraded)"
                Text("Select $label Frame Source", fontSize = 18.sp, fontWeight = FontWeight.Bold)

                ListItem(
                    headlineContent = { Text("Gallery") },
                    supportingContent = { Text("Device photo albums") },
                    leadingContent = { Icon(EngineIcons.Image, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showSourceSheet = false
                        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_IMAGES
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            permissionExplanationDialog = "CC Studio requests media access to quickly browse and import high-res frames from your gallery." to {
                                requestPermissionLauncher.launch(perm)
                            }
                        } else {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    }
                )

                ListItem(
                    headlineContent = { Text("Video Frame") },
                    supportingContent = { Text("Select video clip to extract frame") },
                    leadingContent = { Icon(EngineIcons.Movie, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showSourceSheet = false
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                )

                ListItem(
                    headlineContent = { Text("Camera") },
                    supportingContent = { Text("Snap color card or scene directly") },
                    leadingContent = { Icon(EngineIcons.Camera, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showSourceSheet = false
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            permissionExplanationDialog = "CC Studio requests camera access so you can snap live reference scenes or physical color charts." to {
                                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        } else {
                            cameraLauncher.launch(null)
                        }
                    }
                )

                ListItem(
                    headlineContent = { Text("Files") },
                    supportingContent = { Text("Storage Access Framework document picker") },
                    leadingContent = { Icon(EngineIcons.Folder, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showSourceSheet = false
                        filePickerLauncher.launch(arrayOf("image/*"))
                    }
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    permissionExplanationDialog?.let { (explanation, onProceed) ->
        AlertDialog(
            onDismissRequest = { permissionExplanationDialog = null },
            title = { Text("Permission Notice") },
            text = { Text(explanation) },
            confirmButton = {
                Button(onClick = {
                    val act = onProceed
                    permissionExplanationDialog = null
                    act()
                }) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { permissionExplanationDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    errorMessage?.let { err ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Notice") },
            text = { Text(err) },
            confirmButton = {
                Button(onClick = { errorMessage = null }) {
                    Text("OK")
                }
            }
        )
    }

    cropEditorPair?.let { (idx, isTarget) ->
        val pair = pairsList.getOrNull(idx)
        val frame = if (isTarget) pair?.target else pair?.base
        if (pair != null && frame != null) {
            CropEditorDialog(
                frame = frame,
                title = if (isTarget) "Target Frame Crop" else "Base Frame Crop",
                onDismiss = { cropEditorPair = null },
                onSave = { updated ->
                    if (isTarget) {
                        pairsList[idx] = pair.copy(target = updated)
                    } else {
                        pairsList[idx] = pair.copy(base = updated)
                    }
                    syncAndSave()
                    cropEditorPair = null
                }
            )
        }
    }
}

@Composable
private fun CropEditorDialog(
    frame: ImageFrame,
    title: String,
    onDismiss: () -> Unit,
    onSave: (ImageFrame) -> Unit
) {
    var left by remember { mutableStateOf(frame.cropRect.left) }
    var top by remember { mutableStateOf(frame.cropRect.top) }
    var right by remember { mutableStateOf(frame.cropRect.right) }
    var bottom by remember { mutableStateOf(frame.cropRect.bottom) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Left: ${(left * 100).toInt()}%", fontSize = 12.sp)
                Slider(
                    value = left,
                    onValueChange = { left = it.coerceIn(0f, (right - 0.05f).coerceAtLeast(0f)) },
                    valueRange = 0f..0.95f
                )
                Text("Top: ${(top * 100).toInt()}%", fontSize = 12.sp)
                Slider(
                    value = top,
                    onValueChange = { top = it.coerceIn(0f, (bottom - 0.05f).coerceAtLeast(0f)) },
                    valueRange = 0f..0.95f
                )
                Text("Right: ${(right * 100).toInt()}%", fontSize = 12.sp)
                Slider(
                    value = right,
                    onValueChange = { right = it.coerceIn((left + 0.05f).coerceAtMost(1f), 1f) },
                    valueRange = 0.05f..1f
                )
                Text("Bottom: ${(bottom * 100).toInt()}%", fontSize = 12.sp)
                Slider(
                    value = bottom,
                    onValueChange = { bottom = it.coerceIn((top + 0.05f).coerceAtMost(1f), 1f) },
                    valueRange = 0.05f..1f
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = {
                            left = 0f
                            top = 0f
                            right = 1f
                            bottom = 1f
                        }
                    ) {
                        Text("Reset Crop")
                    }
                    TextButton(
                        onClick = {
                            try {
                                val bmp = BitmapFactory.decodeFile(frame.filePath)
                                if (bmp != null) {
                                    val w = bmp.width
                                    val h = bmp.height
                                    val pixels = IntArray(w * h)
                                    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
                                    val rgbArr = Array(w * h) { i ->
                                        val c = pixels[i]
                                        doubleArrayOf(
                                            android.graphics.Color.red(c) / 255.0,
                                            android.graphics.Color.green(c) / 255.0,
                                            android.graphics.Color.blue(c) / 255.0
                                        )
                                    }
                                    val mFrame = com.motionsalt.ccstudio.core.MatchFrame(w, h, rgbArr)
                                    val bars = com.motionsalt.ccstudio.core.FrameAlignment.letterbox(mFrame)
                                    if (bars.isNotEmpty()) {
                                        val topBar = bars.firstOrNull { it.top == 0.0 }
                                        val botBar = bars.firstOrNull { it.bottom == 1.0 }
                                        if (topBar != null) top = topBar.bottom.toFloat()
                                        if (botBar != null) bottom = botBar.top.toFloat()
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    ) {
                        Text("Auto-detect Bars")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(frame.copy(cropRect = CropRect(left, top, right, bottom)))
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PairCard(
    pair: FramePair,
    index: Int,
    onPickBase: () -> Unit,
    onPickTarget: () -> Unit,
    onSwap: () -> Unit,
    onToggleMode: () -> Unit,
    onEditCrop: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pair #${index + 1}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = pair.mode == PairMode.SAME_FRAME,
                        onClick = onToggleMode,
                        label = { Text(if (pair.mode == PairMode.SAME_FRAME) "Same frame" else "Diff scenes") }
                    )
                    IconButton(onClick = onRemove) {
                        Icon(EngineIcons.Close, contentDescription = "Remove pair", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Base Frame
                FrameThumbnailBox(
                    label = "Base (Ungraded)",
                    frame = pair.base,
                    onPick = onPickBase,
                    onCrop = { onEditCrop(false) },
                    modifier = Modifier.weight(1f)
                )

                // Swap Button
                IconButton(onClick = onSwap) {
                    Icon(EngineIcons.Sync, contentDescription = "Swap base and target")
                }

                // Target Frame
                FrameThumbnailBox(
                    label = "Target (Graded)",
                    frame = pair.target,
                    onPick = onPickTarget,
                    onCrop = { onEditCrop(true) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun FrameThumbnailBox(
    label: String,
    frame: ImageFrame?,
    onPick: () -> Unit,
    onCrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .clickable { onPick() },
            contentAlignment = Alignment.Center
        ) {
            val filePath = frame?.filePath
            val bmp = remember(filePath, frame?.cropRect) {
                if (filePath != null && File(filePath).exists()) {
                    BitmapFactory.decodeFile(filePath)
                } else null
            }
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = onCrop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                ) {
                    Icon(EngineIcons.Crop, contentDescription = "Crop frame", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(EngineIcons.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap to pick", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
