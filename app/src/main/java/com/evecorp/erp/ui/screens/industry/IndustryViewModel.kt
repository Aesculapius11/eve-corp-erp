package com.evecorp.erp.ui.screens.industry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.data.local.DashboardPreferences
import com.evecorp.erp.data.local.entity.IndustryJobEntity
import com.evecorp.erp.data.repository.IndustryRepository
import com.evecorp.erp.sync.EsiRefreshPolicy
import com.evecorp.erp.sync.RefreshDomain
import com.evecorp.erp.ui.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class IndustryJobWith(
    val job: IndustryJobEntity,
    val blueprintName: String,
    val productName: String,
    val installerName: String
)

data class IndustryUiState(
    val jobs: UiState<List<IndustryJobWith>> = UiState.Loading,
    val selectedTab: IndustryTab = IndustryTab.ALL,
    val selectedStatus: String? = null,
    val selectedInstaller: String? = null,
    val availableInstallers: List<String> = emptyList(),
    val allJobsWithNames: List<IndustryJobWith> = emptyList(),
    val isRefreshing: Boolean = false,
    val syncError: String? = null,
    val refreshNotice: String? = null
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
    private val industryRepository: IndustryRepository,
    private val dashboardPreferences: DashboardPreferences
) : ViewModel() {

    private val corpId: Long get() = tokenManager.corporationId
    private val _selectedTab = MutableStateFlow(IndustryTab.ALL)
    private val _selectedStatus = MutableStateFlow<String?>(null)
    private val _selectedInstaller = MutableStateFlow<String?>(null)
    private val _syncError = MutableStateFlow<String?>(null)
    private val _hasSynced = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _refreshNotice = MutableStateFlow<String?>(null)
    private var blockedManualRefreshAttempts = 0

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

        val withNames = allJobs.map { job ->
            IndustryJobWith(
                job = job,
                blueprintName = industryRepository.getTypeName(job.blueprintTypeId),
                productName = job.productTypeId?.let { industryRepository.getTypeName(it) } ?: "-",
                installerName = industryRepository.getTypeName(job.installerId)
            )
        }
        val installers = withNames.map { it.installerName }.distinct().sorted()

        IndustryUiState(
            allJobsWithNames = withNames,
            selectedTab = tab,
            availableInstallers = installers,
            syncError = error
        )
    }.combine(_selectedStatus) { state, statusFilter ->
        val now = System.currentTimeMillis()
        val filtered = when (statusFilter) {
            "active" -> state.allJobsWithNames.filter { it.job.endDate > now }
            "completed" -> state.allJobsWithNames.filter { it.job.endDate <= now }
            else -> state.allJobsWithNames
        }
        val tabFiltered = state.selectedTab.activities?.let { activities ->
            filtered.filter { it.job.activityType in activities }
        } ?: filtered
        state.copy(
            jobs = UiState.Success(tabFiltered),
            selectedStatus = statusFilter
        )
    }.combine(_selectedInstaller) { state, installerFilter ->
        val finalFiltered = if (installerFilter != null) {
            (state.jobs as? UiState.Success)?.data?.filter { it.installerName == installerFilter }
                ?: emptyList()
        } else {
            (state.jobs as? UiState.Success)?.data ?: emptyList()
        }
        state.copy(
            jobs = UiState.Success(finalFiltered),
            selectedInstaller = installerFilter
        )
    }.combine(_refreshNotice) { state, refreshNotice ->
        state.copy(refreshNotice = refreshNotice)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IndustryUiState())

    init {
        viewModelScope.launch {
            val lastRefreshAt = dashboardPreferences.getLastRefreshAt(RefreshDomain.INDUSTRY)
            if (lastRefreshAt <= 0L) {
                performRefresh()
            } else {
                _hasSynced.value = true
            }
        }
    }

    fun selectTab(tab: IndustryTab) {
        _selectedTab.value = tab
    }

    fun selectStatus(status: String?) {
        _selectedStatus.value = status
    }

    fun selectInstaller(installer: String?) {
        _selectedInstaller.value = installer
    }

    fun refresh() {
        viewModelScope.launch {
            if (!canRefreshManually()) return@launch
            performRefresh()
        }
    }

    fun clearRefreshNotice() {
        _refreshNotice.value = null
    }

    private fun canRefreshManually(): Boolean {
        val remaining = EsiRefreshPolicy.manualRemainingMillis(
            dashboardPreferences.getLastRefreshAt(RefreshDomain.INDUSTRY)
        )
        if (remaining <= 0L) {
            blockedManualRefreshAttempts = 0
            _refreshNotice.value = null
            return true
        }

        blockedManualRefreshAttempts += 1
        _refreshNotice.value = if (blockedManualRefreshAttempts >= EsiRefreshPolicy.EXCESSIVE_TAP_THRESHOLD) {
            "工业数据刷新过于频繁，ESI接口有5分钟缓存，请在${EsiRefreshPolicy.formatRemaining(remaining)}后再试"
        } else {
            "工业数据刚刷新过，请在${EsiRefreshPolicy.formatRemaining(remaining)}后再试"
        }
        return false
    }

    private suspend fun performRefresh() {
        _syncError.value = null
        _isRefreshing.value = true
        try {
            val result = industryRepository.syncJobs(corpId)
            _hasSynced.value = true
            result.onSuccess {
                dashboardPreferences.setLastRefreshAt(
                    RefreshDomain.INDUSTRY,
                    System.currentTimeMillis()
                )
                blockedManualRefreshAttempts = 0
                _refreshNotice.value = null
            }.onFailure { e ->
                _syncError.value = e.message ?: "同步失败"
            }
        } finally {
            _isRefreshing.value = false
        }
    }
}
