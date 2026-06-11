package com.evecorp.erp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.evecorp.erp.auth.AuthStateManager
import com.evecorp.erp.auth.EsiAuthManager
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.sync.KeepAliveService
import com.evecorp.erp.ui.EveCorpApp
import com.evecorp.erp.ui.theme.EveCorpTheme
import com.evecorp.erp.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var esiAuthManager: EsiAuthManager
    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var authStateManager: AuthStateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureServiceRunning()

        setContent {
            val isDarkMode by themeManager.isDarkMode.collectAsState(initial = null)
            EveCorpTheme(darkTheme = isDarkMode) {
                EveCorpApp(
                    esiAuthManager = esiAuthManager,
                    tokenManager = tokenManager,
                    themeManager = themeManager,
                    authStateManager = authStateManager
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到前台都检查服务是否在运行
        ensureServiceRunning()
    }

    private fun ensureServiceRunning() {
        if (tokenManager.isLoggedIn()) {
            KeepAliveService.start(this)
        }
    }
}
