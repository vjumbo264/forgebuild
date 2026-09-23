package com.forgebuild.taskflow.ui

import android.Manifest
import android.media.MediaRecorder
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.permissions.EnginePermission
import com.forgebuild.engine.permissions.PermissionWiring
import com.forgebuild.engine.ui.components.ExpressiveLoading
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.taskflow.ai.AgentOrchestrator
import com.forgebuild.taskflow.ai.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class VoiceStage { IDLE, RECORDING, SENDING }

/**
 * AI chat & voice panel.
 * Voice input records the RAW audio clip (MediaRecorder) and sends it straight to Gemini,
 * which natively transcribes/understands the audio itself — no on-device speech-to-text.
 * While recording: live WhatsApp-style audio-spectrum waveform driven by real mic amplitude.
 * After recording stops: a distinct "sending voice note" state while the audio uploads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPanel(
    initialStartListening: Boolean = false,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val orchestrator = remember { AgentOrchestrator(context) }
    val messages by orchestrator.messages.collectAsState()
    val busy by orchestrator.busy.collectAsState()
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var voiceStage by remember { mutableStateOf(VoiceStage.IDLE) }
    var micLevel by remember { mutableFloatStateOf(0f) }
    /** Rolling history of live amplitude samples — one entry per bar, newest last (scrolls right→left). */
    var waveformSamples by remember { mutableStateOf<List<Float>>(emptyList()) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    val listState = rememberLazyListState()

    fun stopRecorder(): File? {
        val f = audioFile
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        micLevel = 0f
        audioFile = null
        return f?.takeIf { it.exists() && it.length() > 0 }
    }

    fun sendRecording() {
        val f = stopRecorder()
        voiceStage = VoiceStage.IDLE
        if (f == null) return
        // Distinct "sending voice note" state while the audio uploads to Gemini.
        voiceStage = VoiceStage.SENDING
        scope.launch {
            try {
                val base64 = withContext(Dispatchers.IO) {
                    Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                }
                orchestrator.sendAudio("audio/mp4", base64)
            } catch (_: Exception) {
            } finally {
                voiceStage = VoiceStage.IDLE
                runCatching { f.delete() }
            }
        }
    }

    fun cancelRecording() {
        val f = stopRecorder()
        voiceStage = VoiceStage.IDLE
        runCatching { f?.delete() }
    }

    fun startRecording() {
        waveformSamples = emptyList()
        runCatching {
            val f = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            val r = MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(96_000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            audioFile = f
            voiceStage = VoiceStage.RECORDING
        }.onFailure {
            recorder = null
            audioFile = null
            voiceStage = VoiceStage.IDLE
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
    }

    fun requestMic() {
        if (PermissionWiring.missing(context, EnginePermission.MICROPHONE).isEmpty()) {
            startRecording()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(initialStartListening) {
        if (initialStartListening) requestMic()
    }

    // Live mic-amplitude sampling: every tick captures ONE new bar that scrolls in from
    // the right while older bars shift left — the WhatsApp voice-note recording behaviour.
    LaunchedEffect(voiceStage) {
        while (voiceStage == VoiceStage.RECORDING && isActive) {
            val maxAmp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
            val level = (maxAmp / 32767f).coerceIn(0f, 1f)
            micLevel = level
            // sqrt-scaling gives a perceptually even response across quiet/loud speech.
            val shaped = kotlin.math.sqrt(level).coerceIn(0.06f, 1f)
            waveformSamples = (waveformSamples + shaped).takeLast(MAX_WAVEFORM_BARS)
            delay(90)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val f = runCatching {
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
                recorder = null
                audioFile
            }.getOrNull()
            runCatching { f?.delete() }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    ModalBottomSheet(
        onDismissRequest = {
            cancelRecording()
            onClose()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SpacingTokens.Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(EngineIcons.SmartToy, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(SpacingTokens.Spacing.xs))
                    Text("TaskFlow AI Assistant", style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = {
                    cancelRecording()
                    onClose()
                }) { Text("Done") }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.xs))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = SpacingTokens.Spacing.sm)
                        ) {
                            Column(Modifier.padding(SpacingTokens.Spacing.md)) {
                                Text(
                                    "Try saying or typing commands like:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text("• \"Create three tasks to do this week and scatter them\"", style = MaterialTheme.typography.bodySmall)
                                Text("• \"Set weekly groceries only on Tuesdays\"", style = MaterialTheme.typography.bodySmall)
                                Text("• \"Repeat every day until next Friday: water the plants\"", style = MaterialTheme.typography.bodySmall)
                                Text("• \"How much time do I have left today?\"", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                items(messages) { m -> ChatBubble(m) }

                if (busy) {
                    item {
                        Row(
                            Modifier.padding(SpacingTokens.Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
                        ) {
                            ExpressiveLoading(size = SpacingTokens.Spacing.lg)
                            Text("Thinking & updating tasks…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.sm))

            when (voiceStage) {
                VoiceStage.RECORDING -> LiveWaveformBanner(
                    samples = waveformSamples,
                    onSend = { sendRecording() },
                    onCancel = { cancelRecording() }
                )
                VoiceStage.SENDING -> SendingVoiceBanner()
                VoiceStage.IDLE -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask AI or record a voice note…") },
                        shape = MaterialTheme.shapes.large,
                        maxLines = 3
                    )
                    IconButton(
                        onClick = { requestMic() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(EngineIcons.Mic, contentDescription = "Record voice note")
                    }
                    IconButton(
                        onClick = {
                            val text = input.trim()
                            if (text.isNotEmpty() && !busy) {
                                input = ""
                                scope.launch { orchestrator.send(text) }
                            }
                        },
                        enabled = input.isNotBlank() && !busy,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(EngineIcons.Send, contentDescription = "Send")
                    }
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.lg))
        }
    }
}

/** Capacity of the rolling waveform history (one amplitude sample per bar). */
private const val MAX_WAVEFORM_BARS = 48

/**
 * Live waveform matching WhatsApp voice-note recording: bars scroll in from the right
 * and travel left continuously while recording; each bar's height is the live amplitude
 * captured at that moment. Uniform bar width, spacing and fully-rounded caps — drawn on
 * a Canvas so the strip stays clean and consistent no matter how loud the input is.
 */
@Composable
private fun LiveWaveformBanner(
    samples: List<Float>,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            Modifier
                .padding(horizontal = SpacingTokens.Spacing.md, vertical = SpacingTokens.Spacing.sm)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha))
                    )
                    Text(
                        "Recording… tap Send to finish",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    FilledTonalButton(
                        onClick = onSend,
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Send")
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Scrolling waveform strip: newest bar at the right edge, history sliding left.
            // Uniform 4dp bars / 3dp gaps / fully rounded caps, single accent colour with a
            // subtle age fade toward the left (older bars) — the chat-app voice-note look.
            val barColor = MaterialTheme.colorScheme.primary
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                val barWidth = 4.dp.toPx()
                val gap = 3.dp.toPx()
                val step = barWidth + gap
                val maxBarH = size.height
                val minBarH = 3.dp.toPx()
                val centerY = size.height / 2f
                val capacity = (size.width / step).toInt().coerceAtLeast(1)
                val visible = samples.takeLast(capacity)
                visible.forEachIndexed { index, amp ->
                    // index 0 = oldest (leftmost); last = newest, flush to the right edge.
                    val fromRight = visible.size - 1 - index
                    val x = size.width - step * (fromRight + 1) + gap / 2f
                    val h = (minBarH + (maxBarH - minBarH) * amp).coerceIn(minBarH, maxBarH)
                    val ageFade = 0.45f + 0.55f * (index + 1).toFloat() / visible.size
                    drawRoundRect(
                        color = barColor.copy(alpha = ageFade),
                        topLeft = androidx.compose.ui.geometry.Offset(x, centerY - h / 2f),
                        size = androidx.compose.ui.geometry.Size(barWidth, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
        }
    }
}

/**
 * Distinct "sending voice note" state shown after recording ends,
 * while the raw audio clip uploads to Gemini and before its response comes back.
 */
@Composable
private fun SendingVoiceBanner() {
    val infiniteTransition = rememberInfiniteTransition(label = "sendPulse")
    val wave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sendWave"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            Modifier
                .padding(horizontal = SpacingTokens.Spacing.md, vertical = SpacingTokens.Spacing.sm)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)
        ) {
            ExpressiveLoading(size = SpacingTokens.Spacing.lg)
            Column(Modifier.weight(1f)) {
                Text(
                    "Sending voice note to Gemini…",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Gemini is listening to your audio — no on-device transcription.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until 4) {
                    val h = (8f + wave * 14f * (1f - kotlin.math.abs(i - 1.5f) / 2f)).coerceIn(6f, 22f)
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(h.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onPrimaryContainer)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(m: ChatMessage) {
    val alignEnd = m.role == ChatMessage.Role.USER
    val color = when (m.role) {
        ChatMessage.Role.USER -> MaterialTheme.colorScheme.primaryContainer
        ChatMessage.Role.MODEL -> MaterialTheme.colorScheme.surfaceContainerHigh
        ChatMessage.Role.ACTION -> MaterialTheme.colorScheme.tertiaryContainer
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = color,
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(Modifier.padding(SpacingTokens.Spacing.sm)) {
                if (m.role == ChatMessage.Role.ACTION) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            EngineIcons.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Executed action",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Text(m.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
