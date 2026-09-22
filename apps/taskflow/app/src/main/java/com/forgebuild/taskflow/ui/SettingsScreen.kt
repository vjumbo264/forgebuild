package com.forgebuild.taskflow.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.forgebuild.engine.permissions.PermissionWiring
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.taskflow.reminder.DueForegroundService
import com.forgebuild.taskflow.settings.AppSettings
import com.forgebuild.taskflow.settings.GeminiKeyStore
import com.forgebuild.taskflow.settings.NotifType
import com.forgebuild.taskflow.settings.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: TaskViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = vm.settings
    val keyStore = remember { GeminiKeyStore.get(context) }
    val keys by keyStore.keys.collectAsState()
    val theme by vm.themeMode.collectAsState()
    var newKey by remember { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(EngineIcons.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(SpacingTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md)) {

            // ---- Gemini API keys ----
            Text("Gemini API keys", style = MaterialTheme.typography.titleMedium)
            Text("Stored encrypted on this device only; sent nowhere except directly to Google's Gemini API. Order = failover priority.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            keys.forEachIndexed { i, k ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(SpacingTokens.Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(EngineIcons.Key, null, tint = MaterialTheme.colorScheme.primary)
                        Text("${i + 1}. ••••${k.takeLast(4)}", Modifier.weight(1f)
                            .padding(horizontal = SpacingTokens.Spacing.xs),
                            style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { keyStore.moveUp(k) }, enabled = i > 0) {
                            Icon(EngineIcons.PriorityHigh, "Move up")
                        }
                        IconButton(onClick = { keyStore.remove(k) }) {
                            Icon(EngineIcons.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = newKey, onValueChange = { newKey = it },
                    label = { Text("New API key") }, modifier = Modifier.weight(1f),
                    visualTransformation = PasswordVisualTransformation(),
                    shape = MaterialTheme.shapes.medium, singleLine = true)
                IconButton(onClick = { keyStore.add(newKey); newKey = "" }) {
                    Icon(EngineIcons.Add, "Add key", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // ---- Notifications ----
            Text("Notifications", style = MaterialTheme.typography.titleMedium)
            NotifType.entries.forEach { type ->
                val label = when (type) {
                    NotifType.DUE -> "Fixed-time task due"
                    NotifType.OVERDUE -> "Overdue task"
                    NotifType.DAILY_AGENDA -> "Daily agenda summary"
                    NotifType.API_KEYS_FAILED -> "All Gemini keys failed"
                    NotifType.AGENT_CONFIRMATION -> "AI agent confirmations"
                    NotifType.APPROACHING_DEADLINE -> "Approaching deadline"
                    NotifType.RECURRING_INSTANCE -> "Recurring instance generated"
                }
                val enabled = runBlocking { settings.notifEnabled(type).first() }
                var checked by remember(type) { mutableStateOf(enabled) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = checked, onCheckedChange = {
                        checked = it; scope.launch { settings.setNotifEnabled(type, it) }
                    })
                }
                if (type == NotifType.APPROACHING_DEADLINE && checked) {
                    var lead by remember { mutableStateOf(runBlocking { settings.deadlineLeadMinutes.first() }.toFloat()) }
                    Column {
                        Text("Warn ${lead.toInt()} min before", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(value = lead, onValueChange = { lead = it },
                            onValueChangeFinished = { scope.launch { settings.setDeadlineLeadMinutes(lead.toInt()) } },
                            valueRange = 5f..60f)
                    }
                }
            }

            // ---- Reliability / permissions ----
            Text("Reminder reliability", style = MaterialTheme.typography.titleMedium)
            Text("Android does not allow any app to run unkillable background work. TaskFlow uses exact alarms plus a low-priority persistent notification, and asks Android to exempt it from battery optimization — the realistic, compliant setup.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val fgActive = runBlocking { settings.foregroundActive.first() }
            var fgChecked by remember { mutableStateOf(fgActive) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Persistent reminder service", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = fgChecked, onCheckedChange = { fgChecked = it; vm.setForegroundService(it) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                OutlinedButton(onClick = { PermissionWiring.requestExactAlarm(context as Activity) }) {
                    Text("Allow exact alarms")
                }
                OutlinedButton(onClick = {
                    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
                    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:${context.packageName}")))
                    }
                }) { Text("Battery exemption") }
            }

            // ---- Theme ----
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(selected = theme == mode,
                        onClick = { vm.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = ThemeMode.entries.size)) {
                        Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }
            Spacer(Modifier.height(SpacingTokens.Spacing.lg))
        }
    }
}
