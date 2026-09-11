package com.forgebuild.clipforgeandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.forgebuild.clipforgeandroid.ui.ClipForgeApp
import com.forgebuild.engine.ui.theme.ForgeBuildTheme

class MainActivity : ComponentActivity() {
    private val vm: ClipForgeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ForgeBuildTheme {
                ClipForgeApp(vm)
            }
        }
    }
}
