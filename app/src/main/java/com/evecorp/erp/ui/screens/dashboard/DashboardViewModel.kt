package com.evecorp.erp.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evecorp.erp.Constants
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.data.local.DashboardPreferences
import com.evecorp.erp.data.local.entity.BalanceSnapshotEntity
import com.evecorp.erp.data.local.entity.SystemCostIndexEntity
import com.evecorp.erp.data.local.entity.WalletBalanceEntity
import com.evecorp.erp.data.local.entity.WalletJournalEntity
import com.evecorp.erp.data.repository.IndustryRepository
import com.evecorp.erp.data.repository.WalletRepository
import com.evecorp.erp.sync.EsiRefreshPolicy
import com.evecorp.erp.sync.RefreshDomain
import com.evecorp.erp.ui.RefreshNotice
import com.evecorp.erp.ui.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SystemSearchResult(val id: Long, val name: String)

private val POPULAR_SYSTEMS = listOf(
    Triple(30000142L, "Jita", "吉他"),
    Triple(30002187L, "Amarr", "艾玛"),
    Triple(30002659L, "Dodixie", "多迪谢"),
    Triple(30002510L, "Rens", "伦斯"),
    Triple(30002053L, "Hek", "赫克"),
    Triple(30001424L, "Haajinen", "哈基能"),
    Triple(30002788L, "Oursulaert", "乌尔苏拉"),
    Triple(30000144L, "Perimeter", "周长星"),
    Triple(30000145L, "Urlen", "乌尔伦"),
    Triple(30000146L, "Niyabainen", "尼亚拜能"),
    Triple(30000147L, "Maurasi", "毛拉西"),
    Triple(30000148L, "Isanamo", "伊萨纳莫"),
    Triple(30000149L, "Sobaseki", "索巴基"),
    Triple(30000150L, "Tunttaras", "图塔拉斯"),
    Triple(30000151L, "Vellaine", "韦莱因"),
    Triple(30000152L, "Sarekuwa", "萨雷库瓦"),
    Triple(30000153L, "Ekuenbiron", "埃昆比龙"),
    Triple(30000154L, "Vuorrassi", "沃拉西"),
    Triple(30000155L, "Olo", "奥洛"),
    Triple(30000156L, "Ikami", "伊卡米"),
    Triple(30000157L, "Hatakani", "哈塔卡尼"),
    Triple(30000158L, "Sivala", "西瓦拉"),
    Triple(30000159L, "Ibura", "伊布拉"),
    Triple(30000160L, "Todaki", "托达基"),
    Triple(30000161L, "Osmon", "奥斯蒙"),
    Triple(30000162L, "Korsiki", "科尔西基"),
    Triple(30000163L, "Umokka", "乌莫卡"),
    Triple(30000164L, "Tama", "塔玛"),
    Triple(30000165L, "Santola", "桑托拉"),
    Triple(30000166L, "Niarja", "尼亚尔贾"),
    Triple(30000167L, "Madirmilire", "马迪米利雷"),
    Triple(30000168L, "Ashab", "阿沙布"),
    Triple(30000169L, "Balle", "巴勒"),
    Triple(30000170L, "Chaven", "查文"),
    Triple(30000171L, "Doore", "多尔"),
    Triple(30000172L, "Ekura", "埃库拉"),
    Triple(30000173L, "Fildar", "菲尔达"),
    Triple(30000174L, "Goram", "戈拉姆")
)

