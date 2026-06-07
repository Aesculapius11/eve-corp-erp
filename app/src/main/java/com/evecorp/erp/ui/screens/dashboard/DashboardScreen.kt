package com.evecorp.erp.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.text.SimpleDateFormat
import java.util.*

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

            // Wallet Balance Card
            item {
                BalanceCard(uiState.balance)
            }

            // Recent Journal
            item {
                Text(
                    stringResource(R.string.recent_journal),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            item {
                JournalCard(uiState.journal)
            }

            // Cost Index
            item {
                Text(
                    stringResource(R.string.cost_index),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            item {
                CostIndexCard(uiState.costIndex)
            }

            // Unpaid Bills
            item {
                Text(
                    stringResource(R.string.unpaid_bills),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            item {
                BillsCard(uiState.bills)
            }

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
private fun JournalCard(state: UiState<List<com.evecorp.erp.data.local.entity.WalletJournalEntity>>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (state) {
                is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        Text(stringResource(R.string.empty_bills), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.data.take(10).forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.refType, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        formatDate(entry.date),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    formatIsk(entry.amount),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = if (entry.amount >= 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                            if (entry != state.data.last()) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                is UiState.Error -> Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
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

@Composable
private fun BillsCard(state: UiState<List<com.evecorp.erp.data.local.entity.CorporationBillEntity>>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (state) {
                is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        Text("暂无未支付账单 ✅", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.data.take(5).forEach { bill ->
                            val daysLeft = ((bill.dueDate - System.currentTimeMillis()) / 86400000).toInt()
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bill.billType, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        if (daysLeft >= 0) stringResource(R.string.bill_due_days, daysLeft)
                                        else stringResource(R.string.bill_overdue),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (daysLeft < 3) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    formatIsk(bill.amount),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                is UiState.Error -> Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatIsk(amount: Double): String {
    return when {
        amount >= 1_000_000_000 -> "${String.format("%.2f", amount / 1_000_000_000)}B ISK"
        amount >= 1_000_000 -> "${String.format("%.1f", amount / 1_000_000)}M ISK"
        amount >= 1_000 -> "${String.format("%.0f", amount / 1_000)}K ISK"
        else -> "${String.format("%.2f", amount)} ISK"
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000} 分钟前"
        diff < 86400_000 -> "${diff / 3600_000} 小时前"
        else -> "${diff / 86400_000} 天前"
    }
}
