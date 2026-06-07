package com.evecorp.erp.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.evecorp.erp.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class EsiAuthActivity : ComponentActivity() {

    @Inject
    lateinit var esiAuthManager: EsiAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent?.data

        if (data == null) {
            showErrorAndFinish("授权回调为空")
            return
        }

        val error = data.getQueryParameter("error")
        if (error != null) {
            val desc = data.getQueryParameter("error_description") ?: error
            showErrorAndFinish("授权被拒绝：$desc")
            return
        }

        val code = data.getQueryParameter("code")
        val state = data.getQueryParameter("state")

        if (code == null || state == null) {
            showErrorAndFinish("回调参数缺失（code 或 state）")
            return
        }

        lifecycleScope.launch {
            val success = esiAuthManager.handleCallback(code, state)
            if (success) {
                val mainIntent = Intent(this@EsiAuthActivity, MainActivity::class.java)
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(mainIntent)
                finish()
            } else {
                showErrorAndFinish("登录失败：token 交换或验证失败，请重试")
            }
        }
    }

    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
