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

## 2026-09-13 — Operator instruction (rewrite Super Series Stage B dispatch = ordinary Stage B chained by completion; finish bot-removal migration: real Pages deploy, parity verify, delete Bot A/B + D1 + KV + dead workflows)

> Repo: https://github.com/motionssalt/clipforge — GITHUB_REPO_PAT = [REDACTED — value provided in operator prompt; not committed per credential policy]; CLOUDFLARE_API_TOKEN = [REDACTED]; CLOUDFLARE_ACCOUNT_ID = [REDACTED].
>
> **Rewrite Super Series Stage B dispatch to work exactly like ordinary Stage B.** Ordinary Stage B works because it is dispatched by ONE direct action at the moment it is needed — site/js/planupload.js uploads the plan and calls the real dispatch function in site/js/github.js (~line 367, STAGE_B_WORKFLOW='stage-b.yml') straight away. No queue, no decision layer, no timer. Super Series Stage B instead has its own parallel dispatch mechanism in scripts/super_sweep/super-sweep.mjs (its own local dispatchWorkflow, its own request plumbing) triggered by a queue-advance decision depending on cron/push machinery — an unnecessary indirection layer in front of what should be the exact same one-line dispatch call.
>
> Super Series Stage B IS ordinary Stage B, chained by completion: (1) operator submits one super-plan up front, parsed once into parts; (2) Part 1 dispatches immediately as an ordinary stage-b.yml run — same dispatch call, same inputs shape; (3) the MOMENT part N's Stage B run reaches genuine successful completion, that completion event directly triggers part N+1's dispatch — real event-driven reaction (workflow_run trigger on stage-b.yml's completion, or a success-only final step in stage-b.yml), NOT client check-in, NOT poll, NOT cron; chain advances unattended with no client open; (4) if a part fails, the chain stops permanently until operator intervention — restarting the failed part IS the resume (its completion triggers the next part); (5) restart scope: only Stage A (whole pipeline) or the CURRENTLY active/most-recent Stage B part — no per-historical-part restart control; (6) cancel scope: only the currently running part.
>
> Do NOT solve via site/app polling for completion — that just moves the flaw from server cron to client poll. If in-workflow chaining / workflow_run follow-up is genuinely infeasible, STOP and flag in BUILD_STATE.json as a blocking design question — never fall back to cron or client-poll.
>
> Fix — full rewrite, not a patch: (1) delete the duplicate dispatch implementation in super-sweep.mjs (local dispatchWorkflow + stage-b dispatch inside tick()); keep the genuine queue-logic (WHICH part next, double-spawn guard, 'Title — Part N' + prior-part-summary context); (2) the decided dispatch must call the SAME real dispatch function ordinary Stage B uses (site/js/github.js dispatch-workflow function or its exact equivalent, imported/reused, not reimplemented) — one shared dispatch function, one code path; (3) delete .github/workflows/super-sweep.yml and any Super Series cron/schedule trigger (news-push.yml unrelated, stays); replace with GitHub-native reaction to stage-b.yml's own completion; (4) client side (submit plan dispatches Part 1; UI reflects only-current-part restart/cancel) identical in site/ AND the Android app; (5) PROVE end-to-end: confirm super-sweep.yml gone; submit real super-plan; confirm real Part 1 stage-b.yml run in the GitHub Actions tab; CLOSE all clients, let Part 1 finish, confirm Part 2 dispatched with ZERO client involvement (directly in Actions tab); deliberately fail a part, confirm chain halts until restart, and restart's completion dispatches the next part; report real workflow run IDs as evidence — never from status.json or app/site-rendered state.
>
> **Remaining Telegram-bot-removal migration (do after the Stage B rewrite is verified, per ordering rule):** still live: bot/ source, bot/wrangler.bot-a.jsonc + wrangler.bot-b.jsonc (Workers still deployed), CLIPFORGE_BOT_D1 + bot KV, .github/workflows/deploy-bots.yml + telegram-relay.yml; the site/ Cloudflare Pages project was never actually deployed (earlier claims false). In order: (1) create + deploy the Pages project for site/ via Cloudflare API, confirm live by fetching the real URL and checking it serves ClipForge Dashboard content; (2) full feature-parity verification against the live deployment (clone creation/auto-login, task creation all source kinds, task detail with real Actions logs, restart/cancel, ordinary Series, Super Series with the fixed chained dispatch, every Settings section, Zernio publish flow + settings, multi-select delete, audio preview caching, video preview/download) — fix anything broken now, don't defer; (3) only then delete Bot A/B Workers, CLIPFORGE_BOT_D1, bot KV via Cloudflare API; (4) delete or archive bot/ (archive under _legacy/ acceptable), delete deploy-bots.yml + telegram-relay.yml; (5) update ARCHITECTURE.md + README to the final GitHub-only, no-Worker, no-bot architecture; (6) re-run step-2 verification after deletions; only then set build_complete: true. Report real evidence: live Pages URL, specific deleted Cloudflare resource IDs, workflow-removal confirmation.

---

## 2026-09-13 — Operator instruction (Super Series Stage B rewrite: ordinary Stage B chained by completion; finish Telegram-bot-removal migration; site multi-file UI restructure)

Target repo: https://github.com/motionssalt/clipforge (credentials provided out-of-band, never committed).

1. Rewrite Super Series Stage B dispatch to work exactly like ordinary Stage B: ONE shared dispatch function (site/js/github.js dispatchWorkflow equivalent), no separate super-sweep dispatch implementation, delete .github/workflows/super-sweep.yml and all Super Series cron (news-push.yml unrelated, stays). Chain continuation via GitHub-native workflow_run trigger on stage-b.yml completion (or final-step in stage-b.yml) — never cron, never client polling. Part 1 dispatches immediately on super-plan submit; part N completion directly dispatches part N+1; failure halts chain permanently until operator restarts the failed part (restart of current part resumes chain via its own completion). Restart scope: only whole-pipeline Stage A or the CURRENT Stage B part. Cancel scope: only the currently running part. Client side (site/ AND Android app) must match. End-to-end verification mandatory, specifically the UNATTENDED property: close all clients, let part finish, verify in GitHub Actions tab that next part's stage-b.yml run dispatched on its own; also verify halt-on-failure and restart-resume. Report real run IDs as evidence.

2. Finish the bot-removal migration in order: (1) create + deploy Cloudflare Pages project for site/ via Cloudflare API, verify live URL serves real ClipForge Dashboard content; (2) full feature-parity verification against live deployment (clone creation/auto-login, task creation all source kinds, task detail real Actions logs, restart/cancel, ordinary Series, Super Series chained dispatch, all Settings, Zernio publish+settings, multi-select delete, audio preview+caching, video preview/download) — fix broken things now; (3) delete Bot A/B Workers + CLIPFORGE_BOT_D1 + bot KV via Cloudflare API; (4) archive bot/ under _legacy/ (or delete), delete deploy-bots.yml + telegram-relay.yml; (5) update ARCHITECTURE.md + README to final GitHub-only architecture; (6) re-verify, then set build_complete=true. Report real evidence: live Pages URL, deleted resource IDs, removed workflows.

3. (Operator follow-up, same day) Site complaint: entire site delivered in one HTML making it unresponsive and UI poor — restructure site/ into modular files and clean up UI; confirm app Super Series works.

---

## 2026-09-13 — Operator instruction (Two real backend gaps: "Part N" banner fix at super_chain slicing + workflow_run default-branch mismatch for super-chain.yml; FULL from-scratch rewire of Super Series in the Android app)

Target repo: https://github.com/motionssalt/clipforge (GITHUB_REPO_PAT, CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID provided in the prompt; redacted here per credential policy — never committed).

Part 1 — Two real backend gaps, separate from Android app work, must be fixed regardless (they affect the site too):

1. "Part N" is still missing from video title banners. The earlier fix was never implemented in the code path that matters: `scripts/super_chain/plan.js` only validates that a part's `title` is a non-empty string when present — it does not inject or guarantee "Part N". Fix at the correct single point (slicing a part out of the super-plan, in `super_chain`'s plan-handling code) so every spawned part's title reliably includes "Part N" in the same format ordinary Series Mode already produces (check a real ordinary-series job's `production.json` for the exact convention), regardless of the authored part title. Verify against a real render: two consecutive parts' actual video banners must show "Part N" / "Part N+1".

