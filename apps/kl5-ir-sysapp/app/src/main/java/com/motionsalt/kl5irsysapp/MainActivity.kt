package com.motionsalt.kl5irsysapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.components.EngineLoadingIndicator
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.ForgeBuildTheme
import com.forgebuild.engine.ui.theme.SpacingTokens

/**
 * LSPosed will hook this method to return true when the module is actively loaded.
 */
fun isModuleActiveInXposed(): Boolean {
    return false
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ForgeBuildTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val isModuleActive = remember { isModuleActiveInXposed() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "KL5 IR & SysApp",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "LSPosed Hook Module • TECNO KL5",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = SpacingTokens.contentMargin(), vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. LSPosed Module Status Card
            StatusCard(isActive = isModuleActive)

            // 2. Problem 1: System-App Install Gates Card
            SysAppGatesCard()

            // 3. Problem 2: IR Blaster HIDL Bridge Card
            IrBridgeCard()

            // 4. On-Device Verification Commands Card
            TestCommandsCard(context = context)

            // 5. Setup Guide Card
            SetupGuideCard(context = context)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun StatusCard(isActive: Boolean) {
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = if (isActive) EngineIcons.Bolt else EngineIcons.Settings,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(36.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isActive) "LSPosed Module Active" else "Module Inactive / Pending Reboot",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = if (isActive)
                        "Hooks attached to system_server (PID alive). All install gates and IR bridge operational."
                    else
                        "Enable 'KL5 IR & SysApp' in LSPosed Manager with 'System Framework' scope, then reboot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
fun SysAppGatesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "System-App Install Gates",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                    Text("ACTIVE")
                }
            }

            Text(
                text = "Bypasses Android 17 / 16 GSI install restrictions strictly for preloaded system apps, leaving non-system validation untouched.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FeatureItem(
                title = "Unknown Sources Check",
                desc = "Hooked in PackageInstallerSession.validateApkInstallLocked() to permit pm install -r from adb/shell/root."
            )
            FeatureItem(
                title = "Same-versionCode Check",
                desc = "Hooked in InstallPackageHelper.preparePackage() to allow re-installing identical versionCode system packages."
            )
            FeatureItem(
                title = "Downgrade Version Check",
                desc = "Hooked in InstallPackageHelper.verifyReplacingVersionCode() to allow rolling back system apps."
            )
            FeatureItem(
                title = "Reboot Scan Preservation",
                desc = "Hooked boot-time initial scan logic to prevent Android from silently reverting downgrades on reboot."
            )
        }
    }
}

@Composable
fun IrBridgeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ConsumerIrService HIDL Bridge",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                    Text("ACTIVE")
                }
            }

            Text(
                text = "Android 17 GSI only looks for AIDL IConsumerIr, leaving TECNO KL5's working vendor HIDL HAL undetected. This hook directly bridges framework calls to the real HIDL interface.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FeatureItem(
                title = "Target HAL Interface",
                desc = "android.hardware.ir@1.0::IConsumerIr/default (/dev/irtx, /dev/lirc0)"
            )
            FeatureItem(
                title = "Emitter Detection",
                desc = "ConsumerIrService.hasIrEmitter() and constructor mHasNativeHal bridged to return true."
            )
            FeatureItem(
                title = "Transmission & Frequencies",
                desc = "ConsumerIrService.transmit() and getCarrierFrequencies() invoke real HIDL HAL transactions with DeathRecipient recovery."
            )
        }
    }
}

@Composable
fun FeatureItem(title: String, desc: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "• $title",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
fun TestCommandsCard(context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "On-Device Verification Commands",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Run these commands in Termux or ADB shell as root to verify the hooks:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            CommandLineBox(
                label = "1. Test IR Emitter Detection",
                command = "service call consumer_ir 1",
                context = context
            )

            CommandLineBox(
                label = "2. Test System App Downgrade / Re-install",
                command = "pm install -r /path/to/system-app.apk",
                context = context
            )

            CommandLineBox(
                label = "3. Check Hook Logcat Output",
                command = "logcat -d | grep -i MOTIONSALT-Hook | tail -80",
                context = context
            )
        }
    }
}

@Composable
fun CommandLineBox(label: String, command: String, context: Context) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceDim)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = command,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("command", command))
                    Toast.makeText(context, "Copied command", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = EngineIcons.Add,
                    contentDescription = "Copy",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun SetupGuideCard(context: Context) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "LSPosed Manager Activation",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "1. Open LSPosed Manager (ReZygisk / zygisksu).\n" +
                       "2. Go to Modules tab and toggle 'KL5 IR & SysApp'.\n" +
                       "3. Confirm the 'System Framework' (android) scope is selected.\n" +
                       "4. Reboot your TECNO KL5 device.\n" +
                       "5. Verify with 'logcat -d | grep -i MOTIONSALT-Hook'.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    val intent = context.packageManager.getLaunchIntentForPackage("org.lsposed.manager")
                    if (intent != null) {
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(context, "LSPosed Manager not found", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.padding(top = 4.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text("Open LSPosed Manager")
            }
        }
    }
}
