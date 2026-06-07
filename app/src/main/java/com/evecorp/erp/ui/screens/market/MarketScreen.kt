package com.evecorp.erp.ui.screens.market

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import com.evecorp.erp.data.local.entity.MarketOrderEntity
import com.evecorp.erp.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    viewModel: MarketViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_market)) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.btn_refresh))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Tab row
            TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                MarketTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label) }
                    )
                }
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

            // Content
            val orders = when (uiState.selectedTab) {
                MarketTab.SELL -> uiState.sellOrders
                MarketTab.BUY -> uiState.buyOrders
            }

            when (orders) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Success -> {
                    if (orders.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.empty_orders), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item { Spacer(Modifier.height(4.dp)) }
                            items(orders.data, key = { it.orderId }) { order ->
                                MarketOrderCard(order)
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
private fun MarketOrderCard(order: MarketOrderEntity) {
    val volumePercent = if (order.volumeTotal > 0) {
        order.volumeRemain.toFloat() / order.volumeTotal.toFloat()
    } else 0f

    val daysLeft = order.duration - ((System.currentTimeMillis() - order.issued) / 86400000).toInt()

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "物品 ID: ${order.typeId}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (order.isBuyOrder) "买单" else "卖单",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (order.isBuyOrder) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("单价", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatIsk(order.price),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("剩余/总量", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${formatNumber(order.volumeRemain)} / ${formatNumber(order.volumeTotal)}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { volumePercent },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "站点: ${order.locationId}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (daysLeft > 0) "剩余 ${daysLeft} 天" else "已过期",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (daysLeft <= 3) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Buy order extra info
            if (order.isBuyOrder) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "范围: ${order.range ?: "station"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (order.minVolume > 1) {
                        Text(
                            "最小成交量: ${order.minVolume}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

private fun formatNumber(n: Int): String {
    return when {
        n >= 1_000_000 -> "${String.format("%.1f", n / 1_000_000.0)}M"
        n >= 1_000 -> "${String.format("%.0f", n / 1_000.0)}K"
        else -> n.toString()
    }
}
