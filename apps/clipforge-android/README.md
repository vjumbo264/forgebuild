# ClipForge Android

Android client for a ClipForge Shadow Clone (https://github.com/motionssalt/clipforge):
create/render vertical video tasks, watch Stage A/B progress live, manage clone
settings — all through the same GitHub repo contracts the Telegram bot uses
(`jobs/<id>/stage-a-request.json`, `status.json`, `production.json`,
`branding/*.json`, stage-a.yml / stage-b.yml dispatches).

Built on the ForgeBuild Engine (Material 3 `ForgeBuildTheme`, EngineIcons,
CacheFirstStore, per-screen FLAG_SECURE where needed). Kotlin + Jetpack
Compose, R8 + resource shrinking on.

## Session-08 additions (v5)

1. **Source classification (bot `wizard.js` parity)** — `data/SourceClassifier.kt`
   classifies pasted sources exactly like the bot: `magnet:?` is checked BEFORE
   the generic URL fallback, plus t.me post / Google Drive / direct-URL rules and
   the bot's disabled-host list (youtube/tiktok/facebook/...). The New Task wizard
   auto-flips the source chip while you type and re-classifies at submit — the
   classifier's verdict always wins over the manual pick, so a magnet URI can
   never be saved as `kind:"url"` and fail at Stage A ingest.
2. **Shadow Clone creation (bot `github.js` parity)** — `data/ShadowClone.kt` +
   `data/ShadowCloneWorkflow.kt`: creates the private repo (auto_init:false),
   bootstraps `.clipforge-sync.json` + the one-time `clone-copy.yml` workflow,
   dispatches it, POLLS `.clipforge-clone-status.json` to completion
   (start 120s / stall 6min / deadline 10min guards), then finalizes (copies the
   source's workflow files with the PAT, deletes the one-time workflow, verifies
   the default branch holds the copied tree). On success the app logs into the
   new clone immediately — create it and you're in. Onboarding shows a
   determinate copy progress bar.
3. **Workflow run link** — the task detail screen shows an "Open Workflow Run"
   button whenever `status.run.workflow_run_url` is present; opens the Actions
   run in the browser.
4. **Stage controls (bot `restarta`/`restartb`/`cancelb` parity)** —
   Restart Stage A (fresh `code_ref`, §8.5), Restart Stage B (guarded by
   production.json presence with the bot's exact block message; dispatches
   production_ref + music_ref + code_ref), and Cancel Running Stage with a
   confirmation dialog and BOTH bot branches: run id present → GitHub Actions
   API cancel; queued without a run → local `state:"cancelled"` merge into the
   existing `status.json` (all other fields preserved).

## Connect / Create

- **Connect Existing**: PAT + `owner/repo` of an existing clone.
- **Create New Clone**: PAT (+ optional name, auto `clipforge-clone-<suffix>`);
   the full bot flow runs (minutes — the copy executes on a GitHub Actions
   runner) and the app enters the clone automatically when it's genuinely ready.

Saving videos uses MediaStore `Movies/ClipForge` on Android 10+ (scoped
storage); task/music lists are cache-first (instant open, background refresh).
