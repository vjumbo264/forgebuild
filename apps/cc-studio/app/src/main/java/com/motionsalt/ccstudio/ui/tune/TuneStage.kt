package com.motionsalt.ccstudio.ui.tune

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgebuild.engine.ui.icons.EngineIcons
import com.motionsalt.ccstudio.core.*
import com.motionsalt.ccstudio.data.EffectRepository
import com.motionsalt.ccstudio.data.ProjectData
import com.motionsalt.ccstudio.data.ProjectRepository
import java.io.File
import java.util.UUID

enum class ViewMode(val label: String) {
    RESULT("Result"),
    WIPE("Wipe"),
    SIDE_BY_SIDE("Side by side"),
    TOP_BOTTOM("Top/bottom"),
    VS_TARGET("Vs Target"),
    DIFFERENCE("Difference"),
    CHANNELS("Channels"),
    SCOPES("Scopes")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuneStage(
    project: ProjectData,
    repository: ProjectRepository,
    effectRepository: EffectRepository
) {
    val availableEffects = remember { effectRepository.getAvailableEffects() }
    var currentViewMode by remember { mutableStateOf(ViewMode.RESULT) }
    var isPressingBefore by remember { mutableStateOf(false) }

    // Wipe options
    var wipeDividerFraction by remember { mutableStateOf(0.5f) }
    var isWipeVertical by remember { mutableStateOf(true) }

    // Difference gain
    var diffGain by remember { mutableStateOf(3f) }

    // Channel isolation: 0=All, 1=Red, 2=Green, 3=Blue, 4=Luma
    var selectedChannel by remember { mutableStateOf(0) }

    // Frame strip selection
    val allFrames = remember(project.pairs) {
        val list = mutableListOf<Pair<String, Boolean>>() // filePath to isTarget
        for (p in project.pairs) {
            p.base?.let { list.add(it.filePath to false) }
            p.target?.let { list.add(it.filePath to true) }
        }
        list
    }
    var activeFramePath by remember {
        mutableStateOf(allFrames.firstOrNull()?.first)
    }

    // Stack and Undo/Redo
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }

    var tuneStateJson by remember { mutableStateOf(project.tuneState.toJsonString()) }
    val currentTuneState = remember(tuneStateJson) { TuneState.fromJsonString(tuneStateJson) }

