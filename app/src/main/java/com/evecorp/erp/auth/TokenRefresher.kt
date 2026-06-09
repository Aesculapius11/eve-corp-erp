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
    private var cachedToken: String? = null

    /**
     * 同步刷新 token，供 Interceptor 调用。
     * 用 Mutex 确保并发时只刷新一次。
     */
    fun refreshBlocking(): String? {
        // 快速路径：如果已经有缓存的 token，直接返回
        cachedToken?.let { return it }
        return runBlocking {
            mutex.withLock {
                // 双重检查：拿到锁后再检查一次
                cachedToken?.let { return@withLock it }
                val result = refreshToken()
                cachedToken = result
                result
            }
        }
    }

    /** 清除缓存的 token（登录/登出时调用） */
    fun clearCache() {
        cachedToken = null
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
                    cachedToken = null
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
