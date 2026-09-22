package com.forgebuild.taskflow.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Gemini API keys, stored ONLY on-device in EncryptedSharedPreferences (AES-256
 * GCM via Android Keystore). Keys are transmitted nowhere except directly to
 * Google's Gemini API. Order in the list = failover priority.
 */
class GeminiKeyStore private constructor(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "gemini_keys",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _keys = MutableStateFlow(load())
    val keys: StateFlow<List<String>> = _keys

    private fun load(): List<String> =
        prefs.getString("keys", "").orEmpty().split("\n").filter { it.isNotBlank() }

    private fun save(list: List<String>) {
        prefs.edit().putString("keys", list.joinToString("\n")).apply()
        _keys.value = list
    }

    fun add(key: String) {
        val k = key.trim()
        if (k.isNotEmpty() && k !in _keys.value) save(_keys.value + k)
    }

    fun remove(key: String) = save(_keys.value - key)

    fun moveUp(key: String) {
        val list = _keys.value.toMutableList()
        val i = list.indexOf(key)
        if (i > 0) { list.add(i - 1, list.removeAt(i)); save(list) }
    }

    fun moveDown(key: String) {
        val list = _keys.value.toMutableList()
        val i = list.indexOf(key)
        if (i in 0 until list.size - 1) { list.add(i + 1, list.removeAt(i)); save(list) }
    }

    companion object {
        @Volatile private var instance: GeminiKeyStore? = null
        fun get(context: Context): GeminiKeyStore =
            instance ?: synchronized(this) {
                instance ?: GeminiKeyStore(context.applicationContext).also { instance = it }
            }
    }
}