    var expandedItemId by remember { mutableStateOf<String?>(currentTuneState.stack.firstOrNull()?.instanceId) }
    var showAddEffectPicker by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    fun commitTuneState(newState: TuneState) {
        undoStack.add(tuneStateJson)
        redoStack.clear()
        val newJson = newState.toJsonString()
        tuneStateJson = newJson
        project.tuneState = newState
        repository.saveProject(project)
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(tuneStateJson)
            tuneStateJson = prev
            project.tuneState = TuneState.fromJsonString(prev)
            repository.saveProject(project)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(tuneStateJson)
            tuneStateJson = next
            project.tuneState = TuneState.fromJsonString(next)
            repository.saveProject(project)
        }
    }

    // Zoom & Pan state
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 4f)
        offset += offsetChange
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // TOP: Viewer Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (isFullscreen) 1f else 0.45f)
                .background(Color.Black)
                .transformable(state = transformState)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressingBefore = true
                            tryAwaitRelease()
                            isPressingBefore = false
                        },
                        onDoubleTap = {
                            scale = if (scale > 1.2f) 1f else 2f
                            offset = Offset.Zero
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Load fitted transform if project has fit result
            val lutTransform = remember(project.fitResult?.lutPath) {
                val lutPath = project.fitResult?.lutPath
                if (lutPath != null && File(lutPath).exists()) {
                    try {
                        ColorMath.parseCube33(File(lutPath).readText(java.nio.charset.StandardCharsets.UTF_8))
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }

            // Real-time stack rendering of the active frame through the complete effect stack
            val bitmap = remember(activeFramePath, tuneStateJson, isPressingBefore, currentViewMode, wipeDividerFraction, isWipeVertical, diffGain, selectedChannel) {
                if (activeFramePath != null && File(activeFramePath!!).exists()) {
                    val rawBmp = BitmapFactory.decodeFile(activeFramePath)
                    if (rawBmp != null) {
                        if (isPressingBefore) {
                            rawBmp // True original before
                        } else {
                            val w = rawBmp.width
                            val h = rawBmp.height
                            val pixels = IntArray(w * h)
                            rawBmp.getPixels(pixels, 0, w, 0, 0, w, h)
                            val inBuf = StackRenderer.PixelBuffer(w, h, pixels)
                            val outBuf = StackRenderer.render(inBuf, currentTuneState, lutTransform)

                            // Apply ViewMode modifications
                            when (currentViewMode) {
                                ViewMode.WIPE -> {
                                    val wipePixels = outBuf.pixels.clone()
                                    for (y in 0 until h) {
                                        for (x in 0 until w) {
                                            val isBefore = if (isWipeVertical) {
                                                (x.toFloat() / w) < wipeDividerFraction
                                            } else {
                                                (y.toFloat() / h) < wipeDividerFraction
                                            }
                                            if (isBefore) {
                                                wipePixels[y * w + x] = pixels[y * w + x]
                                            }
                                        }
                                    }
                                    Bitmap.createBitmap(wipePixels, w, h, Bitmap.Config.ARGB_8888)
                                }
                                ViewMode.CHANNELS -> {
                                    val chanPixels = IntArray(w * h)
                                    for (i in 0 until (w * h)) {
                                        val p = outBuf.pixels[i]
                                        val a = (p ushr 24) and 0xFF
                                        val r = (p ushr 16) and 0xFF
                                        val g = (p ushr 8) and 0xFF
                                        val b = p and 0xFF
                                        chanPixels[i] = when (selectedChannel) {
                                            1 -> (a shl 24) or (r shl 16) // R only
                                            2 -> (a shl 24) or (g shl 8)  // G only
                                            3 -> (a shl 24) or b          // B only
                                            4 -> {
                                                val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)
                                                (a shl 24) or (luma shl 16) or (luma shl 8) or luma
                                            }
                                            else -> p
                                        }
                                    }
                                    Bitmap.createBitmap(chanPixels, w, h, Bitmap.Config.ARGB_8888)
                                }
                                ViewMode.DIFFERENCE -> {
                                    val diffPixels = IntArray(w * h)
                                    for (i in 0 until (w * h)) {
                                        val pOrig = pixels[i]
                                        val pProc = outBuf.pixels[i]
                                        val rDiff = (Math.abs(((pProc ushr 16) and 0xFF) - ((pOrig ushr 16) and 0xFF)) * diffGain).toInt().coerceIn(0, 255)
                                        val gDiff = (Math.abs(((pProc ushr 8) and 0xFF) - ((pOrig ushr 8) and 0xFF)) * diffGain).toInt().coerceIn(0, 255)
                                        val bDiff = (Math.abs((pProc and 0xFF) - (pOrig and 0xFF)) * diffGain).toInt().coerceIn(0, 255)
                                        diffPixels[i] = (0xFF shl 24) or (rDiff shl 16) or (gDiff shl 8) or bDiff
                                    }
                                    Bitmap.createBitmap(diffPixels, w, h, Bitmap.Config.ARGB_8888)
                                }
                                else -> {
                                    Bitmap.createBitmap(outBuf.pixels, w, h, Bitmap.Config.ARGB_8888)
                                }
                            }
                        }
                    } else null
                } else null
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Tune Preview Viewer",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            } else {
                Text("No Frame Selected", color = Color.White)
            }

            // Mode Overlays
            when (currentViewMode) {
                ViewMode.RESULT -> {
                    if (isPressingBefore) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(8.dp)
                        ) {
                            Text("HOLDING: ORIGINAL (BEFORE)", color = Color.Yellow, fontSize = 11.sp, modifier = Modifier.padding(6.dp))
                        }
                    }
                }
                ViewMode.WIPE -> {
                    // Wipe divider overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isWipeVertical) "Vert" else "Horiz", color = Color.White, fontSize = 11.sp)
                            IconButton(onClick = { isWipeVertical = !isWipeVertical }, modifier = Modifier.size(24.dp)) {
                                Icon(EngineIcons.Sync, contentDescription = "Flip wipe", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                        Slider(
                            value = wipeDividerFraction,
                            onValueChange = { wipeDividerFraction = it },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
                ViewMode.DIFFERENCE -> {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Gain: ${diffGain.toInt()}x", color = Color.White, fontSize = 12.sp)
                        Slider(value = diffGain, onValueChange = { diffGain = it }, valueRange = 1f..10f, modifier = Modifier.width(160.dp))
                    }
                }
                ViewMode.CHANNELS -> {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("RGB", "R", "G", "B", "Luma").forEachIndexed { index, label ->
                            FilterChip(
                                selected = selectedChannel == index,
                                onClick = { selectedChannel = index },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
                ViewMode.SCOPES -> {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(120.dp, 80.dp)
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Text("RGB Parade / Histogram", color = Color.LightGray, fontSize = 9.sp)
                            Spacer(Modifier.height(4.dp))
                            // Simple waveform visualization
                            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Box(modifier = Modifier.fillMaxHeight().width(24.dp).background(Color.Red.copy(alpha = 0.5f)))
                                Box(modifier = Modifier.fillMaxHeight().width(24.dp).background(Color.Green.copy(alpha = 0.5f)))
                                Box(modifier = Modifier.fillMaxHeight().width(24.dp).background(Color.Blue.copy(alpha = 0.5f)))
                            }
                        }
                    }
                }
                else -> {}
            }

            // Fullscreen toggle button
            IconButton(
                onClick = { isFullscreen = !isFullscreen },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    if (isFullscreen) EngineIcons.Close else EngineIcons.Fullscreen,
                    contentDescription = "Toggle Fullscreen",
                    tint = Color.White
                )
            }
        }

        if (!isFullscreen) {
            // VIEW MODE CHIP ROW
            ScrollableTabRow(
                selectedTabIndex = currentViewMode.ordinal,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                ViewMode.values().forEach { mode ->
                    Tab(
                        selected = currentViewMode == mode,
                        onClick = { currentViewMode = mode },
                        text = { Text(mode.label, fontSize = 12.sp) }
                    )
                }
            }

            // FRAME STRIP
            if (allFrames.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(allFrames) { (path, isTarget) ->
                        val isSelected = path == activeFramePath
                        Box(
                            modifier = Modifier
                                .size(50.dp, 36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { activeFramePath = path }
                        ) {
                            val fBitmap = remember(path) { BitmapFactory.decodeFile(path) }
                            if (fBitmap != null) {
                                Image(
                                    bitmap = fBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Text(
                                if (isTarget) "T" else "B",
                                fontSize = 9.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 2.dp)
                            )
                        }
                    }
                }
            }

            // STACK PANEL (Bottom Sheet / Draggable Area)
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Stack (${currentTuneState.stack.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) {
                            Icon(EngineIcons.Undo, contentDescription = "Undo")
                        }
                        IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty()) {
                            Icon(EngineIcons.Redo, contentDescription = "Redo")
                        }
                        FilledTonalButton(
                            onClick = { showAddEffectPicker = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(EngineIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Effect", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Stack List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(currentTuneState.stack) { index, item ->
                        val def = availableEffects.firstOrNull { it.id == item.effectId }
                        val isExpanded = expandedItemId == item.instanceId

                        StackItemCard(
                            item = item,
                            def = def,
                            isFirst = index == 0,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedItemId = if (isExpanded) null else item.instanceId
                            },
                            onToggleEnabled = {
                                item.enabled = !item.enabled
                                commitTuneState(currentTuneState)
                            },
                            onParamChange = { key, value ->
                                item.params[key] = value
                                commitTuneState(currentTuneState)
                            },
                            onMoveUp = {
                                if (index > 1) { // 0 is always ms_lut
                                    currentTuneState.stack.removeAt(index)
                                    currentTuneState.stack.add(index - 1, item)
                                    commitTuneState(currentTuneState)
                                }
                            },
                            onMoveDown = {
                                if (index > 0 && index < currentTuneState.stack.size - 1) {
                                    currentTuneState.stack.removeAt(index)
                                    currentTuneState.stack.add(index + 1, item)
                                    commitTuneState(currentTuneState)
                                }
                            },
                            onDuplicate = {
                                val copied = item.copyItem()
                                currentTuneState.stack.add(index + 1, copied)
                                commitTuneState(currentTuneState)
                            },
                            onReset = {
                                def?.parameters?.forEach { p ->
                                    item.params[p.key] = p.defaultValue
                                }
                                commitTuneState(currentTuneState)
                            },
                            onDelete = {
                                if (item.effectId != "ms_lut") {
                                    currentTuneState.stack.removeAt(index)
                                    commitTuneState(currentTuneState)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddEffectPicker) {
        AddEffectPickerSheet(
            effects = availableEffects,
            onDismiss = { showAddEffectPicker = false },
            onSelectEffect = { def ->
                val newItem = StackItem(
                    instanceId = UUID.randomUUID().toString(),
                    effectId = def.id,
                    enabled = true,
                    expose = true,
                    params = def.parameters.associate { it.key to it.defaultValue }.toMutableMap()
                )
                currentTuneState.stack.add(newItem)
                expandedItemId = newItem.instanceId
                commitTuneState(currentTuneState)
                showAddEffectPicker = false
            }
        )
    }
}

@Composable
fun StackItemCard(
    item: StackItem,
    def: EffectDefinition?,
    isFirst: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleEnabled: () -> Unit,
    onParamChange: (String, Any) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.enabled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f).clickable { onToggleExpand() }
                ) {
                    Switch(
                        checked = item.enabled,
                        onCheckedChange = { onToggleEnabled() },
                        modifier = Modifier.scale(0.8f)
                    )
                    Column {
                        Text(
                            def?.name ?: item.effectId,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            def?.category ?: "Custom",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isFirst) {
                        IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                            Icon(EngineIcons.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                            Icon(EngineIcons.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                        }
                    }

                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                            Icon(EngineIcons.MoreVert, contentDescription = "More", modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                onClick = { menuOpen = false; onDuplicate() }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset to Defaults") },
                                onClick = { menuOpen = false; onReset() }
                            )
                            if (!isFirst) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; onDelete() }
                                )
                            }
                        }
                    }

                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (isExpanded) EngineIcons.ExpandLess else EngineIcons.ExpandMore,
                            contentDescription = "Expand",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    def?.parameters?.forEach { p ->
                        val currentValue = item.params[p.key] ?: p.defaultValue

                        when (p.type) {
                            ParamType.SCALAR -> {
                                val num = when (currentValue) {
                                    is Number -> currentValue.toFloat()
                                    else -> p.defaultValue as? Float ?: 0f
                                }
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(p.label, fontSize = 12.sp)
                                        Text(String.format("%.2f", num), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                    Slider(
                                        value = num,
                                        onValueChange = { onParamChange(p.key, it) },
                                        valueRange = p.min..p.max
                                    )
                                }
                            }
                            ParamType.BOOL -> {
                                val b = currentValue as? Boolean ?: (currentValue == 1.0)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(p.label, fontSize = 12.sp)
                                    Switch(checked = b, onCheckedChange = { onParamChange(p.key, it) })
                                }
                            }
                            ParamType.CHOICE -> {
                                var dropOpen by remember { mutableStateOf(false) }
                                val idx = (currentValue as? Number)?.toInt() ?: 0
                                val optText = p.options.getOrNull(idx) ?: "Option $idx"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(p.label, fontSize = 12.sp)
                                    Box {
                                        OutlinedButton(onClick = { dropOpen = true }) {
                                            Text(optText, fontSize = 12.sp)
                                        }
                                        DropdownMenu(expanded = dropOpen, onDismissRequest = { dropOpen = false }) {
                                            p.options.forEachIndexed { i, opt ->
                                                DropdownMenuItem(
                                                    text = { Text(opt) },
                                                    onClick = {
                                                        dropOpen = false
                                                        onParamChange(p.key, i)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            ParamType.COLOR -> {
                                val colorStr = currentValue.toString()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(p.label, fontSize = 12.sp)
                                    OutlinedTextField(
                                        value = colorStr,
                                        onValueChange = { onParamChange(p.key, it) },
                                        modifier = Modifier.width(120.dp),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEffectPickerSheet(
    effects: List<EffectDefinition>,
    onDismiss: () -> Unit,
    onSelectEffect: (EffectDefinition) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Colour correction", "Tone", "Toning", "Detail", "Look")

    val filtered = effects.filter { eff ->
        val matchesCat = selectedCategory == "All" || eff.category.equals(selectedCategory, ignoreCase = true)
        val matchesSearch = searchQuery.isBlank() || eff.name.contains(searchQuery, ignoreCase = true) || eff.id.contains(searchQuery, ignoreCase = true)
        matchesCat && matchesSearch
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add Effect to Stack", fontSize = 20.sp, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search 17 Keyflaw effects...") },
                leadingIcon = { Icon(EngineIcons.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(selectedCategory),
                edgePadding = 0.dp
            ) {
                categories.forEach { cat ->
                    Tab(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        text = { Text(cat, fontSize = 12.sp) }
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered) { eff ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectEffect(eff) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(eff.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    eff.description.ifEmpty { "${eff.parameters.size} parameters • Keyflaw 1.4.9" },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            SuggestionChip(
                                onClick = { onSelectEffect(eff) },
                                label = { Text(eff.category, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }
    }
}
