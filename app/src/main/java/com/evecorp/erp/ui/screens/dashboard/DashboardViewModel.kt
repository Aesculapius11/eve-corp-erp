package com.evecorp.erp.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evecorp.erp.Constants
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.data.local.entity.BalanceSnapshotEntity
import com.evecorp.erp.data.local.entity.SystemCostIndexEntity
import com.evecorp.erp.data.local.entity.WalletBalanceEntity
import com.evecorp.erp.data.local.entity.WalletJournalEntity
import com.evecorp.erp.data.repository.IndustryRepository
import com.evecorp.erp.data.repository.WalletRepository
import com.evecorp.erp.ui.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
data class SystemSearchResult(val id: Long, val name: String)

data class DashboardUiState(
    val balance: UiState<WalletBalanceEntity> = UiState.Loading,
    val journal: UiState<List<WalletJournalEntity>> = UiState.Loading,
    val costIndex: UiState<SystemCostIndexEntity> = UiState.Loading,
    val balanceHistory: UiState<List<BalanceSnapshotEntity>> = UiState.Loading,
    val selectedSystemId: Long = Constants.HAAJINEN_SYSTEM_ID,
    val selectedSystemName: String = "吉他",
    val systemSearchResults: List<SystemSearchResult> = emptyList(),
    val isSearchingSystem: Boolean = false,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val walletRepository: WalletRepository,
    private val industryRepository: IndustryRepository
) : ViewModel() {

    private val corpId: Long get() = tokenManager.corporationId
    private val _isRefreshing = MutableStateFlow(false)
    private val _selectedSystemId = MutableStateFlow(Constants.HAAJINEN_SYSTEM_ID)
    private val _selectedSystemName = MutableStateFlow("吉他")
    private val _systemSearchResults = MutableStateFlow<List<SystemSearchResult>>(emptyList())
    private val _isSearchingSystem = MutableStateFlow(false)

    // 财务数据流（balance + journal + history + costIndex）
    private val financialData: StateFlow<DashboardUiState> = combine(
        walletRepository.getBalance(corpId).map { it?.let { UiState.Success(it) } ?: UiState.Loading },
        walletRepository.getRecentJournal(corpId).map { UiState.Success(it) },
        walletRepository.getBalanceHistory(corpId).map { UiState.Success(it) },
        _selectedSystemId.flatMapLatest { systemId ->
            industryRepository.getCostIndex(systemId).map { it?.let { UiState.Success(it) } ?: UiState.Loading }
        }
    ) { balance, journal, history, costIndex ->
        DashboardUiState(
            balance = balance,
            journal = journal,
            costIndex = costIndex,
            balanceHistory = history
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    val uiState: StateFlow<DashboardUiState> = combine(
        financialData,
        _isRefreshing,
        _selectedSystemId,
        _selectedSystemName,
        _systemSearchResults
    ) { data, refreshing, systemId, systemName, searchResults ->
        data.copy(
            isRefreshing = refreshing,
            selectedSystemId = systemId,
            selectedSystemName = systemName,
            systemSearchResults = searchResults,
            isSearchingSystem = _isSearchingSystem.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun selectSystem(systemId: Long, systemName: String) {
        _selectedSystemId.value = systemId
        _selectedSystemName.value = systemName
        _systemSearchResults.value = emptyList()
        viewModelScope.launch {
            industryRepository.syncCostIndices(listOf(systemId))
        }
    }

    fun searchSystem(query: String) {
        if (query.isBlank()) {
            _systemSearchResults.value = emptyList()
            return
        }
        _isSearchingSystem.value = true
        viewModelScope.launch {
            industryRepository.searchSystems(query)
                .onSuccess { results ->
                    _systemSearchResults.value = results.map { SystemSearchResult(it.first, it.second) }
                }
                .onFailure {
                    _systemSearchResults.value = emptyList()
                }
            _isSearchingSystem.value = false
        }
    }

    fun clearSearchResults() {
        _systemSearchResults.value = emptyList()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            walletRepository.syncBalance(corpId)
            walletRepository.syncJournal(corpId)
            walletRepository.reconstructHistoryFromJournal(corpId)
            industryRepository.syncCostIndices(listOf(_selectedSystemId.value))
            _isRefreshing.value = false
        }
    }
}
