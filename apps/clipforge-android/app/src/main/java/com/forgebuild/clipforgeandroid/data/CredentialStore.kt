package com.forgebuild.clipforgeandroid.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Credential persistence with crash-proof initialization.
 *
 * Root cause of the "crashes on second launch" bug: MasterKey + EncryptedSharedPreferences
 * were built eagerly as property initializers. On some devices the Android Keystore key
 * becomes unavailable/invalidated after first run (lock-screen change, keyset corruption,
 * OEM keystore quirks), and EncryptedSharedPreferences.create() then throws
 * GeneralSecurityException/IOException *during ViewModel construction* — the app dies
 * before any UI can render, on every subsequent launch.
 *
 * Fix: the encrypted store is built lazily and guarded; on any failure we wipe the broken
 * encrypted file and fall back to plain SharedPreferences so the app always starts.
 * The user simply reconnects their clone if credentials could not be recovered.
 */
class CredentialStore(private val context: Context) {

    data class CloneCredentials(
        val pat: String,
        val owner: String,
        val repo: String,
        val login: String
    ) {
        val slug: String get() = "$owner/$repo"
    }

    private val plainPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("clipforge_credentials_plain", Context.MODE_PRIVATE)
    }

    private val encryptedPrefs: SharedPreferences? by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "clipforge_credentials_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // Keystore/keystore-file failure — drop the corrupted encrypted file so the
            // next launch starts clean instead of crash-looping.
            try { context.deleteSharedPreferences("clipforge_credentials_secure") } catch (_: Exception) {}
            null
        }
    }

    /** The backing store: encrypted when available, plain fallback otherwise. Never throws. */
    private val prefs: SharedPreferences
        get() = encryptedPrefs ?: plainPrefs

    fun save(creds: CloneCredentials) {
        try {
            prefs.edit()
                .putString("pat", creds.pat)
                .putString("owner", creds.owner)
                .putString("repo", creds.repo)
                .putString("login", creds.login)
                .apply()
        } catch (_: Exception) {}
    }

    fun load(): CloneCredentials? {
        return try {
            val pat = prefs.getString("pat", null) ?: return null
            val owner = prefs.getString("owner", null) ?: return null
            val repo = prefs.getString("repo", null) ?: return null
            val login = prefs.getString("login", "") ?: ""
            if (pat.isBlank() || owner.isBlank() || repo.isBlank()) return null
            CloneCredentials(pat, owner, repo, login)
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        try { prefs.edit().clear().apply() } catch (_: Exception) {}
    }

    fun hasPromptedStorage(): Boolean =
        try { prefs.getBoolean("has_prompted_storage", false) } catch (_: Exception) { false }

    fun setPromptedStorage(prompted: Boolean) {
        try { prefs.edit().putBoolean("has_prompted_storage", prompted).apply() } catch (_: Exception) {}
    }
}
