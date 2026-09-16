package com.forgebuild.themeshowcase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.forgebuild.engine.ui.theme.EngineTheme
import com.forgebuild.engine.ui.theme.ForgeBuildTheme

/**
 * FORGEBUILD: the generating AI replaces the contents of setContent with the
 * real app UI, writing ordinary Kotlin/Compose code against the Engine themes
 * (com.forgebuild.engine.ui.theme / .ui.miuix / .ui.glass) and icon set
 * (com.forgebuild.engine.ui.icons).
 *
 * This is a component/theme library — there is no fixed layout schema.
 *
 * The Engine ships three selectable themes (see [EngineTheme]):
 *  - EngineTheme.MATERIAL      -> Material 3 Expressive (DEFAULT, backward compatible)
 *  - EngineTheme.MIUIX         -> Miuix (com.forgebuild.engine.ui.miuix.MiuixEngineTheme)
 *  - EngineTheme.LIQUID_GLASS  -> Liquid Glass (com.forgebuild.engine.ui.glass.LiquidGlassTheme)
 *
 * DEFAULT remains Material 3 Expressive below, so existing generated apps do
 * not change. A generated app that wants Miuix / Liquid Glass switches the
 * wrapper here (and may persist the choice via the [EngineTheme] enum).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // DEFAULT theme: Material 3 Expressive (unchanged behavior).
            ForgeBuildTheme {
                // Starter surface — replaced per app.
                com.forgebuild.engine.StarterScreen()
            }
        }
    }
}
