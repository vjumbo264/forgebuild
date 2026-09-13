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

---

## 2026-09-12 — Session 09 operator instruction (eight fixes)

> Repo: https://github.com/motionssalt/clipforge — GITHUB_PAT = (operator-held, never committed)
> (write access).
>
> Eight fixes, grounded in the real bot behavior in `bot/src/` — read the
> relevant real code before implementing each one, don't guess.
>
> **1. Music selection is empty until visiting Settings and coming back, and
> leaving it on default/none plays no music even when a default track IS set
> in Settings.**
>
> The New Video screen's music picker isn't loading the music library (or
> the current default) at the time that screen opens — it's only populated
> as a side effect of having visited the Music settings screen earlier in
> the same session. Find wherever the app loads the music library list and
> the current default-track setting, and make the New Video screen load both
> directly when it opens, not rely on another screen having already
> populated some shared state. Separately: when the operator leaves the
> music selection on "default/none," the created task must use whatever
> track is actually set as the default in Settings — not skip music
> entirely. Check how the bot resolves this (a task's `music.ref` /
> `music.source` fields, and how "no explicit per-task choice" falls back to
> the settings default) and reproduce that resolution exactly. Verify by
> setting a default track in Settings, going straight to New Video without
> visiting Music settings again, leaving music on default, and confirming
> the created job's music reference matches the default track.
>
> **2. No way to preview a narrator voice before choosing it.**
>
> The bot already supports this with pre-rendered sample clips — see
> `index.js` around the Narrator settings handler, which reads
> `assets/tts-previews/<voiceId>.mp3` from the repo via
> `getRepositoryFileBytes` and sends it as a preview. Add a play/preview
> button next to each voice option in the Narrator settings screen that
> fetches and plays that same `assets/tts-previews/<voiceId>.mp3` file for
> the corresponding voice ID (see `constants.js`'s `VOICES` map for the real
> voice IDs) — stream/cache it the same way other audio previews in the app
> already work, don't re-synthesize a new sample.
>
> **3. Logs need to look and behave better: expandable per-step, only the
> currently-running step expanded, cleaner visual design overall.**
>
> Model this on GitHub Actions' own run-log UI, which the operator is
> already comparing it to: each pipeline step is its own collapsible section
> with a header (step name + status icon + duration) and its detail lines
> collapsed by default. The step that is CURRENTLY RUNNING should be
> auto-expanded, showing its live output as it streams in; the moment that
> step finishes and the next one starts, the finished step auto-collapses
> and the new current step auto-expands — mirroring exactly how the
> operator described GitHub Actions' own behavior. Completed/failed steps
> remain available to manually expand afterward by tapping them. Give this a
> proper visual treatment (clear step boundaries, status-colored icons/left
> border per step — success/running/failed/pending — readable monospace for
> the actual log lines) rather than one plain scrolling text block.
>
> **4. No "Start Next Part" button anywhere for a finished series part.**
>
> The bot's real mechanism is `startNextSeriesPart` in `index.js`: it
> resolves the continuation for the just-finished part, computes the next
> part's job id via `nextPartJobId(continuation)`, and — critically — checks
> whether a Stage A request or status already exists for that next job id
> as a duplicate-dispatch guard; if one exists, it shows "Part N already
> exists as task X" instead of a start button, which is exactly why deleting
> that next part makes the button reappear (the existence check then finds
> nothing). Add this button to a completed series part's task detail view in
> the app, wired to the same underlying mechanism (reuse
> `nextPartRequestBody`/`nextPartJobId`/the existing-job check rather than
> reimplementing the continuation logic from scratch), so the button's
> presence/absence in the app matches the bot's real logic exactly, not a
> simplified approximation.
>
> **5. Downloaded final videos can't be previewed/played from the app.**
>
> When the operator downloads a series part's final MP4, save it associated
> with that job's series id (so it can be found again from the series view
> later), and once downloaded, show a "Play" affordance directly in the app
> (in-app video player, not just a file-system save) both immediately after
> download and later when revisiting a completed series part — if the file
> was already downloaded previously, show "Play" instead of "Download Final
> MP4" for that part.
>
> **6. Download progress doesn't show total file size, only current speed.**
>
> Every download progress indicator (final video, and anything else that
> downloads) must show the total size being downloaded alongside the current
> speed — the size should be available from the response's Content-Length
> (or the GitHub release asset's known size, since these are GitHub Release
> assets) at the point the download starts; use that instead of only
> reporting speed with no sense of total size or how much remains.
>
> **7. Add an "About" section in Settings** (app name/version, and whatever
> else is standard for an About screen — your judgment on exact contents).
>
> **8. Source input in New Video should auto-detect the link type instead of
> requiring the operator to pick "Magnet" vs "Drive" etc. before pasting.**
>
> The bot never makes the user choose a source type — `classifySourceText`
> in `wizard.js` inspects the pasted text itself and determines whether it's
> a magnet URI, a Google Drive link, a direct URL, a torrent file upload, or
> a t.me channel link, entirely from the content. Remove any source-type
> selector from the New Video screen and instead run the same classification
> logic directly against whatever the operator pastes, exactly like the bot
> does — the operator should just paste (or upload a .torrent file) and the
> app figures out the kind on its own, including correctly ordering the
> magnet check before any generic URL fallback (this ordering was the
> subject of a previous fix — make sure this rework doesn't regress it).
>
> Note: do not touch anything related to per-tab navigation state (what
> screen reappears when switching back to a tab) — that is explicitly out of
> scope for this fix regardless of anything mentioned elsewhere.

---

## 2026-09-12 — Session 10 operator instruction (nine fixes, ground truth moved to motionssalt/clipforge)

Repo: https://github.com/motionssalt/clipforge — GITHUB_PAT = [REDACTED — fine-grained PAT with write access, supplied out-of-band; rejected once by GitHub push protection as plaintext] (write access).

Nine items. Items 8 and 9 involve a feature (Super Series mode) added to the repo very recently — read the real current code for it before implementing anything; do not rely on any older description of series behavior for it.

**1. In-app video player for downloaded videos looks bad / isn't nice.** Replace it with a proper native video player experience — standard playback controls (play/pause, seek bar with scrubbing, current/total time, fullscreen toggle), not a bare minimal player. Use ExoPlayer/Media3 (the standard modern Android media playback library) if not already in use, styled to match the app's Material 3 theme rather than looking like a default unstyled player.

**2. After downloading a video and leaving/returning to that screen, the app asks to download it again even though the file is already on storage.** The app is not checking for the already-downloaded file before showing the download prompt again. Fix so that whenever this screen/task is opened, the app first checks whether the final video for this job (matched by job id / series id, per the existing per-job save-location convention) already exists on device storage, and if so shows "Play" directly instead of "Download" — this is the same already-downloaded-detection this app needs generally (per item 5 in the previous fix round) but confirm it's actually wired up correctly here specifically, since it's evidently not working for at least this screen.

**3. After creating a clone, it doesn't automatically log in, and gives no confirmation that the clone was created.** Fix both: once clone creation genuinely completes (the real flow is `beginShadowCloneCreation` → `pollShadowCloneJob` → `finalizeShadowClone` in `bot/src/github.js` — confirm the Android app is actually waiting for the poll to reach a finished state before treating creation as done, not just firing the initial create call and assuming success), show a clear success confirmation, then automatically proceed into a logged-in session for that new clone with no separate manual login step required.

**4. When creating a new clone, allow either typing a name or letting the app auto-decide one** — a clear "auto-generate a name" option alongside the manual name field, rather than requiring the operator to type something.

**5. No obvious loading/progress indication when Stage A or Stage B starts, so the operator can't tell whether anything happened without manually scrolling up to find the progress bar.** The moment the operator starts a stage (creates a task, taps restart stage, etc.), immediately show an obvious, unmissable loading state at the point of that action (e.g. the button itself enters a loading state, or a prominent inline "Starting Stage A…" indicator appears right where the action was taken) — the operator should never have to scroll to confirm something is happening.

**6. Toast messages should use the phone's native Android toast, not an in-app/custom toast UI.** Replace all custom in-app toast/snackbar-style notifications with the platform's native `Toast.makeText(...)` (or the standard Android equivalent) throughout the app.

**7. Storage permission keeps being re-requested even though it was already granted, sometimes triggered just by returning to the app.** Find everywhere the app checks/requests storage permission and make sure it's checking the ACTUAL current granted state via `ContextCompat.checkSelfPermission(...)` (or the modern `ActivityResultContracts` permission API) at the moment it's needed, not relying on a cached "did we ask before" flag that can go stale or desync from the real OS-level grant state — this mismatch is almost certainly why it re-prompts on things like returning to the app from the background.

**8. HTTP 403 "Resource not accessible by personal access token" when creating a task on a different device (a friend's phone) while logged in with the SAME account and SAME PAT that works correctly on the operator's own phone.** This rules out a scope/permission problem — the token itself is fine, it works for this exact operation on the operator's device. The failure is therefore specific to something about the second device/app instance, not the token's rights. Investigate: (a) confirm the app isn't installed at a different/older version on the second device with a bug already fixed elsewhere, or built from stale/different code — check the actual installed version before assuming the code is identical; (b) check whether the failing write is a Contents API *update* (needs the current file's `sha`) versus *create* (must not send a stale/wrong `sha`) — a previous fix in this app (the torrent-selection 422 bug) was caused by exactly this kind of `sha` handling mistake; check whether this 403 is the same category of bug resurfacing in a different code path, where the second device has different locally-cached state (e.g. a locally cached `sha` for a file that's since changed on GitHub via the first device) causing it to send a precondition GitHub rejects; (c) check for any per-device or per-install local cache/state (credentials cache, file-sha cache, clone metadata cache) that could be stale, missing, or malformed on a fresh install/second device in a way it isn't on the operator's own already-used device; (d) get the exact failing request (method, path, and whether a `sha`/conditional header was sent) via logging, don't guess. Do not conclude this is a token-scope issue and stop there — it has already been ruled out by the operator using the identical working token on both devices.

**9. Full support for the newly added Super Series mode.** This is a real, already-implemented backend feature — read `ARCHITECTURE.md` section "Super Series mode (orthogonal on top of Series Mode, feature-01)", `bot/src/super_series.js`, `bot/src/settings_super.js`, and `pipeline/plan/super_series.py` before implementing anything. Key real mechanics to match exactly: (a) Super Series is orthogonal ON TOP of ordinary Series Mode — it can only be enabled when Series Mode is already on, and turning Series Mode off must force Super Series off too (the bot enforces this — "the two toggles cannot desync," see `wizard.js`); reproduce this dependency in the app's New Video screen: the Super Series toggle should be disabled/hidden whenever Series Mode is off, and switching Series Mode off must also turn off Super Series if it was on. (b) The Super Series setting has a stored default at `branding/super_series_settings.json` (`readSuperSeriesSettings` / `saveSuperSeriesSettings` in `bot/src/settings_super.js`) — add this as its own Settings section in the app (parallel to the existing Series Mode default setting), following the same read/display/write pattern used for the other settings sections that were fixed in previous rounds. (c) When Super Series is on, the operator uploads ONE document (the "super-plan") describing the entire series up front, instead of the anchor job producing a single-part production.json — the app's upload/paste flow for production.json needs a Super Series-aware variant (or mode) that accepts this whole-series document instead, validated the same way the bot validates it (`validate_super_plan` — same accept/reject decisions and error strings the bot uses, don't invent different validation). (d) After that, parts are queued and dispatched SEQUENTIALLY and automatically by a queue controller — the app does not need a manual "start next part" action for Super Series parts specifically (unlike ordinary Series Mode, where that button IS needed per the previous fix round) — but the app's Series view must clearly show queued/upcoming Super Series parts as part of the same series, and must show a clear halted/error state (matching the real behavior: a spawned part reaching `error`/`cancelled` halts the whole queue until that part is restarted via the existing Stage B restart flow, after which the controller auto-resumes) rather than presenting a halted queue as if nothing is happening. (e) Every Super Series part is, from Stage B onward, an entirely ordinary job — the existing task detail view (logs, restart/cancel controls, download, play) should already work correctly for a Super Series part with no special-casing needed there; the app-side work is specifically in the New Video creation flow (dependency on Series Mode, the whole-series document upload, and the settings section) and the Series overview screen (showing queue/halt state), not in the per-task detail screen.

Verify item 9 against a real Super Series run: enable Series Mode, enable Super Series, upload/paste a real super-plan document, confirm parts queue and dispatch sequentially without a manual per-part button, and confirm a deliberately-failed part halts the queue and resumes correctly after being restarted.

**Addendum from operator (same session, verbatim):**

> Okay, I forgot to mention the person's phone that I tested it on. was a
> Samsung Galaxy s20 the Samsung Galaxy s20 and it was the latest version.
> This current version is that I tested on his phone.

(Clarifies item 8: the second device showing HTTP 403 was a Samsung Galaxy S20 running the then-latest app build — i.e. NOT a stale-version mismatch; investigate the sha/cache/per-device-state hypotheses and get the exact failing request via logging.)

## 2026-09-12 — Operator instruction (swipe-to-refresh, log export, Super Series parts, series prompt wording)

> Okay, so I need swipe to refresh. Instead of having that refresh button at the corner, or leave the refresh button in the corner, but also allow me the web to swipe down and refresh, you understand? It should show me refreshing. And also, if I click that refresh button in the corner, it should be spinning like a normal refreshing. And when I swipe to refresh, it should also be spinning the same way. Also the debug log, I want it to be exported to my Documents folder under ClipForge, not to a folder I can't even get to. And the Super Series, I don't know if it's also like that in the Telegram bot also, but it is lacking the necessary parts, like the Part 1, Part 2, Part 3 parts. Like when it does, it just gives them topic folders, add the Part 1, Part 2, Part 3, parts, for those stuff, not adding them. Also, the Super Series prompts, I don't know if it is the same with the normal series prompts, but in case it is not the way I'm about to explain, then fix it. Right now, this is how I want it to be. Series prompts. If I copy the prompt for the Part 1, it should state that this is Part 1 of the series in the prompt. The same thing for Part 2, Part 3, Part 4, Part 5, and so on. If it's a series, if it's a Super Series, then once I... the prompt should state that this is a Super Series and the videos it is going to give, the parts, like, you understand the way the series already works, so that you know that it is parts that it is going to produce, you understand? Just use your own judgment for this.


## 2026-09-13 — Operator instruction (Super Series Stage B never starts — repo-based anchor tracking; hold-to-delete series; step-log expansion shows "null"; Super Series title banners missing "Part N")

> Repo: https://github.com/motionssalt/clipforge — GITHUB_PAT = GITHUB_REPO_PAT = [REDACTED — value provided in operator prompt; not committed per credential policy]
> CLOUDFLARE_API_TOKEN = [REDACTED]; CLOUDFLARE_ACCOUNT_ID = [REDACTED] (write access).
>
> **1. Super Series: Stage B never actually starts** — confirmed by checking GitHub Actions directly, nothing is being dispatched, not merely "not picked up by the UI." Root cause: the cron sweep (`scheduled()` → `superQueueTick` in `bot/src/index.js`) only acts on jobs registered as an anchor via `setTaskOptions(env, chatId, jobId, { super_series_anchor: true })`, which is backed by `env.CLIPFORGE_BOT_D1` (the bot Worker's D1 `task_options` table). The Android app has no access to this D1 and must NOT depend on it. Real fix: make Super Series anchor-tracking and queue-sweep work entirely off the GitHub repo (same source of truth every other job/task uses) — a durable repo-based marker (e.g. the anchor's own `jobs/<anchorJobId>/super-plan.json` presence/shape, or a small explicit marker alongside it; repo's judgment on cleanest shape). Update the cron sweep to discover active anchors by scanning the repo (e.g. listing `jobs/*/super-plan.json` without `state.done`) instead of querying D1. App must be able to start a working Super Series purely by writing to GitHub, picked up by the existing cron sweep, dispatching Stage B normally, end to end. Telegram bot Super Series flow must keep working EXACTLY as today (same commands/messages/halt/resume) — migration of where anchor state lives, not a rewrite. Do NOT remove the `task_options` D1 table or `setTaskOptions`/`getTaskOptions` if anything else legitimately uses them — only stop relying on D1 for `super_series_anchor` tracking. Roll out safely: check live D1 for active (mid-queue) anchors before the switch; migrate in-flight state to the repo-based marker if any exist, otherwise note that in BUILD_STATE.json and proceed without migration. Verify BOTH surfaces end to end: (a) app-submitted plan (GitHub writes only) → actual GitHub Actions Stage B run within ~one cron tick; (b) bot-started Super Series unchanged.
>
> **2. Series parts (ordinary Series Mode) had no restart option visible — side effect of issue 1, not a separate bug.** No separate fix beyond verifying restart controls (from a previous fix round) correctly appear once a Super Series part is spawned and visible as an ordinary job — verify specifically, don't skip.
>
> **3. Add hold-to-delete for an entire series from the Series tab.** Long-press on a series entry offers delete-entire-series with a confirmation step (destructive), matching the app's existing delete-confirmation UX pattern rather than inventing a new one; removes the series overview entry and all of that series' parts/jobs identifiable as belonging to it.
>
> **4. Log step expansion shows "null" instead of real content, for both the currently-running step and completed steps.** The expand mechanism exists but renders "null". Fix the field/path read that comes back empty/undefined and renders as literal "null" instead of real content or a proper empty/loading state. Must work for (a) the active step with live-streaming content and (b) a completed step expanded after the fact. Verify against a real running task and a real completed task.
>
> **Bug: Super Series video title banners show only topic/title text, no "Part N" — unlike ordinary Series Mode.** Root cause confirmed: ordinary Series Mode's Stage A agent gets an explicit "SERIES MODE — this is Part N of series..." directive (index.js ~line 1096) so it writes "Part N" into production.json's title; Super Series has no equivalent per-part directive — the super-plan is authored up front in one shot (`bot/src/super_series.js`, `pipeline/plan/super_series.py`), and neither the JSON schema nor `validateSuperPlan`/`validate_super_plan` enforces/adds "Part N" (they only check non-empty + unique). Fix at the single correct point — almost certainly super-plan slicing time: `sliceSuperPart` (bot) / Python-side equivalent (`pipeline/plan/super_series.py`) must ensure each part's production.json `title` includes "Part N" (exact format/casing matching ordinary Series Mode — check a real ordinary-series production.json) when the author-supplied title lacks it. Consistent for both entry points (Telegram bot AND Android app) via the shared slicing logic. Verify against a real Super Series run: plain titles in, banners for ≥2 consecutive parts show "Part N"/"Part N+1" in the ordinary-series visual format.


## 2026-09-13 — Operator instruction (ClipForge migration: remove Telegram Bot A/B entirely; GitHub becomes the ONLY backend; build a Cloudflare Pages Dashboard in a new `site/` folder of motionssalt/clipforge with full bot feature parity; delete bot Workers + CLIPFORGE_BOT_D1 + bot-only workflows only after parity verified live)

> Repo: https://github.com/motionssalt/clipforge — everything happens against this repo; the Dashboard's code lives in a new top-level `site/` folder; do NOT create a separate repo. `site/BUILD_STATE.json` is the migration's persistent memory (task-01..task-12 seeded there). Credentials: GITHUB_REPO_PAT / CLOUDFLARE_API_TOKEN / CLOUDFLARE_ACCOUNT_ID [REDACTED — values provided in operator prompt; never committed per credential policy].
>
> WHY: Bot A (bot/src/, its Cloudflare Worker, D1 CLIPFORGE_BOT_D1) repeatedly caused bugs invisible to the Android app (most recently Super Series stage-B dispatch state living in bot-side D1). Architectural fix: remove the Telegram bot entirely; GitHub is the only backend, full stop. Both the new Dashboard and the Android app talk DIRECTLY to the GitHub REST API — no Worker, no bot-side KV/D1, no server-side session. Build/test features on the site first, then port to the app, both against the same GitHub-only backend.
>
> REMOVE ENTIRELY (LAST tasks only, after parity verified): Bot A (bot/src/index.js + everything it is the entry point for — menus, wizards, settings, /tasks, /done, series/restart/cancel commands, Zernio flow, clone creation); Bot A Worker deployment (wrangler.bot-a.jsonc); CLIPFORGE_BOT_D1 + bot-side KV namespaces (confirm the Super Series anchor migration to repo-based state actually landed first — session-13 notes say it did, motionssalt/clipforge @11006dde — finish the migration if half-done); the telegram_relay source kind incl. Bot B (bot/src/relay-worker.js) + telegram-relay.yml; deploy-bots.yml / news-push.yml / any bot-only workflow (verify each remaining workflow against ARCHITECTURE.md — keep stage-a/stage-b/publish/diagnostics/cleanup); _legacy/telegram-bot/ if fully superseded.
>
> KEEP (owner-only, GitHub-driven instead of Telegram-driven): telegram_channel source kind (public t.me link, MTProto-based retrieval in Stage A — a pipeline concern, not a bot concern; isOriginalRepo main-account gate must be reproduced identically in site + app source pickers); pipeline workflows stage-a.yml / stage-b.yml / publish.yml / restart/cancel equivalents, dispatched via workflow_dispatch — confirm no hidden dependency on the bot Worker.
>
> DASHBOARD ARCHITECTURE (non-negotiable): static/client-rendered Cloudflare Pages site talking DIRECTLY to the GitHub REST API from the browser; NO Worker dependency of any kind (a CORS blocker is a real blocking issue to research, not a reason to add a Worker). Auth: one-time GitHub PAT prompt -> localStorage -> never re-prompt except explicit disconnect.
>
> FEATURE PARITY (catalog every Bot A feature from bot/src/index.js + bot/src/commands/ and reimplement ALL against GitHub): clone creation (beginShadowCloneCreation/pollShadowCloneJob/finalizeShadowClone) + auto-login; new task creation for every source kind (url/drive/magnet/torrent_file/telegram_channel main-account-only) with classifySourceText-exact auto-detection; task list/completed/detail with live status + real GitHub Actions logs (GET actions/runs/{run_id}/jobs + actions/jobs/{job_id}/logs, per-step collapsible sections, active step auto-expanded, full step content, local cache + append-only polling, stop at terminal state); restart Stage A/B (production.json-required guard), cancel (confirmation + active-run vs queued-undispatched two-branch logic, matching restarta/restartb/cancelb/cancelby); Series (dedicated view, per-part status, Start Next Part with duplicate-dispatch guard, hold-to-delete series); Super Series (settings, super-plan submission, queue/halt display, repo-based anchor tracking — verify a real submission dispatches a real Stage B run in GitHub Actions); all Settings sections (GitHub clone incl. visibility/sync/main-account push, Narrator w/ assets/tts-previews/ voice previews, Music library w/ default-track resolution, Watermark, Series Mode default, Super Series default, Zernio full settings); Zernio per-task publish (3 platforms TikTok/YouTube/Instagram, 3 modes publish-now/manual-schedule/smart-schedule, per-account multi-target toggles, conditional visibility, read bot/src/zernio.js in full first); music/task multi-select+delete; audio tap-to-play cached; in-app video player w/ downloaded detection; browser download w/ real progress+size. Fix known-broken app behaviors in BOTH surfaces from the same correct GitHub-only mechanism — never port broken behavior into a second surface.
>
> DISCIPLINE: never stop because part is already done; never produce status reports; repo is source of truth; never reset progress / never redo completed work; ordering rule — do NOT delete Bot A/B, D1, or bot code until every parity feature has a working verified replacement; continuous execution with per-step START->PERFORM->VALIDATE->UPDATE BUILD_STATE.json->COMMIT->PUSH->VERIFY protocol.
>
> TASKS task-01..task-12: (01) read ARCHITECTURE.md/bot/src fully, catalog features, confirm Super Series anchor status, scaffold site/; (02) GitHub-only auth + clone creation; (03) new video/task creation w/ classifySourceText port; (04) tasks/completed/detail + real Actions-logs rework + restart/cancel; (05) Series; (06) Super Series fully + verified real Stage B dispatch; (07) all Settings sections; (08) Zernio full feature set; (09) multi-select/delete, audio/video preview, browser download w/ progress; (10) cross-check catalog vs built, fix gaps; (11) deploy Dashboard to Cloudflare Pages + verify live end to end; (12) only then delete Workers/D1/bot code/dead workflows, update ARCHITECTURE.md+README, build_complete=true.

## 2026-09-13 — Super Series Stage B not dispatching (operator)

Operator reported: after submitting a super production.json and pressing Start, the app showed
"Stage B started" but NO Stage B run appeared in GitHub Actions. Also reported the new Dashboard
site was never actually deployed (Cloudflare showed no new Pages project; what they were seeing was
someone else's site), and Bot A/B + D1 were still not deleted. Fix: the Super Series queue sweep
lived only in the removed Telegram bot's per-minute cron, so nothing spawned Part 1 or dispatched
stage-b.yml. Shipped a headless scheduled super-sweep.yml workflow in motionssalt/clipforge AND an
app-side self-drive (advanceSuperQueue) so a submitted plan spawns + dispatches Part 1 directly.
