package com.forgebuild.everbrowse

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the keep-alive service after device reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            BrowserKeepAliveService.start(context)
        }
    }
}
