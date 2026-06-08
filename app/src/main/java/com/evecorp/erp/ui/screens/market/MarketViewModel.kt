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

data class MarketUiState(
    val sellOrders: UiState<List<MarketOrderEntity>> = UiState.Loading,
    val buyOrders: UiState<List<MarketOrderEntity>> = UiState.Loading,
    val selectedTab: MarketTab = MarketTab.SELL,
    val searchQuery: String = "",
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
    private val _searchQuery = MutableStateFlow("")
    private val _syncError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MarketUiState> = combine(
        _selectedTab,
        _searchQuery,
        marketRepository.getAllActiveOrders(),
        _syncError
    ) { tab, query, allOrders, error ->
        val sellOrders = allOrders.filter { !it.isBuyOrder }
        val buyOrders = allOrders.filter { it.isBuyOrder }
        val filteredSell = filterOrders(sellOrders, query)
        val filteredBuy = filterOrders(buyOrders, query)
        MarketUiState(
            sellOrders = UiState.Success(filteredSell),
            buyOrders = UiState.Success(filteredBuy),
            selectedTab = tab,
            searchQuery = query,
            syncError = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MarketUiState())

    fun selectTab(tab: MarketTab) {
        _selectedTab.value = tab
    }

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun refresh() {
        viewModelScope.launch {
            _syncError.value = null
            // 同步公司订单
            marketRepository.syncOrders(corpId).onFailure { e ->
                _syncError.value = "公司订单: ${e.message}"
            }
            // 同步个人订单
            marketRepository.syncCharacterOrders(tokenManager.characterId).onFailure { e ->
                _syncError.value = (_syncError.value?.plus("; ") ?: "") + "个人订单: ${e.message}"
            }
        }
    }

    private fun filterOrders(orders: List<MarketOrderEntity>, query: String): List<MarketOrderEntity> {
        if (query.isBlank()) return orders
        return orders.filter {
            it.typeId.toString().contains(query, ignoreCase = true) ||
                it.locationId.toString().contains(query, ignoreCase = true)
        }
    }
}
