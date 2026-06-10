package com.evecorp.erp.ui.screens.hangar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.data.local.entity.CorporationDivisionEntity
import com.evecorp.erp.data.local.entity.HangarItemEntity
import com.evecorp.erp.data.repository.HangarRepository
import com.evecorp.erp.ui.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HangarUiState(
    val divisions: List<CorporationDivisionEntity> = emptyList(),
    val selectedDivision: CorporationDivisionEntity? = null,
    val items: List<HangarItemWith> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
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
    private val _selectedDivisionId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _syncError = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(true)

    // 所有 divisions（单个 Room Flow 订阅）
    private val _divisions = MutableStateFlow<List<CorporationDivisionEntity>>(emptyList())

    // 当前选中的 division
    private val _resolvedDivision = MutableStateFlow<CorporationDivisionEntity?>(null)

    // 当前 division 的物品
    private val _items = MutableStateFlow<List<HangarItemEntity>>(emptyList())

    val uiState: StateFlow<HangarUiState> = MutableStateFlow(HangarUiState())

    init {
        // 监听 divisions 变化
        viewModelScope.launch {
            hangarRepository.getAllDivisions().collect { divisions ->
                _divisions.value = divisions
                // 自动选择 division
                val current = _resolvedDivision.value
                if (current == null || divisions.none { it.divisionId == current.divisionId }) {
                    _resolvedDivision.value = divisions.firstOrNull { it.isMain }
                        ?: divisions.firstOrNull()
                }
                _isLoading.value = false
                updateUiState()
            }
        }

        // 监听选中 division 变化，更新物品列表
        viewModelScope.launch {
            _resolvedDivision.collect { division ->
                if (division == null) {
                    _items.value = emptyList()
                    updateUiState()
                } else {
                    hangarRepository.getItemsByDivision(division.divisionId).collect { items ->
                        _items.value = items
                        updateUiState()
                    }
                }
            }
        }
    }

    private suspend fun updateUiState() {
        val divisions = _divisions.value
        val selectedDiv = _resolvedDivision.value
        val items = _items.value
        val query = _searchQuery.value

        val withNames = items.map { item ->
            HangarItemWith(
                item = item,
                typeName = hangarRepository.getTypeName(item.typeId)
            )
        }
        val filtered = if (query.isBlank()) withNames
        else withNames.filter { it.typeName.contains(query, ignoreCase = true) }

        (uiState as MutableStateFlow).value = HangarUiState(
            divisions = divisions,
            selectedDivision = selectedDiv,
            items = filtered,
            isLoading = _isLoading.value,
            searchQuery = query,
            syncError = _syncError.value
        )
    }

    fun selectDivision(division: CorporationDivisionEntity) {
        _resolvedDivision.value = division
        viewModelScope.launch { updateUiState() }
    }

    fun updateSearch(query: String) {
        _searchQuery.value = query
        viewModelScope.launch { updateUiState() }
    }

    fun refresh() {
        viewModelScope.launch {
            _syncError.value = null
            _isLoading.value = true
            updateUiState()

            hangarRepository.syncDivisions(corpId).onFailure { e ->
                _syncError.value = "部门同步失败: ${e.message}"
                _isLoading.value = false
                updateUiState()
                return@launch
            }
            hangarRepository.syncAssets(corpId).onFailure { e ->
                _syncError.value = "资产同步失败: ${e.message}"
            }
            _isLoading.value = false
            // divisions 的 Flow 会自动触发更新
        }
    }

    fun setMainDivision(divisionId: Long) {
        viewModelScope.launch {
            hangarRepository.setMainDivision(divisionId)
        }
    }
}
