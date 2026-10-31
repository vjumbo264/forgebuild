@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.forgebuild.otgenabler.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.components.EngineCircularWavyProgress
import com.forgebuild.engine.ui.components.EngineLoadingIndicator
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.ElevationTokens
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.otgenabler.*
import kotlinx.coroutines.launch

@Composable
fun OtgEnablerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { OtgController(context.applicationContext) }
    val usbMonitor = remember { UsbMonitor(context.applicationContext) }

    val isOtgEnabled by controller.isOtgEnabled.collectAsState()
    val selectedStrategy by controller.selectedStrategy.collectAsState()
    val rootStatus by RootCommander.rootStatus.collectAsState()
    val rootFlavor by RootCommander.rootFlavor.collectAsState()
    val logs by RootCommander.logs.collectAsState()
    val discoveredNodes by controller.discoveredNodes.collectAsState()
    val isScanning by controller.isScanning.collectAsState()
    val autoEnableOnBoot by controller.autoEnableOnBoot.collectAsState()
    val connectedDevices by usbMonitor.connectedDevices.collectAsState()

    var isToggling by remember { mutableStateOf(false) }
    var selinuxStatus by remember { mutableStateOf("Checking...") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showStrategyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        RootCommander.checkRoot()
        selinuxStatus = RootCommander.getSELinuxStatus()
        usbMonitor.refresh()
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
                                usbMonitor.refresh()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = SpacingTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md)
        ) {
            // 1. Hero OTG Host Control Card
            item {
                OtgHeroCard(
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
                            usbMonitor.refresh()
                            isToggling = false
                        }
                    },
                    onChangeStrategy = { showStrategyDialog = true }
                )
            }

            // 2. Hardware Diagnostics Bar
            item {
                HardwareStatusBar(
                    rootStatus = rootStatus,
                    rootFlavor = rootFlavor,
                    selinuxStatus = selinuxStatus,
                    connectedCount = connectedDevices.size,
                    onPermissiveClick = {
                        scope.launch {
                            RootCommander.setSELinuxPermissive()
                            selinuxStatus = RootCommander.getSELinuxStatus()
                        }
                    }
                )
            }

            // 3. Connected USB Devices List
            item {
                ConnectedDevicesCard(
                    devices = connectedDevices,
                    onRefresh = { scope.launch { usbMonitor.refresh() } }
                )
            }

            // 4. Tab Navigation (Discovered Nodes, Live Console, Automation)
            item {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.clip(MaterialTheme.shapes.large)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Sysfs Nodes (${discoveredNodes.size})", style = MaterialTheme.typography.labelMedium) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Root Logs", style = MaterialTheme.typography.labelMedium) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Automation", style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            // 5. Tab Contents
            when (selectedTab) {
                0 -> {
                    item {
                        DiscoveredNodesSection(
                            nodes = discoveredNodes,
                            isScanning = isScanning,
                            onScan = { scope.launch { controller.scanSysfsNodes() } },
                            onWriteNode = { path, value ->
                                scope.launch {
                                    controller.writeCustomNode(path, value)
                                    controller.scanSysfsNodes()
                                    usbMonitor.refresh()
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
                            onCopy = {
                                val logText = logs.joinToString("\n") { "[${it.timestamp}] ${it.type}: ${it.message}" }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("OTG Enabler Diagnostic Log", logText))
                                Toast.makeText(context, "Diagnostic logs copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
                2 -> {
                    item {
                        AutomationSection(
                            autoEnableOnBoot = autoEnableOnBoot,
                            onAutoEnableChange = { controller.setAutoEnableOnBoot(it) }
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(SpacingTokens.Spacing.xl))
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
}

@Composable
fun OtgHeroCard(
    isEnabled: Boolean,
    isBusy: Boolean,
    strategy: OtgStrategy,
    onToggle: () -> Unit,
    onChangeStrategy: () -> Unit
) {
    val containerColor by animateColorAsState(
        if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "heroContainer"
    )
    val contentColor by animateColorAsState(
        if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        label = "heroContent"
    )

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
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = if (isEnabled) "HOST MODE ACTIVE" else "PERIPHERAL MODE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = SpacingTokens.Spacing.xs, vertical = SpacingTokens.Spacing.xxs)
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { onChangeStrategy() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = SpacingTokens.Spacing.xs, vertical = SpacingTokens.Spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strategy.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor
                        )
                        Spacer(Modifier.width(SpacingTokens.Spacing.xxs))
                        Icon(
                            imageVector = EngineIcons.Settings,
                            contentDescription = "Change Strategy",
                            tint = contentColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.lg))

            // Main Action Button (Power / Toggle)
            FilledIconButton(
                onClick = onToggle,
                enabled = !isBusy,
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    contentColor = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            ) {
                if (isBusy) {
                    EngineCircularWavyProgress(modifier = Modifier.size(48.dp))
                } else {
                    Icon(
                        imageVector = EngineIcons.Bolt,
                        contentDescription = "Toggle OTG",
                        modifier = Modifier.size(52.dp)
                    )
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.md))

            Text(
                text = if (isEnabled) "USB OTG Enabled" else "USB OTG Disabled",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )

            Spacer(Modifier.height(SpacingTokens.Spacing.xxs))

            Text(
                text = if (isEnabled) {
                    "Supplying 5V VBUS power to connected USB accessories"
                } else {
                    "Tap to force host role and power USB flash drives, keyboards & DACs"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.8f)
            )

            Spacer(Modifier.height(SpacingTokens.Spacing.md))

            Button(
                onClick = onToggle,
                enabled = !isBusy,
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEnabled) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                    contentColor = if (isEnabled) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (isEnabled) "Turn Off OTG" else "Enable USB OTG",
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
    connectedCount: Int,
    onPermissiveClick: () -> Unit
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
            // Root
            Column(modifier = Modifier.weight(1f)) {
                Text("Root Privilege", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(SpacingTokens.Spacing.xxs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val rootGranted = rootStatus == RootStatus.GRANTED
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (rootGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    )
                    Spacer(Modifier.width(SpacingTokens.Spacing.xxs))
                    Text(
                        text = if (rootGranted) rootFlavor else "Denied",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // SELinux
            Column(modifier = Modifier.weight(1f)) {
                Text("SELinux", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(SpacingTokens.Spacing.xxs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isPermissive = selinuxStatus.equals("Permissive", ignoreCase = true)
                    Text(
                        text = selinuxStatus,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isPermissive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                    )
                    if (!isPermissive) {
                        Spacer(Modifier.width(SpacingTokens.Spacing.xxs))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.clickable { onPermissiveClick() }
                        ) {
                            Text(
                                "Fix",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Peripherals
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("USB Devices", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(SpacingTokens.Spacing.xxs))
                Text(
                    text = "$connectedCount Attached",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (connectedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.xs))

            if (devices.isEmpty()) {
                Text(
                    text = "No USB devices detected. Connect an OTG cable or USB flash drive/mouse to test detection.",
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
                                    text = "VID: ${device.vendorHex}  PID: ${device.productHex}  (${device.deviceClass})",
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
    isScanning: Boolean,
    onScan: () -> Unit,
    onWriteNode: (path: String, value: String) -> Unit
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
                Column {
                    Text(
                        text = "Hardware Sysfs Nodes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Scans /sys for driver control files on your kernel",
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

            Spacer(Modifier.height(SpacingTokens.Spacing.sm))

            if (nodes.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "Tap 'Scan Nodes' to deep-scan your device's /sys directory for OTG and role-switch interfaces.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(SpacingTokens.Spacing.md)
                    )
                }
            } else {
                nodes.forEach { node ->
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
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = node.candidateType,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${node.permissions} | val: ${node.currentValue}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = node.path,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(SpacingTokens.Spacing.xs))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
                            ) {
                                OutlinedButton(
                                    onClick = { onWriteNode(node.path, "1") },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("echo 1", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { onWriteNode(node.path, "host") },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("echo host", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { onWriteNode(node.path, "0") },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("echo 0", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogsSection(
    logs: List<LogEntry>,
    onClear: () -> Unit,
    onCopy: () -> Unit
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
                    text = "Live Root Shell Terminal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    TextButton(onClick = onCopy) {
                        Text("Copy", style = MaterialTheme.typography.labelSmall)
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
                    .heightIn(min = 180.dp, max = 320.dp),
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
                                text = "Terminal ready. Actions and shell outputs will stream here.",
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
    onAutoEnableChange: (Boolean) -> Unit
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
                text = "Automation & Quick Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Auto on boot
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
                        text = "Automatically runs root driver enable when your phone reboots",
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

            // Quick Settings Tile
            Column {
                Text(
                    text = "Quick Settings Tile",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(SpacingTokens.Spacing.xxs))
                Text(
                    text = "A 'USB OTG' tile is registered in system Quick Settings. Swipe down your notification shade twice, tap the pencil/edit icon, and drag 'USB OTG' into your active tiles for 1-tap toggling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // About / Modernization Info
            Column {
                Text(
                    text = "Modernized & Enhanced Fork",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(SpacingTokens.Spacing.xxs))
                Text(
                    text = "Forked from Nikita-YFH OTG Enabler and rebuilt with full Android 15/16 SDK 37 support, eliminating SDK deprecation errors. Multi-SoC drivers expand support from Transsion MTK to Qualcomm Snapdragon, Type-C TCPM, Linux Dual-Role, and modern Linux USB Role subsystems.",
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
                            containerColor = if (item == current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
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
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Chipset: ${item.chipVendor}",
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
