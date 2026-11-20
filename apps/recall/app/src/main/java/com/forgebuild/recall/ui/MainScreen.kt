@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.forgebuild.recall.ui

import android.Manifest
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forgebuild.engine.permissions.PermissionWiring
import com.forgebuild.engine.ui.components.EngineLinearWavyProgress
import com.forgebuild.engine.ui.components.ExpressiveButton
import com.forgebuild.engine.ui.components.ExpressiveTonalButton
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.ElevationTokens
import com.forgebuild.engine.ui.theme.MotionTokens
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.recall.data.ClockSyncEngine
import com.forgebuild.recall.data.RecallAppState
import com.forgebuild.recall.data.RecallInterval
import com.forgebuild.recall.data.RecallSound
import com.forgebuild.recall.data.RecallStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    state: RecallAppState,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onPreviewSound: (RecallSound, Float) -> Unit,
    onGlobalTest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Visible-only UI clock ticker: zero background CPU cost when minimized or screen-off.
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(500L)
        }
    }

    var missingReliabilitySetup by remember {
        mutableStateOf(checkReliabilityWarning(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                nowMs = System.currentTimeMillis()
                missingReliabilitySetup = checkReliabilityWarning(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val nextChime = remember(state.intervals, state.status, state.lastFiredBoundaryMs, nowMs / 1000L) {
        if (state.status == RecallStatus.ACTIVE) {
            val effectiveAfter = maxOf(nowMs, state.lastFiredBoundaryMs)
            ClockSyncEngine.nextGlobalEvent(state.intervals, effectiveAfter)
        } else {
            null
        }
    }

    val enabledIntervals = remember(state.intervals) { state.intervals.filter { it.enabled } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("RECALL", style = MaterialTheme.typography.titleLarge)
                },
                actions = {
                    if (missingReliabilitySetup) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = EngineIcons.Warning,
                                contentDescription = "Background permissions need attention",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = EngineIcons.Settings,
                            contentDescription = "Sound and interval settings",
                        )
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
                // Hero Clock + Next Chime Card
                val statusContainerColor by animateColorAsState(
                    targetValue = when (state.status) {
                        RecallStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                        RecallStatus.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer
                        RecallStatus.STOPPED -> ElevationTokens.tonalContainerColor(4)
                    },
                    animationSpec = MotionTokens.scheme.defaultEffectsSpec(),
                    label = "statusBg",
                )
                val statusContentColor by animateColorAsState(
                    targetValue = when (state.status) {
                        RecallStatus.ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
                        RecallStatus.PAUSED -> MaterialTheme.colorScheme.onTertiaryContainer
                        RecallStatus.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = MotionTokens.scheme.defaultEffectsSpec(),
                    label = "statusFg",
                )

                Surface(
                    shape = MaterialTheme.shapes.extraLargeIncreased,
                    color = ElevationTokens.tonalContainerColor(2),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(SpacingTokens.Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = statusContainerColor,
                            ) {
                                Text(
                                    text = when (state.status) {
                                        RecallStatus.ACTIVE -> "Active · Synced to clock"
                                        RecallStatus.PAUSED -> "Paused"
                                        RecallStatus.STOPPED -> "Inactive"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = statusContentColor,
                                    modifier = Modifier.padding(
                                        horizontal = SpacingTokens.Spacing.sm,
                                        vertical = SpacingTokens.Spacing.xxs,
                                    ),
                                )
                            }
                            Text(
                                text = formatDateShort(nowMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Text(
                            text = formatClockWithSeconds(context, nowMs),
                            style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Spacer(Modifier.height(SpacingTokens.Spacing.xxs))

                        when {
                            state.status == RecallStatus.ACTIVE && nextChime != null -> {
                                val remainingMs = (nextChime.boundaryEpochMs - nowMs).coerceAtLeast(0L)
                                val prevBoundary = ClockSyncEngine.previousBoundaryMillis(
                                    nextChime.winningInterval.minutes,
                                    nowMs,
                                )
                                val spanMs = (nextChime.boundaryEpochMs - prevBoundary).coerceAtLeast(1_000L)
                                val progress = ((nowMs - prevBoundary).toFloat() / spanMs.toFloat()).coerceIn(0f, 1f)

                                Text(
                                    text = "Next chime in ${formatCountdown(remainingMs)}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "${formatClockShort(context, nextChime.boundaryEpochMs)} · ${nextChime.winningInterval.label} (${nextChime.winningInterval.sound.displayName})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (nextChime.coincidingIntervals.size > 1) {
                                    val others = nextChime.coincidingIntervals.drop(1)
                                        .joinToString(", ") { "${it.minutes}m" }
                                    Text(
                                        text = "Priority overrides $others at this boundary",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                                Spacer(Modifier.height(SpacingTokens.Spacing.xxs))
                                EngineLinearWavyProgress(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            state.status == RecallStatus.ACTIVE && nextChime == null -> {
                                Text(
                                    text = "No enabled intervals",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = "Enable at least one interval in Sound & Interval Settings.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            state.status == RecallStatus.PAUSED -> {
                                Text(
                                    text = "Time awareness paused",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Tap Resume to align with the next real-world clock boundary.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            else -> {
                                Text(
                                    text = "Ready to start",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Press Start to synchronize audio cues with real clock boundaries.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm),
                ) {
                    when (state.status) {
                        RecallStatus.STOPPED -> {
                            ExpressiveButton(
                                onClick = onStartOrResume,
                                enabled = enabledIntervals.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(EngineIcons.PlayArrow, null, modifier = Modifier.size(SpacingTokens.Spacing.md))
                                Spacer(Modifier.width(SpacingTokens.Spacing.xs))
                                Text("Start")
                            }
                            ExpressiveTonalButton(
                                onClick = onGlobalTest,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(EngineIcons.VolumeUp, null, modifier = Modifier.size(SpacingTokens.Spacing.md))
                                Spacer(Modifier.width(SpacingTokens.Spacing.xs))
                                Text("Test Sound")
                            }
                        }

                        RecallStatus.ACTIVE -> {
                            ExpressiveTonalButton(
                                onClick = onPause,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(EngineIcons.Pause, null, modifier = Modifier.size(SpacingTokens.Spacing.md))
                                Spacer(Modifier.width(SpacingTokens.Spacing.xs))
                                Text("Pause")
                            }
                            OutlinedButton(
                                onClick = onStop,
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(EngineIcons.Stop, null, modifier = Modifier.size(SpacingTokens.Spacing.md))
                                Spacer(Modifier.width(SpacingTokens.Spacing.xs))
                                Text("Stop")
                            }
                        }

                        RecallStatus.PAUSED -> {
                            ExpressiveButton(
                                onClick = onStartOrResume,
                                enabled = enabledIntervals.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(EngineIcons.PlayArrow, null, modifier = Modifier.size(SpacingTokens.Spacing.md))
                                Spacer(Modifier.width(SpacingTokens.Spacing.xs))
                                Text("Resume")
                            }
                            OutlinedButton(
                                onClick = onStop,
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(EngineIcons.Stop, null, modifier = Modifier.size(SpacingTokens.Spacing.md))
                                Spacer(Modifier.width(SpacingTokens.Spacing.xs))
                                Text("Stop")
                            }
                        }
                    }
                }

                // Enabled Intervals Overview
                Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Enabled intervals",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TextButton(onClick = onOpenSettings) {
                            Text("Sound & Intervals")
                        }
                    }

                    if (enabledIntervals.isEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = ElevationTokens.tonalContainerColor(1),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(SpacingTokens.Spacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "No intervals enabled.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = onOpenSettings) {
                                    Text("Configure")
                                }
                            }
                        }
                    } else {
                        enabledIntervals.forEach { interval ->
                            EnabledIntervalRow(
                                interval = interval,
                                nextBoundary = ClockSyncEngine.nextBoundaryMillis(interval.minutes, nowMs),
                                context = context,
                                onPreview = { onPreviewSound(interval.sound, interval.volume) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnabledIntervalRow(
    interval: RecallInterval,
    nextBoundary: Long,
    context: Context,
    onPreview: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = ElevationTokens.tonalContainerColor(1),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpacingTokens.Spacing.md, vertical = SpacingTokens.Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xxs),
            ) {
                Text(
                    text = interval.label,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${interval.sound.displayName} · ${(interval.volume * 100).roundToInt()}% · Next ${formatClockShort(context, nextBoundary)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = ElevationTokens.tonalContainerColor(3),
                ) {
                    Text(
                        text = "P${interval.priority}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = SpacingTokens.Spacing.xs,
                            vertical = SpacingTokens.Spacing.xxs,
                        ),
                    )
                }
                IconButton(onClick = onPreview) {
                    Icon(
                        imageVector = EngineIcons.VolumeUp,
                        contentDescription = "Test ${interval.label}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun checkReliabilityWarning(context: Context): Boolean {
    val notifOk = Build.VERSION.SDK_INT < 33 ||
        PermissionWiring.isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
    val alarmOk = PermissionWiring.canScheduleExactAlarms(context)
    return !notifOk || !alarmOk
}

private fun formatClockWithSeconds(context: Context, epochMs: Long): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMs))
}

private fun formatClockShort(context: Context, epochMs: Long): String =
    DateFormat.getTimeFormat(context).format(Date(epochMs))

private fun formatDateShort(epochMs: Long): String =
    SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(epochMs))

private fun formatCountdown(ms: Long): String {
    val totalSec = (ms + 999L) / 1000L
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return when {
        h > 0 -> "${h}h ${m}m ${s}s"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
