package com.forgebuild.taskflow.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.ButtonDefaults
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
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * AI chat & voice panel:
 * Supports interactive natural language commands, full function calling,
 * and live WhatsApp-style audio waveform voice input animation.
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
    var listening by remember { mutableStateOf(false) }
    var rmsdB by remember { mutableFloatStateOf(0f) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    val listState = rememberLazyListState()

    // Intent fallback launcher for devices without SpeechRecognizer service
    val speechIntentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        listening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                input = spoken
                scope.launch { orchestrator.send(spoken) }
            }
        }
    }

    fun stopListening() {
        listening = false
        rmsdB = 0f
        runCatching {
            recognizer?.stopListening()
            recognizer?.destroy()
        }
        recognizer = null
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            // Launch standard Android voice dialog fallback
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a task or command…")
                }
                speechIntentLauncher.launch(intent)
            } catch (_: Exception) {}
            return
        }

        stopListening()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() { listening = true }
            override fun onRmsChanged(rmsdBValue: Float) {
                // Normalize rmsdB (typically -2 to 10) to 0.0 .. 1.0 range
                val norm = ((rmsdBValue + 2f) / 12f).coerceIn(0f, 1f)
                rmsdB = norm
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false; rmsdB = 0f }
            override fun onError(error: Int) {
                listening = false
                rmsdB = 0f
                runCatching { sr.destroy() }
                recognizer = null
            }
            override fun onResults(results: Bundle?) {
                listening = false
                rmsdB = 0f
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    input = text
                    scope.launch { orchestrator.send(text) }
                }
                runCatching { sr.destroy() }
                recognizer = null
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    input = partial
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        listening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            sr.startListening(intent)
        } catch (_: Exception) {
            listening = false
            sr.destroy()
            recognizer = null
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening()
    }

    LaunchedEffect(initialStartListening) {
        if (initialStartListening) {
            if (PermissionWiring.missing(context, EnginePermission.MICROPHONE).isEmpty()) {
                startListening()
            } else {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopListening() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    ModalBottomSheet(
        onDismissRequest = {
            stopListening()
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
                TextButton(onClick = onClose) { Text("Done") }
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
                                Text("• \"How much time do I have left today?\"", style = MaterialTheme.typography.bodySmall)
                                Text("• \"Mark morning workout as completed\"", style = MaterialTheme.typography.bodySmall)
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

            // LIVE AUDIO WAVEFORM RECORDING STATE (WhatsApp voice note style)
            if (listening) {
                LiveWaveformBanner(
                    rmsLevel = rmsdB,
                    onStop = { stopListening() }
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask AI or speak a task…") },
                        shape = MaterialTheme.shapes.large,
                        maxLines = 3
                    )
                    IconButton(
                        onClick = {
                            if (PermissionWiring.missing(context, EnginePermission.MICROPHONE).isEmpty()) {
                                startListening()
                            } else {
                                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(EngineIcons.Mic, contentDescription = "Voice input")
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

/**
 * Live audio spectrum / waveform animation matching WhatsApp-style voice-note recording.
 */
@Composable
private fun LiveWaveformBanner(
    rmsLevel: Float,
    onStop: () -> Unit
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
                        "Listening… Speak your command",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                FilledTonalButton(
                    onClick = onStop,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Done")
                }
            }

            Spacer(Modifier.height(10.dp))

            // Spectrum bars (24 animated bars)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val barCount = 24
                for (i in 0 until barCount) {
                    val phase = (i.toFloat() / barCount) * 3.14159f
                    val sinFactor = (sin((phase * 2.2f).toDouble()).toFloat() + 1.2f) / 2.2f
                    val targetHeight = (8f + (rmsLevel * 36f * sinFactor)).coerceIn(6f, 40f)
                    val animatedHeight by animateFloatAsState(
                        targetValue = targetHeight,
                        animationSpec = tween(120),
                        label = "barHeight$i"
                    )

                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(animatedHeight.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (i % 2 == 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary
                            )
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
