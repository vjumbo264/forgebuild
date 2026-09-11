package com.forgebuild.tapcounter

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.icons.EngineIcons

@Composable
fun CounterScreen() {
    var count by remember { mutableStateOf(0) }
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$count", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(32.dp))
            FilledIconButton(onClick = { count++ }, modifier = Modifier.size(72.dp)) {
                Icon(EngineIcons.Add, contentDescription = "Add one", modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { count = 0 }) { Text("Reset") }
        }
    }
}
