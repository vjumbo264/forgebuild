package com.forgebuild.forgecompanion

object PromptGenerator {
    private const val OWNER = "vjumbo264"
    private const val REPO = "forgebuild"

    private fun contractHead(contractType: String): String =
        "# ForgeBuild App Build Contract — $contractType\n\n" +
        "CREDENTIALS (operator fills before copying — never commit these values):\n" +
        "GITHUB_PAT = <PLACEHOLDER — fill in your fine-grained PAT before copying to an AI session. Recommended: NO delete_repo scope — the build session never deletes the repo.>\n" +
        "REPO = https://github.com/$OWNER/$REPO (the ONE ForgeBuild repo — engine, dashboard and every app live here)\n"

    private const val RULES = """You are one of many independent AI sessions working sequentially on this app. You have no reliable memory of previous sessions. The repository, git history, and per-app BUILD_STATE.json are the project's persistent memory. Your task is to determine what the repository says needs to be done next and do it — never stop just because part of the work is already done; never return a status report instead of work; never ask the operator what to do next; never wait for user input.

THE REPOSITORY IS THE SOURCE OF TRUTH. At session start: clone/pull vjumbo264/forgebuild, read this app's apps/<slug>/BUILD_STATE.json completely, resume from the first pending or in_progress item. Never reset existing progress, never redo work marked done, never overwrite previous releases.

PER-STEP COMMIT AND PUSH PROTOCOL (mandatory, never batched):
START STEP → PERFORM STEP → VALIDATE STEP → UPDATE apps/<slug>/BUILD_STATE.json → COMMIT → PUSH → VERIFY PUSH LANDED on origin/main → ONLY THEN start the next step. Every push must be verified via the GitHub API. "Done" = implemented + validated + checkpoint updated + committed + pushed + push verified.

PROMPT HISTORY is append-only at PROMPT_HISTORY/<slug>.md: append every operator instruction in order before acting on it.

BUILD QUALITY CONSTRAINTS (non-negotiable):
- Material 3 only: build the UI with ForgeBuildTheme (dynamic color, light/dark) and EngineIcons from engine/. Never emoji, never mismatched icon sets, never raw unstyled UI.
- The engine/ folder is a component/theme library AND the starting point: copy its content into your app folder, then write real Kotlin + Jetpack Compose code on top of the copy. Never modify engine/ itself UNLESS the operator's prompt explicitly says the change is an Engine-level fix — in that case apply it in engine/ AND propagate it into every apps/<slug>/ folder that carries a copy.
- Lightweight by default: keep R8 + resource shrinking on; add a dependency only when a feature truly needs it, and record the justification in BUILD_STATE.json notes.
- Proper adaptive icon: run tools/make_adaptive_icon.py (inside your app folder) with foreground artwork (operator-supplied if given, otherwise create simple artwork first). Never a square icon with white padding.
- Permissions: enable only what the app legitimately needs — manifest entries + com.forgebuild.engine.permissions.PermissionWiring (the full Android permission surface is available; see engine/ARCHITECTURE.md, and never declare a permission the app does not use).
- Live-data apps: any app that fetches or syncs data from a network source MUST use the Engine cache-first background-refresh pattern (com.forgebuild.engine.data.CacheFirstStore, documented in engine/ARCHITECTURE.md) — render instantly from local cache on open, then refresh in the background and reconcile. NEVER reload everything from scratch on every open.
- Sensitive screens: if the app description involves private/sensitive content on certain screens, use the Engine ScreenSecurity helper (FLAG_SECURE per-screen) on exactly those screens.
- Saving files: if the app saves or downloads files, use the Engine SafeSave helper (Storage Access Framework — user-directed, permission-scoped saves), NEVER Android's native DownloadManager/system-Downloads behavior.
- Slugs: if the operator's prompt gives a fixed APP_SLUG, use it exactly; if it says the slug is not predetermined, choose it yourself before creating anything, record it as the first field of apps/<slug>/BUILD_STATE.json, and treat it as permanent.
- Free tier only. No paid services. The operator works from an Android phone (Termux/browser) — no local-CLI assumptions in any docs you write.

SIGNING & RELEASES: release signing secrets (KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD) already exist as GitHub Actions secrets on vjumbo264/forgebuild — reuse them; only regenerate if explicitly told. To release: dispatch the "Build & Release App APK" workflow (Actions > workflow_dispatch, or POST /repos/vjumbo264/forgebuild/actions/workflows/release.yml/dispatches with ref=main and inputs app_path="apps/<slug>", release_notes). The workflow builds the folder, auto-increments the tag as <slug>-vN, and creates a GitHub Release on this repo with the APK + forgebuild-manifest.json. Never overwrite an existing release/tag — versions are <slug>-v1, <slug>-v2, ... monotonically.

STOPPING: only stop at a genuine execution boundary after committing/pushing an updated apps/<slug>/BUILD_STATE.json whose notes say exactly what is done, what remains, and the exact next operation — or when the release is fully verified live on the GitHub Releases API."""

