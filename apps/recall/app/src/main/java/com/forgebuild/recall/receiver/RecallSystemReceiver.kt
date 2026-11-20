package com.forgebuild.recall.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.forgebuild.recall.scheduler.RecallController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores RECALL's clock-synchronized schedule after device reboot, app update,
 * system time changes, timezone changes, or exact-alarm permission changes.
 */
class RecallSystemReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isHandled = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        if (!isHandled) return

        val appCtx = context.applicationContext
        val resetDedup = action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RecallController.onSystemRecovery(appCtx, reason = action, resetDedup = resetDedup)
            } finally {
                pending.finish()
            }
        }
    }
}
