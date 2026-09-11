package com.forgebuild.clipforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.forgebuild.clipforge.ui.ClipForgeApp
import com.forgebuild.engine.ui.theme.ForgeBuildTheme

/** ClipForge — Android client for the ClipForge Telegram bot (motionssalt/clipforge). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ForgeBuildTheme {
                ClipForgeApp()
            }
        }
    }
}
