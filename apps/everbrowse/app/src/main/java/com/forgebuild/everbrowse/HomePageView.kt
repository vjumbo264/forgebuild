package com.forgebuild.everbrowse

import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens

private data class QuickShortcut(
    val name: String,
    val url: String,
    val icon: ImageVector,
    val category: String
)

@Composable
fun HomePageView(
    onSearch: (String) -> Unit,
    onOpenTabs: () -> Unit,
    onNewTab: () -> Unit,
    onOpenKeepAlive: () -> Unit,
    needsKeepAliveSetup: Boolean,
    modifier: Modifier = Modifier
) {
    var searchInput by remember { mutableStateOf("") }
    val s = SpacingTokens.Spacing
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val submitSearch: (String) -> Unit = { query ->
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            keyboardController?.hide()
            focusManager.clearFocus()
            onSearch(trimmed)
        }
    }

    val shortcuts = remember {
        listOf(
            QuickShortcut("Google", "https://www.google.com", EngineIcons.Search, "Search"),
            QuickShortcut("Wikipedia", "https://www.wikipedia.org", EngineIcons.Language, "Reference"),
            QuickShortcut("GitHub", "https://github.com", EngineIcons.Code, "Development"),
            QuickShortcut("DuckDuckGo", "https://duckduckgo.com", EngineIcons.Security, "Privacy"),
            QuickShortcut("Reddit", "https://www.reddit.com", EngineIcons.Forum, "Community"),
            QuickShortcut("Hacker News", "https://news.ycombinator.com", EngineIcons.Newspaper, "Tech News")
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = s.md, vertical = s.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(s.md)
    ) {
        Spacer(modifier = Modifier.height(s.sm))

        // --- Brand Header ---
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(68.dp)
        ) {
            Icon(
                imageVector = EngineIcons.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(s.sm)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "EverBrowse",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(s.xxs))
            Text(
                text = "Ultra-persistent · Distraction-free",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(s.xs))

        // --- Central Search Bar (Never cropped, vertically centered) ---
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = s.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = EngineIcons.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(s.xs))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchInput.isEmpty()) {
                        Text(
                            text = "Search or type web address…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            submitSearch(searchInput)
                        })
                    )
                }
                if (searchInput.isNotBlank()) {
                    IconButton(onClick = { submitSearch(searchInput) }) {
                        Icon(
                            imageVector = EngineIcons.ArrowForward,
                            contentDescription = "Go",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // --- Keep-Alive Notification Card if permissions missing ---
        if (needsKeepAliveSetup) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(s.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        EngineIcons.Alarm,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(s.xs))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Keep-Alive Setup Required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Grant exact alarms & battery exemption so pages never refresh.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    FilledTonalButton(
                        onClick = onOpenKeepAlive,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Setup")
                    }
                }
            }
        } else {
            // Keep-alive status badge (Active)
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = s.sm, vertical = s.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        EngineIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(s.xs))
                    Text(
                        text = "Anti-refresh keep-alive active",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Protected",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(s.xs))

        // --- Speed Dial / Quick Shortcuts with Real Icons ---
        Text(
            text = "Frequently Visited",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(s.xs)
        ) {
            shortcuts.take(3).forEach { shortcut ->
                ShortcutItem(
                    shortcut = shortcut,
                    onClick = { submitSearch(shortcut.url) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(s.xs)
        ) {
            shortcuts.drop(3).take(3).forEach { shortcut ->
                ShortcutItem(
                    shortcut = shortcut,
                    onClick = { submitSearch(shortcut.url) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(s.sm))

        // --- Quick Tab & Navigation Controls ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(s.xs)
        ) {
            FilledTonalButton(
                onClick = onNewTab,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(EngineIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(s.xxs))
                Text("New Tab")
            }
            FilledTonalButton(
                onClick = onOpenTabs,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(EngineIcons.Menu, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(s.xxs))
                Text("Open Tabs")
            }
        }
    }
}

@Composable
private fun ShortcutItem(
    shortcut: QuickShortcut,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = SpacingTokens.Spacing
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = s.sm, horizontal = s.xs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = shortcut.icon,
                        contentDescription = shortcut.name,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.height(s.xxs))
            Text(
                text = shortcut.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = shortcut.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
