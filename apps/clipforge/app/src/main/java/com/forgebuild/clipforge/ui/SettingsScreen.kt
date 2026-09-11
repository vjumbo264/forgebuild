package com.forgebuild.clipforge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.icons.EngineIcons

/** Settings — mirrors the bot's /settings (narrator voice, watermark, captions, reframe). */
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    var voice by remember(state.settings) { mutableStateOf(state.settings.narratorVoice) }
    var watermark by remember(state.settings) { mutableStateOf(state.settings.watermarkText) }
    var captions by remember(state.settings) { mutableStateOf(state.settings.captionsEnabled) }
    var vertical by remember(state.settings) { mutableStateOf(state.settings.verticalReframe) }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(value = voice, onValueChange = { voice = it },
                label = { Text("Narrator voice (Edge TTS)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = watermark, onValueChange = { watermark = it },
                label = { Text("Creator watermark") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Burn captions", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = captions, onCheckedChange = { captions = it })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("9:16 vertical reframe", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = vertical, onCheckedChange = { vertical = it })
            }
            Button(onClick = {
                vm.saveSettings(state.settings.copy(
                    narratorVoice = voice, watermarkText = watermark,
                    captionsEnabled = captions, verticalReframe = vertical))
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Save settings")
            }
            HorizontalDivider()
            OutlinedButton(onClick = { vm.disconnect() }, modifier = Modifier.fillMaxWidth()) {
                Icon(EngineIcons.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Disconnect clone")
            }
        }
    }
}
