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

---

## 2026-09-12 — Operator instruction (session-06 update pass)

"""
Bug, still unresolved after a previous fix attempt: the ClipForge Android
app crashes every time it's opened for the SECOND time onward — first open
after install/login works fine, then every subsequent open crashes. This
has already been "fixed" multiple times without success, so stop
attempting to trace and patch it forward. Instead:

My other app, ForgeBuild (built from the same ForgeBuild Engine, in the
`vjumbo264/forgebuild` repo), had this exact same category of persistent-
login/second-open behavior and did NOT have this crash. I deleted its
`apps/` app folder from that repo, but nothing is actually lost — it still
exists in that repo's git history. Do this:

1. In `vjumbo264/forgebuild`'s git history, find the commit(s) right before
   the app folder was deleted (`git log --diff-filter=D --summary` or
   equivalent, then `git log -- <path>` on the folder once you find its old
   path) and recover the last known-working version of whatever handled:
   app startup / cold start, persisted login/session restore, and any local
   cache or database initialization that runs on every app open. These are
   the most likely shared cause between "worked in ForgeBuild" and "breaks
   in ClipForge on second open" — a first-open-only bug almost always means
   something about reading back previously-saved state (a cached session, a
   local database, a settings cache) is different or broken from the
   from-scratch state the first open uses.
2. Compare that recovered working code against ClipForge Android's current
   equivalent code for the same responsibilities (session/credential
   restore on launch, local cache/database open, any first-run-vs-later-run
   branching logic).
3. Where ClipForge's current implementation diverges from the old working
   ForgeBuild version in a way that's plausibly the cause, replace it with
   the working approach (adapted to ClipForge's actual data — clone
   credentials, settings, cached job data — not copied verbatim where the
   underlying data model differs), rather than continuing to patch the
   current broken version in place.
4. You cannot install or run the app yourself, so you cannot personally
   reproduce the crash on-device. Since it's already known and reliably
   reproducible (crashes every second open, no exception so far), reason
   about correctness from the code itself: trace every code path that runs
   differently on a "cold start, no saved state" open versus a "saved state
   already exists" open, and confirm — by reading the code, not by running
   it — that the fixed version handles the second case safely. Also add
   proper crash/error logging around app-launch state restoration if it
   isn't already there, so that if this fix turns out to be incomplete, the
   next report from the operator (who does have the device) comes with a
   real stack trace instead of another blind guess.
"""

---

## 2026-09-12 — Operator instruction (session-08 update pass)

**Addendum from operator (same session, verbatim):**
> Note that that first in gesture failure was probably. My mistake. I might have accidentally selected URL instead of magnets during the creation of the task. So if you don't see anything wrong with it, there's no need to over investigate. It's probably from me.

**Instruction (verbatim from operator; PAT redacted — never commit credential values):**
> Repo: https://github.com/motionssalt/clipforge — GITHUB_PAT = [REDACTED — fine-grained PAT with write access, supplied out-of-band] (write access).
>
> Four fixes needed, all against the real existing bot behavior in
> `bot/src/` as ground truth — do not invent new mechanics, the bot already
> does all of this correctly.
>
> **1. Magnet links are being misclassified, causing every magnet-source task
> to fail immediately at Stage A ingest.**
>
> Confirmed from a real failed job's `stage-a-request.json`: a magnet URI
> (`magnet:?xt=urn:btih:...`) was saved with `"source": {"kind": "url", ...}`
> instead of `"kind": "magnet"`. The bot's own classifier in
> `bot/src/wizard.js` checks `MAGNET_RE = /^magnet:\?/i` and returns
> `{ kind: 'magnet', value }` BEFORE falling through to the generic URL
> case — this check must run first, or a magnet URI (which is technically
> also a URI) gets wrongly caught by a generic "is this a URL" check instead.
> Find the Android app's equivalent source-classification code and fix the
> ordering/logic so magnet URIs are matched by the magnet check before any
> generic URL fallback, exactly like `wizard.js` does. Verify by creating a
> task from a real magnet link and confirming `stage-a-request.json` is
> written with `"kind": "magnet"`, and that Stage A actually proceeds past
> ingest instead of failing immediately.
>
> **2. Clone creation doesn't work from the login/onboarding screen.**
>
> Tapping to create a new clone does nothing / doesn't complete. The bot's
> real clone-creation flow (`bot/src/github.js`:
> `beginShadowCloneCreation(pat, requestedName, options)` →
> `pollShadowCloneJob(...)` → `finalizeShadowClone(...)`, using
> `CLONE_COPY_DEADLINE_MS` as the timeout) creates the new repo, copies the
> source content into it, and polls until that copy job finishes before
> the clone is usable. Read this real flow in `github.js` and `index.js`
> (search for `beginShadowCloneCreation`) and reproduce it faithfully in the
> Android app rather than a simplified guess — in particular, don't skip the
> polling step; a shadow clone isn't immediately ready the instant the repo
> is created. Once creation succeeds, the app must automatically log into
> the newly created clone with no separate manual login step — same
> "create it and you're in" behavior the operator wants overall, applied
> specifically to this flow, which is apparently currently broken or
> incomplete.
>
> **3. Add a direct link to the GitHub Actions workflow run from an ongoing
> task's detail view.**
>
> Every job's `status.json` already contains
> `run.workflow_run_url` (a real example:
> `"https://github.com/motionssalt/clipforge/actions/runs/34666886936"`).
> Add a button/link in the task detail screen, visible whenever a workflow
> run is associated with the current stage, that opens this URL directly
> (system browser or in-app browser, your choice) — this field already
> exists in the data the app already reads, this is purely a missing UI
> affordance, not new data plumbing.
>
> **4. Add restart-stage and cancel-stage controls to the task detail view,
> matching the bot's real existing actions exactly:**
>
> - **Restart Stage A** — available whenever the task is at or has failed
>   Stage A. Matches the bot's `restarta` action (`restartStageA` in
>   `index.js`).
> - **Restart Stage B** — available whenever the task has already produced a
>   production.json (i.e., has reached or passed Stage A) — matches the
>   bot's `restartb` action (`restartStageB` in `index.js`), which itself
>   blocks with a clear message if no production.json exists yet ("upload
>   one, or restart Stage A, before Stage B can run") — reproduce that same
>   guard, don't allow Stage B restart without production.json present.
> - **Cancel running stage** — available whenever a stage is actively
>   running. Matches the bot's `cancelb`/`cancelby` actions
>   (`confirmCancelStageB` / `cancelStageB` in `index.js`): require a
>   confirmation step before actually cancelling (the bot shows "Cancel
>   Stage B for task X? The running render is stopped and the job moves to
>   cancelled. You can restart it afterwards." before the real cancel), and
>   handle both cases the bot handles — an actively running workflow run
>   gets cancelled via the GitHub Actions API
>   (`cancelWorkflowRun(credentials, credentials.repo, runId)`), while a
>   job that's queued but has no run id yet is cancelled locally by writing
>   `state: 'cancelled'` directly to `status.json` without needing to touch
>   GitHub Actions at all. Reproduce both branches, not just the common one.
>
> Verify all three task-detail additions (workflow link, restart controls,
> cancel control) against a real task in each relevant state (Stage A
> running, Stage A failed, Stage B running, Stage B failed with
> production.json present) rather than only the happy path.
