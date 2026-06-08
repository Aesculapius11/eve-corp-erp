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
    private val _selectedDivision = MutableStateFlow<CorporationDivisionEntity?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _syncError = MutableStateFlow<String?>(null)

    private val _itemsWithNames: Flow<List<HangarItemWith>> = _selectedDivision
        .filterNotNull()
        .flatMapLatest { division ->
            hangarRepository.getItemsByDivision(division.divisionId).map { items ->
                items.map { item ->
                    HangarItemWith(
                        item = item,
                        typeName = hangarRepository.getTypeName(item.typeId)
                    )
                }
            }
        }

    val uiState: StateFlow<HangarUiState> = combine(
        hangarRepository.getAllDivisions(),
        _selectedDivision,
        _itemsWithNames,
        _searchQuery,
        _syncError
    ) { divisions, selected, items, query, error ->
        val selectedDiv = selected ?: divisions.firstOrNull { it.isMain } ?: divisions.firstOrNull()
        val filtered = if (query.isBlank()) items
        else items.filter { it.typeName.contains(query, ignoreCase = true) }

        HangarUiState(
            divisions = UiState.Success(divisions),
            selectedDivision = selectedDiv,
            items = UiState.Success(filtered),
            searchQuery = query,
            syncError = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HangarUiState())

    init {
        viewModelScope.launch {
            hangarRepository.getAllDivisions().first().let { divisions ->
                if (_selectedDivision.value == null) {
                    _selectedDivision.value = divisions.firstOrNull { it.isMain } ?: divisions.firstOrNull()
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
