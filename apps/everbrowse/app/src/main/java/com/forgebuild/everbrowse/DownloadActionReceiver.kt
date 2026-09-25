package com.forgebuild.everbrowse

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver for handling interactive notification actions:
 * Pause, Resume, and Cancel download from the Android notification bar.
 */
class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return
        when (intent.action) {
            ACTION_PAUSE_DOWNLOAD -> {
                DownloadCoordinator.pauseDownload(context, downloadId)
            }
            ACTION_RESUME_DOWNLOAD -> {
                DownloadCoordinator.resumeDownload(context, downloadId)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                DownloadCoordinator.cancelDownload(context, downloadId)
            }
        }
    }

    companion object {
        const val ACTION_PAUSE_DOWNLOAD = "com.forgebuild.everbrowse.ACTION_PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "com.forgebuild.everbrowse.ACTION_RESUME_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.forgebuild.everbrowse.ACTION_CANCEL_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "download_id"
    }
}
