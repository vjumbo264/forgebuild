package com.forgebuild.forgecompanion

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.icons.EngineIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    gitHubService: GitHubService,
    onNavigateToApps: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var description by remember { mutableStateOf("") }
    var slug by remember { mutableStateOf("") }
    var generatedPrompt by remember { mutableStateOf<String?>(null) }
    var showPatDialog by remember { mutableStateOf(false) }

    var isConnected by remember { mutableStateOf(gitHubService.isConnected()) }

    if (showPatDialog) {
        PatDialog(
            currentPat = gitHubService.getPat(),
            onSave = { token ->
                gitHubService.setPat(token)
                isConnected = gitHubService.isConnected()
                showPatDialog = false
                onShowMessage("GitHub PAT saved successfully")
            },
            onClear = {
                gitHubService.clearPat()
                isConnected = false
                showPatDialog = false
                onShowMessage("GitHub PAT cleared")
            },
            onDismiss = { showPatDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            EngineIcons.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("ForgeBuild Companion", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToApps) {
                        Icon(EngineIcons.Apps, contentDescription = "View Apps")
                    }
                    IconButton(onClick = { showPatDialog = true }) {
                        Icon(EngineIcons.Settings, contentDescription = "Settings / PAT")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(8.dp))

            // PAT Status Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isConnected) "Connected to vjumbo264/forgebuild" else "No PAT connected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isConnected) "Authenticated with GitHub PAT" else "Tap Settings to add your fine-grained token",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showPatDialog = true }) {
                        Text(if (isConnected) "Configure" else "Connect")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Quick navigation to apps list
            OutlinedCard(
                onClick = onNavigateToApps,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            EngineIcons.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Manage Existing Apps", style = MaterialTheme.typography.titleMedium)
                            Text("Browse releases, APKs, prompt history, and versions", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Icon(EngineIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(24.dp))

            // New App Prompt Form
            Text(
                text = "Create a New App",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Describe what you want to build. Generate the Type-1 build contract to copy into a new AI session.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("App Description (required)") },
                placeholder = {
                    Text("e.g. A Pomodoro streak tracker with customizable intervals, audio chimes, and persistent local daily logs...")
                },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = slug,
                onValueChange = { slug = it },
                label = { Text("App Slug (optional)") },
                placeholder = { Text("Leave blank to let AI choose it (e.g. pomodoro-streaks)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("If left blank, the building AI will select an unused slug per protocol.")
                }
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (description.isNotBlank()) {
                        generatedPrompt = PromptGenerator.promptNewApp(
                            desc = description.trim(),
                            rawSlug = slug.takeIf { it.isNotBlank() }
                        )
                        onShowMessage("Build prompt generated!")
                    }
                },
                enabled = description.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(EngineIcons.Bolt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Generate Type-1 Build Prompt")
            }

            // Display Generated Prompt Card
            if (generatedPrompt != null) {
                Spacer(Modifier.height(24.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Type-1 Build Contract",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("ForgeBuild Prompt", generatedPrompt)
                                    clipboard.setPrimaryClip(clip)
                                    onShowMessage("Contract copied to clipboard!")
                                }
                            ) {
                                Icon(EngineIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy Prompt")
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Fill in your GITHUB_PAT before handing this prompt to a new AI session. The AI will build, test, and release <slug>-v1 automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = generatedPrompt ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
