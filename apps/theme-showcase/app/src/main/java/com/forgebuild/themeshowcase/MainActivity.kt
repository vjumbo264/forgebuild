package com.forgebuild.themeshowcase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.forgebuild.engine.ui.theme.EngineTheme
import com.forgebuild.engine.ui.theme.ForgeBuildTheme

/**
 * Theme Showcase — experience the Engine's three design languages in one app:
 * Material 3 Expressive, Miuix, and Liquid Glass.
 *
 * The app opens on a selector (rendered under the Engine default,
 * Material 3 Expressive). Picking a suite swaps the whole UI into that system
 * and opens its full component gallery; the choice is persisted via
 * [ThemeStore] and restored on the next launch.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val store = remember { ThemeStore(context.applicationContext) }
            var theme by remember { mutableStateOf(store.load()) }
            // Null = show the selector; non-null = show that suite's gallery.
            var gallery by remember { mutableStateOf<EngineTheme?>(null) }

            fun select(t: EngineTheme) {
                theme = t
                store.save(t)
                gallery = t
            }

            // System back from a gallery returns to the selector.
            if (gallery != null) {
                BackHandler { gallery = null }
            }

            when (gallery) {
                EngineTheme.MATERIAL -> MaterialGallery(onBack = { gallery = null })
                EngineTheme.MIUIX -> MiuixGallery(onBack = { gallery = null })
                EngineTheme.LIQUID_GLASS -> GlassGallery(onBack = { gallery = null })
                null -> ForgeBuildTheme {
                    SelectorScreen(current = theme, onSelect = { t -> select(t) })
                }
            }
        }
    }
}
