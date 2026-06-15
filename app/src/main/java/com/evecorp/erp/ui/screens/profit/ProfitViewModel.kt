package com.evecorp.erp.ui.screens.profit

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

private const val ProfitUrl = "http://www.ceve-industry.cn/#/"
private const val ProfitLogTag = "ProfitGeckoView"

@HiltViewModel
class ProfitViewModel @Inject constructor(
    @ApplicationContext context: Context
) : ViewModel() {

    private val runtime = ProfitGeckoRuntimeHolder.get(context)
    private val session = GeckoSession().apply {
        settings.userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        settings.viewportMode = GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
        settings.useTrackingProtection = false
        settings.suspendMediaWhenInactive = false
        open(runtime)
        setNavigationDelegate(ProfitNavigationDelegate())
        setProgressDelegate(ProfitProgressDelegate())
        setPromptDelegate(ProfitPromptDelegate())
        loadUri(ProfitUrl)
    }

    private val _progress = MutableStateFlow(100)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    private val _prompt = MutableStateFlow<ProfitPromptState?>(null)
    val prompt: StateFlow<ProfitPromptState?> = _prompt.asStateFlow()

    fun attachTo(geckoView: GeckoView) {
        if (geckoView.session !== session) {
            geckoView.setSession(session)
        }
        session.setActive(true)
        session.setFocused(true)
    }

    fun setVisible(visible: Boolean) {
        session.setActive(visible)
        session.setFocused(visible)
    }

    fun reload() {
        session.reload()
    }

    fun goBack() {
        if (_canGoBack.value) {
            session.goBack()
        }
    }

    fun goForward() {
        if (_canGoForward.value) {
            session.goForward()
        }
    }

    fun dismissPrompt() {
        _prompt.value?.onDismiss?.invoke()
    }

    fun confirmPrompt() {
        _prompt.value?.onConfirm?.invoke()
    }

    override fun onCleared() {
        session.close()
        super.onCleared()
    }

    private inner class ProfitNavigationDelegate : GeckoSession.NavigationDelegate {
        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            _canGoBack.value = canGoBack
        }

        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
            _canGoForward.value = canGoForward
        }
    }

    private inner class ProfitProgressDelegate : GeckoSession.ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
            _progress.value = 0
            Log.d(ProfitLogTag, "PageStart $url")
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) {
            _progress.value = progress
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            _progress.value = 100
            Log.d(ProfitLogTag, "PageStop success=$success")
        }
    }

    private inner class ProfitPromptDelegate : GeckoSession.PromptDelegate {
        override fun onAlertPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.AlertPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            _prompt.value = ProfitPromptState(
                title = prompt.title,
                message = prompt.message.orEmpty(),
                confirmLabel = "确定",
                dismissLabel = null,
                onConfirm = {
                    _prompt.value = null
                    result.complete(prompt.dismiss())
                },
                onDismiss = {
                    _prompt.value = null
                    result.complete(prompt.dismiss())
                }
            )
            return result
        }

        override fun onButtonPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.ButtonPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            _prompt.value = ProfitPromptState(
                title = prompt.title,
                message = prompt.message.orEmpty(),
                confirmLabel = "确定",
                dismissLabel = "取消",
                onConfirm = {
                    _prompt.value = null
                    result.complete(
                        prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE)
                    )
                },
                onDismiss = {
                    _prompt.value = null
                    result.complete(
                        prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE)
                    )
                }
            )
            return result
        }
    }
}

data class ProfitPromptState(
    val title: String?,
    val message: String,
    val confirmLabel: String,
    val dismissLabel: String?,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit
)

private object ProfitGeckoRuntimeHolder {
    @Volatile
    private var runtime: GeckoRuntime? = null

    fun get(context: Context): GeckoRuntime {
        return runtime ?: synchronized(this) {
            runtime ?: GeckoRuntime.create(context.applicationContext).also { created ->
                runtime = created
            }
        }
    }
}
