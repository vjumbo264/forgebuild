package com.forgebuild.taskflow.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.permissions.PermissionWiring
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.taskflow.settings.GeminiKeyStore
import com.forgebuild.taskflow.settings.NotifType
import com.forgebuild.taskflow.settings.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: TaskViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = vm.settings
    val keyStore = remember { GeminiKeyStore.get(context) }
    val keys by keyStore.keys.collectAsState()
    val theme by vm.themeMode.collectAsState()
    val retentionDays by vm.retentionDays.collectAsState()

    var newKey by remember { mutableStateOf("") }
    var isBatteryExempt by remember { mutableStateOf(PermissionWiring.isBatteryOptimizationExempt(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(EngineIcons.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SpacingTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.lg)
        ) {
            // ---- GEMINI API KEYS ----
            Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                Text("Gemini API Keys", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Stored encrypted on-device only; sent directly to Google Gemini API. Order equals failover priority.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                keys.forEachIndexed { i, k ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(SpacingTokens.Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(EngineIcons.Key, null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "${i + 1}. ••••${k.takeLast(4)}",
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = SpacingTokens.Spacing.xs),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { keyStore.moveUp(k) }, enabled = i > 0) {
                                Icon(EngineIcons.PriorityHigh, "Move up")
                            }
                            IconButton(onClick = { keyStore.remove(k) }) {
                                Icon(EngineIcons.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        label = { Text("New API key") },
                        modifier = Modifier.weight(1f),
                        visualTransformation = PasswordVisualTransformation(),
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true
                    )
                    IconButton(
                        onClick = {
                            if (newKey.isNotBlank()) {
                                keyStore.add(newKey.trim())
                                newKey = ""
                            }
                        },
                        enabled = newKey.isNotBlank()
                    ) {
                        Icon(EngineIcons.Add, "Add key", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ---- COMPLETED TASK RETENTION PERIOD ----
            Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                Text("Completed Tasks Retention", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Completed tasks auto-delete after this duration so your archive doesn't grow indefinitely.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                    listOf(1, 3, 7, 14, 30, 60).forEach { days ->
                        FilterChip(
                            selected = retentionDays == days,
                            onClick = { vm.setRetentionDays(days) },
                            label = { Text(if (days == 1) "1 day" else "$days days") }
                        )
                    }
                }
            }

            // ---- NOTIFICATIONS ----
            Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                Text("Notifications", style = MaterialTheme.typography.titleMedium)

                NotifType.entries.forEach { type ->
                    val label = when (type) {
                        NotifType.DUE -> "Fixed-time task due"
                        NotifType.OVERDUE -> "Overdue task alert"
                        NotifType.DAILY_AGENDA -> "Daily morning agenda summary"
                        NotifType.API_KEYS_FAILED -> "All Gemini keys failed"
                        NotifType.AGENT_CONFIRMATION -> "AI agent action confirmation"
                        NotifType.APPROACHING_DEADLINE -> "Approaching deadline warning"
                        NotifType.RECURRING_INSTANCE -> "Recurring instance generated"
                    }
                    val enabled = runBlocking { settings.notifEnabled(type).first() }
                    var checked by remember(type) { mutableStateOf(enabled) }

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = checked, onCheckedChange = {
                            checked = it
                            scope.launch { settings.setNotifEnabled(type, it) }
                        })
                    }

                    if (type == NotifType.APPROACHING_DEADLINE && checked) {
                        var lead by remember { mutableFloatStateOf(runBlocking { settings.deadlineLeadMinutes.first() }.toFloat()) }
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(
                                "Warn ${lead.toInt()} min before due time",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = lead,
                                onValueChange = { lead = it },
                                onValueChangeFinished = { scope.launch { settings.setDeadlineLeadMinutes(lead.toInt()) } },
                                valueRange = 5f..60f
                            )
                        }
                    }
                }
            }

            // ---- BACKGROUND EXECUTION & RELIABILITY ----
            Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                Text("Reminder Reliability & Background Execution", style = MaterialTheme.typography.titleMedium)
                Text(
                    "TaskFlow uses exact alarms, a persistent foreground notification, and a battery exemption to ensure reminders are never missed by Android Doze.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(SpacingTokens.Spacing.md), verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Persistent Background Service", style = MaterialTheme.typography.bodyMedium)
                            Text("Always active", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = { (context as? Activity)?.let { PermissionWiring.requestExactAlarm(it) } }) {
                                Text("Allow exact alarms")
                            }

                            OutlinedButton(onClick = {
                                PermissionWiring.requestBatteryExemption(context)
                                isBatteryExempt = PermissionWiring.isBatteryOptimizationExempt(context)
                            }) {
                                Text(if (isBatteryExempt) "Battery exempt ✓" else "Request battery exemption")
                            }
                        }
                    }
                }
            }

            // ---- THEME ----
            Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = theme == mode,
                            onClick = { vm.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = ThemeMode.entries.size)
                        ) {
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.xl))
        }
    }
}
