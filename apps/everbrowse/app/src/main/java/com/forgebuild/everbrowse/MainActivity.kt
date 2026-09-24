package com.forgebuild.everbrowse

import android.Manifest
import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.forgebuild.engine.permissions.EnginePermission
import com.forgebuild.engine.permissions.PermissionWiring
import com.forgebuild.engine.ui.components.EngineLinearWavyProgress
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.ForgeBuildTheme
import com.forgebuild.engine.ui.theme.SpacingTokens
import kotlinx.coroutines.launch

/**
 * EverBrowse — ultra-persistent tabbed browser.
 *
 * Anti-refresh strategy (the whole point of the app):
 *  - configChanges in the manifest -> rotation/keyboard/theme never recreate
 *    this Activity, so WebViews are never torn down on those changes.
 *  - Tabs hold long-lived WebView instances; only the ACTIVE tab is attached
 *    to the view tree, the rest stay alive off-screen with their page state.
 *  - Full WebView state bundles are saved in onSaveInstanceState and restored
 *    in onCreate so a process death + task relaunch returns pages without a
 *    network reload.
 *  - BrowserKeepAliveService (START_STICKY + PARTIAL_WAKE_LOCK + persistent
 *    notification) keeps the process out of the LMK kill list in background.
 */
class MainActivity : ComponentActivity() {