    private fun helperHints(desc: String): String {
        val d = desc.lowercase()
        val hints = mutableListOf<String>()
        if (Regex("sensitiv|privat|secret|vault|lock|hidden|nsfw|confidential|password|journal|diary|intimate").containsMatchIn(d)) {
            hints.add("- SENSITIVE CONTENT detected in the description: use the Engine ScreenSecurity helper (com.forgebuild.engine.security.ScreenSecurity, FLAG_SECURE) on exactly the screens showing that content — per screen, not app-wide — so those screens cannot be screenshotted, screen-recorded or thumbnailed in recents.")
        }
        if (Regex("download|save|export|backup|file|pdf|csv|photo|image|video|music|offline copy").containsMatchIn(d)) {
            hints.add("- FILE SAVING detected in the description: use the Engine SafeSave helper (com.forgebuild.engine.files.SafeSave, Storage Access Framework: ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT_TREE) with the app's own save UI and user-picked destination. NEVER android.app.DownloadManager / the system Downloads folder.")
        }
        if (Regex("sync|fetch|feed|news|api|server|online|live|remote|refresh|updates from|from a repo").containsMatchIn(d)) {
            hints.add("- LIVE/REMOTE DATA detected in the description: you MUST use the Engine cache-first pattern (com.forgebuild.engine.data.CacheFirstStore) — instant render from local cache on open, background refresh + reconcile, never reload-from-scratch with a blocking spinner.")
        }
        return if (hints.isNotEmpty()) {
            "\nENGINE HELPERS RELEVANT TO THIS APP (standing Engine capabilities — use them, do not reinvent):\n" +
            hints.joinToString("\n") + "\n"
        } else ""
    }

    fun promptNewApp(desc: String, rawSlug: String?): String {
        val slug = rawSlug?.trim()?.lowercase()?.replace(Regex("[^a-z0-9-]+"), "-")?.trim('-')
        val pkg = "com.forgebuild." + (if (!slug.isNullOrEmpty()) slug.replace(Regex("[^a-z0-9]"), "") else "<slug with dashes removed>")
        val slugBlock = if (!slug.isNullOrEmpty()) {
            "APP_SLUG = $slug (FIXED — the operator chose this slug. Use it EXACTLY, character for character: the folder apps/$slug/, every release tag $slug-v1, $slug-v2, ..., PROMPT_HISTORY/$slug.md, and a top-level \"slug\": \"$slug\" field in apps/$slug/BUILD_STATE.json. Do NOT alter, shorten, translate or re-derive it.)\n"
        } else {
            "APP_SLUG = NOT PREDETERMINED — YOU (the building AI) CHOOSE IT. The operator left the slug blank on purpose: the Dashboard does NOT derive one from the description, and neither may any Dashboard-side code. Before creating anything, read APP_DESCRIPTION below, understand what the app is, and choose a short, clear, kebab-case slug (lowercase letters/digits/dashes, e.g. \"pomodoro-streaks\") that names the app. Check the repo first (GET /repos/$OWNER/$REPO/contents/apps) and pick a different slug if your choice collides with an existing folder. That slug is then PERMANENT for this app: the folder apps/<slug>/, every release tag (<slug>-v1, <slug>-v2, ...), PROMPT_HISTORY/<slug>.md, and every future session on this app all use exactly it. The very FIRST field of the very first apps/<slug>/BUILD_STATE.json you write must be \"slug\": \"<slug>\" so the Dashboard reads it back from the repository — it never computes or guesses a slug itself. Wherever this contract says <slug>, substitute your chosen value.\n"
        }

        val task1 = if (!slug.isNullOrEmpty()) {
            "1. Create apps/$slug/ if absent and copy engine/'s full content into it as the starting point (it is a folder in this repo, not a template to \"generate\" from). Add PROMPT_HISTORY/$slug.md and seed apps/$slug/BUILD_STATE.json — its first field must be \"slug\": \"$slug\" (exactly the operator-given value). Commit + push immediately as push-access proof."
        } else {
            "1. CHOOSE THE SLUG (see APP_SLUG above) — before anything else. Create apps/<slug>/ if absent and copy engine/'s full content into it as the starting point (it is a folder in this repo, not a template to \"generate\" from). Add PROMPT_HISTORY/<slug>.md and seed apps/<slug>/BUILD_STATE.json — its first field must be \"slug\": \"<slug>\" (the value you chose). Commit + push immediately as push-access proof."
        }

        return contractHead("NEW APP") +
            slugBlock +
            "APP_FOLDER = apps/<slug>/ (inside $OWNER/$REPO — do NOT create a new repository)\n" +
            "APP_DESCRIPTION (the operator's full intent — build exactly this):\n" +
            "\"\"\"\n$desc\n\"\"\"\n\n" +
            "TASKS (seed apps/<slug>/BUILD_STATE.json with these, all pending, then execute in order):\n" +
            "$task1\n" +
            "2. Set identity inside apps/<slug>/: namespace/applicationId in app/build.gradle.kts (derive a safe package, e.g. $pkg), app_name in res/values/strings.xml.\n" +
            "3. Adaptive icon via apps/<slug>/tools/make_adaptive_icon.py (create artwork if none supplied). Commit + push.\n" +
            "4. Confirm the repo-level signing secrets exist (see SIGNING & RELEASES); do not regenerate them.\n" +
            "5. Implement the app described in APP_DESCRIPTION, ONE FEATURE PER COMMIT/PUSH STEP, following the per-step protocol and quality constraints above.\n" +
            "6. Validate the build configuration, then release <slug>-v1 (see SIGNING & RELEASES: dispatch release.yml with app_path=apps/<slug>) and verify the release + APK asset exist via the GitHub Releases API on $OWNER/$REPO.\n" +
            "7. Set apps/<slug>/BUILD_STATE.json build_complete: true only after <slug>-v1 is verified live.\n" +
            helperHints(desc) +
            RULES
    }

