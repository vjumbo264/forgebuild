package com.forgebuild.themeshowcase

import android.content.Context
import android.content.SharedPreferences
import com.forgebuild.engine.ui.theme.EngineTheme

/**
 * Persists the selected theme system. SharedPreferences only — the app is a
 * purely local demo (no network), so no DataStore dependency is added, in line
 * with the Engine's "store the selection however the app already stores
 * settings" guidance for [EngineTheme].
 */
class ThemeStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("theme_showcase_prefs", Context.MODE_PRIVATE)

    fun load(): EngineTheme =
        prefs.getString(KEY, null)
            ?.let { runCatching { EngineTheme.valueOf(it) }.getOrNull() }
            ?: EngineTheme.MATERIAL

    fun save(theme: EngineTheme) {
        prefs.edit().putString(KEY, theme.name).apply()
    }

    private companion object {
        const val KEY = "selected_theme"
    }
}
