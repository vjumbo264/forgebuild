@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.forgebuild.forgeandroid.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.components.EngineLoadingIndicator
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.forgeandroid.data.AppDetail
import com.forgebuild.forgeandroid.data.formatBytes

/** App page: releases with APK/manifest downloads, live BUILD_STATE.json, prompt history. */
@Composable
fun AppDetailScreen(
    detail: AppDetail?,
    refreshing: Boolean,
    lastError: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val margin = SpacingTokens.contentMargin(LocalConfiguration.current.screenWidthDp.dp)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.slug ?: "App", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(EngineIcons.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (refreshing) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            EngineLoadingIndicator(Modifier.size(28.dp))
                        }
                    }
                    IconButton(onClick = onRefresh) { Icon(EngineIcons.Refresh, contentDescription = "Refresh") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Releases") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Build state") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("History") })
            }
            if (lastError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = margin, vertical = SpacingTokens.Spacing.xs)
                ) {
                    Text(
                        "Couldn't refresh — showing cached data. $lastError",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(SpacingTokens.Spacing.sm)
                    )
                }
            }
            when (tab) {
                0 -> ReleasesTab(detail, margin)
                1 -> CodePane(detail?.buildStateJson ?: "No BUILD_STATE.json found for this app.", margin)
                else -> CodePane(detail?.promptHistoryMd ?: "No prompt history found for this app.", margin)
            }
        }
    }
}

@Composable
private fun ReleasesTab(detail: AppDetail?, margin: Dp) {
    val context = LocalContext.current
    val releases = detail?.releases.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = margin, vertical = SpacingTokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)
    ) {
        if (releases.isEmpty()) {
            item {
                Text(
                    "No releases yet for this app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(releases) { rel ->
            ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(SpacingTokens.Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(rel.tag, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            rel.publishedAt.take(10),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (rel.notes.isNotBlank()) {
                        Spacer(Modifier.height(SpacingTokens.Spacing.xs))
                        Text(rel.notes.take(600), style = MaterialTheme.typography.bodyMedium)
                    }
                    rel.assets.forEach { asset ->
                        Spacer(Modifier.height(SpacingTokens.Spacing.xs))
                        Surface(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(asset.downloadUrl)))
                            },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(
                                    horizontal = SpacingTokens.Spacing.sm,
                                    vertical = SpacingTokens.Spacing.xs
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    EngineIcons.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(Modifier.width(SpacingTokens.Spacing.xs))
                                Text(
                                    asset.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    formatBytes(asset.sizeBytes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodePane(text: String, margin: Dp) {
    val v = rememberScrollState()
    val h = rememberScrollState()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = margin, vertical = SpacingTokens.Spacing.md)
    ) {
        SelectionContainer {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(v)
                    .horizontalScroll(h)
                    .padding(SpacingTokens.Spacing.sm)
            )
        }
    }
}
