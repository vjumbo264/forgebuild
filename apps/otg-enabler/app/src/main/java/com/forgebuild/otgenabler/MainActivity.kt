package com.forgebuild.otgenabler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.forgebuild.engine.ui.theme.ForgeBuildTheme
import com.forgebuild.otgenabler.ui.OtgEnablerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ForgeBuildTheme {
                OtgEnablerScreen()
            }
        }
    }
}