2. The completion-chained dispatch may be silently failing due to GitHub's `workflow_run` default-branch restriction. `super-chain.yml` correctly triggers on `workflow_run` for `stage-b.yml`'s completion and workflow name strings match exactly. However GitHub only fires a `workflow_run` trigger when the triggering run happened on the repository's DEFAULT branch. `stage-b.yml` accepts an explicit ref/SHA input and is dispatched with a specific pinned SHA on every run (including, presumably, Super Series parts) — confirm whether Super Series part dispatches are happening against the default branch or against a pinned SHA/ref that GitHub does not consider a default-branch run for `workflow_run` purposes. If this is the mismatch, this is almost certainly why "the second part is not automatically dispatching". Fix however is correct for this repo's actual setup (e.g. ensuring Super Series Stage B dispatches reference the default branch itself rather than a pinned SHA where that would break the trigger, if safe; or finding the correct equivalent GitHub-native completion trigger not subject to this restriction if pinning the SHA is required for other correctness reasons — read why `stage-b.yml` pins a SHA per dispatch before changing that behavior). Verify with a real two-part Super Series run, checked directly in the GitHub Actions tab, that Part 2 dispatches automatically and promptly the moment Part 1 completes, with no client open.

Part 2 — Full from-scratch rewire of Super Series in the Android app (current implementation broken: settings incorrect, wrong controls on wrong screens — delete and rebuild clean, do NOT patch further):

Step 1 — remove first: delete all existing Super Series-specific code, screens, and settings from the Android app. Ordinary Series Mode code must be left untouched — only Super Series-specific additions are removed.

