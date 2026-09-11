package com.forgebuild.clipforgeandroid.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "clipforge_credentials_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    data class CloneCredentials(
        val pat: String,
        val owner: String,
        val repo: String,
        val login: String
    ) {
        val slug: String get() = "$owner/$repo"
    }

    fun save(creds: CloneCredentials) {
        prefs.edit()
            .putString("pat", creds.pat)
            .putString("owner", creds.owner)
            .putString("repo", creds.repo)
            .putString("login", creds.login)
            .apply()
    }

    fun load(): CloneCredentials? {
        val pat = prefs.getString("pat", null) ?: return null
        val owner = prefs.getString("owner", null) ?: return null
        val repo = prefs.getString("repo", null) ?: return null
        val login = prefs.getString("login", "") ?: ""
        if (pat.isBlank() || owner.isBlank() || repo.isBlank()) return null
        return CloneCredentials(pat, owner, repo, login)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun hasPromptedStorage(): Boolean = prefs.getBoolean("has_prompted_storage", false)
    fun setPromptedStorage(prompted: Boolean) {
        prefs.edit().putBoolean("has_prompted_storage", prompted).apply()
    }
}