    fun promptExtend(slug: String, instruction: String, latest: String?): String {
        return contractHead("EXTEND / UPDATE") +
            "APP_SLUG = $slug\n" +
            "APP_FOLDER = apps/$slug/ (inside $OWNER/$REPO)\n" +
            "CURRENT LATEST RELEASE = ${latest ?: "(check the Releases API filtered by prefix $slug-)"}\n" +
            "NEW INSTRUCTION FROM OPERATOR (apply exactly this; append it to PROMPT_HISTORY/$slug.md first):\n" +
            "\"\"\"\n$instruction\n\"\"\"\n\n" +
            "TASKS:\n" +
            "1. Clone/pull $OWNER/$REPO. Read apps/$slug/BUILD_STATE.json — if any task is in_progress, this prompt does not apply: switch to the RESUME contract (finish the in-progress work first).\n" +
            "2. Append the NEW INSTRUCTION to PROMPT_HISTORY/$slug.md with today's date. Commit + push.\n" +
            "3. Seed/refresh apps/$slug/BUILD_STATE.json tasks for this update (implement change → validate → release).\n" +
            "4. Implement the instruction following the per-step protocol and quality constraints below, working from the latest released source in apps/$slug/. If the change is an Engine-level fix, apply it in engine/ AND propagate it into apps/$slug/ (and note it).\n" +
            "5. Release the next version via the release.yml workflow (app_path=apps/$slug); the tag auto-increments to $slug-v(N+1). NEVER overwrite or delete any prior release. Verify the new release live via the API, then set build_complete: true.\n" +
            helperHints(instruction) +
            RULES
    }

    fun promptResume(slug: String): String {
        return contractHead("RESUME UNFINISHED BUILD") +
            "APP_SLUG = $slug\n" +
            "APP_FOLDER = apps/$slug/ (inside $OWNER/$REPO)\n" +
            "This is a RESUME contract. There is nothing new to build — the previous session was interrupted mid-build.\n\n" +
            "TASKS:\n" +
            "1. Clone/pull $OWNER/$REPO. Read apps/$slug/BUILD_STATE.json completely — it is the source of truth for where the build stopped.\n" +
            "2. Read PROMPT_HISTORY/$slug.md for the full history of operator intent.\n" +
            "3. Resume from the first task marked pending or in_progress (the notes field says exactly what was done, what remains, and the exact next operation). Do not redo tasks marked done.\n" +
            "4. Continue the per-step protocol below through to the release the in-progress version was targeting (dispatch release.yml with app_path=apps/$slug), verify the release live on the GitHub Releases API (tag prefix $slug-), then set build_complete: true.\n" +
            RULES
    }
}
