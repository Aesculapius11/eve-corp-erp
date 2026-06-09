package com.evecorp.erp.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import com.evecorp.erp.ui.formatIsk
import com.evecorp.erp.ui.formatTimeAgo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_dashboard)) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.btn_refresh))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // 钱包余额卡片
            item { BalanceCard(uiState.balance) }

            // 30 天余额趋势图
            item {
                Text(
                    "30 天余额趋势",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            item {
                BalanceHistoryCard(uiState.balanceHistory)
            }

            // 成本指数
            item {
                Text(
                    stringResource(R.string.cost_index),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            item { CostIndexCard(uiState.costIndex) }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun BalanceCard(state: UiState<com.evecorp.erp.data.local.entity.WalletBalanceEntity>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(stringResource(R.string.wallet_balance), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            when (state) {
                is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                is UiState.Success -> {
                    Text(
                        text = formatIsk(state.data.balance),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "更新于 ${formatTimeAgo(state.data.lastUpdated)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is UiState.Error -> Text(
                    if (state.isOffline) stringResource(R.string.error_offline)
                    else stringResource(R.string.error_generic),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun BalanceHistoryCard(state: UiState<List<com.evecorp.erp.data.local.entity.BalanceSnapshotEntity>>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        when (state) {
            is UiState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无历史数据，同步后将开始记录",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    BalanceChart(
                        snapshots = state.data,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is UiState.Error -> Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CostIndexCard(state: UiState<com.evecorp.erp.data.local.entity.SystemCostIndexEntity>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (state) {
                is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                is UiState.Success -> {
                    val ci = state.data
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CostItem(stringResource(R.string.manufacturing), ci.manufacturing)
                        CostItem(stringResource(R.string.invention), ci.invention)
                        CostItem(stringResource(R.string.copying), ci.copying)
                        CostItem("材料研究", ci.researchingMaterialEfficiency)
                    }
                }
                is UiState.Error -> Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CostItem(label: String, value: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${String.format("%.2f", value * 100)}%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
