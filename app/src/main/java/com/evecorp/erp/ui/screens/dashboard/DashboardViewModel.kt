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
import com.evecorp.erp.ui.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
data class SystemSearchResult(val id: Long, val name: String)

/** 热门星系列表（ID, 英文名, 中文昵称） */
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
    Triple(30000174L, "Goram", "戈拉姆"),
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
    val nextSyncCountdown: String = ""
)

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
    private var countdownJob: kotlinx.coroutines.Job? = null

    // 财务数据流（balance + journal + history + costIndex）
    private val financialData: StateFlow<DashboardUiState> = combine(
        walletRepository.getBalance(corpId).map { it?.let { UiState.Success(it) } ?: UiState.Loading },
        walletRepository.getRecentJournal(corpId).map { UiState.Success(it) },
        walletRepository.getBalanceHistory(corpId).map { UiState.Success(it) },
        _selectedSystemId.flatMapLatest { systemId ->
            industryRepository.getCostIndex(systemId).map { it?.let { UiState.Success(it) } ?: UiState.Loading }
        },
        _hasSynced
    ) { balance, journal, history, costIndex, hasSynced ->
        if (!hasSynced) {
            DashboardUiState() // 全部 Loading
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
        combine(_systemSearchResults, _isSearchingSystem) { results, searching ->
            results to searching
        }
    ) { data, refreshing, systemId, systemName, (searchResults, searching) ->
        data.copy(
            isRefreshing = refreshing,
            selectedSystemId = systemId,
            selectedSystemName = systemName,
            systemSearchResults = searchResults,
            isSearchingSystem = searching
        )
    }.combine(_nextSyncCountdown) { state, countdown ->
        state.copy(nextSyncCountdown = countdown)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    init {
        refresh()
        startCountdown()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var nextSyncTime = System.currentTimeMillis() + dashboardPreferences.getSyncIntervalMinutes() * 60_000L

            while (true) {
                kotlinx.coroutines.delay(1000)

                // 每次循环都读取最新间隔设置
                val intervalMs = dashboardPreferences.getSyncIntervalMinutes() * 60_000L
                val remaining = nextSyncTime - System.currentTimeMillis()

                if (remaining <= 0) {
                    refreshInternal()
                    nextSyncTime = System.currentTimeMillis() + intervalMs
                }

                val totalSec = ((nextSyncTime - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                val min = totalSec / 60
                val sec = totalSec % 60
                _nextSyncCountdown.value = if (min > 0) "${min}分${sec}秒后刷新" else "${sec}秒后刷新"
            }
        }
    }

    /** 设置变更时重启倒计时 */
    fun resetCountdown() {
        startCountdown()
    }

    private suspend fun refreshInternal() {
        _isRefreshing.value = true
        walletRepository.syncBalance(corpId)
        kotlinx.coroutines.delay(100)
        _hasSynced.value = true
        _isRefreshing.value = false
        kotlinx.coroutines.coroutineScope {
            launch {
                walletRepository.syncJournal(corpId)
                walletRepository.reconstructHistoryFromJournal(corpId)
            }
            launch {
                industryRepository.syncCostIndices(listOf(_selectedSystemId.value))
            }
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
            // 显示热门星系
            _systemSearchResults.value = POPULAR_SYSTEMS.map {
                SystemSearchResult(it.first, "${it.second} (${it.third})")
            }
            return
        }
        _isSearchingSystem.value = true
        val q = query.lowercase()
        // 本地模糊搜索（中英文）
        val localResults = POPULAR_SYSTEMS.filter { (_, en, cn) ->
            en.lowercase().contains(q) || cn.contains(query)
        }.map { SystemSearchResult(it.first, "${it.second} (${it.third})") }

        _systemSearchResults.value = localResults
        _isSearchingSystem.value = false

        // 如果本地没有匹配，尝试 ESI 搜索（英文名）
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
            refreshInternal()
            // 手动刷新后重置倒计时
            startCountdown()
        }
    }
}
