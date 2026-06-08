package com.evecorp.erp.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.data.remote.api.EveEsiApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val characterName: String = "",
    val characterId: Long = 0,
    val corporationName: String = "",
    val corporationId: Long = 0,
    val allianceName: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val esiApi: EveEsiApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadInfo()
    }

    private fun loadInfo() {
        viewModelScope.launch {
            val charId = tokenManager.characterId
            val charName = tokenManager.characterName ?: "未知"
            val corpId = tokenManager.corporationId

            var corpName = ""
            var allianceName: String? = null

            try {
                if (corpId > 0) {
                    val corp = esiApi.getCorporation(corpId)
                    corpName = corp.name
                    corp.allianceId?.let { aid ->
                        try {
                            val alliance = esiApi.getAlliance(aid)
                            allianceName = alliance.name
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}

            _uiState.value = SettingsUiState(
                characterName = charName,
                characterId = charId,
                corporationName = corpName,
                corporationId = corpId,
                allianceName = allianceName,
                isLoading = false
            )
        }
    }

    fun logout() {
        tokenManager.logout()
    }
}
