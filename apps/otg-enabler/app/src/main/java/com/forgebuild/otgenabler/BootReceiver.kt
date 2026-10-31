package com.forgebuild.otgenabler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            val prefs = context.getSharedPreferences("otg_enabler_prefs", Context.MODE_PRIVATE)
            val autoEnable = prefs.getBoolean("auto_enable_on_boot", false)
            val wasEnabled = prefs.getBoolean("is_otg_enabled", false)

            if (autoEnable && wasEnabled) {
                val controller = OtgController(context.applicationContext)
                CoroutineScope(Dispatchers.IO).launch {
                    RootCommander.log(LogType.INFO, "Reapplying USB OTG Host mode after boot completion...")
                    controller.enableOtg()
                }
            }
        }
    }
}
