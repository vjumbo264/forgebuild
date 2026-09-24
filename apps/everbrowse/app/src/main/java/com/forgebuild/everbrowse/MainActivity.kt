package com.forgebuild.everbrowse

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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.forgebuild.engine.files.SafeSave
import com.forgebuild.engine.permissions.EnginePermission
import com.forgebuild.engine.permissions.PermissionWiring
import com.forgebuild.engine.ui.components.EngineLinearWavyProgress
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.ForgeBuildTheme
import com.forgebuild.engine.ui.theme.SpacingTokens

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

    // ---- browser state ( survives config change because Activity survives ) ----
    private val tabs = mutableStateListOf<BrowserTab>()
    private var activeTabIndex by mutableIntStateOf(0)
    private var addressText by mutableStateOf("")
    private var loadProgress by mutableIntStateOf(100)
    private var downloadStatus by mutableStateOf("")
    private var showOnboarding by mutableStateOf(false)

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

        if (savedInstanceState != null) restoreTabs(savedInstanceState)
        if (tabs.isEmpty()) {
            newTab(intent?.dataString ?: "https://www.google.com")
            intent?.dataString?.let { intent.data = null }
        }

        setContent {
            ForgeBuildTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BrowserScreen()
                    if (showOnboarding) OnboardingDialog()
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
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val removed = tabs.removeAt(index)
        removed.webView.destroy()
        if (tabs.isEmpty()) newTab("https://www.google.com")
        activeTabIndex = activeTabIndex.coerceIn(0, tabs.lastIndex)
    }

    private fun wireTab(tab: BrowserTab) {
        tab.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (tabs.getOrNull(activeTabIndex) == tab) { addressText = url; loadProgress = 0 }
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (tabs.getOrNull(activeTabIndex) == tab) { addressText = url; loadProgress = 100 }
            }
        }
        tab.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (tabs.getOrNull(activeTabIndex) == tab) loadProgress = newProgress
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
                    fileChooserLauncher.launch(intent); true
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null; false
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
                val tab = BrowserTab(System.nanoTime(), this, "about:blank")
                tab.webView.stopLoading()
                tab.restore(b)
                wireTab(tab)
                tabs.add(tab)
            }
        }
        activeTabIndex = state.getInt("activeTab", 0).coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        tabs.getOrNull(activeTabIndex)?.url?.let { if (it.isNotBlank()) addressText = it }
    }

    // ---------------- UI ----------------

    @Composable
    private fun BrowserScreen() {
        val tab = tabs.getOrNull(activeTabIndex)
        val s = SpacingTokens.Spacing

        Column(modifier = Modifier.fillMaxSize()) {
            // --- top action bar: back / forward / reload / address / go ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = s.xs, vertical = s.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { tab?.webView?.let { if (it.canGoBack()) it.goBack() } }) {
                    Icon(EngineIcons.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = { tab?.webView?.let { if (it.canGoForward()) it.goForward() } }) {
                    Icon(EngineIcons.ArrowForward, contentDescription = "Forward")
                }
                IconButton(onClick = { tab?.webView?.reload() }) {
                    Icon(EngineIcons.Refresh, contentDescription = "Reload")
                }
                OutlinedTextField(
                    value = addressText,
                    onValueChange = { addressText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = MaterialTheme.shapes.extraLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        tab?.webView?.loadUrl(AddressResolver.resolve(addressText))
                    })
                )
                IconButton(onClick = { tab?.webView?.loadUrl(AddressResolver.resolve(addressText)) }) {
                    Icon(EngineIcons.Search, contentDescription = "Go / Search")
                }
            }

            // --- secondary row: downloads + keep-alive settings ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = s.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    Toast.makeText(
                        this@MainActivity,
                        downloadStatus.ifBlank { "No recent downloads" },
                        Toast.LENGTH_SHORT
                    ).show()
                }) { Icon(EngineIcons.Download, contentDescription = "Download status") }
                Spacer(Modifier.width(s.xxs))
                Text(
                    text = downloadStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                TextButton(onClick = { showOnboarding = true }) {
                    Icon(EngineIcons.AdminPanelSettings, contentDescription = null)
                    Spacer(Modifier.width(s.xxs))
                    Text("Keep-alive", style = MaterialTheme.typography.labelLarge)
                }
            }

            // --- expressive loading progress ---
            if (loadProgress < 100) {
                EngineLinearWavyProgress(
                    progress = { loadProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // --- tab strip ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = s.xs, vertical = s.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(s.xs)
            ) {
                tabs.forEachIndexed { i, t ->
                    val selected = i == activeTabIndex
                    Surface(
                        onClick = { activeTabIndex = i; addressText = t.url },
                        shape = MaterialTheme.shapes.large,
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = s.sm, top = s.xxs, bottom = s.xxs)
                        ) {
                            Text(
                                text = t.title.ifBlank { t.url.ifBlank { "New tab" } }.take(18),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = { closeTab(i) }) {
                                Icon(EngineIcons.Close, contentDescription = "Close tab")
                            }
                        }
                    }
                }
                Surface(
                    onClick = { newTab("https://www.google.com") },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        EngineIcons.Add, contentDescription = "New tab",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(s.xs)
                    )
                }
            }

            // --- active WebView ---
            Box(modifier = Modifier.fillMaxSize()) {
                if (tab != null) {
                    AndroidView(
                        factory = { tab.webView },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // ---------------- permissions & admin onboarding ----------------

    @Composable
    private fun OnboardingDialog() {
        AlertDialog(
            onDismissRequest = { showOnboarding = false },
            title = { Text("Keep-alive setup", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                    Text(
                        "Grant these so EverBrowse can stay resident and never refresh your pages in the background.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OnboardRow("Notifications (keep-alive notice)") {
                        PermissionWiring.request(this@MainActivity, EnginePermission.NOTIFICATIONS)
                    }
                    OnboardRow("Ignore battery optimization") { requestIgnoreBatteryOpt() }
                    OnboardRow("Exact alarms") { PermissionWiring.requestExactAlarm(this@MainActivity) }
                    OnboardRow("Device admin keep-alive") {
                        PermissionWiring.requestDeviceAdmin(
                            this@MainActivity, AdminReceiver::class.java,
                            "Lets EverBrowse resist being killed so your tabs stay loaded."
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOnboarding = false }) { Text("Done") }
            }
        )
    }

    @Composable
    private fun OnboardRow(label: String, onClick: () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClick) { Text("Grant", style = MaterialTheme.typography.labelLarge) }
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
            }
        } else {
            Toast.makeText(this, "Already exempted from battery optimization", Toast.LENGTH_SHORT).show()
        }
    }
}
