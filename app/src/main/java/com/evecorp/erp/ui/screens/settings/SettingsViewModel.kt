package com.evecorp.erp.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.ui.theme.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val characterName: String = "",
    val characterId: Long = 0,
    val corporationId: Long = 0,
    val isLoading: Boolean = true,
    val isDarkMode: Boolean? = null,
    val followSystem: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val themeManager: ThemeManager
) : ViewModel() {

    private val _loaded = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        themeManager.isDarkMode,
        themeManager.followSystem,
        _loaded
    ) { darkMode, followSystem, loaded ->
        SettingsUiState(
            characterName = tokenManager.characterName ?: "未知",
            characterId = tokenManager.characterId,
            corporationId = tokenManager.corporationId,
            isLoading = !loaded,
            isDarkMode = darkMode,
            followSystem = followSystem
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    init {
        _loaded.value = true
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { themeManager.setDarkMode(enabled) }
    }

    fun setFollowSystem(enabled: Boolean) {
        viewModelScope.launch { themeManager.setFollowSystem(enabled) }
    }

    fun logout() {
        tokenManager.logout()
    }
}
