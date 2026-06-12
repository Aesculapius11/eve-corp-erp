package com.evecorp.erp.ui.screens.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.data.local.DashboardPreferences
import com.evecorp.erp.data.local.entity.MarketOrderEntity
import com.evecorp.erp.data.repository.MarketRepository
import com.evecorp.erp.sync.EsiRefreshPolicy
import com.evecorp.erp.sync.RefreshDomain
import com.evecorp.erp.ui.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MarketOrderWith(
    val order: MarketOrderEntity,
    val typeName: String,
    val issuerName: String
)

data class MarketUiState(
    val sellOrders: UiState<List<MarketOrderWith>> = UiState.Loading,
    val buyOrders: UiState<List<MarketOrderWith>> = UiState.Loading,
    val selectedTab: MarketTab = MarketTab.SELL,
    val selectedIssuer: String? = null,
    val availableIssuers: List<String> = emptyList(),
    val allOrdersWithNames: List<MarketOrderWith> = emptyList(),
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
    private val marketRepository: MarketRepository,
    private val dashboardPreferences: DashboardPreferences
) : ViewModel() {

    private val corpId: Long get() = tokenManager.corporationId
    private val _selectedTab = MutableStateFlow(MarketTab.SELL)
    private val _selectedIssuer = MutableStateFlow<String?>(null)
    private val _syncError = MutableStateFlow<String?>(null)
    private val _hasSynced = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _refreshNoticeEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val refreshNoticeEvents = _refreshNoticeEvents.asSharedFlow()
    private var blockedManualRefreshAttempts = 0

    val uiState: StateFlow<MarketUiState> = combine(
        _selectedTab,
        marketRepository.getAllActiveOrders(corpId),
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
                issuerName = order.issuedBy?.let { marketRepository.getTypeName(it) } ?: "-"
            )
        }
        val issuers = withNames.map { it.issuerName }.distinct().sorted()

        MarketUiState(
            allOrdersWithNames = withNames,
            selectedTab = tab,
            availableIssuers = issuers,
            syncError = error
        )
    }.combine(_selectedIssuer) { state, issuerFilter ->
        val filtered = if (issuerFilter != null) {
            state.allOrdersWithNames.filter { it.issuerName == issuerFilter }
        } else {
            state.allOrdersWithNames
        }

        state.copy(
            sellOrders = UiState.Success(filtered.filter { !it.order.isBuyOrder }),
            buyOrders = UiState.Success(filtered.filter { it.order.isBuyOrder }),
            selectedIssuer = issuerFilter
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MarketUiState())

    init {
        viewModelScope.launch {
            val lastRefreshAt = dashboardPreferences.getLastRefreshAt(RefreshDomain.MARKET)
            if (lastRefreshAt <= 0L) {
                performRefresh()
            } else {
                _hasSynced.value = true
            }
        }
    }

    fun selectTab(tab: MarketTab) {
        _selectedTab.value = tab
    }

    fun selectIssuer(issuer: String?) {
        _selectedIssuer.value = issuer
    }

    fun refresh() {
        viewModelScope.launch {
            if (!canRefreshManually()) return@launch
            performRefresh()
        }
    }

    private fun canRefreshManually(): Boolean {
        val remaining = EsiRefreshPolicy.manualRemainingMillis(
            dashboardPreferences.getLastRefreshAt(RefreshDomain.MARKET)
        )
        if (remaining <= 0L) {
            blockedManualRefreshAttempts = 0
            return true
        }

        blockedManualRefreshAttempts += 1
        val message = if (blockedManualRefreshAttempts >= EsiRefreshPolicy.EXCESSIVE_TAP_THRESHOLD) {
            "市场数据刷新过于频繁，ESI接口有5分钟缓存，请在${EsiRefreshPolicy.formatRemaining(remaining)}后再试"
        } else {
            "市场数据刚刷新过，请在${EsiRefreshPolicy.formatRemaining(remaining)}后再试"
        }
        _refreshNoticeEvents.tryEmit(message)
        return false
    }

    private suspend fun performRefresh() {
        _syncError.value = null
        _isRefreshing.value = true
        try {
            marketRepository.syncOrders(corpId).onFailure { e ->
                _syncError.value = "公司订单: ${e.message}"
            }
            marketRepository.syncCharacterOrders(tokenManager.characterId).onFailure { e ->
                _syncError.value = (_syncError.value?.plus("; ") ?: "") + "个人订单: ${e.message}"
            }
            val allOrders = marketRepository.getAllActiveOrders(corpId).first()
            marketRepository.syncTypeNames(allOrders)
            _hasSynced.value = true

            if (_syncError.value == null) {
                dashboardPreferences.setLastRefreshAt(
                    RefreshDomain.MARKET,
                    System.currentTimeMillis()
                )
                blockedManualRefreshAttempts = 0
            }
        } finally {
            _isRefreshing.value = false
        }
    }
}
