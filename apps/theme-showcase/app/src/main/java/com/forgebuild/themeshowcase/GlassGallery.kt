package com.forgebuild.themeshowcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.glass.GlassBottomNav
import com.forgebuild.engine.ui.glass.GlassBottomNavItem
import com.forgebuild.engine.ui.glass.GlassButton
import com.forgebuild.engine.ui.glass.GlassCard
import com.forgebuild.engine.ui.glass.GlassDialog
import com.forgebuild.engine.ui.glass.GlassSlider
import com.forgebuild.engine.ui.glass.GlassSurfaceButton
import com.forgebuild.engine.ui.glass.GlassSwitch
import com.forgebuild.engine.ui.glass.LiquidGlassSupport
import com.forgebuild.engine.ui.glass.LiquidGlassTheme
import com.forgebuild.engine.ui.glass.liquidBackdropSource
import com.forgebuild.engine.ui.glass.rememberLiquidGlassBackdrop
import com.forgebuild.engine.ui.icons.EngineIcons

/**
 * Liquid Glass gallery — refracting/blurring glass components over a colorful
 * backdrop: button, card, switch, slider, dialog and bottom navigation.
 *
 * The colorful gradient + discs behind are the glass SOURCE (marked with
 * [liquidBackdropSource]); the Glass* components refract/blur that layer.
 * Below API 33 every component auto-renders the frosted-lite fallback — the
 * selector notes this via [LiquidGlassSupport.isSupported].
 */
@Composable
fun GlassGallery(onBack: () -> Unit) {
    LiquidGlassTheme {
        val backdrop = rememberLiquidGlassBackdrop()
        val colors = LiquidGlassTheme.colors
        var navIndex by remember { mutableIntStateOf(0) }
        var showDialog by remember { mutableStateOf(false) }
        val navIcons = listOf(EngineIcons.Home, EngineIcons.Search, EngineIcons.Settings)
        val navLabels = listOf("Home", "Search", "Settings")

        Box(Modifier.fillMaxSize()) {
            // Glass SOURCE: colorful background the components refract/blur.
            Box(
                Modifier
                    .fillMaxSize()
                    .liquidBackdropSource(backdrop)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF6750A4), Color(0xFF3C5A99), Color(0xFFFF6900))
                        )
                    )
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 40.dp, y = (-30).dp)
                        .size(220.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0A84FF).copy(alpha = 0.55f))
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = (-50).dp, y = 60.dp)
                        .size(260.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFB300).copy(alpha = 0.5f))
                )
            }

            // Gallery content (glass components draw from the backdrop above).
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassSurfaceButton(onClick = onBack) {
                        Icon(EngineIcons.ArrowBack, contentDescription = "Back", tint = colors.content)
                        Text("Back", color = colors.content)
                    }
                }
                Text("Liquid Glass", color = colors.content, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                Text(
                    if (LiquidGlassSupport.isSupported)
                        "Real AGSL refraction + blur: the components bend the colorful backdrop behind them."
                    else
                        "Frosted-lite fallback — this device is below API 33, so components render the translucent fallback surface.",
                    color = colors.contentDim
                )

                // Buttons
                Text("Buttons", color = colors.content)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton(onClick = {}, backdrop = backdrop) { Text("Glass", color = colors.content) }
                    GlassButton(onClick = {}, backdrop = backdrop, tint = colors.accent) { Text("Tinted", color = colors.content) }
                }

                // Card
                Text("Card", color = colors.content)
                GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
                    Text("Glass card", color = colors.content)
                    Spacer(Modifier.height(4.dp))
                    Text("Vibrancy, blur, lens refraction, highlight and inner shadow over the backdrop.", color = colors.contentDim)
                }

                // Switch + Slider
                Text("Switch & Slider", color = colors.content)
                var switched by remember { mutableStateOf(true) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassSwitch(checked = switched, onCheckedChange = { switched = it }, backdrop = backdrop)
                    Text(if (switched) "On" else "Off", color = colors.content)
                }
                var slider by remember { mutableFloatStateOf(0.55f) }
                GlassSlider(value = slider, onValueChange = { slider = it }, backdrop = backdrop)

                // Dialog
                Text("Dialog", color = colors.content)
                GlassButton(onClick = { showDialog = true }, backdrop = backdrop) { Text("Open dialog", color = colors.content) }
            }

            // Bottom navigation (glass capsule).
            GlassBottomNav(
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                navLabels.forEachIndexed { i, label ->
                    GlassBottomNavItem(selected = navIndex == i, onClick = { navIndex = i }) {
                        Icon(
                            navIcons[i],
                            contentDescription = label,
                            tint = if (navIndex == i) colors.accent else colors.contentDim
                        )
                        Text(label, color = colors.content, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Dialog overlay.
            if (showDialog) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassDialog(
                        show = showDialog,
                        onDismissRequest = { showDialog = false },
                        backdrop = backdrop
                    ) {
                        Text("Glass dialog", color = colors.content, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("A glass surface floated over a dimming scrim.", color = colors.contentDim)
                        Spacer(Modifier.height(16.dp))
                        GlassSurfaceButton(onClick = { showDialog = false }) { Text("Close", color = colors.content) }
                    }
                }
            }
        }
    }
}
