package com.evecorp.erp.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evecorp.erp.Constants
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.data.local.entity.CorporationBillEntity
import com.evecorp.erp.data.local.entity.SystemCostIndexEntity
import com.evecorp.erp.data.local.entity.WalletBalanceEntity
import com.evecorp.erp.data.local.entity.WalletJournalEntity
import com.evecorp.erp.data.repository.CorporationBillRepository
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
    val bills: UiState<List<CorporationBillEntity>> = UiState.Loading,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val walletRepository: WalletRepository,
    private val industryRepository: IndustryRepository,
    private val corporationBillRepository: CorporationBillRepository
) : ViewModel() {

    private val corpId: Long get() = tokenManager.corporationId
    private val _isRefreshing = MutableStateFlow(false)

    private val dataState: StateFlow<DashboardUiState> = combine(
        walletRepository.getBalance(corpId).map { it?.let { UiState.Success(it) } ?: UiState.Loading },
        walletRepository.getRecentJournal(corpId).map { UiState.Success(it) },
        industryRepository.getCostIndex(Constants.HAAJINEN_SYSTEM_ID).map { it?.let { UiState.Success(it) } ?: UiState.Loading },
        corporationBillRepository.getUnpaid(corpId).map { UiState.Success(it) }
    ) { balance, journal, costIndex, bills ->
        DashboardUiState(balance = balance, journal = journal, costIndex = costIndex, bills = bills)
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
            industryRepository.syncCostIndices(listOf(Constants.HAAJINEN_SYSTEM_ID))
            corporationBillRepository.syncBills(corpId)
            _isRefreshing.value = false
        }
    }


}