Step 2 — rebuild against the exact real flow:
- Super Series can only be enabled when ordinary Series Mode is already on; turning Series Mode off forces Super Series off too (reproduce `bot/src/wizard.js`'s enforcement of this dependency, or its current equivalent in `super_chain`/`site/`, exactly). Toggle disabled/hidden whenever Series Mode is off.
- Settings: a dedicated Super Series section (parallel to, not merged into, ordinary Series Mode settings) reading/writing `branding/super_series_settings.json` exactly as the real backend expects — verify the exact current shape of this file/its read-write functions in the current codebase (find the current equivalent under `site/` or `scripts/super_chain/`, match that, not the old bot-era `bot/src/settings_super.js` path if it no longer exists).
- Creating a Super Series: operator submits ONE super-plan document (paste or upload, no size truncation) instead of a single-part production.json — validated the same way the current `parseAndValidateSuperPlan`/`validate_super_plan` equivalent validates it, with the same accept/reject decisions and error strings, not app-invented validation.
- Part 1 dispatches immediately on submission. Part 2 onward dispatch automatically, chained by completion (per Part 1's fix) — the app does not dispatch these itself and needs no "start next part" action for Super Series specifically.
- SPECIFIC BUG: a completed/previous Super Series part must NOT show a "Start Next Part" button. That button belongs ONLY to ordinary Series Mode parts and must never appear on a Super Series part's detail view, regardless of state. The task detail view must correctly distinguish "this is a Super Series part" from "this is an ordinary series part" and render the right controls for each — checked explicitly, not assumed from context.
- Restart/cancel scope on a Super Series part: only Stage A (whole pipeline from scratch) or the CURRENTLY active/most-recently-dispatched part's Stage B can be restarted or cancelled. Earlier, already-completed parts show NO restart or cancel control at all — absent, not disabled/hidden.
- Series overview must clearly distinguish a Super Series from an ordinary series (badge/label) and show queued/upcoming/halted state correctly — a halted chain (a part failed) must be visually obvious, naming which part halted it, with restarting that part being the only way shown to resume (no separate "resume queue" action anywhere).
- Every Super Series part is, from Stage B onward, an ordinary job for every other purpose — logs (real GitHub Actions log view), download, in-app preview all reuse the exact same task-detail code ordinary tasks use, with no special-casing beyond the restart/cancel-scope and no-next-part-button rules above.

Step 3 — verify end to end using the now-fixed backend: enable Series Mode, enable Super Series, submit a real super-plan, confirm Part 1 dispatches and its video banner shows "Part 1", confirm Part 2 dispatches automatically on Part 1's completion with no manual action and no client needing to stay open, confirm no "Start Next Part" button anywhere in this Super Series's parts, confirm only the current part (or Stage A) shows restart/cancel controls, and confirm a deliberately-failed part halts the chain and resumes correctly once restarted.

---

## 2026-09-13 — Operator instruction (slow "Local vision-assist stack" visibility fix in motionssalt/clipforge + ForgeBuild Dashboard stuck-"ongoing" fix)

Target repos: https://github.com/motionssalt/clipforge (GITHUB_REPO_PAT, CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID provided in the prompt; redacted here per credential policy — never committed) and https://github.com/vjumbo264/forgebuild (same GITHUB_PAT placeholder as the contract header).

Part 1 — Investigate + add visibility to a slow "Local vision-assist stack" step (DO NOT add a timeout, kill switch, or anything that fails this step early):

The step has run reliably (typically 2-3 minutes) and never failed; one recent run took 20+ minutes with zero visibility because `detect_shots` in `pipeline/stage_a/scenes.py` calls `subprocess.run(cmd, capture_output=True, text=True)` on the ffmpeg scene-detection pass with no progress output until the single-pass decode finishes. Git history shows the file was only ever touched once (original implementation commit) — no recent regression; most likely cause is an unusually long/high-resolution/high-scene-activity source video or a slower runner, NOT a broken approach. Required:

1. Check the slow job's actual source video duration/resolution/bitrate (via ffprobe, from the recoverable work directory or the job's own logged ffprobe/duration-detection step earlier in Stage A) and compare against a few typical prior runs in the normal 2-3 minute range; record the finding in BUILD_STATE.json notes regardless of outcome.
2. Do NOT add a `timeout=` or any other mechanism that aborts/fails/errors this step based on elapsed time — a slow-but-completing run must still complete successfully.
3. Fix the real issue: give the step live progress visibility. Change this specific call (and any other single-pass `subprocess.run(..., capture_output=True)` calls in the same vision-assist stack sharing the no-visibility-until-done problem — check `scenes.py` fully, not just `detect_shots`) to stream ffmpeg's stderr line-by-line as produced (e.g. `subprocess.Popen` with piped output read incrementally, printing each line as it arrives) instead of capturing silently and dumping at the end.
4. If step 1 finds the source was a clear outlier, note that as the explanation and do not otherwise alter detection logic — only the visibility fix is warranted. If nothing unusual is found and the slowdown remains unexplained even after live progress, note it in BUILD_STATE.json as still-open rather than inventing a fix.
5. Verify the visibility fix doesn't change the step's behavior or output at all — same shot-boundary results, same file outputs, same success path — only live progress lines added to the Actions log.

Part 2 — Previously-requested ForgeBuild fix, now in scope (repo: https://github.com/vjumbo264/forgebuild):

The ClipForge Android app's release status on the ForgeBuild Dashboard is stuck showing version 9 as "ongoing" even though nothing is running; the operator must go to the repo's Releases page directly for the real APK. Fix:

1. Find exactly how the Dashboard determines a version is "ongoing" (almost certainly reading `apps/<slug>/BUILD_STATE.json` for `build_complete: false` or an in-progress task status). Check that file for the ClipForge app: genuinely stuck (session ended without final checkpoint) or Dashboard status-detection logic wrong (deriving "ongoing" from a tag's mere existence rather than the actual build workflow run's completion).
2. If BUILD_STATE.json is stale, correct it to reflect reality based on the repo's actual state and commit history — don't blindly mark complete without confirming.
3. If the Dashboard's status logic is the bug, fix it to derive version status from the actual corresponding GitHub Actions workflow run's real state via the Actions API, not from BUILD_STATE.json or a tag's existence alone.
4. Verify specifically against ClipForge version 9: the Dashboard must show its true state afterward, and the real release/APK must be reachable directly from the Dashboard without going to the Releases page manually.

---

## 2026-09-13 — Copy-agent-prompt release-link parity fix (clipforge-android) + Telegram removal confirmed

Repo: https://github.com/motionssalt/clipforge — GITHUB_REPO_PAT = [REDACTED-PER-CONTRACT] / CLOUDFLARE_API_TOKEN = [REDACTED-PER-CONTRACT] / CLOUDFLARE_ACCOUNT_ID = 5dc9710ac37c9ce333dce2434ce4343b

# Bug: the Android app's "copy agent prompt" text has no link to the
GitHub release at all, in every mode — the site's equivalent has this
correctly, the app's does not

To be precise about which prompt this is, since it was previously
misidentified: this is the prompt the operator copies from the task detail
screen (in the app) and pastes into an external AI agent, so that agent
can open the release, read `00_READ_THIS_FIRST.txt` and the evidence
assets, and produce the production.json (or, for Super Series, the
whole-series super-plan). This is NOT `00_READ_THIS_FIRST.txt` itself
(which correctly has never contained links — that's by design, unrelated
to this bug).

The correct reference implementation already exists and works: read
`agentPromptCard(status, request)` in `site/js/features/tasks.js` in full.
Its very first constructed prompt line is:

    `Open this GitHub release: ${releaseUrl}`

where `releaseUrl` is `status.release_url` (falling back to a constructed
`https://github.com/${repo}/releases/tag/clipforge-${jobId}` only if
`status.release_url` is empty/missing). This line is present for every
mode this function handles — ordinary task, ordinary Series Mode part, and
Super Series anchor — since it's built once, before the mode-specific
`seriesClause` branching happens.

Find the Android app's own implementation of this same "copy agent prompt"
text (it is very likely a separate, independently-written copy rather than
shared code, since the site's version is correct and the operator has
confirmed the app's output is missing the release link entirely). Fix it
by:

1. Locating the exact function/method in the app that builds this prompt
   text.
2. Adding the equivalent of the site's `Open this GitHub release:
   ${releaseUrl}` line, using the app's own already-available job status
   data (`status.release_url`, with the same same-shaped fallback
   construction if that field is ever empty) — as the first line of the
   constructed prompt, exactly matching where the site places it.
3. Cross-checking the REST of the app's prompt text against
   `agentPromptCard` line by line while this is open — confirm the app's
   version also correctly includes the target-duration/narration-length
   instructions, the mode-specific series clause (ordinary series vs
   Super Series, worded to match), and the exact delivery/format
   instructions at the end. Fix any other divergence found, not just the
   missing link line — the operator should get functionally identical
   prompt text from the app and the site, since previous fixes have
   specifically aimed for that parity and this divergence shows it isn't
   fully there yet.
4. Verify by generating a real prompt from the app for an ordinary task, an
   ordinary series part, and a Super Series anchor, and confirming each one
   includes a real, correct, clickable-when-pasted-elsewhere release link
   as its first line, matching what the site produces for the same job.

---

## 2026-09-13 — Trailing operator line (task-90 unblock)

"YOU SHOULD DELETE THE OLD TELEGRAM STUFF. I'VE VALIDATED AND CONFIRM THAT ITS NO LONGER NEEDED."

→ Operator confirms task-90's pending parity verification is complete: Bot A/B Workers, CLIPFORGE_BOT_D1 (720de60e-1a08-492a-9ce6-64cb9534098d), bot KV (b09957b233c247deb40cc703ffadc95d), deploy-bots.yml, telegram-relay.yml, bot/ and relay/ may all be deleted; docs updated to GitHub-only architecture.

---

## 2026-09-14 — Super Series spawned parts missing stage-a-request.json (Stage B re-fetch fails for >2GiB sources)

(credentials redacted before commit — operator rule: never commit credential values)

Repo: https://github.com/motionssalt/clipforge — GITHUB_REPO_PAT = (redacted)
CLOUDFLARE_API_TOKEN = (redacted)
CLOUDFLARE_ACCOUNT_ID = 5dc9710ac37c9ce333dce2434ce4343b

# Bug: Super Series parts spawned via the completion-chain have no
`stage-a-request.json`, so Stage B's source re-fetch fails whenever the
original source exceeded GitHub's 2GiB per-asset limit

Confirmed root cause by reading the actual job data and code:

- A real failing job, `jobs/series-1789333712217-p1/`, contains only
  `production.json` and `status.json` — no `stage-a-request.json`.
- An ordinary Series Mode part's job folder (e.g. `jobs/
  series-1789293925544-p1/`) DOES have its own `stage-a-request.json`,
  confirming this file is supposed to exist on every spawned part, Super
  Series included, and is what Stage B falls back to reading when it needs
  to re-fetch a source video that was too large to keep as a release asset
  (>2GiB), per the exact error in the operator's screenshot: "the Stage A
  release intentionally omitted source_input.bin because it exceeded
  GitHub's 2 GiB per-asset limit, so the source is being re-fetched from
  the original source reference saved in stage-a-request.json... no
  stage-a-request.json found for the job — cannot re-fetch."
- The OLDER Super Series spawn path, `site/js/supertick.js` (line ~147),
  correctly calls `saveStageARequest(credentials, repo, outcome.jobId,
  requestBody)` when spawning a part — this is the correct, working
  behavior, and `scripts/super_chain/super.js` even has a function
  specifically for synthesizing this request body for a spawned part
  (search for its comment: "Synthesize the ordinary §7.1 stage-a-request
  body for a spawned Super Series part").
- The NEWER completion-chain spawn path, `scripts/super_chain/
  super-chain.mjs` (the one actually running now, per the earlier fix that
  replaced cron/polling with a `workflow_run`-triggered chain), READS the
  anchor's `stage-a-request.json` (to get the source reference) but never
  WRITES one for the newly spawned part — `dispatch.mjs` even defines
  `STAGE_A_REQUEST_PATH` as a constant but nothing calls a save function
  with it anywhere in `super-chain.mjs` or `dispatch.mjs`. This step was
  present in the older `supertick.js` path and was dropped when the
  completion-chain dispatch replaced it — a real gap introduced by that
  rewrite, not a pre-existing bug.

**Fix:** In `super-chain.mjs` (or wherever it calls into `dispatch.mjs` to
spawn the next part), add the missing step: synthesize and save
`stage-a-request.json` for the newly spawned part, using the exact same
approach `site/js/supertick.js` already uses correctly — reuse
`super.js`'s existing part-request-synthesis function rather than writing
new logic, since this was already built correctly once and just needs to
be called from the code path that's actually running now. The synthesized
request body must carry forward the anchor's real source reference (so a
re-fetch has something to re-fetch from), not a placeholder.

Verify by:
1. Confirming, for a fresh Super Series run where the source exceeds
   2GiB, that every spawned part's job folder now contains a correct
   `stage-a-request.json` with the real source reference.
2. Confirming Stage B's re-fetch path succeeds for such a part (does not
   hit "no stage-a-request.json found for the job") when Stage B needs to
   re-obtain the source video.
3. Restarting the currently-failed job (`series-1789333712217-p1`, or
   whichever job is the operator's current failed instance) after this
   fix lands, and confirming it now progresses past the re-fetch step —
   note in `BUILD_STATE.json` if this specific job needs its
   `stage-a-request.json` backfilled manually (synthesized from the
   anchor `jobs/manual-1789333712217/stage-a-request.json`) to unblock it
   immediately, separate from fixing the code so future parts don't hit
   this at all.

---

## 2026-09-14 — Super Series: Stage B spawned "outside" Stage A does not inherit Stage A state; stale duplicate card

Operator report (verbatim, voice-transcribed): "When I start a new Super
Plan, Stage A finishes and I put the production plan in it. It shows that
it has started. But when I check it, the [anchor] is still [there]; I come
out and check the full overview and I see that a new Stage B has started
outside that one. The other one is showing as though it has already
started, while it is not updating anything. The new one is the one that
actually does the stuff. Because the Stage B is being created outside the
Stage A, it is not continuing from where the Stage A stopped — some things
are not being inherited by this Stage B from this Stage A. Please fix this;
this is the third time a new issue has come up here."

Screenshot (operator-provided): Dashboard "Active Tasks" shows
- `Rick-p1` — Failed — "Series: Part 1" — "Stage B failed. See workflow
  run for logs: https://github.com/motionssalt/clipforge/actions/runs/34824770259"
- `manual-1789372351898` — "Rendering queued" — "Series: Part 1" —
  "Super Series part 1/7 dispatched."

Interpretation to verify against live job data: (a) the Super Series anchor
job card stays in a stale "dispatched/queued" state forever because nothing
updates the anchor's status.json after Part 1 is dispatched; (b) the
spawned part (series-...-p1) failed in Stage B — read the failing run's
logs to find what Stage-A output/state the spawned part's Stage B did not
inherit (third inheritance-type gap after the 2GiB stage-a-request.json
bug), and fix the spawn path so spawned parts inherit everything Stage B
needs from the anchor's Stage A.

---

## 2026-09-14 — Super Series fixes: segments/cuts validator mismatch, AI part-count conservatism + per-part word budget, submit-button double-fire

Operator report (voice-transcribed, condensed): (1) submitting a Super-Plan
shows "parts[N]: `segments` must be an array / `series_summary` must be a
non-empty string" even when the plan is valid — root cause: the app validates
`segments` (SuperSeries.kt) while the backend validates `cuts`
(pipeline/plan/schema.py), so plans authored with `cuts` are rejected by the
app and plans authored with `segments` are rejected by the backend. (2) The AI
is conservative on part count (2-3 parts even when the source supports more)
and ambiguous about per-part narration length (once treated a 30s PER-PART
target as ~90 words for the WHOLE series). (3) The "Submit Super-Plan" button
shows no loading state, so it gets tapped multiple times and the duplicate
submit errors. Fix: accept/normalize `cuts`<->`segments` on BOTH validators
(app SuperSeries.kt + backend schema.py, site/js/plan.js, scripts/super_chain/
plan.js, super_series.py slicer); super-plan prompts now state no part-count
cap (up to 20, plan as many as the source supports) and explicit per-part
word-budget arithmetic (~3.1 words/s, per-part reference table); submit button
disables itself and shows a spinner while the submit coroutine runs. Unstick:
Rick-p1 production.json + anchor super-plan normalized to `cuts`; Stage B
restarted on the fixed code.

## 2026-09-15 — EXTEND/UPDATE: ForgeHouse 50 — Leaderboard Points Fix, Broken Scripture Loading, New Icon (credentials redacted)

Operator instruction (verbatim, credentials redacted): ForgeHouse 50 has
three fixes. Issue 1: the leaderboard's primary "Overall Score" regressed to
a per-day average / confidence-weighted adjusted_score
((raw_average x elapsed_days) / (elapsed_days + K)); it must be raw
cumulative total points (reading completion + quiz + streaks + observations +
questions + community sharing + weekly targets + full-programme completion),
no division, no damping, on both site and Android app (same backend — likely
one backend fix; verify both surfaces; invalidate app-side cached leaderboard
data so a stale averaged value cannot mask the fix). adjusted_score was only
ever meant as a narrow secondary fairness signal for mid-programme averaged
comparisons — if kept at all it must be a clearly-labeled secondary view,
never the default; remove if it adds confusion. The end-of-programme final
snapshot already uses raw totals — confirm untouched. Verify live: displayed
value must match a manual sum of a real user's point history. Issue 2: the
Bible/scripture reading screen is stuck on loading forever — diagnose which
surface(s) (site and/or app), reproduce with real network evidence, fix the
root cause (unhandled rejection / loading flag never flipped / hung request),
add a timeout + clear error fallback, verify live on >= 2 reading days.
Issue 3: replace app icon with newly attached forgehouse50-icon-v3.png
(glossier blue rounded-square flame/house/globe/"50" mark) as the new master
source replacing forgehouse50-logo-source.png; regenerate the Android
adaptive icon set (foreground/background/safe-zone/legacy, no corner
artifacts, pixel-verified) AND the web PWA icon set (192/512 + maskable,
favicon, apple-touch-icon) from it; verify rendered on-device and in the web
manifest. Discipline: BUILD_STATE.json as source of truth, persistent
session, per-step commit+push, verify live with real evidence, section
leaderboard_scripture_icon_fix_v1 appended to the web repo BUILD_STATE.json
and the Android app's build-state file.

## 2026-09-16 — Operator instruction
"""
Full UI overhaul with the new Google Material Design and loading, animations, squiggle, screens, everything nice to make the UI beautiful. change whatever you need in the UI.
"""

## 2026-09-19 — Operator instruction
"""
EXTEND/UPDATE — clipforge-android. Operator instruction 2026-09-19.
Follow the standard extend contract (append to PROMPT_HISTORY/clipforge-android.md, seed tasks, checkpoint BUILD_STATE.json after every task, cut clipforge-android-v(n+1), do not touch prior releases).

AUTONOMY: Run fully autonomously. Ask no clarifying questions. Where something is ambiguous, pick the most sensible option, record the decision in BUILD_STATE notes, and continue.

NO DEVICE TESTING: The AI cannot install or run the APK. Do NOT screenshot, do NOT attempt on-device or emulator testing, and do NOT add or keep any step in the prompts, tasks or build flow that asks for screenshots or runtime verification of the app. Verification = compile success, unit/kotlinc checks, and the release.yml run + Releases API asset check only. Remove any existing screenshot/test-the-app language from the generated prompts and workflows.

Scope: complete UI sweep plus the fixes below. Work through them as separate tasks.

1. NEW APP ICON (AI-generated, clean, premium)
The current icon is ugly. Generate a new one with an AI image model (use whatever image-generation access exists; if none is reachable from the build session, procedurally generate a clean vector-style mark in code, and record which method was used). Direction: minimal, modern, flat/soft-gradient mark that reads as "clip + forge/spark" (a play-triangle or film-cut motif with a spark/flame accent); rounded-square friendly; one strong focal shape; no text; no clutter; works on light and dark. Brand palette: keep the app's olive/dark-green identity unless the new UI overhaul defines a better primary. Regenerate the full adaptive icon set: ic_launcher_foreground (inside the 66dp safe zone, no clipping), ic_launcher_background, ic_launcher_monochrome (themed-icon silhouette), legacy mipmaps at mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi, and round variants. Pixel-check for no corner artifacts, no white fringes, no off-centre mark. Keep the master source PNG committed in the app folder.

2. COMPLETE UI OVERHAUL with the upgraded ForgeBuild engine UI
The ForgeBuild engine/dashboard UI was upgraded. Pull the CURRENT engine/ folder and re-base the app's shared UI on it (theme, typography, shape, elevation, icons, motion primitives), then rebuild EVERY screen on top of it so nothing keeps the old look: setup/login (incl. the Connect Existing / Create New Clone screen), tasks list, task detail, new-task wizard, completed videos, music, series and Super Series screens, settings, dialogs, empty states, toasts/snackbars, bottom bar/nav, splash, video player and audio preview. Requirements: clean, fresh, premium; Material 3 Expressive/Material You with dynamic colour and proper light+dark; consistent spacing scale, type scale and corner radii; tonal surfaces instead of hard borders; large-touch-target controls; purposeful motion (shared Motion.kt: staggered list entrances, screen transitions, squiggle/expressive loaders, skeleton shimmer while loading); no leftover default-looking widgets. Every screen must have a designed loading state, empty state and error state. Do this as one coherent design system pass, not per-screen patches.

3. LOGGER REBUILD (UI + behaviour)
Symptoms: a running task's log shows only status "ongoing" and nothing under it; when finished it shows only "status: completed / result: success" per action instead of the actual steps that ran; the logger's look does not match the app.
Fix in ClipForgeViewModel (the detail-logs builder that reads GET /actions/runs/{id}/jobs) and the log UI:
 a) Root-cause why steps are missing/empty: check pagination and per_page, the per-job `steps` array being skipped by `?: continue` when null, polling that stops or never re-fetches while a run is queued/in_progress, runs that are looked up before the job list exists, and cached/CacheFirstStore data masking fresh fetches. Fix the cause, not the symptom.
 b) Show EVERY step of EVERY job of every relevant run (stage-a, stage-b, publish, super-chain), each with: name, status icon, coloured state (pending=neutral grey, running=animated accent, success=green, failed=red, skipped=amber, cancelled=muted), duration, started/finished time. Under each step, when expanded, show its real content: the step log lines. Fetch and parse the per-job log (GET /actions/jobs/{job_id}/logs, or the run log zip as fallback), split it by step using the `##[group]`/step markers, strip timestamps and ANSI codes, and render as a monospaced, selectable, collapsible block. Never show just "status/result" as the whole entry.
 c) While a task is running: live-poll (adaptive interval, faster while in_progress), show a loading/progress indicator on the running step (squiggle/indeterminate bar), auto-append new steps and new log lines as they arrive, and auto-expand the currently running step. Queued state shows a designed "waiting for runner" state, not blank space.
 d) Completed tasks keep the full step history (expandable), with an overall result chip plus total duration; failed steps are auto-expanded and their last error lines highlighted; add copy-log and share-log actions.
 e) Visual design matches the new premium system: card-based timeline with a vertical connector, status-coloured nodes, subtle motion on expand/collapse.

