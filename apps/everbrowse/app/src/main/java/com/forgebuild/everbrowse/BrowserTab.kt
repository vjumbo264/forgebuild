package com.forgebuild.everbrowse

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * One browser tab. The WebView instance is created once per tab and kept alive
 * for the tab's lifetime; across configuration changes the SAME WebView is
 * reused (the Activity is not destroyed thanks to configChanges), and across
 * process death within the same task the full WebView state bundle is saved /
 * restored so pages come back without a network refresh.
 */
class BrowserTab(
    val id: Long,
    context: Context,
    initialUrl: String = AddressResolver.HOME_URL,
) {
    var isHomePage by mutableStateOf(
        initialUrl.isBlank() || initialUrl == AddressResolver.HOME_URL || initialUrl == "about:blank"
    )
    var currentUrl by mutableStateOf(if (isHomePage) AddressResolver.HOME_URL else initialUrl)
    var title by mutableStateOf(if (isHomePage) "Home" else "")
    var isLoading by mutableStateOf(false)
    var progress by mutableIntStateOf(100)

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(context).apply {
        // Transparent background so no blinding white flash occurs during page transitions
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            // Clean User-Agent: strip "; wv" and "Version/4.0 " so Google Colab, Google Docs,
            // and modern interactive web apps treat EverBrowse as standard Chrome on Android
            val rawUa = userAgentString
            userAgentString = rawUa.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
        }

        // Enable third-party and cross-origin cookies so Colab notebook iframes (*.googleusercontent.com)
        // and OAuth authentication frames work seamlessly without blank screens
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, true)
    }

    var restoredFromState = false
        private set

    init {
        if (!isHomePage && initialUrl.isNotBlank()) {
            load(initialUrl)
        }
    }

    fun load(url: String) {
        val resolved = AddressResolver.resolve(url)
        if (resolved == AddressResolver.HOME_URL) {
            isHomePage = true
            currentUrl = AddressResolver.HOME_URL
            title = "Home"
            isLoading = false
            progress = 100
            webView.stopLoading()
        } else {
            isHomePage = false
            currentUrl = resolved
            isLoading = true
            progress = 10
            webView.loadUrl(resolved)
        }
    }

    fun stopLoading() {
        if (!isHomePage) {
            webView.stopLoading()
            isLoading = false
            progress = 100
        }
    }

    fun reload() {
        if (isHomePage) {
            isLoading = false
            progress = 100
            return
        }
        isLoading = true
        progress = 15
        val target = webView.url?.takeIf { it.isNotBlank() && it != "about:blank" } ?: currentUrl
        if (target.isNotBlank() && target != AddressResolver.HOME_URL) {
            webView.loadUrl(target)
        } else {
            webView.reload()
        }
    }

    fun goBack(): Boolean {
        if (!isHomePage && webView.canGoBack()) {
            webView.goBack()
            return true
        } else if (!isHomePage) {
            load(AddressResolver.HOME_URL)
            return true
        }
        return false
    }

    fun save(out: Bundle) {
        out.putBoolean("isHomePage", isHomePage)
        out.putString("currentUrl", currentUrl)
        out.putString("title", title)
        if (!isHomePage) {
            webView.saveState(out)
        }
    }

    fun restore(state: Bundle): Boolean {
        isHomePage = state.getBoolean("isHomePage", false)
        currentUrl = state.getString("currentUrl", AddressResolver.HOME_URL) ?: AddressResolver.HOME_URL
        title = state.getString("title", if (isHomePage) "Home" else "") ?: ""
        if (!isHomePage) {
            restoredFromState = webView.restoreState(state) != null
            return restoredFromState
        }
        return true
    }

    val displayTitle: String
        get() = when {
            isHomePage -> "Home"
            title.isNotBlank() -> title
            currentUrl.isNotBlank() -> currentUrl
            else -> "New Tab"
        }

    val displayUrl: String
        get() = when {
            isHomePage -> "everbrowse://home"
            currentUrl.isNotBlank() -> currentUrl
            else -> ""
        }
}
