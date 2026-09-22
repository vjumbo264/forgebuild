package com.forgebuild.taskflow.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.forgebuild.engine.permissions.EnginePermission
import com.forgebuild.engine.permissions.PermissionWiring
import com.forgebuild.engine.ui.components.ExpressiveLoading
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.taskflow.ai.AgentOrchestrator
import com.forgebuild.taskflow.ai.ChatMessage
import kotlinx.coroutines.launch

/** AI chat/voice panel: persistent mic FAB opens this bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPanel(onClose: () -> Unit) {
    val context = LocalContext.current
    val orchestrator = remember { AgentOrchestrator(context) }
    val messages by orchestrator.messages.collectAsState()
    val busy by orchestrator.busy.collectAsState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    val listState = rememberLazyListState()

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                listening = false
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { input = it }
                sr.destroy(); recognizer = null
            }
            override fun onError(error: Int) { listening = false; sr.destroy(); recognizer = null }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        listening = true
        sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening()
    }

    DisposableEffect(Unit) { onDispose { recognizer?.destroy() } }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    ModalBottomSheet(onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = SpacingTokens.Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(EngineIcons.SmartToy, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.padding(SpacingTokens.Spacing.xxs))
                Text("TaskFlow AI", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(SpacingTokens.Spacing.sm))
            LazyColumn(state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(min = SpacingTokens.Spacing.xxxl * 2, max = SpacingTokens.Spacing.xxxl * 9),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                items(messages) { m -> ChatBubble(m) }
                if (busy) item { ExpressiveLoading(size = SpacingTokens.Spacing.xl) }
            }
            Spacer(Modifier.height(SpacingTokens.Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                OutlinedTextField(value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (listening) "Listening…" else "Tell the AI what to do…") },
                    shape = MaterialTheme.shapes.large, maxLines = 3)
                IconButton(onClick = {
                    if (PermissionWiring.missing(context, EnginePermission.MICROPHONE).isEmpty()) startListening()
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }) {
                    Icon(EngineIcons.Mic, contentDescription = "Voice input",
                        tint = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty() && !busy) {
                        input = ""
                        scope.launch { orchestrator.send(text) }
                    }
                }) { Icon(EngineIcons.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.height(SpacingTokens.Spacing.lg))
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start) {
        Surface(shape = MaterialTheme.shapes.large, color = color,
            modifier = Modifier.fillMaxWidth(0.86f)) {
            Column(Modifier.padding(SpacingTokens.Spacing.sm)) {
                if (m.role == ChatMessage.Role.ACTION) {
                    Text("action", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Text(m.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
