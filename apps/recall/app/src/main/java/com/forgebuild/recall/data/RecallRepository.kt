package com.forgebuild.recall.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface RecallStorage {
    fun load(): String?
    fun save(raw: String)
}

class InMemoryRecallStorage(initial: String? = null) : RecallStorage {
    @Volatile
    private var data: String? = initial
    override fun load(): String? = data
    override fun save(raw: String) {
        data = raw
    }
}

class SharedPrefsRecallStorage(context: Context) : RecallStorage {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): String? = prefs.getString(KEY_STATE, null)

    override fun save(raw: String) {
        // commit() guarantees synchronous disk durability across Receivers/Services/reboots.
        prefs.edit().putString(KEY_STATE, raw).commit()
    }

    companion object {
        private const val PREFS_NAME = "recall_state_prefs"
        private const val KEY_STATE = "recall_app_state_json"
    }
}

class RecallRepository(private val storage: RecallStorage) {

    private val lock = Any()
    private val _state: MutableStateFlow<RecallAppState> = MutableStateFlow(loadInitialState())
    val state: StateFlow<RecallAppState> = _state.asStateFlow()

    val currentState: RecallAppState
        get() = _state.value

    private fun loadInitialState(): RecallAppState {
        val decoded = RecallJsonCodec.decode(storage.load())
        if (decoded != null) return decoded
        val fresh = RecallAppState()
        storage.save(RecallJsonCodec.encode(fresh))
        return fresh
    }

    private inline fun mutate(transform: (RecallAppState) -> RecallAppState): RecallAppState {
        synchronized(lock) {
            val next = transform(_state.value)
            storage.save(RecallJsonCodec.encode(next))
            _state.value = next
            return next
        }
    }

    fun setStatus(status: RecallStatus): RecallAppState = mutate { it.copy(status = status) }

    fun recordFiredBoundary(boundaryMs: Long): RecallAppState = mutate {
        it.copy(lastFiredBoundaryMs = maxOf(it.lastFiredBoundaryMs, boundaryMs))
    }

    fun resetLastFiredBoundary(): RecallAppState = mutate {
        it.copy(lastFiredBoundaryMs = 0L)
    }

    fun addInterval(
        label: String,
        minutes: Int,
        sound: RecallSound,
        volume: Float,
        priority: Int,
    ): RecallAppState = mutate { current ->
        val cleanMinutes = minutes.coerceIn(1, 1440)
        val cleanLabel = label.trim().ifEmpty { RecallDefaults.defaultLabelForMinutes(cleanMinutes) }
        val newItem = RecallInterval(
            label = cleanLabel,
            minutes = cleanMinutes,
            enabled = true,
            sound = sound,
            volume = volume.coerceIn(0f, 1f),
            priority = priority,
        )
        current.copy(intervals = (current.intervals + newItem).sortedBy { it.minutes })
    }

    fun updateInterval(updated: RecallInterval): RecallAppState = mutate { current ->
        val clean = updated.copy(
            minutes = updated.minutes.coerceIn(1, 1440),
            label = updated.label.trim().ifEmpty { RecallDefaults.defaultLabelForMinutes(updated.minutes.coerceIn(1, 1440)) },
            volume = updated.volume.coerceIn(0f, 1f),
        )
        current.copy(
            intervals = current.intervals.map { if (it.id == clean.id) clean else it }
                .sortedBy { it.minutes }
        )
    }

    fun toggleInterval(id: String, enabled: Boolean): RecallAppState = mutate { current ->
        current.copy(
            intervals = current.intervals.map { if (it.id == id) it.copy(enabled = enabled) else it }
        )
    }

    fun deleteInterval(id: String): RecallAppState = mutate { current ->
        current.copy(intervals = current.intervals.filterNot { it.id == id })
    }

    fun restoreDefaultIntervals(): RecallAppState = mutate { current ->
        current.copy(intervals = RecallDefaults.defaultIntervals())
    }

    fun setHeadsUpNotifications(enabled: Boolean): RecallAppState = mutate {
        it.copy(headsUpNotificationsEnabled = enabled)
    }

    fun setHighPrecisionAlarmClock(enabled: Boolean): RecallAppState = mutate {
        it.copy(highPrecisionAlarmClockMode = enabled)
    }

    fun markOnboardingDone(): RecallAppState = mutate {
        it.copy(onboardingDone = true)
    }

    companion object {
        @Volatile
        private var instance: RecallRepository? = null

        fun get(context: Context): RecallRepository =
            instance ?: synchronized(this) {
                instance ?: RecallRepository(SharedPrefsRecallStorage(context)).also { instance = it }
            }
    }
}
