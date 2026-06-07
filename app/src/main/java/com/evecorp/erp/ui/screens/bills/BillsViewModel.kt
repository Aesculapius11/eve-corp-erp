package com.evecorp.erp.ui.screens.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.data.local.entity.CorporationBillEntity
import com.evecorp.erp.data.repository.CorporationBillRepository
import com.evecorp.erp.ui.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BillsUiState(
    val unpaidBills: UiState<List<CorporationBillEntity>> = UiState.Loading,
    val paidBills: UiState<List<CorporationBillEntity>> = UiState.Loading,
    val selectedTab: BillsTab = BillsTab.UNPAID,
    val isRefreshing: Boolean = false
)

enum class BillsTab(val label: String) {
    UNPAID("未支付"),
    PAID("已支付")
}

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val corporationBillRepository: CorporationBillRepository
) : ViewModel() {

    private val corpId: Long get() = tokenManager.corporationId
    private val _selectedTab = MutableStateFlow(BillsTab.UNPAID)

    val uiState: StateFlow<BillsUiState> = combine(
        corporationBillRepository.getUnpaid(corpId),
        corporationBillRepository.getAll(corpId),
        _selectedTab
    ) { unpaid, all, tab ->
        val paid = all.filter { it.paid }
        BillsUiState(
            unpaidBills = UiState.Success(unpaid),
            paidBills = UiState.Success(paid),
            selectedTab = tab
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BillsUiState())

    fun selectTab(tab: BillsTab) {
        _selectedTab.value = tab
    }

    fun refresh() {
        viewModelScope.launch {
            corporationBillRepository.syncBills(corpId)
        }
    }
}
