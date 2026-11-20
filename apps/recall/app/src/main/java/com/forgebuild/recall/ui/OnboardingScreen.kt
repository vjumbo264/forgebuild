package com.forgebuild.recall.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forgebuild.engine.permissions.PermissionWiring
import com.forgebuild.engine.ui.components.ExpressiveButton
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.ElevationTokens
import com.forgebuild.engine.ui.theme.SpacingTokens

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifGranted = Build.VERSION.SDK_INT < 33 ||
                    PermissionWiring.isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
                alarmGranted = PermissionWiring.canScheduleExactAlarms(context)
                batteryExempt = PermissionWiring.isBatteryOptimizationExempt(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifGranted = granted
    }

    Scaffold { padding ->
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
                    .padding(horizontal = margin, vertical = SpacingTokens.Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md),
            ) {
                Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = EngineIcons.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(SpacingTokens.Spacing.md)
                            .size(SpacingTokens.Spacing.xl),
                    )
                }

                Text(
                    text = "Welcome to RECALL",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "RECALL keeps you gently anchored to the real clock with synchronized audio chimes. To chime reliably when the screen is off or locked, grant these standard permissions:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OnboardingPermissionCard(
                    icon = EngineIcons.Notifications,
                    title = "Notifications",
                    explanation = "Displays the persistent status indicator with Pause/Resume controls and optional visual chime alerts.",
                    granted = notifGranted,
                    onAllow = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notifGranted = true
                        }
                    },
                )

                OnboardingPermissionCard(
                    icon = EngineIcons.Alarm,
                    title = "Exact alarms",
                    explanation = "Lets RECALL trigger chimes at exact clock boundaries (:00, :05, :30) even while the device is idle.",
                    granted = alarmGranted,
                    onAllow = {
                        (context as? Activity)?.let { PermissionWiring.requestExactAlarm(it) }
                    },
                )

                OnboardingPermissionCard(
                    icon = EngineIcons.Bolt,
                    title = "Battery optimization exemption",
                    explanation = "Prevents Android OEM power managers from deferring 5-minute clock chimes during Doze.",
                    granted = batteryExempt,
                    onAllow = {
                        PermissionWiring.requestBatteryExemption(context)
                    },
                )

                Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                ExpressiveButton(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Continue to RECALL")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnboardingPermissionCard(
    icon: ImageVector,
    title: String,
    explanation: String,
    granted: Boolean,
    onAllow: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (granted) {
            ElevationTokens.tonalContainerColor(3)
        } else {
            ElevationTokens.tonalContainerColor(1)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(SpacingTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (granted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xxs),
                ) {
                    Icon(
                        imageVector = EngineIcons.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(SpacingTokens.Spacing.md),
                    )
                    Text(
                        text = "Granted",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onAllow,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text("Allow")
                }
            }
        }
    }
}
