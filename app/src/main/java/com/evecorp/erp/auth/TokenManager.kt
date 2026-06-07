package com.evecorp.erp.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "eve_auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var characterId: Long
        get() = prefs.getLong(KEY_CHARACTER_ID, 0)
        set(value) = prefs.edit().putLong(KEY_CHARACTER_ID, value).apply()

    var characterName: String?
        get() = prefs.getString(KEY_CHARACTER_NAME, null)
        set(value) = prefs.edit().putString(KEY_CHARACTER_NAME, value).apply()

    var corporationId: Long
        get() = prefs.getLong(KEY_CORPORATION_ID, 0)
        set(value) = prefs.edit().putLong(KEY_CORPORATION_ID, value).apply()

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Int) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + expiresIn * 1000L)
            .apply()
    }

    fun refreshAccessToken(): String? {
        // TODO: Implement OAuth token refresh with ESI
        // POST https://login.eveonline.com/v2/oauth/token
        // grant_type=refresh_token&refresh_token={token}
        return null
    }

    fun isLoggedIn(): Boolean = getAccessToken() != null && characterId > 0

    fun logout() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_CHARACTER_ID = "character_id"
        private const val KEY_CHARACTER_NAME = "character_name"
        private const val KEY_CORPORATION_ID = "corporation_id"
    }
}