4. RESTORE ZERNIO PUBLISHING SETTINGS (parity with the site and the old Telegram bot)
The app currently only has the API key, an enable toggle and the accounts list. Port the full settings surface from motionssalt/clipforge site/js/features/settings.js (read it; it is the source of truth) and the settings document branding/zernio_settings.json in the user's clone. The schema is:
 { version, enabled, auto_publish, automatic_mode: "publish_now" | "smart_schedule", target_accounts: {platform: [accountIds]}, smart_schedule: { timezone (IANA), interval_hours (1–8760), preferred_time (HH:MM), queue_depth, start_mode: "next_available" | "custom", custom_start ("YYYY-MM-DDTHH:MM") } }
Add designed controls for: automatic publish on/off; automatic mode (publish now vs smart schedule); interval in hours; preferred time (time picker); timezone (searchable IANA picker); queue depth; start mode (next available vs custom first slot with date-time picker); and per-platform target account selection. Validate exactly like zernio.py (interval 1–8760 whole hours, HH:MM, valid IANA zone, custom_start required when start_mode=custom). Read and write the same settings file through the existing GitHub contents API path with SHA-safe updates so the site and the app never overwrite each other. Show a live summary line ("every 6h at 05:00 UTC, queue depth 100, next available").

5. PER-TASK PUBLISHING ON COMPLETED TASKS (parity with the bot/site)
Completed tasks currently have no publish controls. Port site/js/features/tasks.js publishing (zernioTaskPublishButton, dispatchZernioPublish, dispatchZernioPostAction). On a completed task, when Zernio is enabled: show a Publish card with the current publishing status (not_requested / publishing / scheduled / published / partial / failed) and targets; actions "Publish now", "Smart schedule", and "Schedule for…" (date-time picker, timezone from settings, validated as YYYY-MM-DDTHH:MM). Per-post controls: Retry, Publish now, Reschedule, Cancel (with confirmation) for each platform post. Dispatch the same publish.yml workflow with the same inputs the site uses (mode, scheduled_for, timezone, task label, idempotency key, action/post id), and reflect the result live in the task detail. Also expose "publish again / reschedule" on tasks that were already published. Add the same publish action to the completed-videos list as a quick action.

