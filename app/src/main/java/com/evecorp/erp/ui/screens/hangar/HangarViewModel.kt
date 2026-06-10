package com.evecorp.erp.ui.screens.hangar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.data.local.entity.CorporationDivisionEntity
import com.evecorp.erp.data.local.entity.HangarItemEntity
import com.evecorp.erp.data.repository.HangarRepository
import com.evecorp.erp.ui.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HangarUiState(
    val divisions: UiState<List<CorporationDivisionEntity>> = UiState.Loading,
    val selectedDivision: CorporationDivisionEntity? = null,
    val items: UiState<List<HangarItemWith>> = UiState.Loading,
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val syncError: String? = null
)

data class HangarItemWith(
    val item: HangarItemEntity,
    val typeName: String
)

@HiltViewModel
class HangarViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val hangarRepository: HangarRepository
) : ViewModel() {

    private val corpId: Long get() = tokenManager.corporationId
    private val _selectedDivision = MutableStateFlow<CorporationDivisionEntity?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _syncError = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HangarUiState> = combine(
        hangarRepository.getAllDivisions(),
        _selectedDivision,
        _searchQuery,
        _syncError
    ) { divisions, selected, query, error ->
        // 自动选择：优先选中的 → 主仓库 → 第一个
        val selectedDiv = selected
            ?: divisions.firstOrNull { it.isMain }
            ?: divisions.firstOrNull()

        HangarUiState(
            divisions = UiState.Success(divisions),
            selectedDivision = selectedDiv,
            items = UiState.Loading, // 先占位，下面再计算真实 items
            searchQuery = query,
            syncError = error
        )
    }.flatMapLatest { partial ->
        val div = partial.selectedDivision
        if (div == null) {
            // divisions 为空 → emit 一个 items=Success(empty) 的状态，不再永远 Loading
            flowOf(partial.copy(items = UiState.Success(emptyList())))
        } else {
            hangarRepository.getItemsByDivision(div.divisionId).map { items ->
                val withNames = items.map { item ->
                    HangarItemWith(
                        item = item,
                        typeName = hangarRepository.getTypeName(item.typeId)
                    )
                }
                val filtered = if (partial.searchQuery.isBlank()) withNames
                else withNames.filter { it.typeName.contains(partial.searchQuery, ignoreCase = true) }
                partial.copy(items = UiState.Success(filtered))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HangarUiState())

    init {
        // 持续监听 divisions 变化，自动选择默认 division
        viewModelScope.launch {
            hangarRepository.getAllDivisions().collect { divisions ->
                if (_selectedDivision.value == null || divisions.none { it.divisionId == _selectedDivision.value?.divisionId }) {
                    _selectedDivision.value = divisions.firstOrNull { it.isMain }
                        ?: divisions.firstOrNull()
                }
            }
        }
    }

    fun selectDivision(division: CorporationDivisionEntity) {
        _selectedDivision.value = division
    }

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun refresh() {
        viewModelScope.launch {
            _syncError.value = null
            hangarRepository.syncDivisions(corpId).onFailure { e ->
                _syncError.value = "部门同步失败: ${e.message}"
            }
            hangarRepository.syncAssets(corpId).onFailure { e ->
                _syncError.value = "资产同步失败: ${e.message}"
            }
        }
    }

    fun setMainDivision(divisionId: Long) {
        viewModelScope.launch {
            hangarRepository.setMainDivision(divisionId)
        }
    }
}
