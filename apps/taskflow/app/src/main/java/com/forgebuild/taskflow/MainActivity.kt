package com.forgebuild.taskflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forgebuild.engine.ui.theme.ForgeBuildTheme
import com.forgebuild.taskflow.reminder.DueForegroundService
import com.forgebuild.taskflow.settings.ThemeMode
import com.forgebuild.taskflow.ui.TaskFlowNav
import com.forgebuild.taskflow.ui.TaskViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DueForegroundService.start(this)
        setContent {
            val vm: TaskViewModel = viewModel()
            val theme by vm.themeMode.collectAsState()
            val dark = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            ForgeBuildTheme(darkTheme = dark) {
                TaskFlowNav(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        DueForegroundService.start(this)
    }
}
