package com.forgebuild.aandroidversionofforgebuild

import java.util.Locale

object ContractGenerator {
    const val OWNER = "vjumbo264"
    const val REPO = "forgebuild"
    private val TQ = String(charArrayOf('"', '"', '"'))

    private fun contractHead(kind: String): String = buildString {
        appendLine("# ForgeBuild App Build Contract — " + kind)
        appendLine()
        appendLine("CREDENTIALS (operator fills before copying — never commit these values):")
        appendLine("GITHUB_PAT = <PLACEHOLDER — operator: create a fine-grained GitHub PAT scoped to ONLY the repository " + OWNER + "/" + REPO + ", permissions: Contents = Read and Write, Actions = Read and Write. Paste it here before handing this prompt to an AI session. Recommended: NO delete_repo scope — the build session never deletes the repo.>")
        appendLine()
        appendLine("REPO = https://github.com/" + OWNER + "/" + REPO + " (the ONE ForgeBuild repo — engine, dashboard and every app live here)")
    }

    private fun appendRules(sb: java.lang.StringBuilder) {
        sb.appendLine()
        sb.appendLine("You are one of many independent AI sessions working sequentially on this app. You have no reliable memory of previous sessions. The repository, git history, and per-app BUILD_STATE.json are the project's persistent memory. Your task is to determine what the repository says needs to be done next and do it — never stop just because part of the work is already done; never return a status report instead of work; never ask the operator what to do next; never wait for user input.")
        sb.appendLine()
        sb.appendLine("THE REPOSITORY IS THE SOURCE OF TRUTH. At session start: clone/pull " + OWNER + "/" + REPO + ", read this app's apps/<slug>/BUILD_STATE.json completely, resume from the first pending or in_progress item. Never reset existing progress, never redo work marked done, never overwrite previous releases.")
        sb.appendLine()
        sb.appendLine("PER-STEP COMMIT AND PUSH PROTOCOL (mandatory, never batched):")
        sb.appendLine("START STEP → PERFORM STEP → VALIDATE STEP → UPDATE apps/<slug>/BUILD_STATE.json → COMMIT → PUSH → VERIFY PUSH LANDED on origin/main → ONLY THEN start the next step. Every push must be verified via the GitHub API. 'Done' = implemented + validated + checkpoint updated + committed + pushed + push verified.")
        sb.appendLine()
        sb.appendLine("PROMPT HISTORY is append-only at PROMPT_HISTORY/<slug>.md: append every operator instruction in order before acting on it.")
        sb.appendLine()
        sb.appendLine("BUILD QUALITY CONSTRAINTS (non-negotiable):")
        sb.appendLine("- Material 3 only: build the UI with ForgeBuildTheme (dynamic color, light/dark) and EngineIcons from engine/. Never emoji, never mismatched icon sets, never raw unstyled UI.")
        sb.appendLine("- The engine/ folder is a component/theme library AND the starting point: copy its content into your app folder, then write real Kotlin + Jetpack Compose code on top of the copy. Never modify engine/ itself UNLESS the operator's prompt explicitly says the change is an Engine-level fix — in that case apply it in engine/ AND propagate it into every apps/<slug>/ folder that carries a copy.")
        sb.appendLine("- Lightweight by default: keep R8 + resource shrinking on; add a dependency only when a feature truly needs it, and record the justification in BUILD_STATE.json notes.")
        sb.appendLine("- Proper adaptive icon: run tools/make_adaptive_icon.py (inside your app folder) with foreground artwork (operator-supplied if given, otherwise create simple artwork first). Never a square icon with white padding.")
        sb.appendLine("- Permissions: enable only what the app legitimately needs — manifest entries + com.forgebuild.engine.permissions.PermissionWiring (STORAGE, CAMERA, NOTIFICATIONS, FOREGROUND_SERVICE, BACKGROUND_LOCATION, DEVICE_ADMIN available).")
        sb.appendLine("- Free tier only. No paid services. The operator works from an Android phone (Termux/browser) — no local-CLI assumptions in any docs you write.")
        sb.appendLine()
        sb.appendLine("SIGNING & RELEASES: release signing secrets (KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD) already exist as GitHub Actions secrets on " + OWNER + "/" + REPO + " — reuse them; only regenerate if explicitly told. To release: dispatch the 'Build & Release App APK' workflow (Actions > workflow_dispatch, or POST /repos/" + OWNER + "/" + REPO + "/actions/workflows/release.yml/dispatches with ref=main and inputs app_path='apps/<slug>', release_notes). The workflow builds the folder, auto-increments the tag as <slug>-vN, and creates a GitHub Release on this repo with the APK + forgebuild-manifest.json. Never overwrite an existing release/tag — versions are <slug>-v1, <slug>-v2, ... monotonically.")
        sb.appendLine()
        sb.appendLine("STOPPING: only stop at a genuine execution boundary after committing/pushing an updated apps/<slug>/BUILD_STATE.json whose notes say exactly what is done, what remains, and the exact next operation — or when the release is fully verified live on the GitHub Releases API.")
    }

