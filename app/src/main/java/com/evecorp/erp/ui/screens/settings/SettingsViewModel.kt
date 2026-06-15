package com.evecorp.erp.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.data.local.DashboardPreferences
import com.evecorp.erp.notification.AlertCheckReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val characterName: String = "",
    val characterId: Long = 0,
    val corporationId: Long = 0,
    val isLoading: Boolean = true,
    val syncInterval: Int = DashboardPreferences.DEFAULT_SYNC_INTERVAL,
    val alertInterval: Int = DashboardPreferences.DEFAULT_ALERT_INTERVAL
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val tokenManager: TokenManager,
    private val dashboardPreferences: DashboardPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = SettingsUiState(
                characterName = tokenManager.characterName ?: "未知",
                characterId = tokenManager.characterId,
                corporationId = tokenManager.corporationId,
                isLoading = false,
                syncInterval = dashboardPreferences.getSyncIntervalMinutes(),
                alertInterval = dashboardPreferences.getAlertIntervalMinutes()
            )
        }
    }

    fun setSyncInterval(minutes: Int) {
        dashboardPreferences.setSyncIntervalMinutes(minutes)
        _uiState.value = _uiState.value.copy(syncInterval = minutes)
        AlertCheckReceiver.scheduleNextSync(appContext)
    }

    fun setAlertInterval(minutes: Int) {
        dashboardPreferences.setAlertIntervalMinutes(minutes)
        _uiState.value = _uiState.value.copy(alertInterval = minutes)
        AlertCheckReceiver.scheduleNextCheck(appContext)
    }

    fun logout() {
        tokenManager.logout()
    }
}
