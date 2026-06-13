package com.evecorp.erp.ui.screens.industry

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evecorp.erp.R
import com.evecorp.erp.ui.UiState
import com.evecorp.erp.ui.components.WaterfallItem
import com.evecorp.erp.ui.components.rememberWaterfallTrigger
import com.evecorp.erp.ui.components.triggerWaterfall
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndustryScreen(
    viewModel: IndustryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val waterfallTrigger = rememberWaterfallTrigger()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.refreshNotice?.id) {
        val notice = uiState.refreshNotice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(notice.message)
        viewModel.clearRefreshNotice(notice.id)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.tab_industry),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    FilledIconButton(
                        onClick = {
                            viewModel.refresh()
                            if (!uiState.isRefreshing) {
                                waterfallTrigger.triggerWaterfall()
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.btn_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 同步错误提示
            uiState.syncError?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "同步失败: $error",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Tab row
            TabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                IndustryTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (uiState.selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // 过滤器
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 状态过滤（可左右滑动）
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedStatus == null,
                        onClick = { viewModel.selectStatus(null) },
                        label = { Text("全部状态") }
                    )
                    FilterChip(
                        selected = uiState.selectedStatus == "active",
                        onClick = { viewModel.selectStatus("active") },
                        label = { Text("进行中") }
                    )
                    FilterChip(
                        selected = uiState.selectedStatus == "completed",
                        onClick = { viewModel.selectStatus("completed") },
                        label = { Text("已完成") }
                    )
                }

                // 角色过滤（可左右滑动）
                if (uiState.availableInstallers.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = uiState.selectedInstaller == null,
                            onClick = { viewModel.selectInstaller(null) },
                            label = { Text("全部角色") }
                        )
                        uiState.availableInstallers.forEach { installer ->
                            FilterChip(
                                selected = uiState.selectedInstaller == installer,
                                onClick = { viewModel.selectInstaller(if (uiState.selectedInstaller == installer) null else installer) },
                                label = { Text(installer, maxLines = 1) }
                            )
                        }
                    }
                }
            }

            // Content
            when (val jobs = uiState.jobs) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.5.dp)
                }
                is UiState.Success -> {
                    if (jobs.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.empty_jobs),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(jobs.data) { index, jobWith ->
                                WaterfallItem(index, waterfallTrigger.value) {
                                    IndustryJobCard(jobWith)
                                }
                            }
                            item { Spacer(Modifier.height(8.dp)) }
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
private fun IndustryJobCard(jobWith: IndustryJobWith) {
    val job = jobWith.job
    val activityLabel = when (job.activityType) {
        "manufacturing" -> "制造"
        "invention" -> "发明"
        "copying" -> "拷贝"
        "researching_time_efficiency" -> "时间研究"
        "researching_material_efficiency" -> "材料研究"
        else -> job.activityType
    }

    val now = System.currentTimeMillis()
    val progress = if (job.endDate > job.startDate) {
        ((now - job.startDate).toFloat() / (job.endDate - job.startDate).toFloat()).coerceIn(0f, 1f)
    } else 1f

    val timeLeft = job.endDate - now
    val timeLeftText = when {
        timeLeft <= 0 -> "已完成"
        timeLeft < 3600_000 -> "${timeLeft / 60_000} 分钟"
        timeLeft < 86400_000 -> "${timeLeft / 3600_000} 小时"
        else -> "${timeLeft / 86400_000} 天"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = activityLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    timeLeftText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "蓝图: ${jobWith.blueprintName}\n产品: ${jobWith.productName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "安装者: ${jobWith.installerName}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            // 进度条（底色 + 进度色重叠）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "剩余 runs: ${job.runs}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (job.activityType == "invention" && job.successfulRuns != null) {
                    Text(
                        "成功: ${job.successfulRuns}/${job.runs}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (job.successfulRuns > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    "设施: ${job.facilityId}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
