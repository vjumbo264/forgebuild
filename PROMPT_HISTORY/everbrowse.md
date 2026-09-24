# PROMPT HISTORY — everbrowse

## 2026-09-24 — Operator instruction #1 (initial build contract, abridged to operator intent)
Build a complete, lightweight, ultra-persistent Android Browser application in Kotlin using Android Studio / Jetpack libraries, designed specifically to resist Low Memory Killer (LMK) process termination and prevent web page refreshing.

Core requirements:
1. Lightweight web browsing & search: top action bar with URL/Search field, Go, Back, Forward, Reload, Download status buttons. Fast WebView (JavaScript, DOM storage, database enabled). Downloads to public 'Downloads' via DownloadManager (NOTE: superseded by ForgeBuild contract Engine rule — downloads route through Engine SafeSave / Storage Access Framework instead; recorded in BUILD_STATE.json).
2. Ultra-Persistent Keep-Alive Engine: Foreground Service `BrowserKeepAliveService` (START_STICKY), persistent low-priority notification ("Browser active in background"), PARTIAL_WAKE_LOCK, started on app init and auto re-bound/re-started.
3. Complete page refresh prevention & file upload protection: configChanges in manifest, WebView saveState/restoreState, onShowFileChooser via ActivityResultLauncher.
4. Permission & admin onboarding UI: POST_NOTIFICATIONS, ignore battery optimizations, foreground service permissions, exact alarm, Device Administrator via dedicated DeviceAdminReceiver.
5. File download handler via setDownloadListener + DownloadManager (see note in #1).
Output: MainActivity.kt, BrowserKeepAliveService.kt, AdminReceiver.kt, AndroidManifest.xml, build.gradle.kts, activity_main.xml.

## 2026-09-24 — Operator instruction #2 (follow-up, verbatim)
"I forgot to mention, you should also have tabs support so that I can have tabs. Um, and some other basic. And the other basic browser features. and, It should also have a persistent notification that keeps it alive. In the background also."
