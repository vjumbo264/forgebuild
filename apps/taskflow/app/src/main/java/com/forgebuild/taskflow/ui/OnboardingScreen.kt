package com.forgebuild.taskflow.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.forgebuild.engine.permissions.EnginePermission
import com.forgebuild.engine.permissions.PermissionWiring
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens

/** First-run permission flow: every request explained in plain language, never silent. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    var notifGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || PermissionWiring.isGranted(context, Manifest.permission.POST_NOTIFICATIONS))
    }
    var micGranted by remember {
        mutableStateOf(PermissionWiring.isGranted(context, Manifest.permission.RECORD_AUDIO))
    }
    var batteryExempt by remember {
        mutableStateOf(PermissionWiring.isBatteryOptimizationExempt(context))
    }
    // Pass 7 FIX: live exact-alarm status so the card flips to granted after the OS flow.
    var alarmGranted by remember {
        mutableStateOf(PermissionWiring.canScheduleExactAlarms(context))
    }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                alarmGranted = PermissionWiring.canScheduleExactAlarms(context)
                batteryExempt = PermissionWiring.isBatteryOptimizationExempt(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifGranted = granted
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
    }

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SpacingTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md)
        ) {
            Spacer(Modifier.height(SpacingTokens.Spacing.xl))
            Icon(
                EngineIcons.CheckCircle, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(SpacingTokens.Spacing.xxxl)
            )
            Text("Welcome to TaskFlow", style = MaterialTheme.typography.headlineMedium)
            Text(
                "A priority-ordered to-do list with time tracking, exact-time reminders, and an AI voice assistant. To work reliably in the background, it requests a few standard Android permissions:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PermissionCard(
                icon = EngineIcons.Alarm,
                title = "Notifications",
                why = "Allows TaskFlow to alert you when fixed-time tasks are due, send approaching-deadline warnings, and your daily agenda. No spam.",
                isGranted = notifGranted,
                onGrant = {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else notifGranted = true
                }
            )

            PermissionCard(
                icon = EngineIcons.CalendarToday,
                title = "Exact alarms",
                why = "Ensures reminders fire at the exact minute you set, even in Doze mode. Android shows you the official system permission screen for this.",
                isGranted = alarmGranted,
                onGrant = {
                    (context as? Activity)?.let { PermissionWiring.requestExactAlarm(it) }
                    alarmGranted = PermissionWiring.canScheduleExactAlarms(context)
                }
            )

            PermissionCard(
                icon = EngineIcons.Bolt,
                title = "Run in background (Battery exemption)",
                why = "Android aggressively suspends background apps. Granting this prompt ensures your reminders and background service stay alive reliably.",
                isGranted = batteryExempt,
                onGrant = {
                    PermissionWiring.requestBatteryExemption(context)
                    batteryExempt = PermissionWiring.isBatteryOptimizationExempt(context)
                }
            )

            PermissionCard(
                icon = EngineIcons.Mic,
                title = "Microphone (Voice input)",
                why = "Enables real-time voice input with live audio waveform animation so you can speak tasks directly to the AI assistant.",
                isGranted = micGranted,
                onGrant = {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            )

            Spacer(Modifier.height(SpacingTokens.Spacing.md))
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Text("Get started")
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    why: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            Modifier.padding(SpacingTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
            ) {
                Icon(icon, null, tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isGranted) {
                Text("✓ Permission granted", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            } else {
                OutlinedButton(onClick = onGrant) { Text("Allow") }
            }
        }
    }
}