data class DashboardUiState(
    val balance: UiState<WalletBalanceEntity> = UiState.Loading,
    val journal: UiState<List<WalletJournalEntity>> = UiState.Loading,
    val costIndex: UiState<SystemCostIndexEntity> = UiState.Loading,
    val balanceHistory: UiState<List<BalanceSnapshotEntity>> = UiState.Loading,
    val selectedSystemId: Long = Constants.HAAJINEN_SYSTEM_ID,
    val selectedSystemName: String = "吉他",
    val systemSearchResults: List<SystemSearchResult> = emptyList(),
    val isSearchingSystem: Boolean = false,
    val isRefreshing: Boolean = false,
    val nextSyncCountdown: String = "",
    val refreshNotice: RefreshNotice? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val walletRepository: WalletRepository,
    private val industryRepository: IndustryRepository,
    private val dashboardPreferences: DashboardPreferences
) : ViewModel() {

    private val corpId: Long get() = tokenManager.corporationId
    private val _isRefreshing = MutableStateFlow(false)
    private val _selectedSystemId = MutableStateFlow(dashboardPreferences.getCostIndexSystemId())
    private val _selectedSystemName = MutableStateFlow(dashboardPreferences.getCostIndexSystemName())
    private val _systemSearchResults = MutableStateFlow<List<SystemSearchResult>>(emptyList())
    private val _isSearchingSystem = MutableStateFlow(false)
    private val _hasSynced = MutableStateFlow(false)
    private val _nextSyncCountdown = MutableStateFlow("")
    private val _refreshNotice = MutableStateFlow<RefreshNotice?>(null)
    private var countdownJob: Job? = null
    private var blockedManualRefreshAttempts = 0
    private var refreshNoticeId = 0L

    private val financialData: StateFlow<DashboardUiState> = combine(
        walletRepository.getBalance(corpId).map { it?.let { balance -> UiState.Success(balance) } ?: UiState.Loading },
        walletRepository.getRecentJournal(corpId).map { UiState.Success(it) },
        walletRepository.getBalanceHistory(corpId).map { UiState.Success(it) },
        _selectedSystemId.flatMapLatest { systemId ->
            industryRepository.getCostIndex(systemId).map { it?.let { item -> UiState.Success(item) } ?: UiState.Loading }
        },
        _hasSynced
    ) { balance, journal, history, costIndex, hasSynced ->
        if (!hasSynced) {
            DashboardUiState()
        } else {
            DashboardUiState(
                balance = balance,
                journal = journal,
                costIndex = costIndex,
                balanceHistory = history
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    val uiState: StateFlow<DashboardUiState> = combine(
        financialData,
        _isRefreshing,
        _selectedSystemId,
        _selectedSystemName,
        combine(_systemSearchResults, _isSearchingSystem) { results, searching -> results to searching }
    ) { data, refreshing, systemId, systemName, searchState ->
        data.copy(
            isRefreshing = refreshing,
            selectedSystemId = systemId,
            selectedSystemName = systemName,
            systemSearchResults = searchState.first,
            isSearchingSystem = searchState.second
        )
    }.combine(_refreshNotice) { state, refreshNotice ->
        state.copy(refreshNotice = refreshNotice)
    }.combine(_nextSyncCountdown) { state, countdown ->
        state.copy(nextSyncCountdown = countdown)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    init {
        viewModelScope.launch {
            ensureInitialDataLoaded()
            startCountdown()
        }
    }

    fun selectSystem(systemId: Long, systemName: String) {
        _selectedSystemId.value = systemId
        _selectedSystemName.value = systemName
        _systemSearchResults.value = emptyList()
        dashboardPreferences.setCostIndexSystem(systemId, systemName)
        viewModelScope.launch {
            industryRepository.syncCostIndices(listOf(systemId))
        }
    }

    fun searchSystem(query: String) {
        if (query.isBlank()) {
            _systemSearchResults.value = POPULAR_SYSTEMS.map {
                SystemSearchResult(it.first, "${it.second} (${it.third})")
            }
            return
        }

        _isSearchingSystem.value = true
        val q = query.lowercase()
        val localResults = POPULAR_SYSTEMS.filter { (_, en, cn) ->
            en.lowercase().contains(q) || cn.contains(query)
        }.map {
            SystemSearchResult(it.first, "${it.second} (${it.third})")
        }

        _systemSearchResults.value = localResults
        _isSearchingSystem.value = false

        if (localResults.isEmpty() && query.length >= 3) {
            viewModelScope.launch {
                industryRepository.searchSystems(query)
                    .onSuccess { results ->
                        _systemSearchResults.value = results.map { SystemSearchResult(it.first, it.second) }
                    }
                _isSearchingSystem.value = false
            }
        }
    }

    fun clearSearchResults() {
        _systemSearchResults.value = emptyList()
    }

    fun refresh() {
        viewModelScope.launch {
            if (!canRefreshManually()) return@launch
            performRefresh()
        }
    }

    fun clearRefreshNotice(id: Long) {
        if (_refreshNotice.value?.id == id) {
            _refreshNotice.value = null
        }
    }

    private suspend fun ensureInitialDataLoaded() {
        if (_hasSynced.value) return
        val lastRefreshAt = dashboardPreferences.getLastRefreshAt(RefreshDomain.DASHBOARD)
        if (lastRefreshAt <= 0L) {
            performRefresh()
        } else {
            _hasSynced.value = true
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive) {
                val lastRefreshAt = dashboardPreferences.getLastRefreshAt(RefreshDomain.DASHBOARD)
                if (lastRefreshAt <= 0L) {
                    _nextSyncCountdown.value = ""
                    delay(1000)
                    continue
                }

                val remaining = EsiRefreshPolicy.automaticRemainingMillis(
                    lastRefreshAt = lastRefreshAt,
                    configuredIntervalMinutes = dashboardPreferences.getSyncIntervalMinutes()
                )
                if (remaining <= 0L && !_isRefreshing.value) {
                    performRefresh()
                    continue
                }

                _nextSyncCountdown.value = "${EsiRefreshPolicy.formatRemaining(remaining)}后刷新"
                delay(1000)
            }
        }
    }

    private fun canRefreshManually(): Boolean {
        val remaining = EsiRefreshPolicy.manualRemainingMillis(
            dashboardPreferences.getLastRefreshAt(RefreshDomain.DASHBOARD)
        )
        if (remaining <= 0L) {
            blockedManualRefreshAttempts = 0
            return true
        }

        blockedManualRefreshAttempts += 1
        val message = if (blockedManualRefreshAttempts >= EsiRefreshPolicy.EXCESSIVE_TAP_THRESHOLD) {
            "刷新过于频繁，ESI接口有5分钟缓存，请在${EsiRefreshPolicy.formatRemaining(remaining)}后再试"
        } else {
            "刚刷新过，请在${EsiRefreshPolicy.formatRemaining(remaining)}后再试"
        }
        refreshNoticeId += 1
        _refreshNotice.value = RefreshNotice(refreshNoticeId, message)
        return false
    }

    private suspend fun performRefresh() {
        _isRefreshing.value = true
        try {
            walletRepository.syncBalance(corpId)
            delay(100)
            _hasSynced.value = true
            coroutineScope {
                launch {
                    walletRepository.syncJournal(corpId)
                    walletRepository.reconstructHistoryFromJournal(corpId)
                }
                launch {
                    industryRepository.syncCostIndices(listOf(_selectedSystemId.value))
                }
            }
            dashboardPreferences.setLastRefreshAt(
                RefreshDomain.DASHBOARD,
                System.currentTimeMillis()
            )
            blockedManualRefreshAttempts = 0
            _refreshNotice.value = null
        } finally {
            _isRefreshing.value = false
        }
    }
}
