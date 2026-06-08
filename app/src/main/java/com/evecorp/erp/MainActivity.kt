package com.evecorp.erp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.evecorp.erp.auth.EsiAuthManager
import com.evecorp.erp.auth.TokenManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkMode by themeManager.isDarkMode.collectAsState(initial = null)
            EveCorpTheme(darkTheme = isDarkMode) {
                EveCorpApp(
                    esiAuthManager = esiAuthManager,
                    tokenManager = tokenManager,
                    themeManager = themeManager
                )
            }
        }
    }
}
