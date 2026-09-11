# ClipForge Android — Prompt History
Append-only log of every operator instruction, in order.

---

## Session 1 — Fresh Start Build Contract (2026-09-11)

**Instruction (verbatim from ForgeBuild Contract):**
> ClipForge Android — Fresh Start Build Contract
> Repository: https://github.com/motionssalt/clipforge.git
> A previous attempt at this Android app produced broken, incomplete results (settings missing, audio pause broken, wrong task navigation, copy agent prompt broken, torrent upload broken, etc.).
> This is a fresh start build.
> Slugs checked on vjumbo264/forgebuild: apps/clipforge already exists (with old release clipforge-v1). Chosen slug per contract: clipforge-android (apps/clipforge-android/).

---

## Session 2 — Update / Bug-fix pass (2026-09-11)

**Instruction (verbatim from operator):**
> Okay, these are the current bugs that needs to be fixed. The first bug is that the logs, the logs are not... I don't understand them. It's supposed not to show the entire actions running. It's just supposed to show each one, one by one, like an automatic scrolling log that shows each action one by one. You understand? So the log will be better. Secondly, the Zernu settings is completely missing. It only shows where you paste your token, but it doesn't even show anything. Also, some settings that were there are not... is not pulling the settings, like the watermark settings and the other settings that I already set previously. It is not showing there. It's always showing as if I should set it newly. Also, tasks keep failing. I don't know why. Maybe because... I don't know, they fail. And I think it's mainly torrent tasks. Because there's nothing... there's no way that the torrent scan... I don't think the app is supporting for torrent to ask me to select the video. Because normally when it was a Telegram bot and I use a torrent or a magnet link, when it gets to a point, it will stop and I will have to select video. That was also a step. So I think that is what is making it to fail, and it needs to be fixed, but still investigate. Also, after the first time I used the app, it crashes. It doesn't launch again for some reason. I don't know why. Also, buttons like connect and other buttons that require connecting with the API, it should show me something like loading, not just that I tap it and I don't even know that it's doing anything or not. It should show me something, an indication that something is happening. Also, yeah, that is all. That's all the fixes I need for the second version.

---

## 2026-09-11 — Operator instruction (session-03 update pass)

"""
Okay, these are the bugs that needs to be fixed in the next version. The first bug is the duration selection doesn't have 30 seconds option and also doesn't have custom duration option. Secondly, the icon of the app is terrible. It looks very stupid. If a new one that is AI generated or whatever can be created, I would appreciate it. Thirdly, the logs are not, like, they're not bad, but it can be better. For example, logs that are not yet, like, that haven't yet been done will be the normal grayish color I'm seeing. Then logs that have, that the steps that have passed, that worked, will be green, or whatever color you think is better. The ones that were skipped, the steps that were skipped, will be yellow, or whatever color you think is better. And the ones that failed will be red, or whatever color you think is better. And whatever other color coding options the logs can support. Also, I noticed that initially, I thought that the fix for tasks failing was because it was torrent, but I found out that every task fails at the ingestion part for some reason. Please find out how the Telegram bot does it that it always works, because right now no matter the task I create, it always fails. It's always failing. And additionally, the second time I launch the app, it always crashes. It never even loads. Once I launch it, it crashes. I've tried fixing this issue before, yet it is still appearing again.
"""

---

## 2026-09-11 — Operator instruction (session-04 update pass)

"""
Repo: https://github.com/motionssalt/clipforge — read-only PAT for investigation (token redacted from this log; provided in the operator contract; no write access; use it only to inspect the repo's actual stored settings data and how the bot persists/reads it, to understand ground truth before touching the app's code).

Bug: the ClipForge Android app's Settings screen is not reading real, already-existing settings from GitHub. Zernio accounts I already connected and refreshed via the Telegram bot don't show up in the app — tapping Refresh in the app says nothing is set up, as if the account were never connected. Same problem with the Narrator, Watermark, and Music default-track settings: I can't even select a default music track in the app, and nothing I set previously in the bot appears. This is not a Cloudflare-storage question — everything is persisted to GitHub the same way the bot has always stored it, so it should still be there and readable.

What needs investigating: compare exactly how the bot's settings code (`bot/src/storage.js`, `bot/src/github.js`, and the relevant `set:*` handlers in `bot/src/index.js`) reads and writes clone settings/credentials against how the Android app currently reads that same data. Find the actual mismatch — wrong file path, wrong repo, wrong parsing of the stored format, wrong credentials/clone being queried, a settings read that never actually calls GitHub at all, etc. — using the real stored data in my repo as ground truth via the read-only PAT above, not assumptions from either codebase in isolation. Fix the Android app so every settings section (GitHub clone status, Narrator, Music library default, Watermark, Series Mode default, Zernio publishing incl. connected accounts) correctly loads and displays whatever is genuinely already stored in GitHub the moment the app connects to a clone, and correctly writes changes back in the same format/location the bot already uses, so the two stay interchangeable.

Three more real bugs to fix in the same pass:

1. Selecting a video file inside a torrent's file list fails every time with "Selection failed: HTTP 422: Invalid request. \"sha\" wasn't supplied" (GitHub's Contents API create-or-update-file-contents error). This means the app is calling GitHub's create-or-update endpoint to write/update some file (the torrent file-selection record, or the job's request file) without first fetching that file's current `sha` when it already exists — GitHub requires the existing file's `sha` on any update, only omitting it when the file is being created for the first time. Find every GitHub Contents API write in the torrent-selection path, and for each one, fetch the current file (if it exists) first to get its `sha` and include it on the update call; only skip `sha` when the file genuinely doesn't exist yet. Compare against how the bot's own `github.js`/`storage.js` do this correctly, since the bot doesn't have this bug.

2. The app crashes every single time it's opened for the second time (works fine on first install/open, then crashes on every subsequent open). This has reportedly been "fixed" multiple times already without actually resolving — do not repeat a superficial fix. Actually reproduce it (cold-start the app, fully close it, reopen it) and get a real stack trace/crash log before proposing a fix, rather than guessing at the cause again. Pay particular attention to anything that behaves differently between "no saved state yet" and "saved state exists" (cached credentials, cached settings, a local database/cache from the first open) since that's the most common source of a works-once-then-always-crashes pattern.

3. The custom video duration button doesn't show an input field for typing a custom duration — tapping it does nothing usable, there's no way to actually enter a value. Fix so the button opens a real input field (or dialog) that accepts a custom duration and applies it, matching whatever the bot's equivalent duration-setting flow actually does in the wizard step for target duration.
"""