6. FIX "Clone creation failed" (ETIMEDOUT to api.github.com:443)
Reported error: "failed to connect to api.github.com/140.82.121.6 (port 443) ... after 20000ms: isConnected failed: ETIMEDOUT". This is a network-level connect timeout on the operator's mobile data, not an auth or logic error. Make clone creation resilient: raise connectTimeout to 45s for the setup flow, add automatic retry with exponential backoff (at least 4 attempts) on ETIMEDOUT/SocketTimeout/UnknownHost/connection-reset, enable OkHttp `retryOnConnectionFailure`, try IPv4/IPv6 fallback (Dns that orders IPv4 first, then Happy-Eyeballs fallback), and make each step of ShadowClone.begin idempotent so a retry resumes instead of failing on "repo already exists" (detect and reuse a partially created repo). Show progress ("Attempt 2 of 4…", "Retrying in 6s") with a designed progress state instead of a single failure; on final failure show a friendly card: what failed, likely cause (mobile network / data saver / VPN / DNS), and a one-tap Retry plus "Switch network and retry". Also add a small connectivity pre-check (HEAD https://api.github.com) before starting. Keep the existing PAT scopes check and the poll-for-complete logic.

7. CLEANUP JOB EMAIL SPAM (motionssalt/clipforge, .github/workflows/cleanup.yml)
The operator gets GitHub failure emails from this hourly workflow and no longer uses Telegram. IMPORTANT FACTS: cleanup.yml is NOT a Telegram artifact. It deletes expired job releases, tags, jobs/<id>/ folders and per-job branches once their TTL passes (pipeline/cleanup/expired.py, ARCHITECTURE.md §12). It is referenced by stage-a.yml/stage-b.yml TTL logic and has tests. Do NOT delete it blindly, or expired jobs pile up forever. Procedure:
 a) Pull the last runs via the Actions API and read the real failure log. Diagnose the exact cause (bad token permission, missing variable, removed Telegram/D1/KV dependency, rate limit, the expired.py code path, checkout/fetch-depth issue).
 b) If it is fixable, fix it and prove it with a green workflow_dispatch run.
 c) Independently of the fix, stop the failure emails: change it so a transient failure cannot email the operator (fail-soft: catch errors per step, log to the job summary, `continue-on-error` on non-critical steps, exit 0 unless data was corrupted) and reduce the cadence from hourly to every 6 hours (`cron: "7 */6 * * *"`) since TTL is 12–48h.
 d) Remove any leftover Telegram-only cleanup logic, dead D1/KV references and dead env vars from that workflow and module.
 e) Confirm in BUILD_STATE with evidence: last runs before, a green run after, and that the workflow_dispatch still works. If after diagnosis it turns out that NOTHING functional depends on it (e.g. jobs are cleaned some other way) then disable it (`gh workflow disable`) and record why. Do not disable without that evidence.
 Also audit the other backend workflows (diagnostics.yml, news-push.yml) for the same Telegram-era failure emails and fix them the same way.

8. RELEASE
Bump versionCode/versionName, run release.yml, verify the release, tag and APK asset via the Releases API (no device testing, no screenshots). Append the operator instruction verbatim to PROMPT_HISTORY/clipforge-android.md, mark all tasks done in BUILD_STATE.json, set build_complete=true.

