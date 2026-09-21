package com.forgebuild.clipforgeandroid.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * On-device settings cache (operator 2026-09-20, fix 1).
 *
 * The New Video wizard must render with the operator's ACTUAL last-known
 * settings INSTANTLY on open — no loading gap where code defaults are shown and
 * then silently swapped out when the slow clone fetch lands (the bug that turned
 * a chosen 30s target into a rendered 120s video).
 *
 * Contract:
 *  - [loadWizardDefaults] is synchronous and network-free; the ViewModel reads it
 *    at init so the very first composition of the wizard already shows last-known
 *    values.
 *  - loadSettings() writes every successful background fetch here via
 *    [saveFromLoadedSettings] — this updates the cache for NEXT time and may
 *    refresh wizard fields the operator has NOT touched, but the wizard's
 *    per-field explicit-choice flags guarantee a late refresh never overwrites
 *    an in-progress choice.
 *  - An explicit wizard choice ([saveDurationChoice]) becomes the last-known
 *    value immediately.
 */
class SettingsCache(context: Context) {

    private val file: File = File(context.filesDir, "settings_cache.json")

    /** Last-known values the New Video wizard seeds from. */
    data class WizardDefaults(
        val durationSeconds: Int = 120,
        val series: Boolean = false,
        val superSeries: Boolean = false,
        val musicPath: String? = null,
    )

    /** Synchronous, network-free, never throws. Empty cache -> code defaults. */
    fun loadWizardDefaults(): WizardDefaults {
        val j = read() ?: return WizardDefaults()
        val series = j.optBoolean(KEY_SERIES, false)
        return WizardDefaults(
            durationSeconds = j.optInt(KEY_DURATION, 120).coerceIn(1, 36000),
            series = series,
            superSeries = series && j.optBoolean(KEY_SUPER, false),
            musicPath = j.optString(KEY_MUSIC, "").ifBlank { null },
        )
    }

    /** Persist a successful background settings fetch for the next wizard open. */
    fun saveFromLoadedSettings(series: Boolean, superSeries: Boolean, musicPath: String?) {
        val j = read() ?: JSONObject()
        j.put(KEY_SERIES, series)
        j.put(KEY_SUPER, superSeries)
        if (musicPath != null) j.put(KEY_MUSIC, musicPath) else j.remove(KEY_MUSIC)
        write(j)
    }

    /** An explicit in-wizard duration choice — the strongest last-known signal. */
    fun saveDurationChoice(seconds: Int) {
        if (seconds !in 1..36000) return
        val j = read() ?: JSONObject()
        j.put(KEY_DURATION, seconds)
        write(j)
    }

    /**
     * An explicit in-wizard series/super-series choice (round-12 fix 1): persisted
     * the INSTANT it is made — not at submit time — so a recreated screen or cold
     * start reseeds from the operator's real choice, never a stale default.
     */
    fun saveSeriesChoice(series: Boolean, superSeries: Boolean) {
        val j = read() ?: JSONObject()
        j.put(KEY_SERIES, series)
        j.put(KEY_SUPER, series && superSeries)
        write(j)
    }

    /** An explicit in-wizard music choice, persisted the instant it is made. */
    fun saveMusicChoice(path: String?) {
        val j = read() ?: JSONObject()
        if (path != null) j.put(KEY_MUSIC, path) else j.remove(KEY_MUSIC)
        write(j)
    }

    fun clear() {
        try { file.delete() } catch (_: Exception) {}
    }

    private fun read(): JSONObject? = try {
        if (file.exists()) JSONObject(file.readText()) else null
    } catch (_: Exception) { null }

    private fun write(j: JSONObject) {
        try { file.writeText(j.toString()) } catch (_: Exception) {}
    }

    private companion object {
        const val KEY_DURATION = "last_duration_seconds"
        const val KEY_SERIES = "series_default"
        const val KEY_SUPER = "super_series_default"
        const val KEY_MUSIC = "default_music"
    }
}
