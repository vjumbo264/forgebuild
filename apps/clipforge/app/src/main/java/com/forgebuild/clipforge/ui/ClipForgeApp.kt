package com.forgebuild.clipforge.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forgebuild.engine.ui.icons.EngineIcons

@Composable
fun ClipForgeApp(vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var screen by remember { mutableStateOf("home") }
    var tab by remember { mutableIntStateOf(0) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    if (state.connection == null) {
        ConnectScreen(vm)
        return
    }

    when (screen) {
        "new" -> NewJobScreen(vm, onBack = { screen = "home" })
        else -> Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(EngineIcons.Home, contentDescription = null) },
                        label = { Text("Home") })
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(EngineIcons.Settings, contentDescription = null) },
                        label = { Text("Settings") })
                }
            }
        ) { padding ->
            Surface(modifier = Modifier.padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(vm, onNewJob = { screen = "new" })
                    else -> SettingsScreen(vm)
                }
            }
        }
    }
}
