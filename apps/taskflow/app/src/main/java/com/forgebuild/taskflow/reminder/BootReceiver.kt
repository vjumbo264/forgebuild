package com.forgebuild.taskflow.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.forgebuild.taskflow.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Re-arm all exact alarms after a reboot; restart the reminder service if enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler(context).rescheduleAll()
                if (AppSettings.get(context).foregroundActive.first()) DueForegroundService.start(context)
            } finally { pending.finish() }
        }
    }
}
