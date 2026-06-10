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
     * 同步刷新 token，供 Interceptor 调用。
     * 用 Mutex 确保并发时只刷新一次。
     */
    fun refreshBlocking(): String? {
        return runBlocking {
            mutex.withLock {
                // 检查是否仍然过期（可能在等待锁期间已被其他线程刷新）
                if (!tokenManager.isTokenExpired()) {
                    return@withLock tokenManager.getAccessToken()
                }
                val result = refreshToken()
                // refreshToken 内部已调用 tokenManager.saveTokens，所以直接返回最新 token
                result?.let { tokenManager.getAccessToken() }
            }
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

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body!!.string())
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
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TOKEN_URL = "https://login.eveonline.com/v2/oauth/token"
    }
}
