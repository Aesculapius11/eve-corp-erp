package com.evecorp.erp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.evecorp.erp.ui.EveCorpApp
import com.evecorp.erp.ui.theme.EveCorpTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EveCorpTheme {
                EveCorpApp()
            }
        }
    }
}
