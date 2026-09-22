package com.forgebuild.taskflow.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "taskflow_settings")

/** Per-type notification toggles (each independently switchable in Settings > Notifications). */
enum class NotifType(val defaultOn: Boolean) {
    DUE(true), OVERDUE(true), DAILY_AGENDA(true), API_KEYS_FAILED(true),
    AGENT_CONFIRMATION(true), APPROACHING_DEADLINE(false), RECURRING_INSTANCE(false)
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class AppSettings private constructor(private val context: Context) {
    private val keyEnabled = { t: NotifType -> booleanPreferencesKey("notif_${t.name.lowercase()}") }
    private val keyTheme = intPreferencesKey("theme_mode")
    private val keyLeadMinutes = intPreferencesKey("deadline_lead_minutes")
    private val keyOnboarded = booleanPreferencesKey("onboarding_done")
    private val keyForegroundActive = booleanPreferencesKey("foreground_service_active")
    private val keyAgendaHour = intPreferencesKey("agenda_hour")

    fun notifEnabled(type: NotifType): Flow<Boolean> =
        context.dataStore.data.map { it[keyEnabled(type)] ?: type.defaultOn }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        ThemeMode.entries.getOrElse(it[keyTheme] ?: 0) { ThemeMode.SYSTEM }
    }
    val deadlineLeadMinutes: Flow<Int> = context.dataStore.data.map { it[keyLeadMinutes] ?: 15 }
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[keyOnboarded] ?: false }
    val foregroundActive: Flow<Boolean> = context.dataStore.data.map { it[keyForegroundActive] ?: true }
    val agendaHour: Flow<Int> = context.dataStore.data.map { it[keyAgendaHour] ?: 8 }

    suspend fun setNotifEnabled(type: NotifType, enabled: Boolean) {
        context.dataStore.edit { it[keyEnabled(type)] = enabled }
    }
    suspend fun setThemeMode(mode: ThemeMode) { context.dataStore.edit { it[keyTheme] = mode.ordinal } }
    suspend fun setDeadlineLeadMinutes(m: Int) { context.dataStore.edit { it[keyLeadMinutes] = m.coerceIn(1, 120) } }
    suspend fun setOnboardingDone() { context.dataStore.edit { it[keyOnboarded] = true } }
    suspend fun setForegroundActive(active: Boolean) { context.dataStore.edit { it[keyForegroundActive] = active } }
    suspend fun setAgendaHour(h: Int) { context.dataStore.edit { it[keyAgendaHour] = h.coerceIn(0, 23) } }

    companion object {
        @Volatile private var instance: AppSettings? = null
        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
    }
}
