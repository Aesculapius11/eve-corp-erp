package com.evecorp.erp.ui.screens.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.data.local.entity.MarketOrderEntity
import com.evecorp.erp.data.repository.MarketRepository
import com.evecorp.erp.ui.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MarketOrderWith(
    val order: MarketOrderEntity,
    val typeName: String,
    val issuerName: String
)

data class MarketUiState(
    val sellOrders: UiState<List<MarketOrderWith>> = UiState.Loading,
    val buyOrders: UiState<List<MarketOrderWith>> = UiState.Loading,
    val selectedTab: MarketTab = MarketTab.SELL,
    val isRefreshing: Boolean = false,
    val syncError: String? = null
)

enum class MarketTab(val label: String) {
    SELL("卖单"),
    BUY("买单")
}

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val marketRepository: MarketRepository
) : ViewModel() {

    private val corpId: Long get() = tokenManager.corporationId
    private val _selectedTab = MutableStateFlow(MarketTab.SELL)
    private val _syncError = MutableStateFlow<String?>(null)
    private val _hasSynced = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<MarketUiState> = combine(
        _selectedTab,
        marketRepository.getAllActiveOrders(),
        _syncError,
        _hasSynced,
        _isRefreshing
    ) { tab, allOrders, error, hasSynced, isRefreshing ->
        if (!hasSynced || isRefreshing) {
            return@combine MarketUiState(
                sellOrders = UiState.Loading,
                buyOrders = UiState.Loading,
                selectedTab = tab,
                isRefreshing = isRefreshing,
                syncError = error
            )
        }
        val withNames = allOrders.map { order ->
            MarketOrderWith(
                order = order,
                typeName = marketRepository.getTypeName(order.typeId),
                issuerName = order.issuedBy?.let { marketRepository.getTypeName(it) } ?: "—"
            )
        }
        val sellOrders = withNames.filter { !it.order.isBuyOrder }
        val buyOrders = withNames.filter { it.order.isBuyOrder }
        MarketUiState(
            sellOrders = UiState.Success(sellOrders),
            buyOrders = UiState.Success(buyOrders),
            selectedTab = tab,
            syncError = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MarketUiState())

    init { refresh() }

    fun selectTab(tab: MarketTab) {
        _selectedTab.value = tab
    }

    fun refresh() {
        viewModelScope.launch {
            _syncError.value = null
            _isRefreshing.value = true
            marketRepository.syncOrders(corpId).onFailure { e ->
                _syncError.value = "公司订单: ${e.message}"
            }
            marketRepository.syncCharacterOrders(tokenManager.characterId).onFailure { e ->
                _syncError.value = (_syncError.value?.plus("; ") ?: "") + "个人订单: ${e.message}"
            }
            // 解析物品名
            val allOrders = marketRepository.getAllActiveOrders().first()
            marketRepository.syncTypeNames(allOrders)
            _isRefreshing.value = false
            _hasSynced.value = true
        }
    }
}
