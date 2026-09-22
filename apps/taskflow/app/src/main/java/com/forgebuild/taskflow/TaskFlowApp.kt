package com.forgebuild.taskflow

import android.app.Application
import com.forgebuild.taskflow.notify.NotificationHub

class TaskFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHub.ensureChannels(this)
    }
}
