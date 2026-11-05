package com.motionsalt.ccstudio.ui.source

import android.graphics.BitmapFactory
import android.net.Uri
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
import com.forgebuild.engine.ui.icons.EngineIcons
import com.motionsalt.ccstudio.data.*
import java.io.File
import java.io.InputStream
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceStage(
    project: ProjectData,
    repository: ProjectRepository,
    onContinueToFit: () -> Unit
) {
    val context = LocalContext.current
    var pairs by remember { mutableStateOf(project.pairs.toList()) }
    var showSourceSheet by remember { mutableStateOf(false) }
    var activeTargetPairIndex by remember { mutableStateOf<Int?>(null) }
    var isPickingTargetFrame by remember { mutableStateOf(false) } // false = base, true = target
    var cropEditorPair by remember { mutableStateOf<Pair<Int, Boolean>?>(null) } // pairIndex, isTarget

    fun refreshPairs() {
        pairs = project.pairs.toList()
        repository.saveProject(project)
    }

    fun handleImageSelected(uri: Uri?) {
        if (uri == null) return
        val targetIdx = activeTargetPairIndex ?: return
        if (targetIdx !in project.pairs.indices) return

        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val ext = if (uri.toString().contains("png")) "png" else "jpg"
                val copiedPath = repository.copyFileToProject(project.id, inputStream, "frame_${System.currentTimeMillis()}.$ext")
                val frame = ImageFrame(copiedPath)

                val pair = project.pairs[targetIdx]
                if (isPickingTargetFrame) {
                    pair.target = frame
                } else {
                    pair.base = frame
                }
                refreshPairs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Photo Picker launcher (Gallery)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> handleImageSelected(uri) }
    )

    // Document/File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> handleImageSelected(uri) }
    )

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
        onResult = { bitmap ->
            if (bitmap != null) {
                val targetIdx = activeTargetPairIndex ?: return@rememberLauncherForActivityResult
                if (targetIdx in project.pairs.indices) {
                    val pDir = File(context.filesDir, "projects/${project.id}/images")
                    if (!pDir.exists()) pDir.mkdirs()
                    val destFile = File(pDir, "cam_${System.currentTimeMillis()}.jpg")
                    java.io.FileOutputStream(destFile).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    val frame = ImageFrame(destFile.absolutePath)
                    val pair = project.pairs[targetIdx]
                    if (isPickingTargetFrame) pair.target = frame else pair.base = frame
                    refreshPairs()
                }
            }
        }
    )

    val completePairCount = pairs.count { it.isComplete }
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
                Text("Frame Pairs (${pairs.size})", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Pick base (ungraded) and target (graded) frame pairs.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    val newPair = FramePair(id = UUID.randomUUID().toString())
                    project.pairs.add(newPair)
                    refreshPairs()
                    // Immediately trigger base picking for the newly created pair
                    activeTargetPairIndex = project.pairs.size - 1
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

        if (pairs.isEmpty()) {
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
                itemsIndexed(pairs) { index, pair ->
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
                            pair.base = pair.target
                            pair.target = temp
                            refreshPairs()
                        },
                        onToggleMode = {
                            pair.mode = if (pair.mode == PairMode.SAME_FRAME) PairMode.DIFFERENT_SCENES else PairMode.SAME_FRAME
                            refreshPairs()
                        },
                        onEditCrop = { isTarget ->
                            cropEditorPair = index to isTarget
                        },
                        onRemove = {
                            project.pairs.removeAt(index)
                            refreshPairs()
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
                    supportingContent = { Text("Standard system photo picker (no extra permissions needed)") },
                    leadingContent = { Icon(EngineIcons.Image, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showSourceSheet = false
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
                        cameraLauncher.launch(null)
                    }
                )

                ListItem(
                    headlineContent = { Text("Files") },
                    supportingContent = { Text("Pick from storage document tree") },
                    leadingContent = { Icon(EngineIcons.Folder, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showSourceSheet = false
                        filePickerLauncher.launch("image/*")
                    }
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    cropEditorPair?.let { (idx, isTarget) ->
        val pair = project.pairs.getOrNull(idx)
        val frame = if (isTarget) pair?.target else pair?.base
        if (frame != null) {
            CropEditorDialog(
                frame = frame,
                title = if (isTarget) "Target Frame Crop" else "Base Frame Crop",
                onDismiss = { cropEditorPair = null },
                onSave = { updated ->
                    if (isTarget) pair?.target = updated else pair?.base = updated
                    refreshPairs()
                    cropEditorPair = null
                }
            )
        }
    }
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
            if (frame != null && File(frame.filePath).exists()) {
                val bitmap = remember(frame.filePath) {
                    BitmapFactory.decodeFile(frame.filePath)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                IconButton(
                    onClick = onCrop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .size(28.dp)
                ) {
                    Icon(EngineIcons.Crop, contentDescription = "Edit crop", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(EngineIcons.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Tap to add", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun CropEditorDialog(
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
                Text("Ignore black bars or subtitles by adjusting the sampling bounds:", fontSize = 12.sp)
                Text("Left: ${(left * 100).toInt()}%", fontSize = 12.sp)
                Slider(value = left, onValueChange = { left = it.coerceAtMost(right - 0.1f) })
                Text("Top: ${(top * 100).toInt()}%", fontSize = 12.sp)
                Slider(value = top, onValueChange = { top = it.coerceAtMost(bottom - 0.1f) })
                Text("Right: ${(right * 100).toInt()}%", fontSize = 12.sp)
                Slider(value = right, onValueChange = { right = it.coerceAtLeast(left + 0.1f) })
                Text("Bottom: ${(bottom * 100).toInt()}%", fontSize = 12.sp)
                Slider(value = bottom, onValueChange = { bottom = it.coerceAtLeast(top + 0.1f) })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(ImageFrame(frame.filePath, CropRect(left, top, right, bottom), frame.ignoreRects))
                }
            ) {
                Text("Apply Crop")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
