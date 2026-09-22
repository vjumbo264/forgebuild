package com.forgebuild.taskflow.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.forgebuild.engine.permissions.PermissionWiring
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens

/** First-run permission flow: every request explained in plain language, never silent. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Scaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(SpacingTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md)) {
            Spacer(Modifier.height(SpacingTokens.Spacing.xl))
            Icon(EngineIcons.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(SpacingTokens.Spacing.xxxl))
            Text("Welcome to TaskFlow", style = MaterialTheme.typography.headlineMedium)
            Text("A priority-ordered to-do list with exact-time reminders and a built-in AI assistant. To work reliably it needs a few permissions — here's exactly why, in plain language:",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            PermissionCard(EngineIcons.Alarm, "Notifications",
                "So TaskFlow can alert you when a fixed-time task is due, and send your daily agenda. No spam — only the alerts you enable in Settings.") {
                if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            PermissionCard(EngineIcons.CalendarToday, "Exact alarms",
                "So reminders fire at the exact minute you set, even in Doze mode. Android shows you a system screen for this — TaskFlow never grants it silently.") {
                PermissionWiring.requestExactAlarm(context as Activity)
            }
            PermissionCard(EngineIcons.Bolt, "Battery optimization exemption",
                "Android aggressively stops background apps. Exempting TaskFlow keeps your reminders reliable. Honest note: no app can run truly unkillable background work — TaskFlow uses exact alarms + this exemption + a tiny persistent notification instead, the realistic compliant setup.") {
                val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${context.packageName}")))
                }
            }
            PermissionCard(EngineIcons.Mic, "Microphone (optional)",
                "Only if you want to talk to the AI assistant instead of typing. Used solely while you hold the mic button; audio goes to Android's speech recognizer, not stored.") {
                notifPermission.launch(Manifest.permission.RECORD_AUDIO)
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.md))
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large) { Text("Get started") }
        }
    }
}

@Composable
private fun PermissionCard(icon: androidx.compose.ui.graphics.vector.ImageVector,
                           title: String, why: String, onGrant: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(SpacingTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Text(why, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onGrant) { Text("Allow") }
        }
    }
}
