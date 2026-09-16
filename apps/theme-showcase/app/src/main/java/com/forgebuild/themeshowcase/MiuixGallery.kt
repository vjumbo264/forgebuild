package com.forgebuild.themeshowcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.miuix.MiuixEngineBottomNav
import com.forgebuild.engine.ui.miuix.MiuixEngineBottomNavItem
import com.forgebuild.engine.ui.miuix.MiuixEngineButton
import com.forgebuild.engine.ui.miuix.MiuixEngineCard
import com.forgebuild.engine.ui.miuix.MiuixEngineDialog
import com.forgebuild.engine.ui.miuix.MiuixEngineSlider
import com.forgebuild.engine.ui.miuix.MiuixEngineSwitch
import com.forgebuild.engine.ui.miuix.MiuixEngineTextButton
import com.forgebuild.engine.ui.miuix.MiuixEngineTheme
import top.yukonga.miuix.kmp.basic.Text

/**
 * Miuix gallery — the MIUI-style suite: squircle-corner buttons, cards,
 * switch, slider, dialog and bottom navigation under the Miuix base theme.
 */
@Composable
fun MiuixGallery(onBack: () -> Unit) {
    MiuixEngineTheme {
        var navIndex by remember { mutableIntStateOf(0) }
        val navIcons = listOf(EngineIcons.Home, EngineIcons.Search, EngineIcons.Settings)
        val navLabels = listOf("Home", "Search", "Settings")
        var showDialog by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiuixEngineTextButton(text = "Back", onClick = onBack)
                    Text("Miuix")
                }
                Text("Squircle corners and MIUI-style components — buttons, card, switch, slider, dialog and bottom navigation.")

                // Buttons
                Text("Buttons")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiuixEngineButton(onClick = {}) { Text("Filled") }
                    MiuixEngineButton(onClick = {}, enabled = false) { Text("Disabled") }
                }

                // Card
                Text("Card")
                MiuixEngineCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Miuix card")
                        Text("Per-component squircle cornerRadius — Miuix's shape system.")
                    }
                }

                // Switch + Slider
                Text("Switch & Slider")
                var switched by remember { mutableStateOf(true) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiuixEngineSwitch(checked = switched, onCheckedChange = { switched = it })
                    Text(if (switched) "On" else "Off")
                }
                var slider by remember { mutableFloatStateOf(0.55f) }
                MiuixEngineSlider(value = slider, onValueChange = { slider = it })

                // Dialog
                Text("Dialog")
                MiuixEngineButton(onClick = { showDialog = true }) { Text("Open dialog") }
            }

            MiuixEngineBottomNav {
                navLabels.forEachIndexed { i, label ->
                    MiuixEngineBottomNavItem(
                        selected = navIndex == i,
                        onClick = { navIndex = i },
                        icon = navIcons[i],
                        label = label
                    )
                }
            }
        }

        MiuixEngineDialog(
            show = showDialog,
            onDismissRequest = { showDialog = false },
            title = "Miuix dialog",
            summary = "Window dialog with title and summary from the Miuix suite."
        ) {
            MiuixEngineTextButton(text = "Done", onClick = { showDialog = false })
        }
    }
}
