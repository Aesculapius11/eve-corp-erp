package com.evecorp.erp.ui.screens.bills

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
import com.evecorp.erp.data.local.entity.CorporationBillEntity
import com.evecorp.erp.ui.UiState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(
    viewModel: BillsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_bills)) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.btn_refresh))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                BillsTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label) }
                    )
                }
            }

            val bills = when (uiState.selectedTab) {
                BillsTab.UNPAID -> uiState.unpaidBills
                BillsTab.PAID -> uiState.paidBills
            }

            when (bills) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Success -> {
                    if (bills.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.empty_bills), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item { Spacer(Modifier.height(8.dp)) }
                            items(bills.data, key = { it.billId }) { bill ->
                                BillCard(bill)
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
private fun BillCard(bill: CorporationBillEntity) {
    val now = System.currentTimeMillis()
    val daysLeft = ((bill.dueDate - now) / 86400000).toInt()
    val isUrgent = !bill.paid && daysLeft < 3

    val billTypeLabel = when (bill.billType) {
        "corporation_starbase" -> "星际建筑燃料"
        "infrastructure_hub" -> "基础设施中心"
        "asset_safety" -> "资产安全"
        else -> bill.billType
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isUrgent) CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ) else CardDefaults.elevatedCardColors()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    billTypeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "到期：${formatDate(bill.dueDate)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!bill.paid) {
                    Text(
                        when {
                            daysLeft < 0 -> stringResource(R.string.bill_overdue)
                            daysLeft == 0 -> "今天到期"
                            else -> stringResource(R.string.bill_due_days, daysLeft)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (daysLeft < 3) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (daysLeft < 3) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatIsk(bill.amount),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (bill.paid) {
                    Text(
                        "已支付 ✅",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
