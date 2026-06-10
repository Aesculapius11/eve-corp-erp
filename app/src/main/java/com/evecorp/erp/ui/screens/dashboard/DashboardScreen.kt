package com.evecorp.erp.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evecorp.erp.R
import com.evecorp.erp.ui.UiState
import com.evecorp.erp.ui.formatIsk
import com.evecorp.erp.ui.formatTimeAgo
import com.evecorp.erp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.tab_dashboard),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    FilledIconButton(
                        onClick = { viewModel.refresh() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.btn_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 钱包余额卡片（渐变背景） ──
            item { BalanceHeroCard(uiState.balance) }

            // ── 30 天资产变化 ──
            item {
                SectionHeader(
                    icon = Icons.Outlined.ShowChart,
                    title = "30 天资产变化"
                )
            }
            item { BalanceHistoryCard(uiState.balanceHistory) }

            // ── 成本指数（可切换星系） ──
            item {
                CostIndexHeader(
                    systemName = uiState.selectedSystemName,
                    searchResults = uiState.systemSearchResults,
                    isSearching = uiState.isSearchingSystem,
                    onSearch = { viewModel.searchSystem(it) },
                    onSelectSystem = { id, name -> viewModel.selectSystem(id, name) },
                    onClearSearch = { viewModel.clearSearchResults() }
                )
            }
            item { CostIndexCard(uiState.costIndex) }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Section Header — Icon + Title
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  Cost Index Header — Clickable system name with search dialog
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CostIndexHeader(
    systemName: String,
    searchResults: List<SystemSearchResult>,
    isSearching: Boolean,
    onSearch: (String) -> Unit,
    onSelectSystem: (Long, String) -> Unit,
    onClearSearch: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                searchText = ""
                onClearSearch()
            },
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("切换星系", fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = {
                            searchText = it
                            onSearch(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("输入星系名称，如 Jita") },
                        singleLine = true,
                        trailingIcon = {
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (searchResults.isNotEmpty()) {
                        // 限制高度避免弹窗过大
                        Column(
                            modifier = Modifier.heightIn(max = 200.dp)
                        ) {
                            searchResults.forEach { result ->
                                Surface(
                                    onClick = {
                                        onSelectSystem(result.id, result.name)
                                        showDialog = false
                                        searchText = ""
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Transparent
                                ) {
                                    Text(
                                        text = result.name,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    } else if (searchText.length >= 2 && !isSearching) {
                        Text(
                            "未找到匹配的星系",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                    searchText = ""
                    onClearSearch()
                }) {
                    Text("取消")
                }
            }
        )
    }

    // 可点击的标题行
    Surface(
        onClick = { showDialog = true },
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Analytics,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = stringResource(R.string.cost_index),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            // 星系名称标签
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = systemName,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Icon(
                imageVector = Icons.Outlined.SwapHoriz,
                contentDescription = "切换星系",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Balance Hero Card — Gradient background, prominent ISK display
// ═══════════════════════════════════════════════════════════════

@Composable
private fun BalanceHeroCard(state: UiState<com.evecorp.erp.data.local.entity.WalletBalanceEntity>) {
    val gradientColors = listOf(Sky400, Sky500, Sky600)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.wallet_balance),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (state) {
                    is UiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                    }
                    is UiState.Success -> {
                        Text(
                            text = formatIsk(state.data.balance),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "更新于 ${formatTimeAgo(state.data.lastUpdated)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    is UiState.Error -> {
                        Text(
                            text = if (state.isOffline) stringResource(R.string.error_offline)
                            else stringResource(R.string.error_generic),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Balance History Card — Chart wrapper
// ═══════════════════════════════════════════════════════════════

@Composable
private fun BalanceHistoryCard(state: UiState<List<com.evecorp.erp.data.local.entity.BalanceSnapshotEntity>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        when (state) {
            is UiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(strokeWidth = 2.5.dp)
            }
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.BarChart,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "暂无历史数据，同步后将开始记录",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    BalanceChart(
                        snapshots = state.data,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
            is UiState.Error -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.error_generic),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Cost Index Card — Grid layout with accent colors
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CostIndexCard(state: UiState<com.evecorp.erp.data.local.entity.SystemCostIndexEntity>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        when (state) {
            is UiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(strokeWidth = 2.5.dp)
            }
            is UiState.Success -> {
                val ci = state.data
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CostItemCard(
                            label = stringResource(R.string.manufacturing),
                            value = ci.manufacturing,
                            color = Sky100,
                            textColor = Sky700,
                            modifier = Modifier.weight(1f)
                        )
                        CostItemCard(
                            label = stringResource(R.string.invention),
                            value = ci.invention,
                            color = Sakura100,
                            textColor = Sakura600,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CostItemCard(
                            label = stringResource(R.string.copying),
                            value = ci.copying,
                            color = Lavender100,
                            textColor = Lavender400,
                            modifier = Modifier.weight(1f)
                        )
                        CostItemCard(
                            label = "材料研究",
                            value = ci.researchingMaterialEfficiency,
                            color = Mint100,
                            textColor = Mint400,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            is UiState.Error -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.error_generic),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CostItemCard(
    label: String,
    value: Double,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${String.format("%.2f", value * 100)}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}
