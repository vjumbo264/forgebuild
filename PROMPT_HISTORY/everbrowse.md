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


## 2026-09-24 — Operator instruction #3 (verbatim)
"Okay, there are some issues that I noticed. Exact alarms and background usage permissions are not popping up. Also, the tabs is not how I want it to be. There should be a hamburger menu, so that when I open it, I can see the list of tabs, so that I can cancel the ones and switch to the one I want to switch to. I think that will be better than for it to be occupying head space. Also, I need it to have a home page. Instead of going to Google.com every time, it should just have a home page where I can just search what I want. You understand? So that it will not stress me out. That should be the main page when you open the browser. Also, the back gesture should not be closing the app, but rather be going to the previous site. And also there should be a cancel button for loading tabs. You understand? A button that I tap to cancel the loading of any tab that I am in, since that is currently missing.
also, I forgot to mention the top navigation. Panel of the browser is messed up. Like it is. Messed up. So, please fix the UI in that part.it is showing alongside my, um, Status bar like the show under my status bar. Like my settle bar has been overlaid on it and it is still very high and it's very large. Instead of being a bit, um, like it is. It is too vertically, large. you understand for a search or URL bar, so, please fix it also and give the app a UI ux upgrade"


## 2026-09-24 — Operator instruction #4 (verbatim)
"Okay, I tested it and I have seen some issues. First of all, the top search bar, the text inside is cropped, which makes it look very ugly. Secondly, the working in background battery optimization permission still doesn't come out. Secondly, exact alarms is falsely showing granted, meanwhile I have never given it the permission. Thirdly, the admin permission is showing not granted, even though the initial version of the app already asked for the permission and I granted it and it is still active. So it shouldn't be showing not granted. So the permissions need to be fixed properly. Also, there is a tiles button by the right and there is a hamburger menu button by the left, and both of them does the same thing. Also, the shortcuts on the home screen needs to have icons, not just unnecessary shortcuts. Also, I hate that swipe for tabs. It's making me unable to navigate a website. Right now, if I try to move down or up, the tabs menu show up. So please remove that gesture for opening tabs. I don't need it. The button is enough. Additionally, all downloads should save to download folder. It should have a settings where I can set download location, or forget about download location. It must save to download folder, so it must ask me for storage permission to save to download folder. So do this and fix the other permission issues, please, so that it will be working properly."

## 2026-09-24 — Operator instruction #5 (verbatim)
"Right now, there are some sites that are not displaying properly. For example, Google Colab. That is one of the main reasons I created this app, and Google Colab is not showing. I'm only seeing the top bar, but I'm not seeing the content of Google Colab. I'm sure other sites also give me such issue, so ensure that everything loads sites correctly.

Also, if I tap on the refresh button, it should refresh the tab. Right now, it doesn't refresh the tab, so please just fix that so that when I tap on the refresh button, it should actually refresh the tab.

When I type something in the search bar and click go, it currently flashes the screen. I don't need that flash on the screen. If you check, you'll see that there's something that just displays on my, like it first flashes the screen for some time before the site starts loading. I just need it to be direct, so once I type something and click go, it just starts going. I don't see anything that will interrupt my vision of the site, of the app."

## 2026-09-24 — Operator instruction #6 (verbatim)
"Fix for sites like Google Colab appearing empty"

## 2026-09-24 — Operator instruction #7 (verbatim)
"One thing I need you to fix is the homepage UI on dark mode. Currently, on dark mode, the homepage looks very messed up, and some things are white and some things are black, so that needs to be fixed.

Secondly, let Colab not open in desktop mode by default unless I use the desktop mode, because I actually like the desktop mode feature.

Also, when I open the hamburger menu, I want the functionality that when I tap outside the menu, it will actually close it, because right now I used to try tapping outside the menu and it doesn't close the menu."

## 2026-09-24 — Operator instruction #8 (verbatim)
"Nice. Okay, now give it a better icon. I don't like the app icon, so can you give it a better app icon?

And secondly, the keyboard cursor in dark mode is black, which is making it hard for me to see it, so I need you to fix that. And if this issue is vice versa, fix it also for light mode."

## 2026-09-24 — Operator instruction #9 (verbatim)
"Also, enhance the UI. Give it a complete UI overhaul using the new latest Google Material 3 style."

## 2026-09-24 — Operator instruction #10 (verbatim)
"Fix the issue that if I press the refresh button, it doesn't refresh the page for some reason. It just blinks and nothing happens. So, refreshing the page with the button should actually refresh the page.

Also, add a pull to refresh feature so that I can pull to refresh."

## 2026-09-24 — Operator instruction #11 (verbatim)
"Fix the issue that if I press the refresh button, it doesn't refresh the page for some reason. It just blinks and nothing happens. So, refreshing the page with the button should actually refresh the page.

Also, add a pull to refresh feature so that I can pull to refresh.

Okay, now there are two distinct issues I need to fix.

First of all, the spinner for refresh always shows when I'm opening a site or anything I'm doing. It should only show when I pull to refresh, not when I'm doing any kind of site loading thing.

And secondly, the issue that I'm passing through now is that if I tap on a link to open another site, it carries me to the homepage and renders the site behind the homepage stuff. That should be fixed so that if I click on a link that opens another tab, it actually opens the tab correctly."
## 2026-09-24 — Operator instruction #12 (verbatim)
"Please fix it. The button refresh is making me unable to even scroll up in websites. It's not really something I prefer."

## 2026-09-24 — Operator instruction #13 (verbatim)
"The browser doesn't even have a download manager, and it doesn't ask me for storage permission to save files to my downloads folder.
Right now, I can't download files. If I download a file, the browser will say that the file is downloading, and it will say that the file has downloaded, but I don't see anything downloading anywhere. Please just fix that."

## 2026-09-24 — Operator instruction #14 (verbatim)
"The download process should also show in the notification bar. Make sure the download manager has a nice Google Material 3 UI."

## 2026-09-24 — Operator instruction #15 (verbatim)
"Remove the desktop mode and download button from the top bar since it is compressing the search bar."

## 2026-09-24 — Operator instruction #16 (verbatim)
"Also, the app isn't asking for storage permission. I wanted to download a file of 100 megabytes, and once the download started, it finished instantly.

The app the stuff I downloaded was fake or something because it didn't actually download it. So fix it because files are not actually being saved to anywhere."

## 2026-09-25 — Operator instruction #17 (verbatim)
"""
The download feature is completely broken.

The first issue I'm experiencing is that the downloads are showing a fake download size and are not actually downloading. The file I'm trying to download is 3 MB, but it's showing 8 KB and says "download finished" even though I didn't see it downloading. I watched it, and it didn't even show that it was downloading.

Secondly, it doesn't even ask me for storage permission. It only asks me for photos and videos and music permission. It doesn't ask me for actual storage permission.

Also, the notification bar is not showing the progress bar, and I want to be able to pause and resume or cancel the download from the notifications.

Please fix the downloads feature. It's completely broken.
"""
