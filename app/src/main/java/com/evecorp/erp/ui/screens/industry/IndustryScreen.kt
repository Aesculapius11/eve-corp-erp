package com.evecorp.erp.ui.screens.industry

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
fun IndustryScreen(
    viewModel: IndustryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_industry)) },
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
                IndustryTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label) }
                    )
                }
            }

            // Content
            when (val jobs = uiState.jobs) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Success -> {
                    if (jobs.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.empty_jobs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item { Spacer(Modifier.height(8.dp)) }
                            items(jobs.data, key = { it.jobId }) { job ->
                                IndustryJobCard(job)
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
private fun IndustryJobCard(job: com.evecorp.erp.data.local.entity.IndustryJobEntity) {
    val activityLabel = when (job.activityType) {
        "manufacturing" -> "🔧 制造"
        "invention" -> "🔬 发明"
        "copying" -> "📋 拷贝"
        "researching_time_efficiency" -> "⏱ 时间研究"
        "researching_material_efficiency" -> "📐 材料研究"
        else -> "❓ ${job.activityType}"
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

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(activityLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(timeLeftText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "蓝图 ID: ${job.blueprintTypeId}  •  产品 ID: ${job.productTypeId ?: "—"}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("剩余 runs: ${job.runs}", style = MaterialTheme.typography.labelMedium)
                if (job.activityType == "invention" && job.successfulRuns != null) {
                    Text(
                        "成功: ${job.successfulRuns}/${job.runs}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (job.successfulRuns > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
                Text("设施: ${job.facilityId}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
