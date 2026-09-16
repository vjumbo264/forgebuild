package com.forgebuild.themeshowcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.components.EngineCircularWavyProgress
import com.forgebuild.engine.ui.components.EngineLinearWavyProgress
import com.forgebuild.engine.ui.components.EngineLoadingIndicator
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.ForgeBuildTheme

/**
 * Material 3 Expressive gallery — the full expressive suite: dynamic color,
 * expressive shapes + spring motion, wavy/loading indicators, and the standard
 * component set (buttons, card, switch, slider, dialog, bottom navigation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialGallery(onBack: () -> Unit) {
    ForgeBuildTheme {
        var navIndex by remember { mutableIntStateOf(0) }
        val navIcons = listOf(EngineIcons.Home, EngineIcons.Search, EngineIcons.Settings)
        val navLabels = listOf("Home", "Search", "Settings")

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Material 3 Expressive") },
                    navigationIcon = {
                        TextButton(onClick = onBack) {
                            Icon(EngineIcons.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    navLabels.forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = navIndex == i,
                            onClick = { navIndex = i },
                            icon = { Icon(navIcons[i], contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    "Dynamic color, expressive shapes, spring motion, and the wavy/loading indicators — the full Material 3 Expressive suite.",
                    style = MaterialTheme.typography.bodyLarge
                )

                // Buttons
                SectionTitle("Buttons")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {}) { Text("Filled") }
                    OutlinedButton(onClick = {}) { Text("Outlined") }
                    TextButton(onClick = {}) { Text("Text") }
                }

                // Card
                SectionTitle("Card")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Expressive card", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Increased corner tiers and expressive shape system, tinted from the dynamic color scheme.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Switch + Slider
                SectionTitle("Switch & Slider")
                var switched by remember { mutableStateOf(true) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Switch(checked = switched, onCheckedChange = { switched = it })
                    Text(if (switched) "On" else "Off", style = MaterialTheme.typography.bodyLarge)
                }
                var slider by remember { mutableFloatStateOf(0.55f) }
                Slider(value = slider, onValueChange = { slider = it })

                // Expressive progress / loading (the wavy indicators)
                SectionTitle("Expressive progress")
                EngineLinearWavyProgress(progress = { slider }, modifier = Modifier.fillMaxWidth())
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EngineCircularWavyProgress(progress = { slider })
                    EngineLoadingIndicator()
                    EngineLoadingIndicator(progress = { slider })
                }

                // Dialog
                SectionTitle("Dialog")
                var showDialog by remember { mutableStateOf(false) }
                Button(onClick = { showDialog = true }) { Text("Open dialog") }
                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        title = { Text("Expressive dialog") },
                        text = { Text("Spring-based motion and expressive shapes, straight from the theme.") },
                        confirmButton = {
                            TextButton(onClick = { showDialog = false }) { Text("Done") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}
