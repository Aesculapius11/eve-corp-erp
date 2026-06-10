package com.evecorp.erp.data.remote.interceptor

import android.util.Log
import com.evecorp.erp.auth.AuthStateManager
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.auth.TokenRefresher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EsiAuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
    private val tokenRefresher: TokenRefresher,
    private val authStateManager: AuthStateManager
) : Interceptor {
    companion object {
        private const val TAG = "EsiAuthInterceptor"
        const val HEADER_TOKEN_EXPIRED = "X-Token-Expired"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // 先检查 token 是否过期，过期则主动刷新
        if (tokenManager.isTokenExpired() && tokenManager.getRefreshToken() != null) {
            val newToken = tokenRefresher.refreshBlocking()
            if (newToken == null) {
                // refresh token 也过期了，需要重新登录
                Log.e(TAG, "Both access and refresh tokens expired, need re-login")
                return buildExpiredResponse(original)
            }
        }

        val token = tokenManager.getAccessToken()
        if (token == null) {
            return buildExpiredResponse(original)
        }

        val authenticated = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .header("Accept-Language", "zh")
            .build()

        val response = chain.proceed(authenticated)

        // 401 → 尝试刷新后重试
        if (response.code == 401) {
            Log.w(TAG, "Received 401 for ${original.url}, attempting token refresh")
            response.close()
            val refreshed = tokenRefresher.refreshBlocking()
            if (refreshed != null) {
                Log.d(TAG, "Token refreshed successfully, retrying request")
                val retry = original.newBuilder()
                    .header("Authorization", "Bearer $refreshed")
                    .header("Accept-Language", "zh")
                    .build()
                return chain.proceed(retry)
            } else {
                Log.e(TAG, "Token refresh failed, refresh token expired")
                return buildExpiredResponse(original)
            }
        }

        return response
    }

    private fun buildExpiredResponse(original: Request): Response {
        authStateManager.notifyTokenExpired()
        return Response.Builder()
            .request(original)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Token expired, re-login required")
            .header(HEADER_TOKEN_EXPIRED, "true")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
    }
}
