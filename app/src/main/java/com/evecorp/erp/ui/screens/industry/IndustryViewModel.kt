package com.evecorp.erp.ui.screens.industry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.data.local.entity.IndustryJobEntity
import com.evecorp.erp.data.repository.IndustryJobRepository
import com.evecorp.erp.ui.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IndustryUiState(
    val jobs: UiState<List<IndustryJobEntity>> = UiState.Loading,
    val selectedTab: IndustryTab = IndustryTab.ALL,
    val isRefreshing: Boolean = false
)

enum class IndustryTab(val label: String, val activity: String?) {
    ALL("全部", null),
    MANUFACTURING("制造", "manufacturing"),
    INVENTION("发明", "invention"),
    COPYING("拷贝", "copying")
}

@HiltViewModel
class IndustryViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val industryJobRepository: IndustryJobRepository
) : ViewModel() {

    private val corpId: Long get() = tokenManager.corporationId
    private val _selectedTab = MutableStateFlow(IndustryTab.ALL)

    val uiState: StateFlow<IndustryUiState> = combine(
        _selectedTab,
        industryJobRepository.getActiveJobs(corpId)
    ) { tab, allJobs ->
        val filtered = if (tab.activity != null) {
            allJobs.filter { it.activityType == tab.activity }
        } else allJobs
        IndustryUiState(
            jobs = UiState.Success(filtered),
            selectedTab = tab
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IndustryUiState())

    fun selectTab(tab: IndustryTab) {
        _selectedTab.value = tab
    }

    fun refresh() {
        viewModelScope.launch {
            industryJobRepository.syncJobs(corpId)
        }
    }
}
