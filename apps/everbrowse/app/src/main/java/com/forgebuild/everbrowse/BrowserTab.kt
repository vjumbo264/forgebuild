package com.forgebuild.everbrowse

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
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
    val webView: WebView = WebView(context.applicationContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportMultipleWindows(false)
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
            progress = 0
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
        if (!isHomePage) {
            isLoading = true
            progress = 0
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
