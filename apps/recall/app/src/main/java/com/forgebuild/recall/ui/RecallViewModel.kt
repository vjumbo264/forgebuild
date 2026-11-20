package com.forgebuild.recall.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.forgebuild.recall.audio.RecallAudioEngine
import com.forgebuild.recall.data.RecallAppState
import com.forgebuild.recall.data.RecallInterval
import com.forgebuild.recall.data.RecallRepository
import com.forgebuild.recall.data.RecallSound
import com.forgebuild.recall.scheduler.RecallController
import kotlinx.coroutines.flow.StateFlow

class RecallViewModel(application: Application) : AndroidViewModel(application) {
    private val appCtx = application.applicationContext
    private val repo = RecallRepository.get(appCtx)

    val state: StateFlow<RecallAppState> = repo.state

    fun startOrResume() {
        RecallController.startOrResume(appCtx)
    }

    fun pause() {
        RecallController.pause(appCtx)
    }

    fun stop() {
        RecallController.stop(appCtx)
    }

    fun toggleInterval(id: String, enabled: Boolean) {
        repo.toggleInterval(id, enabled)
        RecallController.onIntervalsOrSettingsChanged(appCtx)
    }

    fun addInterval(
        label: String,
        minutes: Int,
        sound: RecallSound,
        volume: Float,
        priority: Int,
    ) {
        repo.addInterval(label, minutes, sound, volume, priority)
        RecallController.onIntervalsOrSettingsChanged(appCtx)
    }

    fun updateInterval(updated: RecallInterval) {
        repo.updateInterval(updated)
        RecallController.onIntervalsOrSettingsChanged(appCtx)
    }

    fun deleteInterval(id: String) {
        repo.deleteInterval(id)
        RecallController.onIntervalsOrSettingsChanged(appCtx)
    }

    fun restoreDefaults() {
        repo.restoreDefaultIntervals()
        RecallController.onIntervalsOrSettingsChanged(appCtx)
    }

    fun setHeadsUpNotifications(enabled: Boolean) {
        repo.setHeadsUpNotifications(enabled)
        RecallController.onIntervalsOrSettingsChanged(appCtx)
    }

    fun setHighPrecisionAlarmClock(enabled: Boolean) {
        repo.setHighPrecisionAlarmClock(enabled)
        RecallController.onIntervalsOrSettingsChanged(appCtx)
    }

    fun markOnboardingDone() {
        repo.markOnboardingDone()
    }

    fun previewSound(sound: RecallSound, volume: Float) {
        RecallAudioEngine.previewAsync(appCtx, sound, volume)
    }

    fun playGlobalTest() {
        RecallAudioEngine.playGlobalTestAsync(appCtx, state.value.intervals)
    }

    fun stopAudio() {
        RecallAudioEngine.stopCurrent()
    }
}
