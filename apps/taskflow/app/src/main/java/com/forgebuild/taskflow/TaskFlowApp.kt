package com.forgebuild.taskflow

import android.app.Application
import com.forgebuild.taskflow.notify.NotificationHub
import com.forgebuild.taskflow.reminder.DueForegroundService

class TaskFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHub.ensureChannels(this)
        // Ensure persistent low-priority background notification is running
        DueForegroundService.start(this)
    }
}
