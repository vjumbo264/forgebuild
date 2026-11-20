package com.forgebuild.recall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forgebuild.engine.ui.theme.ForgeBuildTheme
import com.forgebuild.recall.ui.MainScreen
import com.forgebuild.recall.ui.OnboardingScreen
import com.forgebuild.recall.ui.RecallViewModel
import com.forgebuild.recall.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ForgeBuildTheme {
                val vm: RecallViewModel = viewModel()
                val state by vm.state.collectAsState()
                var showSettings by rememberSaveable { mutableStateOf(false) }

                BackHandler(enabled = showSettings) {
                    showSettings = false
                }

                when {
                    !state.onboardingDone -> {
                        OnboardingScreen(
                            onFinish = { vm.markOnboardingDone() },
                        )
                    }

                    showSettings -> {
                        SettingsScreen(
                            state = state,
                            onBack = { showSettings = false },
                            onToggleInterval = vm::toggleInterval,
                            onAddInterval = vm::addInterval,
                            onUpdateInterval = vm::updateInterval,
                            onDeleteInterval = vm::deleteInterval,
                            onRestoreDefaults = vm::restoreDefaults,
                            onSetHeadsUp = vm::setHeadsUpNotifications,
                            onSetHighPrecision = vm::setHighPrecisionAlarmClock,
                            onPreviewSound = vm::previewSound,
                            onGlobalTest = vm::playGlobalTest,
                            onStopAudio = vm::stopAudio,
                        )
                    }

                    else -> {
                        MainScreen(
                            state = state,
                            onStartOrResume = vm::startOrResume,
                            onPause = vm::pause,
                            onStop = vm::stop,
                            onPreviewSound = vm::previewSound,
                            onGlobalTest = vm::playGlobalTest,
                            onOpenSettings = { showSettings = true },
                        )
                    }
                }
            }
        }
    }
}
