@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.forgebuild.forgeandroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.components.EngineLoadingIndicator
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens

/** Home screen: every app in apps/ of the ForgeBuild repo (cache-first, background refresh). */
@Composable
fun AppsListScreen(
    apps: List<String>,
    refreshing: Boolean,
    lastError: String?,
    hasPat: Boolean,
    onOpen: (String) -> Unit,
    onNewApp: () -> Unit,
    onRefresh: () -> Unit,
    onSavePat: (String?) -> Unit,
) {
    var patDialog by remember { mutableStateOf(false) }
    val margin = SpacingTokens.contentMargin(LocalConfiguration.current.screenWidthDp.dp)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ForgeBuild", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "apps in vjumbo264/forgebuild",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (refreshing) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            EngineLoadingIndicator(Modifier.size(28.dp))
                        }
                    }
                    IconButton(onClick = onRefresh) { Icon(EngineIcons.Refresh, contentDescription = "Refresh apps") }
                    IconButton(onClick = { patDialog = true }) { Icon(EngineIcons.Settings, contentDescription = "GitHub token") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewApp,
                icon = { Icon(EngineIcons.Add, contentDescription = null) },
                text = { Text("Build new app") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = margin,
                end = margin,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + SpacingTokens.Spacing.xxxl + SpacingTokens.Spacing.lg
            ),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xxs)
        ) {
            if (lastError != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Couldn't refresh — showing cached data. $lastError",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(SpacingTokens.Spacing.sm)
                        )
                    }
                }
            }
            items(apps) { slug ->
                ListItem(
                    headlineContent = { Text(slug, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text("Releases, build state & prompt history", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(EngineIcons.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { Icon(EngineIcons.ChevronRight, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(slug) }
                )
            }
            if (apps.isEmpty() && !refreshing) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = SpacingTokens.Spacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(EngineIcons.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(SpacingTokens.Spacing.md))
                        Text("No apps cached yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(SpacingTokens.Spacing.xs))
                        Text(
                            "Pull to refresh using the action above — the list is fetched from the ForgeBuild repo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (patDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { patDialog = false },
            title = { Text("GitHub token") },
            text = {
                Column {
                    Text(
                        "Optional personal access token, used only for GitHub API requests from this device " +
                            "and stored locally (mirrors the dashboard's one-time-PAT model). " +
                            "Public repo data works without one.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        label = { Text(if (hasPat) "Token saved — paste to replace" else "github_pat_…") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) onSavePat(text)
                    patDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onSavePat(null)
                    patDialog = false
                }) { Text("Clear") }
            }
        )
    }
}
