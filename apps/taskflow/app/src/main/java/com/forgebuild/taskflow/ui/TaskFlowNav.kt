package com.forgebuild.taskflow.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler

private enum class Screen { LIST, SETTINGS, ONBOARDING }

/** Lightweight in-app navigation (back-stack of parent ids lives in the ViewModel). */
@Composable
fun TaskFlowNav(vm: TaskViewModel) {
    val onboardingDone by vm.onboardingDone.collectAsState()
    var screen by remember { mutableStateOf(Screen.LIST) }
    var editingTaskId by remember { mutableStateOf<Long?>(null) }
    var chatOpen by remember { mutableStateOf(false) }
    val path by vm.path.collectAsState()

    if (!onboardingDone) {
        OnboardingScreen(onFinish = { vm.completeOnboarding(); screen = Screen.LIST })
        return
    }

    BackHandler(enabled = editingTaskId != null || screen != Screen.LIST || path.isNotEmpty()) {
        when {
            editingTaskId != null -> editingTaskId = null
            screen != Screen.LIST -> screen = Screen.LIST
            else -> vm.navigateUp()
        }
    }

    when {
        editingTaskId != null -> TaskEditScreen(
            vm = vm, taskId = editingTaskId!!,
            onClose = { editingTaskId = null })
        screen == Screen.SETTINGS -> SettingsScreen(vm = vm, onBack = { screen = Screen.LIST })
        else -> TaskListScreen(
            vm = vm,
            onEditTask = { editingTaskId = it },
            onOpenSettings = { screen = Screen.SETTINGS },
            onOpenChat = { chatOpen = true })
    }

    if (chatOpen) ChatPanel(onClose = { chatOpen = false })
}
