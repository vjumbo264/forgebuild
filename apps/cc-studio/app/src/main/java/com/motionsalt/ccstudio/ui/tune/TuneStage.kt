package com.motionsalt.ccstudio.ui.tune

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLSurfaceView
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.forgebuild.engine.ui.icons.EngineIcons
import com.motionsalt.ccstudio.core.*
import com.motionsalt.ccstudio.data.EffectRepository
import com.motionsalt.ccstudio.data.ProjectData
import com.motionsalt.ccstudio.data.ProjectRepository
import com.motionsalt.ccstudio.renderer.KeyflawHostRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val availableEffects = remember { effectRepository.getAvailableEffects() }

    // Persistent Keyflaw host renderer
    val hostRenderer = remember { KeyflawHostRenderer(context) }
    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }
    var saveJob by remember { mutableStateOf<Job?>(null) }

    var currentViewMode by remember { mutableStateOf(ViewMode.RESULT) }
    var isPressingBefore by remember { mutableStateOf(false) }

    // Wipe options
    var wipeDividerFraction by remember { mutableStateOf(0.5f) }
    var isWipeVertical by remember { mutableStateOf(true) }

    // Difference gain
    var diffGain by remember { mutableStateOf(3f) }

    // Channel isolation: 0=All, 1=Red, 2=Green, 3=Blue, 4=Luma
    var selectedChannel by remember { mutableStateOf(0) }

    // Color picker and Eyedropper state
    var activeColorPickerParam by remember { mutableStateOf<Triple<StackItem, EffectParam, String>?>(null) }
    var isEyedropperActive by remember { mutableStateOf(false) }

    // Frame strip selection
    val allFrames = remember(project.pairs) {
        val list = mutableListOf<Pair<String, Boolean>>()
        for (p in project.pairs) {
            p.base?.let { list.add(it.filePath to false) }
            p.target?.let { list.add(it.filePath to true) }
        }
        list
    }
    var activeFramePath by remember {
        mutableStateOf(allFrames.firstOrNull()?.first)
    }
    var activeBaseBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Load active base frame into renderer
    LaunchedEffect(activeFramePath) {
        if (activeFramePath != null && File(activeFramePath!!).exists()) {
            withContext(Dispatchers.IO) {
                try {
                    val bmp = BitmapFactory.decodeFile(activeFramePath)
                    activeBaseBitmap = bmp
                    hostRenderer.setBaseBitmap(bmp)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            glSurfaceViewRef?.requestRender()
        }
    }

    // Load target frame into renderer if available
    val completePair = project.pairs.firstOrNull { it.isComplete }
    LaunchedEffect(completePair?.target?.filePath) {
        val tPath = completePair?.target?.filePath
        if (tPath != null && File(tPath).exists()) {
            withContext(Dispatchers.IO) {
                try {
                    val bmp = BitmapFactory.decodeFile(tPath)
                    hostRenderer.setTargetBitmap(bmp)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            glSurfaceViewRef?.requestRender()
        }
    }

    // Load fitted 3D LUT transform into renderer
    LaunchedEffect(project.fitResult?.lutPath) {
        val lutPath = project.fitResult?.lutPath
        if (lutPath != null && File(lutPath).exists()) {
            withContext(Dispatchers.IO) {
                try {
                    val transform = ColorMath.parseCube33(File(lutPath).readText(java.nio.charset.StandardCharsets.UTF_8))
                    hostRenderer.setLutTransform(transform)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            glSurfaceViewRef?.requestRender()
        }
    }

    // Stack and Undo/Redo
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }
    var tuneStateJson by remember { mutableStateOf(project.tuneState.toJsonString()) }
    val currentTuneState = remember(tuneStateJson) { TuneState.fromJsonString(tuneStateJson) }
    var expandedItemId by remember { mutableStateOf<String?>(currentTuneState.stack.firstOrNull()?.instanceId) }
    var showAddEffectPicker by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Sync state and view options to host renderer
    LaunchedEffect(currentViewMode, isPressingBefore, wipeDividerFraction, isWipeVertical, diffGain, selectedChannel, currentTuneState) {
        hostRenderer.activeViewMode = when (currentViewMode) {
            ViewMode.RESULT -> KeyflawHostRenderer.ViewMode.RESULT
            ViewMode.WIPE -> KeyflawHostRenderer.ViewMode.WIPE
            ViewMode.SIDE_BY_SIDE -> KeyflawHostRenderer.ViewMode.SIDE_BY_SIDE
            ViewMode.TOP_BOTTOM -> KeyflawHostRenderer.ViewMode.TOP_BOTTOM
            ViewMode.VS_TARGET -> KeyflawHostRenderer.ViewMode.VS_TARGET
            ViewMode.DIFFERENCE -> KeyflawHostRenderer.ViewMode.DIFFERENCE
            ViewMode.CHANNELS -> KeyflawHostRenderer.ViewMode.CHANNELS
            ViewMode.SCOPES -> KeyflawHostRenderer.ViewMode.SCOPES
        }
        hostRenderer.isPressingBefore = isPressingBefore
        hostRenderer.wipeFraction = wipeDividerFraction
        hostRenderer.isWipeVertical = isWipeVertical
        hostRenderer.diffGain = diffGain
        hostRenderer.selectedChannel = selectedChannel
        hostRenderer.activeTuneState = currentTuneState
        glSurfaceViewRef?.requestRender()
    }

    fun commitTuneState(newState: TuneState) {
        undoStack.add(tuneStateJson)
        redoStack.clear()
        val newJson = newState.toJsonString()
        tuneStateJson = newJson
        project.tuneState = newState
        repository.saveProject(project)
        hostRenderer.activeTuneState = newState
        glSurfaceViewRef?.requestRender()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(tuneStateJson)
            tuneStateJson = prev
            val restored = TuneState.fromJsonString(prev)
            project.tuneState = restored
            repository.saveProject(project)
            hostRenderer.activeTuneState = restored
            glSurfaceViewRef?.requestRender()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(tuneStateJson)
            tuneStateJson = next
            val restored = TuneState.fromJsonString(next)
            project.tuneState = restored
            repository.saveProject(project)
            hostRenderer.activeTuneState = restored
            glSurfaceViewRef?.requestRender()
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
        // TOP: Viewer Box (Keyflaw GLES Host Renderer)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (isFullscreen) 1f else 0.45f)
                .background(Color.Black)
                .transformable(state = transformState)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (!isEyedropperActive) {
                                isPressingBefore = true
                                tryAwaitRelease()
                                isPressingBefore = false
                            }
                        },
                        onTap = { tapOffset ->
                            if (isEyedropperActive && activeBaseBitmap != null && activeColorPickerParam != null) {
                                val bmp = activeBaseBitmap!!
                                val nx = (tapOffset.x / size.width).coerceIn(0f, 1f)
                                val ny = (tapOffset.y / size.height).coerceIn(0f, 1f)
                                val px = (nx * (bmp.width - 1)).toInt().coerceIn(0, bmp.width - 1)
                                val py = (ny * (bmp.height - 1)).toInt().coerceIn(0, bmp.height - 1)
                                val pixel = bmp.getPixel(px, py)
                                val r = (pixel shr 16) and 0xFF
                                val g = (pixel shr 8) and 0xFF
                                val b = pixel and 0xFF
                                val sampledHex = String.format("#%02X%02X%02X", r, g, b)

                                val (targetItem, targetParam, _) = activeColorPickerParam!!
                                targetItem.params[targetParam.key] = sampledHex
                                hostRenderer.setEffectColorParam(targetItem.effectId, targetParam.key, r / 255f, g / 255f, b / 255f)
                                glSurfaceViewRef?.requestRender()
                                commitTuneState(currentTuneState)
                                isEyedropperActive = false
                                activeColorPickerParam = null
                            }
                        },
                        onDoubleTap = {
                            scale = if (scale > 1.2f) 1f else 2f
                            offset = Offset.Zero
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // GLSurfaceView embedded via AndroidView
            AndroidView(
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLContextClientVersion(2)
                        setRenderer(hostRenderer)
                        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                        glSurfaceViewRef = this
                    }
                },
                update = { view ->
                    glSurfaceViewRef = view
                    view.requestRender()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )

            // Eyedropper sampling overlay banner
            if (isEyedropperActive) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(EngineIcons.Colorize, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Tap image to sample color", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { isEyedropperActive = false }) {
                            Text("Cancel", fontSize = 11.sp)
                        }
                    }
                }
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
                                Icon(EngineIcons.Refresh, contentDescription = "Flip Orientation", tint = Color.White)
                            }
                        }
                        Slider(
                            value = wipeDividerFraction,
                            onValueChange = {
                                wipeDividerFraction = it
                                hostRenderer.wipeFraction = it
                                glSurfaceViewRef?.requestRender()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier
                                .align(if (isWipeVertical) Alignment.BottomCenter else Alignment.CenterEnd)
                                .width(if (isWipeVertical) 200.dp else 120.dp)
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
                        Slider(
                            value = diffGain,
                            onValueChange = {
                                diffGain = it
                                hostRenderer.diffGain = it
                                glSurfaceViewRef?.requestRender()
                            },
                            valueRange = 1f..10f,
                            modifier = Modifier.width(160.dp)
                        )
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
                                onClick = {
                                    selectedChannel = index
                                    hostRenderer.selectedChannel = index
                                    glSurfaceViewRef?.requestRender()
                                },
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

            // STACK PANEL
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
                            onSliderDrag = { key, floatValue ->
                                item.params[key] = floatValue
                                hostRenderer.setEffectParam(item.effectId, key, floatValue)
                                glSurfaceViewRef?.requestRender()

                                saveJob?.cancel()
                                saveJob = coroutineScope.launch {
                                    delay(300)
                                    tuneStateJson = currentTuneState.toJsonString()
                                    project.tuneState = currentTuneState
                                    repository.saveProject(project)
                                }
                            },
                            onOpenColorPicker = { param, hex ->
                                activeColorPickerParam = Triple(item, param, hex)
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

    // Interactive Color Picker Dialog
    activeColorPickerParam?.let { (item, param, currentHex) ->
        ColorPickerDialog(
            initialColorHex = currentHex,
            paramLabel = param.label,
            onDismiss = { activeColorPickerParam = null },
            onColorSelected = { selectedHex ->
                item.params[param.key] = selectedHex
                val clean = selectedHex.removePrefix("#")
                val num = clean.toLongOrNull(16) ?: 0L
                val r = ((num shr 16) and 0xFF) / 255.0f
                val g = ((num shr 8) and 0xFF) / 255.0f
                val b = (num and 0xFF) / 255.0f
                hostRenderer.setEffectColorParam(item.effectId, param.key, r, g, b)
                glSurfaceViewRef?.requestRender()
                commitTuneState(currentTuneState)
                activeColorPickerParam = null
            },
            onActivateEyedropper = {
                isEyedropperActive = true
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
    onSliderDrag: ((String, Float) -> Unit)? = null,
    onOpenColorPicker: ((EffectParam, String) -> Unit)? = null,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.enabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleExpand() }
                ) {
                    Switch(
                        checked = item.enabled,
                        onCheckedChange = { onToggleEnabled() },
                        modifier = Modifier.scale(0.8f)
                    )
                    Column {
                        Text(
                            text = def?.name ?: item.effectId,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isFirst) "Base 3D LUT Transform" else def?.category ?: "Effect",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (isExpanded) EngineIcons.ExpandLess else EngineIcons.ExpandMore,
                            contentDescription = "Expand"
                        )
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                            Icon(EngineIcons.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (!isFirst) {
                                DropdownMenuItem(
                                    text = { Text("Move Up") },
                                    onClick = { menuOpen = false; onMoveUp() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Move Down") },
                                    onClick = { menuOpen = false; onMoveDown() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate") },
                                    onClick = { menuOpen = false; onDuplicate() }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Reset Defaults") },
                                onClick = { menuOpen = false; onReset() }
                            )
                            if (!isFirst) {
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; onDelete() }
                                )
                            }
                        }
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
                                        onValueChange = { onSliderDrag?.invoke(p.key, it) ?: onParamChange(p.key, it) },
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
                                val parsedColor = try {
                                    Color(android.graphics.Color.parseColor(colorStr))
                                } catch (e: Exception) {
                                    Color.White
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(p.label, fontSize = 12.sp)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(parsedColor)
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                                .clickable { onOpenColorPicker?.invoke(p, colorStr) }
                                        )
                                        OutlinedButton(
                                            onClick = { onOpenColorPicker?.invoke(p, colorStr) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(colorStr, fontSize = 11.sp)
                                        }
                                    }
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
        val matchesSearch = searchQuery.isBlank() ||
                eff.name.contains(searchQuery, ignoreCase = true) ||
                eff.id.contains(searchQuery, ignoreCase = true)
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
                label = { Text("Search 22 Keyflaw effects...") },
                leadingIcon = { Icon(EngineIcons.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
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