    fun slugify(text: String): String {
        val s = text.trim().lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9 -]"), "")
            .replace(Regex("[ _]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
        val first30 = if (s.length > 30) s.substring(0, 30).trimEnd('-') else s
        return first30.ifEmpty { "my-app" }
    }

    fun promptNewApp(desc: String, slug: String): String = buildString {
        val safePkg = "com.forgebuild." + slug.replace(Regex("[^a-z0-9]"), "").ifEmpty { "app" }
        append(contractHead("NEW APP"))
        appendLine("APP_SLUG = " + slug)
        appendLine("APP_FOLDER = apps/" + slug + "/ (inside " + OWNER + "/" + REPO + " — do NOT create a new repository)")
        appendLine("APP_DESCRIPTION (the operator's full intent — build exactly this):")
        appendLine(TQ)
        appendLine(desc)
        appendLine(TQ)
        appendLine()
        appendLine("TASKS (seed apps/" + slug + "/BUILD_STATE.json with these, all pending, then execute in order):")
        appendLine("1. Create apps/" + slug + "/ if absent and copy engine/'s full content into it as the starting point (it is a folder in this repo, not a template to 'generate' from). Add PROMPT_HISTORY/" + slug + ".md and seed apps/" + slug + "/BUILD_STATE.json. Commit + push immediately as push-access proof.")
        appendLine("2. Set identity inside apps/" + slug + "/: namespace/applicationId in app/build.gradle.kts (derive a safe package, e.g. " + safePkg + "), app_name in res/values/strings.xml.")
        appendLine("3. Adaptive icon via apps/" + slug + "/tools/make_adaptive_icon.py (create artwork if none supplied). Commit + push.")
        appendLine("4. Confirm the repo-level signing secrets exist (see SIGNING & RELEASES); do not regenerate them.")
        appendLine("5. Implement the app described in APP_DESCRIPTION, ONE FEATURE PER COMMIT/PUSH STEP, following the per-step protocol and quality constraints above.")
        appendLine("6. Validate the build configuration, then release " + slug + "-v1 (see SIGNING & RELEASES: dispatch release.yml with app_path=apps/" + slug + ") and verify the release + APK asset exist via the GitHub Releases API on " + OWNER + "/" + REPO + ".")
        appendLine("7. Set apps/" + slug + "/BUILD_STATE.json build_complete: true only after " + slug + "-v1 is verified live.")
        appendRules(this)
    }

    fun promptExtend(slug: String, instruction: String, latest: String): String = buildString {
        val relText = if (latest.isEmpty()) "(check the Releases API filtered by prefix " + slug + "-)" else latest
        append(contractHead("EXTEND / UPDATE"))
        appendLine("APP_SLUG = " + slug)
        appendLine("APP_FOLDER = apps/" + slug + "/ (inside " + OWNER + "/" + REPO + ")") 
        appendLine("CURRENT LATEST RELEASE = " + relText)
        appendLine("NEW INSTRUCTION FROM OPERATOR (apply exactly this; append it to PROMPT_HISTORY/" + slug + ".md first):")
        appendLine(TQ)
        appendLine(instruction)
        appendLine(TQ)
        appendLine()
        appendLine("TASKS:")
        appendLine("1. Clone/pull " + OWNER + "/" + REPO + ". Read apps/" + slug + "/BUILD_STATE.json — if any task is in_progress, this prompt does not apply: switch to the RESUME contract (finish the in-progress work first).")
        appendLine("2. Append the NEW INSTRUCTION to PROMPT_HISTORY/" + slug + ".md with today's date. Commit + push.")
        appendLine("3. Seed/refresh apps/" + slug + "/BUILD_STATE.json tasks for this update (implement change → validate → release).")
        appendLine("4. Implement the instruction following the per-step protocol and quality constraints below, working from the latest released source in apps/" + slug + "/. If the change is an Engine-level fix, apply it in engine/ AND propagate it into apps/" + slug + "/ (and note it).")
        appendLine("5. Release the next version via the release.yml workflow (app_path=apps/" + slug + "); the tag auto-increments to " + slug + "-v(N+1). NEVER overwrite or delete any prior release. Verify the new release + APK exist on the Releases API before completing.")
        appendLine("6. Set apps/" + slug + "/BUILD_STATE.json build_complete: true with current_task: none.")
        appendRules(this)
    }

    fun promptResume(slug: String): String = buildString {
        append(contractHead("RESUME BUILD"))
        appendLine("APP_SLUG = " + slug)
        appendLine("APP_FOLDER = apps/" + slug + "/ (inside " + OWNER + "/" + REPO + ")")
        appendLine()
        appendLine("INSTRUCTION:")
        appendLine("Resume the interrupted build of " + slug + ". Read apps/" + slug + "/BUILD_STATE.json and PROMPT_HISTORY/" + slug + ".md completely. Find the first task with status: in_progress or pending. Complete it, update BUILD_STATE.json, commit + push, verify the push on origin/main, then move to the next task until the release is verified live and build_complete is true.")
        appendRules(this)
    }
}
