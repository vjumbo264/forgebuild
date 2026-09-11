package com.forgebuild.aandroidversionofforgebuild

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.forgebuild.aandroidversionofforgebuild.ui.ForgeBuildApp
import com.forgebuild.engine.ui.theme.ForgeBuildTheme

class MainActivity : ComponentActivity() {
    private val viewModel by lazy {
        ViewModelProvider(this)[ForgeBuildViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ForgeBuildTheme {
                ForgeBuildApp(viewModel = viewModel)
            }
        }
    }
}
