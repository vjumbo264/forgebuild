package com.forgebuild.taskflow.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.forgebuild.taskflow.util.CrashLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Gemini API keys, stored ONLY on-device in EncryptedSharedPreferences (AES-256
 * GCM via Android Keystore). Keys are transmitted nowhere except directly to
 * Google's Gemini API. Order in the list = failover priority.
 */
class GeminiKeyStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = openEncryptedPrefs(appContext)

    private fun openEncryptedPrefs(ctx: Context): SharedPreferences {
        return runCatching {
            createEncryptedPrefs(ctx)
        }.getOrElse { err ->
            CrashLogger.logHandledException(
                ctx,
                "GeminiKeyStore",
                "EncryptedSharedPreferences initialization failed; resetting keystore entry",
                err
            )
            runCatching { ctx.deleteSharedPreferences("gemini_keys") }
            createEncryptedPrefs(ctx)
        }
    }

    private fun createEncryptedPrefs(ctx: Context): SharedPreferences =
        EncryptedSharedPreferences.create(
            ctx,
            "gemini_keys",
            MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private val _keys = MutableStateFlow(load())
    val keys: StateFlow<List<String>> = _keys

    private fun sanitize(raw: String): String =
        raw.replace(Regex("\\s+"), "").trim('"', '\'')

    private fun load(): List<String> =
        prefs.getString("keys", "")
            .orEmpty()
            .split("\n")
            .map { sanitize(it) }
            .filter { it.isNotEmpty() }
            .distinct()

    private fun save(list: List<String>) {
        val cleaned = list.map { sanitize(it) }.filter { it.isNotEmpty() }.distinct()
        prefs.edit().putString("keys", cleaned.joinToString("\n")).apply()
        _keys.value = cleaned
    }

    fun add(key: String) {
        val k = sanitize(key)
        if (k.isNotEmpty() && k !in _keys.value) save(_keys.value + k)
    }

    fun remove(key: String) {
        val k = sanitize(key)
        save(_keys.value.filterNot { it == key || it == k })
    }

    fun moveUp(key: String) {
        val list = _keys.value.toMutableList()
        val i = list.indexOf(key)
        if (i > 0) {
            list.add(i - 1, list.removeAt(i))
            save(list)
        }
    }

    fun moveDown(key: String) {
        val list = _keys.value.toMutableList()
        val i = list.indexOf(key)
        if (i in 0 until list.size - 1) {
            list.add(i + 1, list.removeAt(i))
            save(list)
        }
    }

    companion object {
        @Volatile private var instance: GeminiKeyStore? = null
        fun get(context: Context): GeminiKeyStore =
            instance ?: synchronized(this) {
                instance ?: GeminiKeyStore(context.applicationContext).also { instance = it }
            }
    }
}
