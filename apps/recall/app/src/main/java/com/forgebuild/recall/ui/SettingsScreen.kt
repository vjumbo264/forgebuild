@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.forgebuild.recall.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forgebuild.engine.permissions.PermissionWiring
import com.forgebuild.engine.ui.components.ExpressiveButton
import com.forgebuild.engine.ui.components.ExpressiveTonalButton
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.ElevationTokens
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.recall.audio.RecallAudioEngine
import com.forgebuild.recall.data.RecallAppState
import com.forgebuild.recall.data.RecallDefaults
import com.forgebuild.recall.data.RecallInterval
import com.forgebuild.recall.data.RecallSound
import com.forgebuild.recall.util.RecallDiagnostics
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: RecallAppState,
    onBack: () -> Unit,
    onToggleInterval: (String, Boolean) -> Unit,
    onAddInterval: (label: String, minutes: Int, sound: RecallSound, volume: Float, priority: Int) -> Unit,
    onUpdateInterval: (RecallInterval) -> Unit,
    onDeleteInterval: (String) -> Unit,
    onRestoreDefaults: () -> Unit,
    onSetHeadsUp: (Boolean) -> Unit,
    onSetHighPrecision: (Boolean) -> Unit,
    onPreviewSound: (RecallSound, Float) -> Unit,
    onGlobalTest: () -> Unit,
    onStopAudio: () -> Unit,
) {
    val context = LocalContext.current

    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                PermissionWiring.isGranted(context, Manifest.permission.POST_NOTIFICATIONS),
        )
    }
    var alarmGranted by remember {
        mutableStateOf(PermissionWiring.canScheduleExactAlarms(context))
    }
    var batteryExempt by remember {
        mutableStateOf(PermissionWiring.isBatteryOptimizationExempt(context))
    }
    var audioRouteText by remember {
        mutableStateOf(RecallAudioEngine.describeOutputRoutes(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifGranted = Build.VERSION.SDK_INT < 33 ||
                    PermissionWiring.isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
                alarmGranted = PermissionWiring.canScheduleExactAlarms(context)
                batteryExempt = PermissionWiring.isBatteryOptimizationExempt(context)
                audioRouteText = RecallAudioEngine.describeOutputRoutes(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notifGranted = granted }

    var editingInterval by remember { mutableStateOf<RecallInterval?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var soundPickerTarget by remember { mutableStateOf<RecallInterval?>(null) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Intervals & Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(EngineIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            val margin = SpacingTokens.contentMargin(maxWidth)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = margin, vertical = SpacingTokens.Spacing.md),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.lg),
            ) {
                // Section 1: Intervals & Sounds
                SettingsSection(
                    icon = EngineIcons.Schedule,
                    title = "Clock-Synchronized Intervals",
                    subtitle = "Aligned to real-world clock boundaries. Higher priority wins when intervals coincide.",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)) {
                        state.intervals.forEach { interval ->
                            IntervalConfigCard(
                                interval = interval,
                                onToggle = { enabled -> onToggleInterval(interval.id, enabled) },
                                onPickSound = { soundPickerTarget = interval },
                                onVolumeChange = { vol -> onUpdateInterval(interval.copy(volume = vol)) },
                                onPreview = { onPreviewSound(interval.sound, interval.volume) },
                                onEdit = { editingInterval = interval },
                                onDelete = { onDeleteInterval(interval.id) },
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ExpressiveTonalButton(onClick = { showAddDialog = true }) {
                                Icon(EngineIcons.Add, null, modifier = Modifier.size(SpacingTokens.Spacing.md))
                                Spacer(Modifier.width(SpacingTokens.Spacing.xs))
                                Text("Add interval")
                            }
                            TextButton(onClick = onRestoreDefaults) {
                                Text("Restore defaults")
                            }
                        }
                    }
                }

                // Section 2: Audio Pipeline & Routing Test
                SettingsSection(
                    icon = EngineIcons.Headphones,
                    title = "Audio Pipeline & Routing",
                    subtitle = "Plays via Android USAGE_ALARM with independent app gain. System Alarm volume is untouched.",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = ElevationTokens.tonalContainerColor(1),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(SpacingTokens.Spacing.sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Detected outputs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(audioRouteText, style = MaterialTheme.typography.bodyMedium)
                                }
                                IconButton(onClick = { audioRouteText = RecallAudioEngine.describeOutputRoutes(context) }) {
                                    Icon(EngineIcons.Refresh, contentDescription = "Refresh audio outputs")
                                }
                            }
                        }
                        Text(
                            text = "Plug or unplug headphones and tap Global Test to verify how your device routes alarm chimes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)) {
                            ExpressiveButton(
                                onClick = onGlobalTest,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(EngineIcons.VolumeUp, null, modifier = Modifier.size(SpacingTokens.Spacing.md))
                                Spacer(Modifier.width(SpacingTokens.Spacing.xs))
                                Text("Global Test")
                            }
                            OutlinedButton(
                                onClick = onStopAudio,
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text("Stop")
                            }
                        }
                    }
                }

                // Section 3: Alerts & Scheduling
                SettingsSection(
                    icon = EngineIcons.Notifications,
                    title = "Notifications & Scheduling",
                    subtitle = "Control visual chime alerts and Android exact-alarm precision.",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Heads-up chime alerts", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Show a silent visual notice when a scheduled chime plays",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.headsUpNotificationsEnabled,
                                onCheckedChange = onSetHeadsUp,
                            )
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("High-precision alarm clock mode", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Uses AlarmManager.setAlarmClock for strict wake-up timing on aggressive OEMs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.highPrecisionAlarmClockMode,
                                onCheckedChange = onSetHighPrecision,
                            )
                        }
                    }
                }

                // Section 4: Background Reliability & Diagnostics
                SettingsSection(
                    icon = EngineIcons.Bolt,
                    title = "Background Reliability",
                    subtitle = "Permissions required for lock-screen, minimized, and post-reboot operation.",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)) {
                        ReliabilityRow(
                            label = "Notifications",
                            granted = notifGranted,
                            onAction = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    notifGranted = true
                                }
                            },
                        )
                        ReliabilityRow(
                            label = "Exact alarms",
                            granted = alarmGranted,
                            onAction = {
                                (context as? Activity)?.let { PermissionWiring.requestExactAlarm(it) }
                            },
                        )
                        ReliabilityRow(
                            label = "Battery optimization exempt",
                            granted = batteryExempt,
                            onAction = { PermissionWiring.requestBatteryExemption(context) },
                        )

                        OutlinedButton(
                            onClick = { showDiagnosticsDialog = true },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(EngineIcons.Info, null, modifier = Modifier.size(SpacingTokens.Spacing.md))
                            Spacer(Modifier.width(SpacingTokens.Spacing.xs))
                            Text("Event & Diagnostics Log")
                        }
                    }
                }

                Spacer(Modifier.height(SpacingTokens.Spacing.lg))
            }
        }
    }

    if (showAddDialog) {
        IntervalEditorDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onPreview = onPreviewSound,
            onSave = { label, minutes, sound, volume, priority ->
                onAddInterval(label, minutes, sound, volume, priority)
                showAddDialog = false
            },
        )
    }

    editingInterval?.let { current ->
        IntervalEditorDialog(
            initial = current,
            onDismiss = { editingInterval = null },
            onPreview = onPreviewSound,
            onSave = { label, minutes, sound, volume, priority ->
                onUpdateInterval(
                    current.copy(
                        label = label,
                        minutes = minutes,
                        sound = sound,
                        volume = volume,
                        priority = priority,
                    ),
                )
                editingInterval = null
            },
        )
    }

    soundPickerTarget?.let { target ->
        SoundPickerDialog(
            selected = target.sound,
            volume = target.volume,
            onPreview = onPreviewSound,
            onSelect = { chosen ->
                onUpdateInterval(target.copy(sound = chosen))
                soundPickerTarget = null
            },
            onDismiss = { soundPickerTarget = null },
        )
    }

    if (showDiagnosticsDialog) {
        DiagnosticsLogDialog(
            context = context,
            onDismiss = { showDiagnosticsDialog = false },
        )
    }
}

