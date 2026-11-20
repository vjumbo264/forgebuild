package com.forgebuild.recall

import android.app.Application
import com.forgebuild.recall.data.RecallRepository
import com.forgebuild.recall.data.RecallStatus
import com.forgebuild.recall.notify.RecallNotificationHub
import com.forgebuild.recall.scheduler.RecallController
import com.forgebuild.recall.util.RecallDiagnostics

class RecallApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RecallDiagnostics.installCrashHandler(this)
        RecallNotificationHub.ensureChannels(this)
        val state = RecallRepository.get(this).currentState
        if (state.status == RecallStatus.ACTIVE || state.status == RecallStatus.PAUSED) {
            RecallController.onSystemRecovery(this, reason = "APP_PROCESS_START", resetDedup = false)
        }
    }
}
