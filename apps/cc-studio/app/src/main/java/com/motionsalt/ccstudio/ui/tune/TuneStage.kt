package com.motionsalt.ccstudio.ui.tune

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.forgebuild.engine.ui.icons.EngineIcons
import com.motionsalt.ccstudio.core.*
import com.motionsalt.ccstudio.data.ProjectData
import com.motionsalt.ccstudio.data.ProjectRepository
import com.motionsalt.ccstudio.renderer.KeyflawHostRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    repository: ProjectRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Persistent Keyflaw host renderer
    val hostRenderer = remember { KeyflawHostRenderer() }
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

    // Frame strip selection
    val allFrames = project.pairs.mapNotNull { p -> p.base?.filePath?.let { it to false } }
    var activeFramePath by remember {
        mutableStateOf(allFrames.firstOrNull()?.first)
    }

    // Load active base frame into renderer
    LaunchedEffect(activeFramePath) {
        if (activeFramePath != null && File(activeFramePath!!).exists()) {
            withContext(Dispatchers.IO) {
                try {
                    val pair = project.pairs.firstOrNull { it.base?.filePath == activeFramePath }
                    val proof = File(context.filesDir, "projects/${project.id}/results/before_${pair?.id}.png")
                    val bmp = BitmapFactory.decodeFile(if(proof.exists())proof.absolutePath else activeFramePath)
                    hostRenderer.setBaseBitmap(bmp)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            glSurfaceViewRef?.requestRender()
        }
    }

    // Load target frame into renderer if available
    val completePair = project.pairs.firstOrNull { it.base?.filePath == activeFramePath && it.isComplete }
    LaunchedEffect(completePair?.target?.filePath) {
        val tPath = completePair?.target?.filePath
        if (tPath != null && File(tPath).exists()) {
            withContext(Dispatchers.IO) {
                try {
                    val proof = File(context.filesDir, "projects/${project.id}/results/target_${completePair?.id}.png")
                    val bmp = BitmapFactory.decodeFile(if(proof.exists())proof.absolutePath else tPath)
                    hostRenderer.setTargetBitmap(bmp)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else hostRenderer.setTargetBitmap(null)
        glSurfaceViewRef?.requestRender()
    }

    LaunchedEffect(project.fitResult?.modelPath) {
        withContext(Dispatchers.IO) {
            project.fitResult?.modelPath?.let { path ->
                if(File(path).exists()) hostRenderer.setMatchModel(ParametricMatch.fromJson(org.json.JSONObject(File(path).readText())))
            }
        }
        glSurfaceViewRef?.requestRender()
    }

    val undoStack = remember(project.id) { mutableStateListOf<Map<String,Double>>() }
    val redoStack = remember(project.id) { mutableStateListOf<Map<String,Double>>() }
    var values by remember(project.id) { mutableStateOf(project.ccValues) }
    var expanded by remember(project.id) { mutableStateOf(setOf("Colour Match")) }
    var isFullscreen by remember { mutableStateOf(false) }
    LaunchedEffect(project.id) { hostRenderer.setCcValues(values); glSurfaceViewRef?.requestRender() }
    DisposableEffect(project.id) { onDispose { saveJob?.cancel(); repository.saveProject(project) } }

    LaunchedEffect(currentViewMode, isPressingBefore, wipeDividerFraction, isWipeVertical, diffGain, selectedChannel) {
        hostRenderer.activeViewMode = KeyflawHostRenderer.ViewMode.valueOf(currentViewMode.name)
        hostRenderer.isPressingBefore = isPressingBefore
        hostRenderer.wipeFraction = wipeDividerFraction
        hostRenderer.isWipeVertical = isWipeVertical
        hostRenderer.diffGain = diffGain
        hostRenderer.selectedChannel = selectedChannel
        glSurfaceViewRef?.requestRender()
    }
    fun applyValues(next: Map<String,Double>) {
        values = next
        project.ccValues = next
        hostRenderer.setCcValues(next)
        glSurfaceViewRef?.requestRender()
        saveJob?.cancel()
        saveJob = coroutineScope.launch { delay(250); repository.saveProject(project) }
    }
    fun startEdit() {
        if (undoStack.lastOrNull() != values) undoStack.add(values)
        redoStack.clear()
    }
    fun undo() {
        if (undoStack.isNotEmpty()) { redoStack.add(values); applyValues(undoStack.removeAt(undoStack.lastIndex)) }
    }
    fun redo() {
        if (redoStack.isNotEmpty()) { undoStack.add(values); applyValues(redoStack.removeAt(redoStack.lastIndex)) }
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
                            if (hostRenderer.activeViewMode == KeyflawHostRenderer.ViewMode.RESULT) {
                                isPressingBefore = true
                                tryAwaitRelease()
                                isPressingBefore = false
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
                        setEGLContextClientVersion(3)
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
                                Icon(EngineIcons.Sync, contentDescription = "Flip Orientation", tint = Color.White)
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

            Column(Modifier.weight(.55f).fillMaxWidth().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text("CC",style=MaterialTheme.typography.titleMedium)
                    Row {
                        IconButton(onClick={undo()},enabled=undoStack.isNotEmpty()) { Icon(EngineIcons.Undo,"Undo") }
                        IconButton(onClick={redo()},enabled=redoStack.isNotEmpty()) { Icon(EngineIcons.Redo,"Redo") }
                    }
                }
                LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    items(CcPlugin.params.groupBy { it.group }.entries.toList(),key={it.key}) { (group, params) ->
                        val toggle = params.first()
                        val enabled = (values[toggle.id] ?: toggle.initial) >= .5
                        Card {
                            Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically) {
                                TextButton(onClick={expanded=if (group in expanded) expanded-group else expanded+group}, modifier=Modifier.weight(1f)) { Text(group) }
                                Switch(checked=enabled,onCheckedChange={startEdit();applyValues(values+(toggle.id to if(it)1.0 else 0.0))})
                            }
                            if(group in expanded) Column(Modifier.padding(horizontal=12.dp,vertical=4.dp)) {
                                for(p in params.drop(1)) key(p.id) {
                                    val value=values[p.id]?:p.initial
                                    if(p.type=="choice") {
                                        var show by remember { mutableStateOf(false) }
                                        Box {
                                            TextButton(onClick={show=true}) { Text("${p.label}: ${p.choices[value.toInt()]}") }
                                            DropdownMenu(expanded=show,onDismissRequest={show=false}) {
                                                p.choices.forEachIndexed { i, label -> DropdownMenuItem(text={Text(label)},onClick={show=false;startEdit();applyValues(values+(p.id to i.toDouble()))}) }
                                            }
                                        }
                                    } else CcRuler(p,value,onStart={startEdit()},onValue={applyValues(values+(p.id to it))})
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
