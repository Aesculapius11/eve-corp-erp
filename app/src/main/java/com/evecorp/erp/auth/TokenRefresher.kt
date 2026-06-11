package com.evecorp.erp.auth

import com.evecorp.erp.BuildConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val mutex = Mutex()

    /**
     * 挂起版本刷新 token，供协程调用（推荐）。
     */
    suspend fun refresh(): String? {
        return mutex.withLock {
            if (!tokenManager.isTokenExpired()) {
                return@withLock tokenManager.getAccessToken()
            }
            val result = refreshToken()
            result?.let { tokenManager.getAccessToken() }
        }
    }

    /**
     * 同步刷新 token，仅供 Interceptor 等无法挂起的场景调用。
     */
    fun refreshBlocking(): String? {
        return runBlocking {
            refresh()
        }
    }

    /** 清除缓存的 token（登录/登出时调用） */
    fun clearCache() {
        // 缓存已移除，此方法保留以保持接口兼容
    }

    private suspend fun refreshToken(): String? {
        val refreshToken = tokenManager.getRefreshToken() ?: return null

        return try {
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

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: return@use null
                    val json = JSONObject(bodyStr)
                    val newAccess = json.getString("access_token")
                    val newRefresh = json.getString("refresh_token")
                    val expiresIn = json.getInt("expires_in")

                    tokenManager.saveTokens(newAccess, newRefresh, expiresIn)
                    newAccess
                } else {
                    if (response.code == 400 || response.code == 401) {
                        tokenManager.logout()
                    }
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TOKEN_URL = "https://login.eveonline.com/v2/oauth/token"
    }
}
