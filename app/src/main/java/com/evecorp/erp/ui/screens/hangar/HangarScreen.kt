package com.evecorp.erp.ui.screens.hangar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evecorp.erp.R
import com.evecorp.erp.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HangarScreen(
    viewModel: HangarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSetMainDialog by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    showSetMainDialog?.let { divisionId ->
        AlertDialog(
            onDismissRequest = { showSetMainDialog = null },
            title = { Text(stringResource(R.string.set_main_hangar)) },
            text = { Text("将此 Division 设为主仓库？主仓库将在首页显示库存概览。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setMainDivision(divisionId)
                    showSetMainDialog = null
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showSetMainDialog = null }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_hangar)) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.btn_refresh))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 同步错误提示
            uiState.syncError?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Division chips
            when (val divisions = uiState.divisions) {
                is UiState.Success -> {
                    ScrollableTabRow(
                        selectedTabIndex = divisions.data.indexOfFirst { it.divisionId == uiState.selectedDivision?.divisionId }.coerceAtLeast(0),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        divisions.data.forEach { division ->
                            Tab(
                                selected = division.divisionId == uiState.selectedDivision?.divisionId,
                                onClick = { viewModel.selectDivision(division) },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (division.isMain) {
                                            Icon(
                                                Icons.Filled.Star,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(division.name.ifEmpty { "Division ${division.divisionKey}" })
                                    }
                                }
                            )
                        }
                    }
                }
                is UiState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else -> {}
            }

            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearch(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )

            // Items list
            when (val items = uiState.items) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Success -> {
                    if (items.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.empty_hangar), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item { Spacer(Modifier.height(4.dp)) }
                            items(items.data, key = { it.item.itemId }) { hangarItem ->
                                HangarItemCard(hangarItem)
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun HangarItemCard(item: HangarItemWith) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.typeName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "ID: ${item.item.typeId}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                formatQuantity(item.item.quantity),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatQuantity(qty: Int): String {
    return when {
        qty >= 1_000_000 -> "${String.format("%.1f", qty / 1_000_000.0)}M"
        qty >= 1_000 -> "${String.format("%.0f", qty / 1_000.0)}K"
        else -> qty.toString()
    }
}
