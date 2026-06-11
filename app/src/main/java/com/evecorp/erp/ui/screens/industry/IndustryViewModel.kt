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

data class IndustryJobWith(
    val job: IndustryJobEntity,
    val blueprintName: String,
    val productName: String,
    val installerName: String
)

data class IndustryUiState(
    val jobs: UiState<List<IndustryJobWith>> = UiState.Loading,
    val selectedTab: IndustryTab = IndustryTab.ALL,
    val isRefreshing: Boolean = false,
    val syncError: String? = null
)

enum class IndustryTab(val label: String, val activities: List<String>?) {
    ALL("全部", null),
    MANUFACTURING("制造", listOf("manufacturing")),
    INVENTION("发明", listOf("invention")),
    COPYING("拷贝", listOf("copying")),
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
    private val _hasSynced = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<IndustryUiState> = combine(
        _selectedTab,
        industryRepository.getActiveJobs(corpId),
        _syncError,
        _hasSynced,
        _isRefreshing
    ) { tab, allJobs, error, hasSynced, isRefreshing ->
        if (!hasSynced || isRefreshing) {
            return@combine IndustryUiState(
                jobs = UiState.Loading,
                selectedTab = tab,
                isRefreshing = isRefreshing,
                syncError = error
            )
        }
        // 解析物品名称
        val withNames = allJobs.map { job ->
            IndustryJobWith(
                job = job,
                blueprintName = industryRepository.getTypeName(job.blueprintTypeId),
                productName = job.productTypeId?.let { industryRepository.getTypeName(it) } ?: "—",
                installerName = industryRepository.getTypeName(job.installerId)
            )
        }
        val filtered = if (tab.activities != null) {
            withNames.filter { it.job.activityType in tab.activities }
        } else withNames
        IndustryUiState(
            jobs = UiState.Success(filtered),
            selectedTab = tab,
            syncError = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IndustryUiState())

    init { refresh() }

    fun selectTab(tab: IndustryTab) {
        _selectedTab.value = tab
    }

    fun refresh() {
        viewModelScope.launch {
            _syncError.value = null
            _isRefreshing.value = true
            val result = industryRepository.syncJobs(corpId)
            _isRefreshing.value = false
            _hasSynced.value = true
            result.onFailure { e ->
                _syncError.value = e.message ?: "同步失败"
            }
        }
    }
}
