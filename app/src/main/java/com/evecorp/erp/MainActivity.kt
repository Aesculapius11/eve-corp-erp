package com.evecorp.erp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.evecorp.erp.auth.EsiAuthManager
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.ui.EveCorpApp
import com.evecorp.erp.ui.theme.EveCorpTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var esiAuthManager: EsiAuthManager

    @Inject
    lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EveCorpTheme {
                EveCorpApp(
                    esiAuthManager = esiAuthManager,
                    tokenManager = tokenManager
                )
            }
        }
    }
}
