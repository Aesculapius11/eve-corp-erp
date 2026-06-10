package com.evecorp.erp.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局认证状态，供 UI 观察 token 过期事件。
 * 拦截器检测到 refresh token 过期时触发 autoLogout。
 */
@Singleton
class AuthStateManager @Inject constructor() {
    private val _tokenExpired = MutableStateFlow(false)

    /** true 时表示需要重新登录 */
    val tokenExpired: StateFlow<Boolean> = _tokenExpired.asStateFlow()

    fun notifyTokenExpired() {
        _tokenExpired.value = true
    }

    fun reset() {
        _tokenExpired.value = false
    }
}
