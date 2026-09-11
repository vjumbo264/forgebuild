package com.forgebuild.clipforge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.icons.EngineIcons

/** Clone connect flow — the Android counterpart of the bot's "Connect existing clone". */
@Composable
fun ConnectScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    var owner by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var pat by remember { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(EngineIcons.Movie, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("ClipForge", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Android client for the ClipForge Telegram bot (motionssalt/clipforge). " +
                    "Connect your private Shadow Clone repo — the same one your bot drives — " +
                    "with a GitHub token (repo + workflow scopes).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(value = owner, onValueChange = { owner = it },
                label = { Text("GitHub owner") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = repo, onValueChange = { repo = it },
                label = { Text("Clone repository") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = pat, onValueChange = { pat = it },
                label = { Text("Personal access token") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            state.connectError?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { vm.connect(owner, repo, pat) },
                enabled = !state.connecting && owner.isNotBlank() && repo.isNotBlank() && pat.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.connecting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(EngineIcons.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.connecting) "Connecting…" else "Connect clone")
            }
        }
    }
}
