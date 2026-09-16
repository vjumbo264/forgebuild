package com.forgebuild.themeshowcase

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.glass.LiquidGlassSupport
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.EngineTheme

/**
 * Theme selector — entry screen. Rendered under the app's outer
 * [com.forgebuild.engine.ui.theme.ForgeBuildTheme] (Material 3 Expressive,
 * the Engine default); picking an entry swaps the whole UI into that system.
 *
 * The Liquid Glass entry notes the device capability via
 * [LiquidGlassSupport.isSupported] (below API 33 the components render the
 * frosted-lite fallback instead of true refraction/blur).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorScreen(
    current: EngineTheme,
    onSelect: (EngineTheme) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme Showcase") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Experience three design languages in one app. Pick a suite to open its full component gallery.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(4.dp))

            ThemeCard(
                title = "Material 3 Expressive",
                subtitle = "Google's expressive suite — dynamic color, spring motion, expressive shapes, wavy progress indicators.",
                icon = EngineIcons.Bolt,
                selected = current == EngineTheme.MATERIAL,
                badge = if (current == EngineTheme.MATERIAL) "Current" else null,
                onClick = { onSelect(EngineTheme.MATERIAL) }
            )
            ThemeCard(
                title = "Miuix",
                subtitle = "MIUI-style suite — squircle corners, Miuix buttons, cards, switch, slider, dialog and bottom navigation.",
                icon = EngineIcons.Home,
                selected = current == EngineTheme.MIUIX,
                badge = if (current == EngineTheme.MIUIX) "Current" else null,
                onClick = { onSelect(EngineTheme.MIUIX) }
            )
            ThemeCard(
                title = "Liquid Glass",
                subtitle = "Refracting, blurring glass surfaces over a live backdrop — buttons, card, switch, slider, dialog, bottom navigation.",
                icon = EngineIcons.Search,
                selected = current == EngineTheme.LIQUID_GLASS,
                badge = when {
                    current == EngineTheme.LIQUID_GLASS -> "Current"
                    !LiquidGlassSupport.isSupported -> "Frosted fallback (below API 33)"
                    else -> null
                },
                onClick = { onSelect(EngineTheme.LIQUID_GLASS) }
            )
        }
    }
}

@Composable
private fun ThemeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    badge: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                if (badge != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}