@Composable
private fun SettingsSection(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = ElevationTokens.tonalContainerColor(3),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(SpacingTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .padding(SpacingTokens.Spacing.xs)
                            .size(SpacingTokens.Spacing.lg),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun IntervalConfigCard(
    interval: RecallInterval,
    onToggle: (Boolean) -> Unit,
    onPickSound: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var sliderVal by remember(interval.id, interval.volume) { mutableFloatStateOf(interval.volume) }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = ElevationTokens.tonalContainerColor(1),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(SpacingTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(interval.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Every ${interval.minutes} min · Priority ${interval.priority}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = interval.enabled, onCheckedChange = onToggle)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onPickSound,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(EngineIcons.MusicNote, null, modifier = Modifier.size(SpacingTokens.Spacing.md))
                    Spacer(Modifier.width(SpacingTokens.Spacing.xs))
                    Text(interval.sound.displayName)
                }
                IconButton(onClick = onPreview) {
                    Icon(
                        EngineIcons.VolumeUp,
                        contentDescription = "Preview ${interval.label}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(EngineIcons.Edit, contentDescription = "Edit ${interval.label}")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        EngineIcons.Delete,
                        contentDescription = "Delete ${interval.label}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
            ) {
                Text(
                    text = "Gain",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = sliderVal,
                    onValueChange = { sliderVal = it },
                    onValueChangeFinished = { onVolumeChange(sliderVal) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(sliderVal * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                )
            }
        }
    }
}

@Composable
private fun ReliabilityRow(
    label: String,
    granted: Boolean,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
        ) {
            Icon(
                imageVector = if (granted) EngineIcons.CheckCircle else EngineIcons.Warning,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(SpacingTokens.Spacing.md),
            )
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        if (granted) {
            Text(
                text = "Granted",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            OutlinedButton(
                onClick = onAction,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("Grant")
            }
        }
    }
}

@Composable
private fun SoundPickerDialog(
    selected: RecallSound,
    volume: Float,
    onPreview: (RecallSound, Float) -> Unit,
    onSelect: (RecallSound) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Chime Sound") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
            ) {
                RecallSound.entries.forEach { sound ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = current == sound,
                            onClick = { current = sound },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sound.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                sound.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onPreview(sound, volume) }) {
                            Icon(
                                EngineIcons.VolumeUp,
                                contentDescription = "Preview ${sound.displayName}",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(current) }) {
                Text("Use Sound")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntervalEditorDialog(
    initial: RecallInterval?,
    onDismiss: () -> Unit,
    onPreview: (RecallSound, Float) -> Unit,
    onSave: (label: String, minutes: Int, sound: RecallSound, volume: Float, priority: Int) -> Unit,
) {
    var minutesText by remember { mutableStateOf((initial?.minutes ?: 15).toString()) }
    var labelText by remember { mutableStateOf(initial?.label ?: "") }
    var sound by remember { mutableStateOf(initial?.sound ?: RecallSound.SONAR_PING) }
    var volume by remember { mutableFloatStateOf(initial?.volume ?: 0.85f) }
    var priority by remember { mutableIntStateOf(initial?.priority ?: RecallInterval.PRIORITY_CUSTOM) }
    var showSoundPicker by remember { mutableStateOf(false) }

    val parsedMinutes = minutesText.toIntOrNull()?.takeIf { it in 1..1440 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Custom Interval" else "Edit Interval") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm),
            ) {
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { input ->
                        if (input.length <= 4 && input.all { it.isDigit() }) minutesText = input
                    },
                    label = { Text("Interval minutes (1 - 1440)") },
                    supportingText = {
                        Text("Aligns to real clock boundaries (e.g., 15 -> :00, :15, :30, :45)")
                    },
                    isError = parsedMinutes == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    label = { Text("Label (optional)") },
                    placeholder = {
                        val m = parsedMinutes ?: 15
                        Text(RecallDefaults.defaultLabelForMinutes(m))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { showSoundPicker = true },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(EngineIcons.MusicNote, null, modifier = Modifier.size(SpacingTokens.Spacing.md))
                        Spacer(Modifier.width(SpacingTokens.Spacing.xs))
                        Text(sound.displayName)
                    }
                    IconButton(onClick = { onPreview(sound, volume) }) {
                        Icon(EngineIcons.VolumeUp, contentDescription = "Preview sound", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                ) {
                    Text("Gain", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${(volume * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                }
                Text("Priority when coinciding", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xxs),
                ) {
                    listOf(
                        RecallInterval.PRIORITY_LOW to "Low (25)",
                        RecallInterval.PRIORITY_CUSTOM to "Standard (50)",
                        RecallInterval.PRIORITY_MEDIUM_HIGH to "High (75)",
                        RecallInterval.PRIORITY_HIGHEST to "Highest (100)",
                    ).forEach { (value, name) ->
                        FilterChip(
                            selected = priority == value,
                            onClick = { priority = value },
                            label = { Text(name) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedMinutes != null,
                onClick = {
                    val m = parsedMinutes ?: return@TextButton
                    onSave(labelText, m, sound, volume, priority)
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )

    if (showSoundPicker) {
        SoundPickerDialog(
            selected = sound,
            volume = volume,
            onPreview = onPreview,
            onSelect = {
                sound = it
                showSoundPicker = false
            },
            onDismiss = { showSoundPicker = false },
        )
    }
}

@Composable
private fun DiagnosticsLogDialog(
    context: Context,
    onDismiss: () -> Unit,
) {
    var logText by remember { mutableStateOf(RecallDiagnostics.getLogs(context)) }
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Event & Diagnostics Log")
                TextButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("RECALL Diagnostics", logText))
                        copied = true
                        Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text(if (copied) "Copied" else "Copy")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SpacingTokens.Spacing.xxxl * 6)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = logText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    RecallDiagnostics.clearLogs(context)
                    logText = "No diagnostic events recorded yet."
                },
            ) {
                Text("Clear", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}
