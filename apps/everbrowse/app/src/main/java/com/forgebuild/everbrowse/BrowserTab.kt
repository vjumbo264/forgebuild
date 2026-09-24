package com.forgebuild.everbrowse

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView

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
    initialUrl: String,
) {
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
        webView.loadUrl(initialUrl)
    }

    fun save(out: Bundle) {
        webView.saveState(out)
    }

    fun restore(state: Bundle): Boolean {
        restoredFromState = webView.restoreState(state) != null
        return restoredFromState
    }

    val title: String get() = webView.title ?: ""
    val url: String get() = webView.url ?: ""
}
