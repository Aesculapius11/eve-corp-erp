package com.evecorp.erp.ui.screens.industry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.data.local.entity.IndustryJobEntity
import com.evecorp.erp.data.repository.IndustryRepository
import com.evecorp.erp.ui.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IndustryUiState(
    val jobs: UiState<List<IndustryJobEntity>> = UiState.Loading,
    val selectedTab: IndustryTab = IndustryTab.ALL,
    val isRefreshing: Boolean = false,
    val syncError: String? = null
)

enum class IndustryTab(val label: String, val activities: List<String>?) {
    ALL("全部", null),
    MANUFACTURING("制造", listOf("manufacturing")),
    INVENTION("发明", listOf("invention")),
    RESEARCH("研究", listOf("researching_time_efficiency", "researching_material_efficiency"))
}

@HiltViewModel
class IndustryViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val industryRepository: IndustryRepository
) : ViewModel() {

    private val corpId: Long get() = tokenManager.corporationId
    private val _selectedTab = MutableStateFlow(IndustryTab.ALL)
    private val _syncError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<IndustryUiState> = combine(
        _selectedTab,
        industryRepository.getActiveJobs(corpId),
        _syncError
    ) { tab, allJobs, error ->
        val filtered = if (tab.activities != null) {
            allJobs.filter { it.activityType in tab.activities }
        } else allJobs
        IndustryUiState(
            jobs = UiState.Success(filtered),
            selectedTab = tab,
            syncError = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IndustryUiState())

    fun selectTab(tab: IndustryTab) {
        _selectedTab.value = tab
    }

    fun refresh() {
        viewModelScope.launch {
            _syncError.value = null
            val result = industryRepository.syncJobs(corpId)
            result.onFailure { e ->
                _syncError.value = e.message ?: "同步失败"
            }
        }
    }
}
