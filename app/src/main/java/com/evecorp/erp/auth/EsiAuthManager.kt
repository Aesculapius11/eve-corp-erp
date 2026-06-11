package com.evecorp.erp.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.evecorp.erp.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EsiAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager,
    @javax.inject.Named("plain") private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "EsiAuth"
        private const val ESI_AUTH_URL = "https://login.eveonline.com/v2/oauth/authorize"
        private const val ESI_TOKEN_URL = "https://login.eveonline.com/v2/oauth/token"
        private const val ESI_VERIFY_URL = "https://login.eveonline.com/oauth/verify"
        private const val PREFS_NAME = "esi_auth_state"
        private const val KEY_STATE = "oauth_state"
        private val SCOPES = listOf(
            // 角色
            "esi-characters.read_corporation_roles.v1",
            "esi-characters.read_standings.v1",
            "esi-characters.read_titles.v1",
            // 军团工业
            "esi-industry.read_corporation_jobs.v1",
            "esi-industry.read_corporation_mining.v1",
            // 军团市场
            "esi-markets.read_corporation_orders.v1",
            // 个人市场
            "esi-markets.read_character_orders.v1",
            // 军团钱包
            "esi-wallet.read_corporation_wallets.v1",
            // 军团资产
            "esi-assets.read_corporation_assets.v1",
            // 军团信息
            "esi-corporations.read_corporation_membership.v1",
            "esi-corporations.read_divisions.v1",
            "esi-corporations.read_blueprints.v1",
            "esi-corporations.read_contacts.v1",
            "esi-corporations.read_container_logs.v1",
            "esi-corporations.read_facilities.v1",
            "esi-corporations.read_medals.v1",
            "esi-corporations.read_standings.v1",
            "esi-corporations.read_starbases.v1",
            "esi-corporations.read_structures.v1",
            "esi-corporations.read_titles.v1",
            "esi-corporations.track_members.v1",
            // 军团合同
            "esi-contracts.read_corporation_contracts.v1",
            // 军团击� mail
            "esi-killmails.read_corporation_killmails.v1",
            // 建筑
            "esi-universe.read_structures.v1"
        )
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAuthorizationUrl(): Uri {
        val state = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_STATE, state).apply()
        return Uri.parse(ESI_AUTH_URL).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", BuildConfig.ESI_CALLBACK_URL)
            .appendQueryParameter("client_id", BuildConfig.ESI_CLIENT_ID)
            .appendQueryParameter("scope", SCOPES.joinToString(" "))
            .appendQueryParameter("state", state)
            .build()
    }

    fun openLogin() {
        val intent = Intent(Intent.ACTION_VIEW, getAuthorizationUrl())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * 确保 token 有效，过期则自动刷新
     * @return true if token is valid after this call
     */
    suspend fun ensureValidToken(): Boolean {
        if (!tokenManager.isLoggedIn()) return false
        if (!tokenManager.isTokenExpired()) return true
        return refreshToken()
    }

    private suspend fun refreshToken(): Boolean {
        val refreshToken = tokenManager.getRefreshToken() ?: return false
        return withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .add("client_id", BuildConfig.ESI_CLIENT_ID)
                    .build()
                val request = Request.Builder()
                    .url(ESI_TOKEN_URL)
                    .post(body)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body!!.string())
                    tokenManager.saveTokens(
                        json.getString("access_token"),
                        json.getString("refresh_token"),
                        json.getInt("expires_in")
                    )
                    true
                } else {
                    Log.e(TAG, "Token refresh failed: ${response.code}")
                    tokenManager.logout()
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token refresh exception", e)
                false
            }
        }
    }

    suspend fun handleCallback(code: String, state: String): Boolean {
        val savedState = prefs.getString(KEY_STATE, null)
        if (state != savedState) {
            Log.w(TAG, "State mismatch: expected=$savedState, got=$state")
            return false
        }
        // 清除已使用的 state
        prefs.edit().remove(KEY_STATE).apply()

        return withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("client_id", BuildConfig.ESI_CLIENT_ID)
                    .build()

                val request = Request.Builder()
                    .url(ESI_TOKEN_URL)
                    .post(body)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body!!.string())
                    val accessToken = json.getString("access_token")
                    val refreshToken = json.getString("refresh_token")
                    val expiresIn = json.getInt("expires_in")

                    tokenManager.saveTokens(accessToken, refreshToken, expiresIn)

                    // Verify and get character info
                    verifyToken(accessToken)
                    true
                } else {
                    Log.e(TAG, "Token exchange failed: ${response.code} ${response.body?.string()}")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleCallback exception", e)
                false
            }
        }
    }

    private suspend fun verifyToken(token: String) {
        withContext(Dispatchers.IO) {
            try {
                val verifyRequest = Request.Builder()
                    .url(ESI_VERIFY_URL)
                    .header("Authorization", "Bearer $token")
                    .build()

                val verifyResponse = okHttpClient.newCall(verifyRequest).execute()
                if (verifyResponse.isSuccessful) {
                    val verifyJson = JSONObject(verifyResponse.body!!.string())
                    val charId = verifyJson.getLong("CharacterID")
                    val charName = verifyJson.getString("CharacterName")

                    tokenManager.characterId = charId
                    tokenManager.characterName = charName

                    // 获取 corporation_id
                    fetchCorporationId(token, charId)
                }
            } catch (_: Exception) { }
        }
    }

    private fun fetchCorporationId(token: String, characterId: Long) {
        try {
            val charUrl = "https://esi.evetech.net/latest/characters/$characterId/"
            val request = Request.Builder()
                .url(charUrl)
                .header("Authorization", "Bearer $token")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body!!.string())
                tokenManager.corporationId = json.getLong("corporation_id")
            } else {
                Log.e(TAG, "fetchCorporationId failed: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchCorporationId exception", e)
        }
    }
}
