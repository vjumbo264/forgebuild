@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.forgebuild.otgenabler.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.forgebuild.engine.security.ScreenSecurity
import com.forgebuild.engine.ui.components.EngineCircularWavyProgress
import com.forgebuild.engine.ui.components.EngineLoadingIndicator
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.otgenabler.ApplyEvaluation
import com.forgebuild.otgenabler.ConnectedUsbDevice
import com.forgebuild.otgenabler.DiagnosticsExporter
import com.forgebuild.otgenabler.DiscoveredNode
import com.forgebuild.otgenabler.LogEntry
import com.forgebuild.otgenabler.LogType
import com.forgebuild.otgenabler.NodeCategory
import com.forgebuild.otgenabler.OtgController
import com.forgebuild.otgenabler.OtgStrategy
import com.forgebuild.otgenabler.OtgVerificationState
import com.forgebuild.otgenabler.RootCommander
import com.forgebuild.otgenabler.RootStatus
import com.forgebuild.otgenabler.UsbMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun OtgEnablerScreen(
    activity: Activity? = null,
    onSaveDiagnosticsFile: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { OtgController(context.applicationContext) }
    val usbMonitor = remember { UsbMonitor(context.applicationContext) }

    val isOtgEnabled by controller.isOtgEnabled.collectAsState()
    val evaluation by controller.evaluation.collectAsState()
    val selectedStrategy by controller.selectedStrategy.collectAsState()
    val rootStatus by RootCommander.rootStatus.collectAsState()
    val rootFlavor by RootCommander.rootFlavor.collectAsState()
    val logs by RootCommander.logs.collectAsState()
    val discoveredNodes by controller.discoveredNodes.collectAsState()
    val isScanning by controller.isScanning.collectAsState()
    val autoEnableOnBoot by controller.autoEnableOnBoot.collectAsState()
    val tryWithSelinux by controller.tryWithSelinuxPermissive.collectAsState()
    val extraLegacyTweaks by controller.extraLegacyTweaks.collectAsState()
    val connectedDevices by usbMonitor.connectedDevices.collectAsState()

    var isToggling by remember { mutableStateOf(false) }
    var isExportingDiag by remember { mutableStateOf(false) }
    var selinuxStatus by remember { mutableStateOf("Checking...") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showStrategyDialog by remember { mutableStateOf(false) }
    var previewReportText by remember { mutableStateOf<String?>(null) }

    // Apply per-view ScreenSecurity ONLY when viewing the raw kernel/props diagnostics dialog
    DisposableEffect(activity, previewReportText) {
        if (activity != null) {
            ScreenSecurity.setSecure(activity, previewReportText != null)
        }
        onDispose {
            if (activity != null) {
                ScreenSecurity.setSecure(activity, false)
            }
        }
    }

    // Initial background startup scan & hardware refresh (CacheFirstStore renders cached nodes immediately)
    LaunchedEffect(Unit) {
        RootCommander.checkRoot()
        selinuxStatus = RootCommander.getSELinuxStatus()
        usbMonitor.refresh()
        controller.scanSysfsNodes()
    }

    // Listen to UsbManager attach/detach broadcasts (Section 6.5)
    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                scope.launch {
                    delay(300L)
                    usbMonitor.refresh()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    // Periodic USB accessory polling while OTG is enabled/partial (Section 6.5)
    LaunchedEffect(isOtgEnabled) {
        if (isOtgEnabled) {
            while (isActive) {
                usbMonitor.refresh()
                delay(2500L)
            }
        }
    }

    fun copyToClipboard(label: String, text: String, toastMessage: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "OTG Enabler",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                RootCommander.checkRoot(forceRefresh = true)
                                selinuxStatus = RootCommander.getSELinuxStatus()
                                controller.scanSysfsNodes()
                                usbMonitor.refresh()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = EngineIcons.Refresh,
                            contentDescription = "Refresh hardware status"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        val writableCount = discoveredNodes.count { it.isControllable }
        val infoOnlyCount = discoveredNodes.size - writableCount

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = SpacingTokens.Spacing.md),
            contentPadding = PaddingValues(bottom = SpacingTokens.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md)
        ) {
            // 1. Hero OTG Host Control Card (Honest Verified / Partial / Failed / Disabled states)
            item {
                OtgHeroCard(
                    evaluation = evaluation,
                    isEnabled = isOtgEnabled,
                    isBusy = isToggling,
                    strategy = selectedStrategy,
                    onToggle = {
                        scope.launch {
                            isToggling = true
                            if (isOtgEnabled) {
                                controller.disableOtg()
                            } else {
                                controller.enableOtg(selectedStrategy)
                            }
                            selinuxStatus = RootCommander.getSELinuxStatus()
                            usbMonitor.refresh()
                            isToggling = false
                        }
                    },
                    onChangeStrategy = { showStrategyDialog = true }
                )
            }

            // 2. Hardware Status Bar
            item {
                HardwareStatusBar(
                    rootStatus = rootStatus,
                    rootFlavor = rootFlavor,
                    selinuxStatus = selinuxStatus,
                    connectedCount = connectedDevices.size
                )
            }

            // 3. Diagnostics Export Bar ("Copy diagnostics" + SAF Save + Secure Preview)
            item {
                DiagnosticsExportBar(
                    isExporting = isExportingDiag,
                    onCopyDiagnostics = {
                        scope.launch {
                            isExportingDiag = true
                            val report = DiagnosticsExporter.buildReport(controller, usbMonitor)
                            copyToClipboard(
                                "OTG Enabler Diagnostics",
                                report,
                                "Diagnostics copied (${report.length} chars)"
                            )
                            isExportingDiag = false
                        }
                    },
                    onSaveDiagnostics = if (onSaveDiagnosticsFile != null) {
                        {
                            scope.launch {
                                isExportingDiag = true
                                val report = DiagnosticsExporter.buildReport(controller, usbMonitor)
                                isExportingDiag = false
                                onSaveDiagnosticsFile(report)
                            }
                        }
                    } else null,
                    onPreviewDiagnostics = {
                        scope.launch {
                            isExportingDiag = true
                            previewReportText = DiagnosticsExporter.buildReport(controller, usbMonitor)
                            isExportingDiag = false
                        }
                    }
                )
            }

            // 4. Connected USB Peripherals List (ignores 1d6b root hubs)
            item {
                ConnectedDevicesCard(
                    devices = connectedDevices,
                    onRefresh = { scope.launch { usbMonitor.refresh() } }
                )
            }

            // 5. Tab Navigation (Sysfs Nodes, Root Logs, Automation)
            item {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.clip(MaterialTheme.shapes.large)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "Sysfs ($writableCount/${discoveredNodes.size})",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "Root Logs (${logs.size})",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                text = "Automation",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            // 6. Tab Contents
            when (selectedTab) {
                0 -> {
                    item {
                        DiscoveredNodesSection(
                            nodes = discoveredNodes,
                            writableCount = writableCount,
                            infoOnlyCount = infoOnlyCount,
                            isScanning = isScanning,
                            onScan = { scope.launch { controller.scanSysfsNodes() } },
                            onWriteNode = { path, value ->
                                scope.launch {
                                    val res = controller.writeCustomNode(path, value)
                                    controller.scanSysfsNodes()
                                    usbMonitor.refresh()
                                    val msg = if (res.ok) {
                                        "Verified: ${path.substringAfterLast('/')} = ${res.after}"
                                    } else {
                                        "Write failed: ${res.stderr}"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                }
                1 -> {
                    item {
                        LogsSection(
                            logs = logs,
                            onClear = { RootCommander.clearLogs() },
                            onCopyLogs = {
                                val logText = logs.joinToString("\n") { "[${it.timestamp}] ${it.type}: ${it.message}" }
                                copyToClipboard("OTG Enabler Root Logs", logText, "Root logs copied")
                            },
                            onCopyDiagnostics = {
                                scope.launch {
                                    isExportingDiag = true
                                    val report = DiagnosticsExporter.buildReport(controller, usbMonitor)
                                    copyToClipboard(
                                        "OTG Enabler Diagnostics",
                                        report,
                                        "Diagnostics copied (${report.length} chars)"
                                    )
                                    isExportingDiag = false
                                }
                            }
                        )
                    }
                }
                2 -> {
                    item {
                        AutomationSection(
                            autoEnableOnBoot = autoEnableOnBoot,
                            onAutoEnableChange = { controller.setAutoEnableOnBoot(it) },
                            tryWithSelinuxPermissive = tryWithSelinux,
                            onTryWithSelinuxChange = { controller.setTryWithSelinuxPermissive(it) },
                            extraLegacyTweaks = extraLegacyTweaks,
                            onExtraLegacyTweaksChange = { controller.setExtraLegacyTweaks(it) }
                        )
                    }
                }
            }
        }
    }

    if (showStrategyDialog) {
        StrategySelectionDialog(
            current = selectedStrategy,
            onSelect = {
                controller.setStrategy(it)
                showStrategyDialog = false
            },
            onDismiss = { showStrategyDialog = false }
        )
    }

    if (previewReportText != null) {
        AlertDialog(
            onDismissRequest = { previewReportText = null },
            title = { Text("Diagnostics Report") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = previewReportText.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val txt = previewReportText.orEmpty()
                        copyToClipboard("OTG Enabler Diagnostics", txt, "Diagnostics copied")
                        previewReportText = null
                    }
                ) {
                    Text("Copy diagnostics")
                }
            },
            dismissButton = {
                TextButton(onClick = { previewReportText = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun OtgHeroCard(
    evaluation: ApplyEvaluation,
    isEnabled: Boolean,
    isBusy: Boolean,
    strategy: OtgStrategy,
    onToggle: () -> Unit,
    onChangeStrategy: () -> Unit
) {
    val state = evaluation.state
    val containerColor = when (state) {
        OtgVerificationState.VERIFIED -> MaterialTheme.colorScheme.primaryContainer
        OtgVerificationState.PARTIAL -> MaterialTheme.colorScheme.secondaryContainer
        OtgVerificationState.FAILED -> MaterialTheme.colorScheme.errorContainer
        OtgVerificationState.DISABLED -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when (state) {
        OtgVerificationState.VERIFIED -> MaterialTheme.colorScheme.onPrimaryContainer
        OtgVerificationState.PARTIAL -> MaterialTheme.colorScheme.onSecondaryContainer
        OtgVerificationState.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        OtgVerificationState.DISABLED -> MaterialTheme.colorScheme.onSurface
    }
    val badgeBg = when (state) {
        OtgVerificationState.VERIFIED -> MaterialTheme.colorScheme.primary
        OtgVerificationState.PARTIAL -> MaterialTheme.colorScheme.tertiary
        OtgVerificationState.FAILED -> MaterialTheme.colorScheme.error
        OtgVerificationState.DISABLED -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val badgeFg = when (state) {
        OtgVerificationState.VERIFIED -> MaterialTheme.colorScheme.onPrimary
        OtgVerificationState.PARTIAL -> MaterialTheme.colorScheme.onTertiary
        OtgVerificationState.FAILED -> MaterialTheme.colorScheme.onError
        OtgVerificationState.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = badgeBg
                ) {
                    Text(
                        text = evaluation.statusBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(
                            horizontal = SpacingTokens.Spacing.xs,
                            vertical = SpacingTokens.Spacing.xxs
                        )
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    modifier = Modifier.clickable { onChangeStrategy() }
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = SpacingTokens.Spacing.xs,
                            vertical = SpacingTokens.Spacing.xxs
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strategy.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(SpacingTokens.Spacing.xxs))
                        Icon(
                            imageVector = EngineIcons.Settings,
                            contentDescription = "Change Strategy",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.lg))

            FilledIconButton(
                onClick = onToggle,
                enabled = !isBusy,
                modifier = Modifier.size(96.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = when (state) {
                        OtgVerificationState.VERIFIED -> MaterialTheme.colorScheme.primary
                        OtgVerificationState.PARTIAL -> MaterialTheme.colorScheme.tertiary
                        OtgVerificationState.FAILED -> MaterialTheme.colorScheme.error
                        OtgVerificationState.DISABLED -> MaterialTheme.colorScheme.outlineVariant
                    },
                    contentColor = when (state) {
                        OtgVerificationState.VERIFIED -> MaterialTheme.colorScheme.onPrimary
                        OtgVerificationState.PARTIAL -> MaterialTheme.colorScheme.onTertiary
                        OtgVerificationState.FAILED -> MaterialTheme.colorScheme.onError
                        OtgVerificationState.DISABLED -> MaterialTheme.colorScheme.onSurface
                    }
                )
            ) {
                if (isBusy) {
                    EngineCircularWavyProgress(modifier = Modifier.size(48.dp))
                } else {
                    val stateIcon = when (state) {
                        OtgVerificationState.VERIFIED -> EngineIcons.Check
                        OtgVerificationState.PARTIAL, OtgVerificationState.FAILED -> EngineIcons.Warning
                        OtgVerificationState.DISABLED -> EngineIcons.Bolt
                    }
                    Icon(
                        imageVector = stateIcon,
                        contentDescription = "Toggle OTG",
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.md))

            Text(
                text = evaluation.headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(SpacingTokens.Spacing.xxs))

            Text(
                text = evaluation.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                textAlign = TextAlign.Center
            )

            if (evaluation.detailMessage.isNotBlank()) {
                Spacer(Modifier.height(SpacingTokens.Spacing.xs))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = evaluation.detailMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(SpacingTokens.Spacing.sm)
                    )
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.md))

            Button(
                onClick = onToggle,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEnabled) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (isEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }
                )
            ) {
                Text(
                    text = when {
                        isBusy -> "Verifying Hardware..."
                        isEnabled -> "Disable OTG & Restore Values"
                        state == OtgVerificationState.FAILED -> "Retry Enable USB OTG"
                        else -> "Enable USB OTG"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun HardwareStatusBar(
    rootStatus: RootStatus,
    rootFlavor: String,
    selinuxStatus: String,
    connectedCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Root Privilege",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(SpacingTokens.Spacing.xxs))
                val rootGranted = rootStatus == RootStatus.GRANTED
                Text(
                    text = if (rootGranted) rootFlavor else "Denied",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (rootGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "SELinux",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(SpacingTokens.Spacing.xxs))
                val isPermissive = selinuxStatus.equals("Permissive", ignoreCase = true)
                Text(
                    text = selinuxStatus,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isPermissive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                )
            }

            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    text = "USB Devices",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(SpacingTokens.Spacing.xxs))
                Text(
                    text = if (connectedCount == 0) "0 Attached" else "$connectedCount Attached",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (connectedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DiagnosticsExportBar(
    isExporting: Boolean,
    onCopyDiagnostics: () -> Unit,
    onSaveDiagnostics: (() -> Unit)?,
    onPreviewDiagnostics: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpacingTokens.Spacing.md, vertical = SpacingTokens.Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onCopyDiagnostics,
                enabled = !isExporting,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                if (isExporting) {
                    EngineLoadingIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Icon(
                        imageVector = EngineIcons.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(SpacingTokens.Spacing.xs))
                    Text("Copy diagnostics", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (onSaveDiagnostics != null) {
                OutlinedButton(
                    onClick = onSaveDiagnostics,
                    enabled = !isExporting,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        imageVector = EngineIcons.Save,
                        contentDescription = "Save diagnostics",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(SpacingTokens.Spacing.xxs))
                    Text("Save", style = MaterialTheme.typography.labelMedium)
                }
            }

            TextButton(
                onClick = onPreviewDiagnostics,
                enabled = !isExporting
            ) {
                Text("View", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun ConnectedDevicesCard(
    devices: List<ConnectedUsbDevice>,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connected USB Peripherals",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = EngineIcons.Refresh,
                        contentDescription = "Refresh USB devices",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.xs))

            if (devices.isEmpty()) {
                Text(
                    text = "No devices attached",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                devices.forEach { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = SpacingTokens.Spacing.xxs),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(SpacingTokens.Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (device.isMassStorage) EngineIcons.DesktopWindows else EngineIcons.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(SpacingTokens.Spacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.displayTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "VID ${device.vendorHex}  PID ${device.productHex}  •  ${device.deviceClass}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveredNodesSection(
    nodes: List<DiscoveredNode>,
    writableCount: Int,
    infoOnlyCount: Int,
    isScanning: Boolean,
    onScan: () -> Unit,
    onWriteNode: (path: String, value: String) -> Unit
) {
    var filterMode by remember { mutableIntStateOf(0) } // 0 = All, 1 = Writable, 2 = Info only
    val filtered = remember(nodes, filterMode) {
        when (filterMode) {
            1 -> nodes.filter { it.isControllable }
            2 -> nodes.filter { !it.isControllable }
            else -> nodes
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(SpacingTokens.Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hardware Sysfs Nodes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$writableCount writable control • $infoOnlyCount info only (dummy_udc excluded)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onScan,
                    enabled = !isScanning,
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (isScanning) {
                        EngineLoadingIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Scan Nodes")
                    }
                }
            }

            if (nodes.isNotEmpty()) {
                Spacer(Modifier.height(SpacingTokens.Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                    FilterChip(
                        selected = filterMode == 0,
                        onClick = { filterMode = 0 },
                        label = { Text("All (${nodes.size})", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = filterMode == 1,
                        onClick = { filterMode = 1 },
                        label = { Text("Writable ($writableCount)", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = filterMode == 2,
                        onClick = { filterMode = 2 },
                        label = { Text("Info only ($infoOnlyCount)", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.sm))

            if (filtered.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "No matching sysfs nodes in this filter. Tap 'Scan Nodes' to refresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(SpacingTokens.Spacing.md)
                    )
                }
            } else {
                filtered.forEach { node ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = SpacingTokens.Spacing.xxs),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(SpacingTokens.Spacing.sm)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = node.candidateType,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (node.isControllable) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = "${node.permissions} | val: ${node.currentValue}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.height(SpacingTokens.Spacing.xxs))

                            Text(
                                text = node.path,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(SpacingTokens.Spacing.xs))

                            if (node.isControllable) {
                                val actions = suggestedEchoValuesForNode(node)
                                Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                                    actions.forEach { echoVal ->
                                        OutlinedButton(
                                            onClick = { onWriteNode(node.path, echoVal) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text("echo $echoVal", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "info only (read-only attribute — echo disabled)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun suggestedEchoValuesForNode(node: DiscoveredNode): List<String> {
    val fn = node.path.substringAfterLast('/').lowercase()
    return when {
        fn == "port_type" -> listOf("dual", "source", "sink")
        fn == "power_role" || fn == "preferred_role" -> listOf("source", "sink")
        node.category == NodeCategory.ROLE_SWITCH || node.category == NodeCategory.TYPEC_DATA_ROLE -> {
            if (node.path.contains("/dual_role_usb/") && fn == "mode") {
                listOf("dfp", "ufp")
            } else {
                listOf("host", "device")
            }
        }
        node.category == NodeCategory.VBUS_OTG_REGULATOR -> listOf("enabled", "disabled", "1", "0").take(3)
        node.category == NodeCategory.EXTCON -> listOf("USB-HOST=1", "USB-HOST=0")
        else -> listOf("1", "0", "host")
    }
}

@Composable
fun LogsSection(
    logs: List<LogEntry>,
    onClear: () -> Unit,
    onCopyLogs: () -> Unit,
    onCopyDiagnostics: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(SpacingTokens.Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Root Logs & Kernel Diff",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    TextButton(onClick = onCopyDiagnostics) {
                        Text("Copy diagnostics", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onCopyLogs) {
                        Text("Copy logs", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onClear) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.xs))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 360.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(SpacingTokens.Spacing.xs)
                ) {
                    if (logs.isEmpty()) {
                        item {
                            Text(
                                text = "Structured sysfs readbacks, real kernel errno messages, and dmesg diffs appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(logs, key = { it.id }) { log ->
                            val color = when (log.type) {
                                LogType.COMMAND -> MaterialTheme.colorScheme.primary
                                LogType.SUCCESS -> MaterialTheme.colorScheme.tertiary
                                LogType.ERROR -> MaterialTheme.colorScheme.error
                                LogType.WARNING -> MaterialTheme.colorScheme.secondary
                                LogType.INFO -> MaterialTheme.colorScheme.onSurface
                            }
                            Text(
                                text = "[${log.timestamp}] ${log.message}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = color
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AutomationSection(
    autoEnableOnBoot: Boolean,
    onAutoEnableChange: (Boolean) -> Unit,
    tryWithSelinuxPermissive: Boolean,
    onTryWithSelinuxChange: (Boolean) -> Unit,
    extraLegacyTweaks: Boolean,
    onExtraLegacyTweaksChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md)
        ) {
            Text(
                text = "Automation & Advanced Options",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reapply OTG on Boot",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Automatically runs verified OTG apply when your device finishes booting",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoEnableOnBoot,
                    onCheckedChange = onAutoEnableChange
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Try with SELinux permissive",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Off by default. Temporarily sets SELinux Permissive during Enable and restores Enforcing on Disable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = tryWithSelinuxPermissive,
                    onCheckedChange = onTryWithSelinuxChange
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Extra legacy tweaks",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Off by default. Also writes optional persist.vendor.otg.support and global otg_enabled settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = extraLegacyTweaks,
                    onCheckedChange = onExtraLegacyTweaksChange
                )
            }

            HorizontalDivider()

            Column {
                Text(
                    text = "Quick Settings Tile",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(SpacingTokens.Spacing.xxs))
                Text(
                    text = "Add the 'USB OTG' tile in Quick Settings to toggle Verified / Partial host mode and restore recorded sysfs values with one tap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StrategySelectionDialog(
    current: OtgStrategy,
    onSelect: (OtgStrategy) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select OTG Enabling Strategy") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                items(OtgStrategy.entries) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = if (item == current) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(SpacingTokens.Spacing.sm)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (item == current) {
                                    Icon(
                                        imageVector = EngineIcons.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Target: ${item.chipVendor}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
