package com.forgebuild.everbrowse

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * EverBrowse device-admin receiver. Granting device-admin gives the app a
 * higher keep-alive standing (the system is more reluctant to kill device
 * admins) and unlocks the lock-screen policy declared in res/xml/device_admin.xml.
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "EverBrowse admin keep-alive enabled", Toast.LENGTH_SHORT).show()
        BrowserKeepAliveService.start(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "EverBrowse admin keep-alive disabled", Toast.LENGTH_SHORT).show()
    }
}
