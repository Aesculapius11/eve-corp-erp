package com.evecorp.erp.data.remote.interceptor

import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.auth.TokenRefresher
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EsiAuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
    private val tokenRefresher: TokenRefresher
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // 先检查 token 是否过期，过期则主动刷新
        if (tokenManager.isTokenExpired() && tokenManager.getRefreshToken() != null) {
            tokenRefresher.refreshBlocking()
        }

        val token = tokenManager.getAccessToken() ?: return chain.proceed(original)

        val authenticated = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .header("Accept-Language", "zh")
            .build()

        val response = chain.proceed(authenticated)

        // 401 → 尝试刷新后重试
        if (response.code == 401) {
            response.close()
            val refreshed = tokenRefresher.refreshBlocking()
            if (refreshed != null) {
                val retry = original.newBuilder()
                    .header("Authorization", "Bearer $refreshed")
                    .header("Accept-Language", "zh")
                    .build()
                return chain.proceed(retry)
            }
        }

        return response
    }
}
