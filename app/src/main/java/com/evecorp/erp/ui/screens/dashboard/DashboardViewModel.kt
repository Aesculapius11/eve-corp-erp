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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val balance: UiState<WalletBalanceEntity> = UiState.Loading,
    val journal: UiState<List<WalletJournalEntity>> = UiState.Loading,
    val costIndex: UiState<SystemCostIndexEntity> = UiState.Loading,
    val balanceHistory: UiState<List<BalanceSnapshotEntity>> = UiState.Loading,
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

    private val dataState: StateFlow<DashboardUiState> = combine(
        walletRepository.getBalance(corpId).map { it?.let { UiState.Success(it) } ?: UiState.Loading },
        walletRepository.getRecentJournal(corpId).map { UiState.Success(it) },
        industryRepository.getCostIndex(Constants.HAAJINEN_SYSTEM_ID).map { it?.let { UiState.Success(it) } ?: UiState.Loading },
        walletRepository.getBalanceHistory(corpId).map { UiState.Success(it) }
    ) { balance, journal, costIndex, history ->
        DashboardUiState(
            balance = balance,
            journal = journal,
            costIndex = costIndex,
            balanceHistory = history
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    val uiState: StateFlow<DashboardUiState> = combine(
        dataState,
        _isRefreshing
    ) { state, refreshing ->
        state.copy(isRefreshing = refreshing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            walletRepository.syncBalance(corpId)
            walletRepository.syncJournal(corpId)
            // 从流水反推过去 30 天每日余额
            walletRepository.reconstructHistoryFromJournal(corpId)
            industryRepository.syncCostIndices(listOf(Constants.HAAJINEN_SYSTEM_ID))
            _isRefreshing.value = false
        }
    }
}