Hard rules: BUILD_STATE.json is the source of truth; persistent session; per-step commit+push; never redo completed work; never commit tokens/PATs; the build session must never delete the repository; never touch prior releases.
"""


---

<!-- appended 2026-09-19 (second round) by ForgeBuild AI session -->

# EXTEND/UPDATE — clipforge-android (v21 → v22)

Operator instruction 2026-09-19 (second round). Follow the standard extend contract: append this instruction verbatim to `PROMPT_HISTORY/clipforge-android.md`, seed tasks, checkpoint `BUILD_STATE.json` after every task, cut `clipforge-android-v22`, never touch prior releases, never delete the repository, never commit tokens/PATs.

**AUTONOMY:** Run fully autonomously. Ask no clarifying questions. Where something is ambiguous, pick the most sensible option, record the decision in BUILD_STATE notes, and continue.

**NO DEVICE TESTING:** You cannot install or run the APK. Do NOT screenshot, do NOT attempt emulator or on-device testing, and do NOT put screenshot or run-the-app steps in any prompt, task or workflow. Verification = successful compile, unit tests, `release.yml` run success, and the Releases API asset check only.

**WHY THIS ROUND EXISTS:** v21 was released but the operator saw NO change in the logger or the UI, the Zernio settings did not land, and background audio is intermittent. The operator is angry. v20 and v21 were marked "done" without the work actually reaching the screen. This round must fix root causes and must be verified by reading the code that ships (see the "Proof of work" section), not by trusting earlier BUILD_STATE claims. Do not mark any task done on the strength of existing notes.

---

## PRIORITY 1 — THE UI IS STILL THE OLD UI. REBUILD IT ON THE ENGINE'S REAL M3 EXPRESSIVE STACK

### 1.0 Root cause (verified in the repo, fix this FIRST)

The app cannot look like the new UI because it is on the wrong library. Facts:

- `apps/clipforge-android/app/build.gradle.kts` uses `platform("androidx.compose:compose-bom:2024.09.00")` and plain `androidx.compose.material3:material3` (M3 1.3.x). That version has NO `MaterialExpressiveTheme`, NO `MotionScheme.expressive()`, NO wavy progress, NO `LoadingIndicator`, NO `ButtonGroup`, NO `SplitButton`, NO `FloatingActionButtonMenu`. Previous sessions could only imitate them by hand.
- The engine (`engine/`) uses `androidx.compose.material3:material3:1.5.0-alpha28` on the `1.13.0-alpha01` compose train, with NO BOM (the latest BOM still pins the non-expressive 1.4.0), AGP 9.4.0, Kotlin plugin 2.4.20, `activity-compose:1.13.0`.
- Only **2 files** in the app (`MainActivity.kt`, `VideoPlayer.kt`) reference the engine theme. Every other screen uses stock or hand-rolled widgets. `ui/Motion.kt` is a hand-drawn imitation of a squiggle loader.

### 1.1 Re-base the app on the engine stack

1. Read `engine/ARCHITECTURE_MATERIAL_DISCIPLINE.md`, `engine/app/build.gradle.kts` and `engine/build.gradle.kts` in full. They are the contract.
2. Make `apps/clipforge-android` build files match the engine EXACTLY: remove the BOM, pin the same compose train and the same `material3:1.5.0-alpha28`, same AGP, same Kotlin compose plugin, same `activity-compose`, same compileSdk/minSdk/targetSdk, and copy any opt-in flags or repositories the engine needs. Keep the app's own signing/versioning/`applicationId`.
3. Copy the engine's UI layer into the app (or reference it as a module, whichever compiles cleanly) so these exist in the app: `ui/theme/Theme.kt (ForgeBuildTheme → MaterialExpressiveTheme)`, `ColorTokens`, `ShapeTokens`, `TypographyTokens`, `MotionTokens`, `ElevationTokens`, `SpacingTokens`, and `ui/components/EngineExpressive.kt` + `EngineProgress.kt`. Do not fork or approximate them; use the real files.
4. If the compile breaks, fix the compile errors. Do NOT fall back to the old BOM. The whole point is to get onto the expressive library.

### 1.2 Delete the old UI and rebuild every screen (no preservation)

The operator's words: *"It should not preserve any previous UI. It should reinvent the UI. Every single thing must be changed."*

- Delete `ui/Motion.kt` (the hand-drawn imitation) and every hand-rolled loader, spinner, shimmer and bespoke card style. Replace with the real engine components.
- Rebuild EVERY screen from scratch on the new stack, none skipped: splash, setup/login (Connect Existing + Create New Clone), tasks list, task detail, new-task wizard (source/torrent selection, duration incl. 30s and custom, music, series, Super Series), completed videos, music library, series screens, Settings (every section), all dialogs and bottom sheets, empty/error states, snackbars, nav bar/rail, video player, audio preview, and the logger.
- Everything inside `ForgeBuildTheme`. No raw hex colours, no raw dp corner radii, no per-screen font sizes. Use `MaterialTheme.colorScheme` roles, `MaterialTheme.typography` roles, `MaterialTheme.shapes` tiers, `SpacingTokens`, `ElevationTokens.tonalContainerColor`, `MotionTokens`. Dynamic colour on API 31+.
- Use the official expressive components everywhere they apply: `EngineLinearWavyProgress`, `EngineCircularWavyProgress`, `EngineLoadingIndicator` (morphing shapes), `EngineButtonGroup`, `EngineSplitButton`, `EngineFabMenu`, `ExpressiveButton` / `ExpressiveTonalButton`, `ExpressiveButtonLoader`. Use the official `MaterialShapes` library and `Morph` for decorative shapes. Large-touch-target controls, tonal surfaces instead of hard borders, expressive top app bars, expressive `ListItem`s, expressive sliders.
- Result must read as a modern Google app (Material 3 Expressive), visibly different from v21 on first launch: new colour, new shapes, new type, new motion.
- Splash → main transition, screen-to-screen transitions, list entrances and expand/collapse all use `MotionTokens` spring specs.

### 1.3 EVERY button must show feedback (fixes "I tap 2-3 times, then get errors")

Measured problem: 76 `Button(` calls in `ui/`, only 21 `busyOps` references. Most buttons give no feedback, so the operator re-taps and triggers duplicate-request errors.

- Introduce ONE shared action-button composable (built on `ExpressiveButton` + `ExpressiveButtonLoader`) that takes `onClick: suspend/async` and a `busy` state. On tap it must, within one frame: disable itself, swap its content for the morphing loading indicator (label may stay), and ignore further taps until the operation finishes.
- Replace ALL 76 raw buttons (including `TextButton`, `IconButton`, dialog confirm buttons, FABs, list-row actions, menu items that trigger network work) with it. Every operation that hits the network, GitHub API, or dispatches a workflow must have a busy key in the ViewModel (`withBusy(key)`), and that key must drive the button.
- Add a global re-entrancy guard in the ViewModel: a second tap on an operation that is already in flight is ignored (not queued, not errored). Idempotent operations use their key as the lock.
- Success/failure end state: brief success tick or an error shake with a snackbar. Never leave a button with no visible change.
- Add a grep-based CI check to `release.yml` that FAILS the build if any `Button(` / `TextButton(` / `IconButton(` / `FilledTonalButton(` remains in `ui/` outside the shared action-button file. This is how you prove the sweep is complete.

---

## PRIORITY 2 — LOGGER MUST SHOW THE REAL GITHUB ACTIONS LOG TEXT

### 2.0 Answer to the operator's question

**Yes, GitHub fully supports it.** `GET /repos/{owner}/{repo}/actions/jobs/{job_id}/logs` returns the raw step-by-step log text (it 302-redirects to a signed URL that OkHttp follows), and `GET /actions/runs/{run_id}/logs` returns a zip of every job's log. The PAT the clone already uses can read them (needs `Actions: read`). This is not a GitHub limitation. The code for it exists in `GitHubClient.jobLog()` and `splitLogByStep()`, yet the screen still shows only "status … · result …". So the wiring is broken. Find and fix why.

### 2.1 Defects found in `ClipForgeViewModel.kt` (around lines 893-957) — fix all of them

1. **Silent failure.** `try { c.splitLogByStep(c.jobLog(id)) } catch (_: Exception) { emptyMap() }` swallows every error, and `jobLog()` returns `""` on ANY non-2xx response. A 403 (missing `Actions: read`), 404, 410 (log expired), rate limit or timeout all become an empty map, which then renders as the fallback `"status: … · result: …"` line. That fallback IS what the operator keeps seeing. Errors must be surfaced, not hidden.
2. **Fallback masquerades as content.** When step lines are empty, the code adds `LogDetail("status: $sStatus · result: …")`. Remove this fallback as the primary content. Replace with explicit states: "Loading log…" (shimmer), "Log not available yet (step still queued)", "Log unavailable: <HTTP code and reason>" with a Retry button, or "Log expired/removed by GitHub".
3. **Fuzzy step matching is unreliable.** `linesForStep` matches on `equals || contains` after `removePrefix("Run ")`. Steps like "Run actions/checkout@v4", "Set up job", "Post Run …", "Complete job" and steps whose group header is the shell command do not match their API step name, so lines land on the wrong step or none. Replace the name-matching with the deterministic approach in 2.2.
4. **Polling gap.** Logs are only fetched when a job is `in_progress` or `completed`, and only re-fetched on the detail refresh. A running step's log never streams in. Add a live poll (see 2.3).
5. **Truncation.** `takeLast(400)` per step and `takeLast(120)` per job hide the actual beginning of long logs. Keep the full log in memory-efficient chunks, render lazily, and add "jump to top/bottom".

### 2.2 Correct log parsing

- Fetch per-job logs via `GET /actions/jobs/{job_id}/logs`. Verify the Authorization header is preserved on the redirect correctly (the redirect target is a pre-signed URL and must NOT carry the Authorization header, or it returns 403; use a client that strips auth on cross-host redirect, or fetch with `followRedirects(false)` and follow manually).
- Map log text to steps by **step number order**, not by name. In the job log, steps appear in order and each begins at its `##[group]…` marker; the API's `steps[]` array is ordered by `number`. Split the log into ordered blocks and zip them to `steps[]` by index (accounting for the synthetic "Set up job" / "Complete job" blocks that appear in the log). Fall back to name matching only if counts disagree, and log a diagnostic when they do.
- Strip ISO timestamps, ANSI escape codes, and convert `##[error]`, `##[warning]`, `##[notice]`, `##[command]`, `::error::` into styled line levels (red / amber / blue / dim). Preserve blank lines and indentation.
- Handle the ZIP fallback (`/runs/{id}/logs`) for completed runs where the per-job endpoint is unavailable: unzip in memory, match `<n>_<job name>/<m>_<step name>.txt` entries to steps.

### 2.3 Live behaviour

- While any run is `queued`/`in_progress`: poll jobs + logs on an adaptive interval (2-3s in progress, 8s queued), append new lines incrementally (track byte/line offset), auto-expand and autoscroll the running step, show `EngineLinearWavyProgress` on the running step and `EngineLoadingIndicator` on a queued run ("Waiting for a runner…"). Stop polling when the run is terminal or the screen is left.
- Completed tasks: show the full step history with real log text, an overall result chip and total duration; failed steps auto-expand with the last error lines highlighted; actions: copy step, copy whole log, share log as `.txt`.
- Show every job and every step for every relevant run (stage-a, stage-b, publish, super-chain), not just the newest.

### 2.4 Visual

Card timeline with a vertical connector and status-coloured nodes (pending grey, running animated accent, success green, failed red, skipped amber, cancelled muted), monospaced selectable log body, expand/collapse with `MotionTokens` springs, sticky step headers. Built entirely from the new engine components and theme, matching the rest of the redesigned app.

---

## PRIORITY 3 — ZERNIO PUBLISHING SETTINGS AND PER-TASK PUBLISHING STILL MISSING

Operator confirms the v21 changes for these did not land. Re-verify against the source at the end of this task by reading the shipped Kotlin, not by trusting BUILD_STATE.

The source of truth is `motionssalt/clipforge` → `site/js/features/settings.js` and `site/js/features/tasks.js`, plus `pipeline/publish/zernio.py` and the clone's `branding/zernio_settings.json`. Schema:

```
{ version, enabled, auto_publish,
  automatic_mode: "publish_now" | "smart_schedule",
  target_accounts: { platform: [accountIds] },
  smart_schedule: { timezone (IANA), interval_hours (1-8760), preferred_time "HH:MM",
                    queue_depth, start_mode: "next_available" | "custom",
                    custom_start "YYYY-MM-DDTHH:MM" } }
```

### 3.1 Settings screen (main Settings → Publishing)

Controls: automatic publish on/off; automatic mode (publish now vs smart schedule); interval hours; preferred time picker; searchable IANA timezone picker; queue depth; start mode (next available / custom first slot with date-time picker); per-platform target-account selection. Validate exactly like `zernio.py` (interval whole hours 1-8760, HH:MM, valid IANA zone, `custom_start` required when `start_mode=custom`). Read and write `branding/zernio_settings.json` through the contents API with SHA-safe updates (re-read, merge, PUT with the latest SHA, retry on 409) so the site and the app never overwrite each other. Show a live summary line, e.g. "every 6h at 05:00 UTC, queue depth 100, next available".

### 3.2 Completed tasks (task detail AND completed list)

Verified fact: the app currently has zero publish UI on completed tasks (`grep -i publish ui/TasksScreens.kt` returns nothing). Port `zernioTaskPublishButton`, `dispatchZernioPublish`, `dispatchZernioPostAction` from `site/js/features/tasks.js`:

- Publish card showing status (`not_requested / publishing / scheduled / published / partial / failed`) and targets, visible when Zernio is enabled.
- Actions: **Publish now**, **Smart schedule**, **Schedule for…** (date-time picker in the settings timezone, validated `YYYY-MM-DDTHH:MM`).
- Per-post controls per platform: **Retry**, **Publish now**, **Reschedule**, **Cancel** (with confirm).
- Also on already-published tasks: **Publish again / Reschedule**.
- Dispatch `publish.yml` with exactly the inputs the site sends (`mode`, `scheduled_for`, `timezone`, task label, idempotency key, action and post id; check `settings.js`/`tasks.js` lines ~688-734 for the exact payload) and reflect the result live in the task detail.
- Quick publish action on rows in the completed-videos list.
- All of it built with the new shared busy-aware button so a tap always shows feedback.

---

## PRIORITY 4 — BACKGROUND AUDIO IS INTERMITTENT ("coin toss"): INVESTIGATE AND MAKE IT DETERMINISTIC

Operator: a music track is selected, yet sometimes the finished video has no background audio, sometimes it does. Unacceptable for professional work. Diagnosis from the code (fix all of these):

### 4.1 Root causes found

1. **The app silently converts every failure into "no music".** `ClipForgeViewModel.resolveMusicRef()` (~line 1112):
   - `readFile("jobs/<id>/stage-a-request.json")` is wrapped in `catch (_: Exception) { null }`, and a null request falls through to `music.optString("source","none")` → `"none"` → returns `""`.
   - For `source:"default"`, both the `music_default.json` read and its parse are wrapped in `catch → ""`.
   - So a network blip, a 404, a rate limit, or a not-yet-visible file all yield an empty `music_ref`. `stage-b.yml` then prints "No music_ref supplied — final video ships without background music" and **still finishes successfully**. Nothing in the app or the video flags it. That is the coin toss.
2. **Race on the request file.** The ref is resolved at dispatch time by re-reading `stage-a-request.json` over the API; if the read happens before the commit is visible (or hits a cache), the ref is empty.
3. **`source:"default"` is resolved late and ambiguously.** Whether music plays depends on `branding/music_default.json` at dispatch time. If it is changed, unset, or unreadable, the same task flips to silent.
4. **No verification that music was actually mixed.** `pipeline/stage_b/run.py` records `music_applied`, but nothing enforces or surfaces it. The `amix` path in `pipeline/stage_b/render.py` also has no guard against the measured gain landing effectively inaudible (`MUSIC_TO_VOICE_LOUDNESS_RATIO = 0.15`, clamps −60..+24 dB) or against a silent/corrupt file falling back to the fixed 0.33 volume.
5. In `stage-b.yml`, a `path:` ref pointing at a missing library file does `exit 1` (loud), but an empty ref exits 0 (silent). The silent path is the dangerous one.

### 4.2 Fixes (both repos; app in ForgeBuild, pipeline in the user's clone / `motionssalt/clipforge`)

**App (`clipforge-android`):**
- **Resolve once, freeze, and pass through.** At task creation, resolve the final music to a concrete `path:` ref and store BOTH the resolved ref and the resolution source in `jobs/<id>/stage-a-request.json` (`music.ref` always a concrete path when music was chosen; keep `source` for audit). `resolveMusicRef` must then read only that frozen ref; the late `source:"default"` indirection is removed for new tasks.
- **Never swallow errors here.** `resolveMusicRef` must throw a typed error on read failure/parse failure/missing ref-when-music-expected, retry with backoff (3 attempts), and if it still cannot resolve, ABORT the dispatch with a clear error card ("Couldn't confirm your background music. Retry / Continue without music") instead of dispatching silently. Only an explicit user choice of "no music" may produce an empty ref.
- After dispatch, when the run completes, read the stage-b result/status (`music_applied`) and show a **"Background music: applied ✓ / NOT applied ⚠"** badge on the task, with the track name. If the operator chose music and `music_applied` is false, mark the task with a warning and offer one-tap "Re-render with music".
- Apply the same fix to `restartStageB`, `startNextSeriesPart`, super-series part spawning and every other caller (~lines 1159, 1590).

**Pipeline (clone / `motionssalt/clipforge`):**
- `stage-b.yml`: if the job's request says music was chosen (`music.source != none`) but `music_ref` arrives empty, **fail loudly** (`exit 1`) instead of shipping silent, with a message that names the cause. Also re-resolve from `jobs/<id>/stage-a-request.json` inside the workflow as a second source of truth, so a dropped dispatch input cannot silence the video.
- `render.py` / `run.py`: after mixing, run an **audibility check** (measure integrated loudness of the output's non-voice component or compare the mixed track against voice-only; e.g. `ffmpeg volumedetect`/`ebur128` on the music stem before mix) and record `music_applied`, `music_gain_db`, `music_lufs`, `music_fallback_used` in the job status. If music was requested and (`music_applied` is false OR the measured music level is below an audibility floor OR the fixed-volume fallback was used because measurement failed) → fail the stage with an explicit message rather than producing a silently-wrong video. Add unit tests for the empty-ref, missing-file, silent-file and inaudible-gain cases.
- Log the resolved music ref, source, LUFS values and final gain at the top of the Stage B run so the (now real) logger shows them.

Acceptance: with music selected, it is impossible for a video to finish "successfully" without audible background music. Any failure path is loud and visible in both the logger and the task card.

---

## PRIORITY 5 — CARRY-OVER CHECKS (do not regress)

- Icon from v21 stays; if the operator's launcher still shows the old icon, verify the adaptive-icon XML, the round variants and the legacy mipmaps all reference the new assets.
- Clone creation resilience from v21 stays. Migrate its UI to the new components.
- Cleanup workflow email-spam fix from v21 stays; confirm the latest cleanup runs are green and no failure emails are possible (fail-soft). Do not re-open unless it regressed.

---

## PROOF OF WORK (mandatory before `build_complete=true`)

Because previous "done" claims were false, this round is closed only with evidence recorded in `BUILD_STATE.json`:

1. **Build files:** show that `app/build.gradle.kts` now has `material3:1.5.0-alpha28`, no BOM, and matches the engine; paste the dependency lines.
2. **Old UI gone:** `ui/Motion.kt` deleted; grep output showing no `CircularProgressIndicator(`/`LinearProgressIndicator(` hand-rolled loaders and no raw `Button(`/`TextButton(`/`IconButton(` outside the shared action-button file; count of engine component usages per screen file (`ForgeBuildTheme`, `EngineLoadingIndicator`, `EngineLinearWavyProgress`, `ExpressiveButton`, etc.) showing EVERY screen file uses them.
3. **Logger:** show the deployed code path that returns real log lines (function names + a unit test that feeds a sample raw GitHub job log and asserts steps get the correct lines), and a test for the 403/404/410 states asserting an explicit error state instead of the status fallback.
4. **Zernio:** grep proof that `SettingsScreen.kt` contains controls for `auto_publish`, `automatic_mode`, `interval_hours`, `preferred_time`, `timezone`, `queue_depth`, `start_mode`, `custom_start`, and that `TasksScreens.kt` contains the publish card and per-post actions; a unit test for validation parity with `zernio.py`.
5. **Audio:** unit tests for `resolveMusicRef` error handling, and pipeline tests for the fail-loud and audibility cases, all passing.
6. `release.yml` green, `clipforge-android-v22` release exists with the APK asset, verified via the Releases API. Bump `versionCode`/`versionName` to 22.

Mark each task done only after its proof is recorded. Set `build_complete=true` last.

**Hard rules:** `BUILD_STATE.json` is the source of truth; persistent session; per-step commit + push; never redo completed work that is genuinely verified; never commit tokens/PATs; never delete the repository; never touch prior releases.


---

## 2026-09-19 — EXTEND/UPDATE: clipforge-android (v23, complete remake)

Operator instruction 2026-09-19 (third round). Follow the standard extend contract: append this instruction verbatim to `PROMPT_HISTORY/clipforge-android.md`, seed tasks, checkpoint `BUILD_STATE.json` after every task, cut `clipforge-android-v23`, never touch prior releases, never delete the repository, never commit tokens/PATs.

**AUTONOMY:** Run fully autonomously. Ask no clarifying questions. Where something is ambiguous, pick the most sensible option, record the decision in BUILD_STATE notes, and continue.

**NO DEVICE TESTING:** You cannot install or run the APK. Do NOT screenshot, do NOT attempt emulator or on-device testing, and do NOT put screenshot or run-the-app steps in any prompt, task or workflow. Verification = successful compile, unit tests, `release.yml` run success, and the Releases API asset check only.

---

## THE TASK

**Remake the app from scratch.** The next version is a complete remake of ClipForge Android with a fresh new UI. Do not reuse the previous app in any way: no old screens, no old components, no old styling carried forward. Everything is rebuilt new.

**Migrate everything the app does.** Every setting and every feature from the current app must exist in the remake. All of the operator's saved settings must carry over so nothing has to be set up again. Nothing may be lost in the move.

## THE PROBLEM

The last rounds did not deliver what was asked. The operator saw only a little UI change. These are still not fixed:

- **The entire UI.** It does not look like a fresh, professional Google app built on Material 3 Expressive. The new button animations, loading animations and squiggly loaders from the operator's tool are not showing up. Buttons still give no visible feedback when tapped, so the operator taps two or three times and then gets duplicate-request errors. The entire UI must be changed, on every screen and every part of the app, including torrent/source selection, settings, dialogs, the logger, and everything else.
- **The logger.** It still shows only status and result lines ("status in progress", "status completed", "result success/skipped/failed") instead of the actual log details of each step. The clone has GitHub access, so the real logs must be shown. A running task must show a loading state with its steps appearing live, not empty space. The logger's UI must also be new.
- **The Zernio settings.** Still missing. The publishing settings that used to exist in the Telegram bot and the main settings must be in the app: smart schedule mode, interval, publish immediately vs scheduled, and the rest of the full settings. Completed tasks must have publishing settings too, so a finished video can be published or scheduled again.

## REQUIREMENTS

- **Floating bottom tabs.** The bottom navigation must be floating tabs, not a standard docked bar.
- **Horizontal swiping.** The operator must be able to swipe horizontally to move between tabs, and the floating tab bar must stay in sync with the swipe.
- **Fresh new UI everywhere**, built on the operator's current tool UI (Material 3 Expressive), with the new animations and loaders used throughout.
- **Every button gives immediate visible feedback** when tapped and cannot be double-fired.
- **Everything else the app currently does keeps working**: clone creation, background music selection, series and Super Series, cleanup, and all other features and settings.

Investigate the current app and repository yourself, decide how to do this, and do it.

---

**Rules:** `BUILD_STATE.json` is the source of truth; persistent session; per-step commit + push; never commit tokens/PATs; never delete the repository; never touch prior releases.
