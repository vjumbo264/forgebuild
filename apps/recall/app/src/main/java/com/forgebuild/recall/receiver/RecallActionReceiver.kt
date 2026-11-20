package com.forgebuild.recall.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.forgebuild.recall.scheduler.RecallController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles Pause / Resume / Stop actions directly from the persistent notification. */
class RecallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appCtx = context.applicationContext
        val action = intent.action ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_PAUSE -> RecallController.pause(appCtx)
                    ACTION_RESUME -> RecallController.startOrResume(appCtx)
                    ACTION_STOP -> RecallController.stop(appCtx)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.forgebuild.recall.action.PAUSE"
        const val ACTION_RESUME = "com.forgebuild.recall.action.RESUME"
        const val ACTION_STOP = "com.forgebuild.recall.action.STOP"
    }
}
