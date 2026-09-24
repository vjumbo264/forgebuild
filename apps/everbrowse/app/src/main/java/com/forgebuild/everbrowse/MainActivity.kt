package com.forgebuild.everbrowse

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog as AndroidAlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
 * Anti-refresh strategy:
 *  - configChanges in manifest -> Activity survives screen/keyboard/theme changes.
 *  - Tabs hold long-lived WebView instances with JS/DOM/databases enabled.
 *  - Full WebView state saved/restored across process death.
 *  - BrowserKeepAliveService (START_STICKY + PARTIAL_WAKE_LOCK + persistent notification).
 *  - Robust multi-window, Desktop Site mode, and full Google Colab / rich app compatibility.
 */
class MainActivity : ComponentActivity() {

    // ---- browser state ----
    private val tabs = mutableStateListOf<BrowserTab>()
    private var activeTabIndex by mutableIntStateOf(0)
    private var addressText by mutableStateOf("")
    private var showOnboarding by mutableStateOf(false)
    private var hasPromptedPermissions by mutableStateOf(false)
    private var resumeCounter by mutableIntStateOf(0)

    // File-upload callback held across system picker
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingDownload: DownloadCoordinator.PendingDownload? = null

    // Storage permission launcher for saving to Downloads folder
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val pending = pendingDownload
        if (isGranted && pending != null) {
            triggerDownload(pending)
        } else if (!isGranted) {
            Toast.makeText(this, "Storage permission required to save downloads", Toast.LENGTH_SHORT).show()
        }
    }

    // System file picker for web <input type=file>
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

    override fun onResume() {
        super.onResume()
        resumeCounter++
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

        // Auto-prompt keep-alive setup on startup if missing
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
                            // Handled by tab.goBack()
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
                    // gesturesEnabled = drawerState.isOpen:
                    // When open -> scrim tap closes drawer; when closed -> no accidental swipe gestures while scrolling web pages
                    gesturesEnabled = drawerState.isOpen,
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
                            onToggleDesktopMode = {
                                currentTab?.toggleDesktopMode()
                                val msg = if (currentTab?.isDesktopMode == true) "Desktop site enabled" else "Mobile site enabled"
                                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
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
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme?.lowercase() ?: ""

                // Let WebView handle all web links and internal browser execution schemes
                // (including blob:, data:, about:, javascript:, file:, content:)
                if (scheme in listOf("http", "https", "about", "data", "blob", "javascript", "content", "file")) {
                    return false
                }

                // Only launch external applications for actual intent/app schemes (e.g. mailto:, tel:, sms:, market:, intent:)
                val url = uri.toString()
                return try {
                    val intent = if (url.startsWith("intent:")) {
                        Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    } else {
                        Intent(Intent.ACTION_VIEW, uri)
                    }
                    startActivity(intent)
                    true
                } catch (e: Exception) {
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                tab.currentUrl = url
                tab.isLoading = true
                tab.progress = 10
                if (tabs.getOrNull(activeTabIndex) == tab) {
                    addressText = url
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                tab.currentUrl = url
                tab.title = view.title ?: ""
                tab.isLoading = false
                tab.progress = 100
                tab.applyDesktopViewport()
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

            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }

            // JavaScript confirmation/alert dialogs (vital for web app confirmations and runtime prompts)
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AndroidAlertDialog.Builder(this@MainActivity)
                    .setTitle(view?.title?.takeIf { it.isNotBlank() } ?: "Notice")
                    .setMessage(message ?: "")
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.confirm() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AndroidAlertDialog.Builder(this@MainActivity)
                    .setTitle(view?.title?.takeIf { it.isNotBlank() } ?: "Confirmation")
                    .setMessage(message ?: "")
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?
            ): Boolean {
                val input = EditText(this@MainActivity).apply {
                    setText(defaultValue ?: "")
                }
                AndroidAlertDialog.Builder(this@MainActivity)
                    .setTitle(view?.title?.takeIf { it.isNotBlank() } ?: "Input")
                    .setMessage(message ?: "")
                    .setView(input)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm(input.text.toString()) }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            // Support multi-window popups (e.g. Google Sign-In, OAuth popups, file dialogues)
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                if (resultMsg == null) return false
                val newTab = BrowserTab(System.nanoTime(), this@MainActivity, "")
                wireTab(newTab)
                tabs.add(newTab)
                activeTabIndex = tabs.lastIndex
                addressText = ""
                val transport = resultMsg.obj as? WebView.WebViewTransport
                transport?.webView = newTab.webView
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                val index = tabs.indexOfFirst { it.webView == window }
                if (index >= 0 && tabs.size > 1) {
                    closeTab(index)
                }
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

        // Downloads save directly to system Downloads folder
        tab.webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            val (name, resolvedMime) = DownloadCoordinator.guessName(url, contentDisposition, mimeType)
            val pending = DownloadCoordinator.PendingDownload(url, name, resolvedMime)
            triggerDownload(pending)
        }
    }

    private fun triggerDownload(pending: DownloadCoordinator.PendingDownload) {
        pendingDownload = pending
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                storagePermissionLauncher.launch(permission)
                return
            }
        }
        Toast.makeText(this, "Downloading ${pending.suggestedName}…", Toast.LENGTH_SHORT).show()
        DownloadCoordinator.saveToDownloads(this, pending, lifecycleScope) { ok, result ->
            if (ok) {
                Toast.makeText(this@MainActivity, "Saved $result", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@MainActivity, "Download failed: $result", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------- state save / restore ----------------

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
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        val isLoading = tab?.isLoading == true || (tab?.progress ?: 100) < 100

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // --- Top Navigation Bar: Clean row with search, home, desktop mode, refresh, new tab ---
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
                        // 1. Hamburger Menu Button (Tabs drawer trigger)
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = EngineIcons.Menu,
                                contentDescription = "Menu and tabs",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // 2. Home Button
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

                        // 3. Compact Search & URL Input Bar: Vertically centered BasicTextField, NEVER cropped
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (addressText.isEmpty()) {
                                        Text(
                                            text = if (tab?.isHomePage == true) "Search or type URL" else "Search or URL",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    BasicTextField(
                                        value = addressText,
                                        onValueChange = { addressText = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                        keyboardActions = KeyboardActions(onGo = {
                                            if (addressText.isNotBlank()) {
                                                keyboardController?.hide()
                                                focusManager.clearFocus()
                                                tab?.load(addressText)
                                            }
                                        })
                                    )
                                }

                                if (isLoading) {
                                    IconButton(
                                        onClick = { tab?.stopLoading() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = EngineIcons.Close,
                                            contentDescription = "Cancel loading",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else if (addressText.isNotBlank()) {
                                    IconButton(
                                        onClick = { addressText = "" },
                                        modifier = Modifier.size(24.dp)
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
                        }

                        // 4. Desktop Site Mode Toggle Button (Optional on-demand desktop view)
                        IconButton(
                            onClick = {
                                tab?.let {
                                    it.toggleDesktopMode()
                                    val msg = if (it.isDesktopMode) "Desktop site enabled" else "Mobile site enabled"
                                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = EngineIcons.DesktopWindows,
                                contentDescription = "Toggle desktop site",
                                tint = if (tab?.isDesktopMode == true) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 5. Action Button: Refresh Tab (ALWAYS present and active)
                        IconButton(
                            onClick = {
                                tab?.reload()
                            }
                        ) {
                            Icon(
                                imageVector = EngineIcons.Refresh,
                                contentDescription = "Refresh tab",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 6. New Tab Quick Button
                        IconButton(
                            onClick = { newTab(AddressResolver.HOME_URL) }
                        ) {
                            Icon(
                                imageVector = EngineIcons.Add,
                                contentDescription = "New tab",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // --- Expressive Loading Progress ---
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
                    // Continuous WebView attachment with explicit layout parameters and visibility control
                    key(tab.id) {
                        AndroidView(
                            factory = {
                                tab.webView.apply {
                                    (parent as? ViewGroup)?.removeView(this)
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    visibility = if (tab.isHomePage) View.GONE else View.VISIBLE
                                }
                            },
                            update = { view ->
                                view.visibility = if (tab.isHomePage) View.GONE else View.VISIBLE
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (tab.isHomePage) {
                        HomePageView(
                            onSearch = { query ->
                                keyboardController?.hide()
                                focusManager.clearFocus()
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
                    }
                }
            }
        }
    }

    // ---------------- permissions & admin onboarding ----------------

    private fun needsKeepAlivePermissions(): Boolean {
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
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, AdminReceiver::class.java)
        val adminActive = dpm.isAdminActive(adminComponent)

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
                        "Grant these permissions so EverBrowse stays resident and never refreshes your open tabs in the background.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Battery Optimization
                    PermissionRow(
                        title = "Ignore Battery Optimization",
                        subtitle = "Lets browser process stay alive in background",
                        isGranted = batteryExempt,
                        onClick = { requestIgnoreBatteryOpt() }
                    )

                    // Exact Alarms
                    PermissionRow(
                        title = "Exact Alarms",
                        subtitle = "Timed watchdog keep-alive",
                        isGranted = exactAlarmAllowed,
                        onClick = { requestExactAlarm() }
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

                    // Device Admin (Correctly checks isAdminActive)
                    PermissionRow(
                        title = "Device Admin",
                        subtitle = if (adminActive) "Device admin active" else "Optional process persistence",
                        isGranted = adminActive,
                        onClick = {
                            if (!adminActive) {
                                PermissionWiring.requestDeviceAdmin(
                                    this@MainActivity, AdminReceiver::class.java,
                                    "Lets EverBrowse resist being killed so your tabs stay loaded."
                                )
                            }
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

    private fun requestExactAlarm() {
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.parse("package:$packageName"))
                )
            }.onFailure {
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:$packageName"))
                    )
                }
            }
        } else {
            Toast.makeText(this, "Exact alarms permitted on this Android version", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestIgnoreBatteryOpt() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= 23 && !pm.isIgnoringBatteryOptimizations(packageName)) {
            runCatching {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
                startActivity(intent)
            }.onFailure {
                runCatching {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                }.onFailure {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:$packageName"))
                    startActivity(intent)
                }
            }
        } else {
            Toast.makeText(this, "Already exempted from battery optimization", Toast.LENGTH_SHORT).show()
        }
    }
}