    // ---- browser state (survives config change because Activity survives) ----
    private val tabs = mutableStateListOf<BrowserTab>()
    private var activeTabIndex by mutableIntStateOf(0)
    private var addressText by mutableStateOf("")
    private var downloadStatus by mutableStateOf("")
    private var showOnboarding by mutableStateOf(false)
    private var hasPromptedPermissions by mutableStateOf(false)
    private var resumeCounter by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeCounter++
    }

    // File-upload callback held across the system picker; NOT nulled on config
    // change because the Activity is not recreated then.
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingDownload: DownloadCoordinator.PendingDownload? = null

    // SAF create-document launcher (Engine SafeSave flow for downloads).
    private val saveLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val pending = pendingDownload
        if (uri == null || pending == null) {
            downloadStatus = "Download cancelled"
            return@registerForActivityResult
        }
        downloadStatus = "Downloading ${pending.suggestedName}…"
        DownloadCoordinator.save(this, pending, uri, lifecycleScope) { ok ->
            downloadStatus = if (ok) "Saved ${pending.suggestedName}" else "Download failed"
            Toast.makeText(this, downloadStatus, Toast.LENGTH_SHORT).show()
        }
    }

    // System file picker for web <input type=file> (file-upload protection).
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cb = filePathCallback ?: return@registerForActivityResult
        val uris: Array<Uri>? = if (result.resultCode == RESULT_OK) {
            result.data?.let { WebChromeClient.FileChooserParams.parseResult(result.resultCode, it) }
        } else null
        cb.onReceiveValue(uris)
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        BrowserKeepAliveService.start(this)

        if (savedInstanceState != null) {
            restoreTabs(savedInstanceState)
        }

        if (tabs.isEmpty()) {
            val initial = intent?.dataString ?: AddressResolver.HOME_URL
            newTab(initial)
            intent?.dataString?.let { intent.data = null }
        }

        // Check if keep-alive permissions are missing; auto-prompt on launch
        if (!hasPromptedPermissions && needsKeepAlivePermissions()) {
            showOnboarding = true
            hasPromptedPermissions = true
        }

        setContent {
            ForgeBuildTheme {
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val currentTab = tabs.getOrNull(activeTabIndex)

                // Sync address bar text with current tab URL
                LaunchedEffect(activeTabIndex, currentTab?.currentUrl) {
                    if (currentTab != null) {
                        addressText = if (currentTab.isHomePage) "" else currentTab.currentUrl
                    }
                }

                // Handle back gesture: WebView back -> Home -> Close tab -> Minimize to back
                BackHandler(enabled = true) {
                    when {
                        drawerState.isOpen -> {
                            scope.launch { drawerState.close() }
                        }
                        currentTab != null && currentTab.goBack() -> {
                            // Handled by tab.goBack() (either went back in WebView or back to Home)
                        }
                        tabs.size > 1 -> {
                            closeTab(activeTabIndex)
                        }
                        else -> {
                            // Keep alive: move task to back instead of killing the activity process
                            moveTaskToBack(true)
                        }
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = true,
                    drawerContent = {
                        TabsDrawerSheet(
                            tabs = tabs,
                            activeTabIndex = activeTabIndex,
                            onSelectTab = { index ->
                                activeTabIndex = index
                                val t = tabs.getOrNull(index)
                                addressText = if (t?.isHomePage == true) "" else (t?.currentUrl ?: "")
                                scope.launch { drawerState.close() }
                            },
                            onCloseTab = { index ->
                                closeTab(index)
                            },
                            onNewTab = {
                                newTab(AddressResolver.HOME_URL)
                                scope.launch { drawerState.close() }
                            },
                            onCloseAllTabs = {
                                closeOtherTabs()
                                scope.launch { drawerState.close() }
                            },
                            onOpenKeepAlive = {
                                showOnboarding = true
                                scope.launch { drawerState.close() }
                            },
                            onCloseDrawer = {
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        BrowserScreen(
                            onOpenDrawer = { scope.launch { drawerState.open() } }
                        )

                        if (showOnboarding) {
                            OnboardingDialog()
                        }
                    }
                }
            }
        }
    }

    // ---------------- tab management ----------------

    private fun newTab(url: String) {
        val tab = BrowserTab(System.nanoTime(), this, url)
        wireTab(tab)
        tabs.add(tab)
        activeTabIndex = tabs.lastIndex
        addressText = if (tab.isHomePage) "" else tab.currentUrl
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val removed = tabs.removeAt(index)
        removed.webView.destroy()
        if (tabs.isEmpty()) {
            newTab(AddressResolver.HOME_URL)
        }
        activeTabIndex = activeTabIndex.coerceIn(0, tabs.lastIndex)
        val current = tabs.getOrNull(activeTabIndex)
        addressText = if (current?.isHomePage == true) "" else (current?.currentUrl ?: "")
    }

    private fun closeOtherTabs() {
        val keep = tabs.getOrNull(activeTabIndex) ?: return
        val toRemove = tabs.filter { it.id != keep.id }
        toRemove.forEach { it.webView.destroy() }
        tabs.clear()
        tabs.add(keep)
        activeTabIndex = 0
    }

    private fun wireTab(tab: BrowserTab) {
        tab.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                tab.currentUrl = url
                tab.isLoading = true
                tab.progress = 0
                if (tabs.getOrNull(activeTabIndex) == tab) {
                    addressText = url
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                tab.currentUrl = url
                tab.title = view.title ?: ""
                tab.isLoading = false
                tab.progress = 100
                if (tabs.getOrNull(activeTabIndex) == tab) {
                    addressText = url
                }
            }
        }

        tab.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                tab.progress = newProgress
                if (newProgress >= 100) {
                    tab.isLoading = false
                }
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                tab.title = title ?: ""
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                val intent = fileChooserParams.createIntent()
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    false
                }
            }
        }

        // Downloads -> Engine SafeSave (SAF), never DownloadManager (contract rule).
        tab.webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            val (name, _) = DownloadCoordinator.guessName(url, contentDisposition, mimeType)
            pendingDownload = DownloadCoordinator.PendingDownload(url, name)
            downloadStatus = "Saving $name…"
            saveLauncher.launch(name)
        }
    }

    // ---------------- state save / restore (page-refresh prevention) ----------------

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tabCount", tabs.size)
        outState.putInt("activeTab", activeTabIndex)
        tabs.forEachIndexed { i, tab ->
            val b = Bundle()
            tab.save(b)
            outState.putBundle("tab_$i", b)
        }
    }

    private fun restoreTabs(state: Bundle) {
        val count = state.getInt("tabCount", 0)
        for (i in 0 until count) {
            state.getBundle("tab_$i")?.let { b ->
                val tab = BrowserTab(System.nanoTime(), this, AddressResolver.HOME_URL)
                tab.webView.stopLoading()
                tab.restore(b)
                wireTab(tab)
                tabs.add(tab)
            }
        }
        activeTabIndex = state.getInt("activeTab", 0).coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        val current = tabs.getOrNull(activeTabIndex)
        addressText = if (current?.isHomePage == true) "" else (current?.currentUrl ?: "")
    }

    // ---------------- UI ----------------

    @Composable
    private fun BrowserScreen(
        onOpenDrawer: () -> Unit
    ) {
        val tab = tabs.getOrNull(activeTabIndex)
        val s = SpacingTokens.Spacing
        val isLoading = tab?.isLoading == true || (tab?.progress ?: 100) < 100

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding() // CRITICAL: Fix status bar overlap!
        ) {
            // --- Top Navigation Bar: Compact, single-row Material 3 Expressive bar ---
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = s.xs, vertical = s.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(s.xxs)
                    ) {
                        // 1. Hamburger Menu Button (opens tab list drawer)
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = EngineIcons.Menu,
                                contentDescription = "Menu and tabs",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // 2. Home Button (instant return to Home Page)
                        IconButton(
                            onClick = {
                                tab?.load(AddressResolver.HOME_URL)
                                addressText = ""
                            }
                        ) {
                            Icon(
                                imageVector = EngineIcons.Home,
                                contentDescription = "Home page",
                                tint = if (tab?.isHomePage == true) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 3. Compact Search & URL Input Bar
                        OutlinedTextField(
                            value = addressText,
                            onValueChange = { addressText = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            placeholder = {
                                Text(
                                    text = if (tab?.isHomePage == true) "Search or type URL" else "Search or URL",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                if (addressText.isNotBlank()) {
                                    tab?.load(addressText)
                                }
                            }),
                            trailingIcon = {
                                if (addressText.isNotBlank()) {
                                    IconButton(
                                        onClick = { addressText = "" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = EngineIcons.Close,
                                            contentDescription = "Clear",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        )

                        // 4. Cancel Loading / Reload Button
                        if (isLoading) {
                            // CANCEL BUTTON: cancels tab loading!
                            IconButton(
                                onClick = {
                                    tab?.stopLoading()
                                }
                            ) {
                                Icon(
                                    imageVector = EngineIcons.Close,
                                    contentDescription = "Cancel loading",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            // RELOAD BUTTON
                            IconButton(
                                onClick = {
                                    if (tab?.isHomePage == true) {
                                        // On home page, reload does nothing or refreshes
                                    } else {
                                        tab?.reload()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = EngineIcons.Refresh,
                                    contentDescription = "Reload",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 5. Tabs Counter Button (pill badge)
                        Surface(
                            onClick = onOpenDrawer,
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${tabs.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // --- Expressive Loading Progress (thin wavy bar) ---
                    if (isLoading) {
                        EngineLinearWavyProgress(
                            progress = { ((tab?.progress ?: 0) / 100f).coerceIn(0.05f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                        )
                    }
                }
            }

            // --- Main Content Area: Native Home Page OR Active WebView ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (tab != null) {
                    if (tab.isHomePage) {
                        // Native Home Page
                        HomePageView(
                            onSearch = { query ->
                                tab.load(query)
                                addressText = query
                            },
                            onOpenTabs = onOpenDrawer,
                            onNewTab = {
                                newTab(AddressResolver.HOME_URL)
                            },
                            onOpenKeepAlive = {
                                showOnboarding = true
                            },
                            needsKeepAliveSetup = needsKeepAlivePermissions()
                        )
                    } else {
                        // Web Page
                        AndroidView(
                            factory = { tab.webView },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // ---------------- permissions & admin onboarding ----------------

    private fun needsKeepAlivePermissions(): Boolean {
        // Reference resumeCounter to trigger Compose recomposition when returning from Settings
        val _counter = resumeCounter
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryExempt = if (Build.VERSION.SDK_INT >= 23) pm.isIgnoringBatteryOptimizations(packageName) else true
        val exactAlarmAllowed = if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } else true
        val notifGranted = if (Build.VERSION.SDK_INT >= 33) {
            PermissionWiring.isGranted(this, Manifest.permission.POST_NOTIFICATIONS)
        } else true
        return !batteryExempt || !exactAlarmAllowed || !notifGranted
    }

    @Composable
    private fun OnboardingDialog() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryExempt = if (Build.VERSION.SDK_INT >= 23) pm.isIgnoringBatteryOptimizations(packageName) else true
        val exactAlarmAllowed = if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } else true
        val notifGranted = if (Build.VERSION.SDK_INT >= 33) {
            PermissionWiring.isGranted(this, Manifest.permission.POST_NOTIFICATIONS)
        } else true

        AlertDialog(
            onDismissRequest = { showOnboarding = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        EngineIcons.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Background Keep-Alive", style = MaterialTheme.typography.titleLarge)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)) {
                    Text(
                        "Grant these permissions so Android's memory killer never closes or refreshes your open tabs.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Exact Alarms
                    PermissionRow(
                        title = "Exact Alarms",
                        subtitle = "Timed watchdog keep-alive",
                        isGranted = exactAlarmAllowed,
                        onClick = { PermissionWiring.requestExactAlarm(this@MainActivity) }
                    )

                    // Battery Optimization
                    PermissionRow(
                        title = "Unrestricted Battery",
                        subtitle = "Ignore battery optimization",
                        isGranted = batteryExempt,
                        onClick = { requestIgnoreBatteryOpt() }
                    )

                    // Notifications
                    PermissionRow(
                        title = "Notifications",
                        subtitle = "Ongoing service notification",
                        isGranted = notifGranted,
                        onClick = {
                            PermissionWiring.request(this@MainActivity, EnginePermission.NOTIFICATIONS)
                        }
                    )

                    // Device Admin (Optional)
                    PermissionRow(
                        title = "Device Admin (Optional)",
                        subtitle = "Maximum process persistence",
                        isGranted = false,
                        onClick = {
                            PermissionWiring.requestDeviceAdmin(
                                this@MainActivity, AdminReceiver::class.java,
                                "Lets EverBrowse resist being killed so your tabs stay loaded."
                            )
                        }
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showOnboarding = false }) {
                    Text("Done")
                }
            }
        )
    }

    @Composable
    private fun PermissionRow(
        title: String,
        subtitle: String,
        isGranted: Boolean,
        onClick: () -> Unit
    ) {
        val s = SpacingTokens.Spacing
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(s.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isGranted) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(s.xxs)
                    ) {
                        Text(
                            text = "Granted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = s.xs, vertical = s.xxs)
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = onClick,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Grant", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    private fun requestIgnoreBatteryOpt() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= 23 && !pm.isIgnoringBatteryOptimizations(packageName)) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                )
            }.onFailure {
                // Fallback to battery optimization settings list
                runCatching {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        } else {
            Toast.makeText(this, "Already exempted from battery optimization", Toast.LENGTH_SHORT).show()
        }
    }
}
