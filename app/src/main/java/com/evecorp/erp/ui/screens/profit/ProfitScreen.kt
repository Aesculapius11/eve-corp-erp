package com.evecorp.erp.ui.screens.profit

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import org.mozilla.geckoview.GeckoView

private const val ProfitUrl = "http://www.ceve-industry.cn/#/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfitScreen(
    viewModel: ProfitViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val progress by viewModel.progress.collectAsState()
    val canGoBack by viewModel.canGoBack.collectAsState()
    val canGoForward by viewModel.canGoForward.collectAsState()
    val prompt by viewModel.prompt.collectAsState()

    DisposableEffect(viewModel) {
        viewModel.setVisible(true)
        onDispose {
            viewModel.setVisible(false)
        }
    }

    BackHandler(enabled = canGoBack) {
        viewModel.goBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    FilledIconButton(
                        onClick = viewModel::goBack,
                        enabled = canGoBack,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    FilledIconButton(
                        onClick = viewModel::goForward,
                        enabled = canGoForward,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward"
                        )
                    }
                    FilledIconButton(
                        onClick = viewModel::reload,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    FilledIconButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ProfitUrl)))
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = "Open in browser")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                factory = { androidContext ->
                    GeckoView(androidContext).apply {
                        viewModel.attachTo(this)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { geckoView ->
                    viewModel.attachTo(geckoView)
                }
            )

            if (progress in 0..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        prompt?.let { dialog ->
            AlertDialog(
                onDismissRequest = viewModel::dismissPrompt,
                title = dialog.title?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                text = { Text(dialog.message) },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmPrompt) {
                        Text(dialog.confirmLabel)
                    }
                },
                dismissButton = dialog.dismissLabel?.let { label ->
                    {
                        TextButton(onClick = viewModel::dismissPrompt) {
                            Text(label)
                        }
                    }
                }
            )
        }
    }
}
