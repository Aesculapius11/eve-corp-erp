package com.evecorp.erp.auth

import com.evecorp.erp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class TokenRefresher @Inject constructor(
    private val tokenManager: TokenManager,
    @Named("plain") private val okHttpClient: OkHttpClient
) {
    /**
     * 同步刷新 token，供 Interceptor 调用。
     * 使用 runBlocking 因为 OkHttp Interceptor 是同步的。
     */
    fun refreshBlocking(): String? = runBlocking {
        refreshToken()
    }

    private suspend fun refreshToken(): String? {
        val refreshToken = tokenManager.getRefreshToken() ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .add("client_id", BuildConfig.ESI_CLIENT_ID)
                    .build()

                val request = Request.Builder()
                    .url(TOKEN_URL)
                    .post(body)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body!!.string())
                    val newAccess = json.getString("access_token")
                    val newRefresh = json.getString("refresh_token")
                    val expiresIn = json.getInt("expires_in")

                    tokenManager.saveTokens(newAccess, newRefresh, expiresIn)
                    newAccess
                } else {
                    // Refresh token 失效，清除登录态
                    if (response.code == 400 || response.code == 401) {
                        tokenManager.logout()
                    }
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    companion object {
        private const val TOKEN_URL = "https://login.eveonline.com/v2/oauth/token"
    }
}
