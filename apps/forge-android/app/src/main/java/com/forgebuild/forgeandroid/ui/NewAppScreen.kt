@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.forgebuild.forgeandroid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens

/** Type-1 prompt generator: describe an app, get the full ForgeBuild build contract to paste into a new AI session. */
@Composable
fun NewAppScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var description by remember { mutableStateOf("") }
    var fixedSlug by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    val margin = SpacingTokens.contentMargin(LocalConfiguration.current.screenWidthDp.dp)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Build new app", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(EngineIcons.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = margin)
        ) {
            Text(
                "Describe the app you want. This generates the full ForgeBuild build-contract prompt — " +
                    "copy it into a new AI session to have the app built, released and installable as an APK.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(SpacingTokens.Spacing.md))
            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    prompt = null
                },
                label = { Text("App description") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(SpacingTokens.Spacing.sm))
            OutlinedTextField(
                value = fixedSlug,
                onValueChange = {
                    fixedSlug = it
                    prompt = null
                },
                label = { Text("Slug (optional — builder chooses if blank)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(SpacingTokens.Spacing.md))
            Button(
                onClick = {
                    prompt = buildNewAppPrompt(description.trim(), fixedSlug.trim().ifBlank { null })
                    copied = false
                },
                enabled = description.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(EngineIcons.Bolt, contentDescription = null)
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Generate contract prompt")
            }
            prompt?.let { p ->
                Spacer(Modifier.height(SpacingTokens.Spacing.md))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Text(
                            p,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .padding(SpacingTokens.Spacing.sm)
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
                Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(p))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(EngineIcons.ContentCopy, contentDescription = null)
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(if (copied) "Copied" else "Copy prompt")
                }
                Spacer(Modifier.height(SpacingTokens.Spacing.lg))
            }
        }
    }
}

/** The full NEW-APP build contract, mirroring the dashboard's generator (slug ownership lives with the building AI). */
fun buildNewAppPrompt(description: String, fixedSlug: String?): String {
    val slugBlock = if (fixedSlug != null) {
        "APP_SLUG = $fixedSlug (operator-given — use exactly; never derive or alter it)"
    } else {
        "APP_SLUG = NOT PREDETERMINED — YOU (the building AI) CHOOSE IT: read APP_DESCRIPTION, pick a short " +
            "kebab-case slug, check apps/ in the repo for collisions, treat it as permanent (folder apps/<slug>/, " +
            "tags <slug>-vN, PROMPT_HISTORY/<slug>.md), and write it as the FIRST field of the first " +
            "apps/<slug>/BUILD_STATE.json."
    }
    return """
# ForgeBuild App Build Contract — NEW APP

CREDENTIALS (operator fills before copying — never commit these values):
GITHUB_PAT = <operator fills — fine-grained PAT, Contents + Actions read/write on vjumbo264/forgebuild>

REPO = https://github.com/vjumbo264/forgebuild (the ONE ForgeBuild repo — engine, dashboard and every app live here)
$slugBlock
APP_FOLDER = apps/<slug>/ (inside vjumbo264/forgebuild — do NOT create a new repository)
APP_DESCRIPTION (the operator's full intent — build exactly this):
$description

TASKS (seed apps/<slug>/BUILD_STATE.json with these, all pending, then execute in order):
1. If the slug is not predetermined, choose it first (see APP_SLUG). Create apps/<slug>/ if absent and copy engine/'s full content into it. Add PROMPT_HISTORY/<slug>.md and seed apps/<slug>/BUILD_STATE.json — first field "slug". Commit + push immediately as push-access proof.
2. Set identity: namespace/applicationId in app/build.gradle.kts (com.forgebuild.<slug with dashes removed>), app_name in res/values/strings.xml.
3. Adaptive icon via apps/<slug>/tools/make_adaptive_icon.py (create artwork if none supplied). Commit + push.
4. Confirm the repo-level signing secrets exist; do not regenerate them.
5. Implement the app, ONE FEATURE PER COMMIT/PUSH, per the protocol below.
6. Validate the build configuration, then release <slug>-v1: POST /repos/vjumbo264/forgebuild/actions/workflows/release.yml/dispatches with ref=main and inputs app_path="apps/<slug>". Verify the release + APK asset exist via the GitHub Releases API.
7. Set apps/<slug>/BUILD_STATE.json build_complete: true only after <slug>-v1 is verified live.

THE REPOSITORY IS THE SOURCE OF TRUTH. At every session start: clone/pull vjumbo264/forgebuild, read apps/<slug>/BUILD_STATE.json completely, resume from the first pending or in_progress item. Never reset existing progress, never redo done work, never overwrite previous releases, never ask the operator what to do next.

PER-STEP COMMIT AND PUSH PROTOCOL (mandatory, never batched): START STEP -> PERFORM STEP -> VALIDATE STEP -> UPDATE apps/<slug>/BUILD_STATE.json -> COMMIT -> PUSH -> VERIFY PUSH LANDED on origin/main via the GitHub API -> ONLY THEN start the next step.

PROMPT HISTORY is append-only at PROMPT_HISTORY/<slug>.md: append every operator instruction in order before acting on it.

BUILD QUALITY CONSTRAINTS (non-negotiable):
- Material 3 Expressive only, via the Engine: ForgeBuildTheme (dynamic color, light/dark), EngineIcons (never emoji), SpacingTokens / ElevationTokens / ShapeTokens / TypographyTokens / MotionTokens, and the expressive components (EngineLinearWavyProgress / EngineCircularWavyProgress / EngineLoadingIndicator / EngineButtonGroup / EngineSplitButton / EngineFabMenu). Never hardcode hex colors or ad-hoc dp.
- engine/ is a read-only library; modify only the copy inside apps/<slug>/ (unless the operator explicitly calls a change an Engine-level fix, then propagate to every apps/<slug>/ copy).
- Lightweight: R8 + resource shrinking stay on; add a dependency only when truly needed and record the justification in BUILD_STATE.json.
- Permissions: enable only what the app uses (manifest + com.forgebuild.engine.permissions.PermissionWiring).
- Live-data apps must use the Engine cache-first pattern (com.forgebuild.engine.data.CacheFirstStore): render instantly from cache on open, refresh in the background, never reload from scratch.
- Sensitive screens: Engine ScreenSecurity (per-screen FLAG_SECURE). Saving files: Engine SafeSave (SAF), never DownloadManager.
- NO LIBRARY SUBSTITUTION: never hand-roll approximations of real official library components; if something is genuinely unavailable, record a "substitution" entry in BUILD_STATE.json (what, why unavailable, what was used instead). Silent substitution is never allowed.
- Free tier only. The operator works from an Android phone (Termux/browser) — no local-CLI assumptions in docs.

SIGNING & RELEASES: secrets KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD already exist as GitHub Actions secrets — reuse them. Releases are <slug>-v1, -v2, ... monotonic; never overwrite an existing release/tag.

STOPPING: only stop at a genuine execution boundary after committing/pushing an updated BUILD_STATE.json whose notes say exactly what is done, what remains, and the exact next operation — or when the release is fully verified live on the GitHub Releases API.
""".trim()
}
