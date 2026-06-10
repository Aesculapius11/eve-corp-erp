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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HangarViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val hangarRepository: HangarRepository
) : ViewModel() {

    private val corpId: Long get() = tokenManager.corporationId
    private val _selectedDivisionId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _syncError = MutableStateFlow<String?>(null)

    // 所有 divisions
    private val divisionsFlow: StateFlow<List<CorporationDivisionEntity>> =
        hangarRepository.getAllDivisions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 选中的 division（自动回退逻辑）
    private val resolvedDivision: StateFlow<CorporationDivisionEntity?> = combine(
        divisionsFlow,
        _selectedDivisionId
    ) { divisions, selectedId ->
        // 优先：用户选中的 → 主仓库 → 第一个
        divisions.find { it.divisionId == selectedId }
            ?: divisions.firstOrNull { it.isMain }
            ?: divisions.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 当前选中 division 的物品列表
    private val itemsFlow: StateFlow<List<HangarItemEntity>> =
        resolvedDivision.flatMapLatest { division ->
            if (division == null) flowOf(emptyList())
            else hangarRepository.getItemsByDivision(division.divisionId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 最终 UI 状态
    val uiState: StateFlow<HangarUiState> = combine(
        divisionsFlow,
        resolvedDivision,
        itemsFlow,
        _searchQuery,
        _syncError
    ) { divisions, selectedDiv, items, query, error ->
        val withNames = items.map { item ->
            HangarItemWith(
                item = item,
                typeName = hangarRepository.getTypeName(item.typeId)
            )
        }
        val filtered = if (query.isBlank()) withNames
        else withNames.filter { it.typeName.contains(query, ignoreCase = true) }

        HangarUiState(
            divisions = UiState.Success(divisions),
            selectedDivision = selectedDiv,
            items = UiState.Success(filtered),
            searchQuery = query,
            syncError = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HangarUiState())

    fun selectDivision(division: CorporationDivisionEntity) {
        _selectedDivisionId.value = division.divisionId
    }

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun refresh() {
        viewModelScope.launch {
            _syncError.value = null
            hangarRepository.syncDivisions(corpId).onFailure { e ->
                _syncError.value = "部门同步失败: ${e.message}"
                return@launch
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
