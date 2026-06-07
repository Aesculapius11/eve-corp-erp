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
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "EsiAuth"
        private const val ESI_AUTH_URL = "https://login.eveonline.com/v2/oauth/authorize"
        private const val ESI_TOKEN_URL = "https://login.eveonline.com/v2/oauth/token"
        private const val ESI_VERIFY_URL = "https://login.eveonline.com/oauth/verify"
        private val SCOPES = listOf(
            "esi-industry.read_corporation_jobs.v1",
            "esi-markets.read_corporation_orders.v1",
            "esi-wallet.read_corporation_wallets.v1",
            "esi-assets.read_corporation_assets.v1",
            "esi-corporations.read_divisions.v1"
        )
    }

    private var currentState: String = ""

    fun getAuthorizationUrl(): Uri {
        currentState = java.util.UUID.randomUUID().toString()
        return Uri.parse(ESI_AUTH_URL).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", BuildConfig.ESI_CALLBACK_URL)
            .appendQueryParameter("client_id", BuildConfig.ESI_CLIENT_ID)
            .appendQueryParameter("scope", SCOPES.joinToString(" "))
            .appendQueryParameter("state", currentState)
            .build()
    }

    fun openLogin() {
        val intent = Intent(Intent.ACTION_VIEW, getAuthorizationUrl())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    suspend fun handleCallback(code: String, state: String): Boolean {
        if (state != currentState) {
            Log.w(TAG, "State mismatch: expected=$currentState, got=$state")
            return false
        }

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
