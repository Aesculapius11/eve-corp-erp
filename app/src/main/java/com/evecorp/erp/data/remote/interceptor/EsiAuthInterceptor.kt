package com.evecorp.erp.data.remote.interceptor

import com.evecorp.erp.auth.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EsiAuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenManager.getAccessToken() ?: return chain.proceed(original)

        val authenticated = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .header("Accept-Language", "zh")
            .build()

        val response = chain.proceed(authenticated)

        // Handle token expiration
        if (response.code == 401) {
            val refreshed = tokenManager.refreshAccessToken()
            if (refreshed != null) {
                val retry = original.newBuilder()
                    .header("Authorization", "Bearer $refreshed")
                    .header("Accept-Language", "zh")
                    .build()
                response.close()
                return chain.proceed(retry)
            }
        }

        return response
    }
}
