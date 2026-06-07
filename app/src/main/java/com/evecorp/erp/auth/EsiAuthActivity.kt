package com.evecorp.erp.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class EsiAuthActivity : ComponentActivity() {

    @Inject
    lateinit var esiAuthManager: EsiAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent?.data ?: run {
            finish()
            return
        }

        val code = data.getQueryParameter("code")
        val state = data.getQueryParameter("state")

        if (code == null || state == null) {
            finish()
            return
        }

        lifecycleScope.launch {
            val success = esiAuthManager.handleCallback(code, state)
            // Navigate to main activity
            val mainIntent = Intent(this@EsiAuthActivity, com.evecorp.erp.MainActivity::class.java)
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(mainIntent)
            finish()
        }
    }
}
