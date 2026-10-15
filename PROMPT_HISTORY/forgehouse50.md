# ForgeHouse 50 (Android) — Prompt History

Slug: `forgehouse50` (chosen by builder AI 2026-09-14; no collision with existing apps).

## 2026-09-14 — Operator: ForgeBuild App Build Contract — NEW APP

Full build contract received: native Android client for the already-live ForgeHouse 50 web app
(Cloudflare Pages + Workers + D1 at forgehouse50.pages.dev; source at github.com/vjumbo264/forgehouse50).
Requirements: feature parity (Home/Today, Read with audio + persistent offline scripture/audio storage,
Notes with 5 types, one-attempt no-gate no-timer quiz with FLAG_SECURE + confetti on day completion,
Progress, confidence-weighted Leaderboard + final results with distinct top-3 treatment, Profile with
illustration avatar picker + downloaded-content manager, Admin dashboard), Brevo OTP auth with secure
on-device token storage, cache-first data via Engine CacheFirstStore, daily reading reminder via
WorkManager with completion check, skippable-but-recurring in-app update prompts with newer-version
supersession, Material 3 + Material Symbols only, system light/dark theme, proper adaptive icon from the
web repo's committed logo artwork. Tasks 1-7 as seeded in apps/forgehouse50/BUILD_STATE.json.

## Session 3 (2026-09-14)
Re-sent the full ForgeBuild App Build Contract for the ForgeHouse 50 Android client (unchanged operator instructions; APP_DESCRIPTION, TASKS 1-7, engine helpers, per-step commit/push protocol, signing & releases). Resumed from repository state: tasks 1-4 done, task 5 in progress with 5.1/5.2 implemented, CI release attempts failing.

## Session 4 (2026-09-15) — EXTEND/UPDATE: Combined Site + Android App Fixes

Operator issued the ForgeBuild App Build Contract EXTEND/UPDATE for forgehouse50 (current latest release forgehouse50-v1; next release forgehouse50-v2). Full instruction text below; credential values (GITHUB_PAT, CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID, TELEGRAM_BOT_TOKEN) were provided in the operator prompt but are REDACTED here per the contract's never-commit-credentials rule:

"""
# ForgeHouse 50 — Combined Site + Android App Fixes

## Scope

This session covers issues found in **both** the live website (`vjumbo264/forgehouse50`) and the Android app (built via ForgeBuild). Some fixes are backend/site-side (surname field, timezone) and apply to both surfaces since the app consumes the same backend; some are Android-only (bundled avatars, icon, persistent notification, screenshot blocking). Read both repositories fully before starting and fix each issue in whichever repo(s) it actually lives in. Persistent-session discipline: BUILD_STATE.json as source of truth, never stop to ask for confirmation, commit and push after every step, verify every fix live/on-device before marking done.

CREDENTIALS: [REDACTED — provided in operator prompt; never committed]

ISSUE 1 — SURNAME FIELD REQUIRED AT REGISTRATION (SITE + APP): add required surname/last_name. Backend/schema: add `surname` (or `last_name`) column to `profiles` in D1, required for new registrations; existing users (if any remain post-launch-wipe — check first) must not break (backfill placeholder or require set-on-next-login, judgment, documented). API: registration endpoint requires/stores the field, clear validation error if missing. Website: required surname input beside given name. Android app: same required input calling the updated API. Everywhere a user's name is displayed (Profile, Leaderboard, admin participant list, celebration/final-results screen) shows "Given name Surname" or "Given name S." (full surname on Profile/admin; whichever cleaner for tight leaderboard rows).

ISSUE 2 — AVATARS MUST BE BUNDLED IN THE APP, NOT FETCHED: bundle the full illustration avatar asset set (same set committed to the web repo from the earlier avatar-import work) into the Android app's resources/assets at build time — one-time asset copy by the build agent, not runtime fetch; fix the registration-flow bug where the avatar picker isn't displaying at all (diagnose actual cause first); app still sends only `avatar_id` to the backend exactly as the web app does.

ISSUE 3 — "REQUEST FAILED (200)" WHEN LOADING SCRIPTURE/AUDIO IN APP: HTTP 200 means the request succeeded — app-side response-handling bug mislabeling a successful response as failure. Reproduce: call the actual API endpoint the Read screen uses, inspect the real 200 body; find the exact parsing/validation logic throwing (JSON shape mismatch, null-handling, or response-wrapper check); fix the parsing bug AND fix error handling so genuine failures report the real HTTP status code (review this bug pattern across endpoints); verify live on-device: Day 1 scripture and audio load with no error.

ISSUE 4 — REGENERATE THE APP ICON: current icon displays incorrectly on operator's device (likely adaptive-icon layering / corner-masking issue like the web PWA icon fix). Use the same source logo (forgehouse50-logo-source.png, committed in web repo during PWA-conversion session); regenerate Android adaptive icon with correct foreground/background separation, safe-zone padding per adaptive icon spec, no corner artifacts (transparent/properly-filled corners, pixel-level verified); all required densities/formats (adaptive icon XML + foreground/background layers + legacy fallback); verify by installing on real/emulated device and visually confirming the home-screen icon (screenshot, not just resource files existing).

ISSUE 5 — KEEP THE EXISTING THEME AS-IS (NO CHANGE): operator considered then rejected Android system Material dynamic-color theming. Do not change the app's current color theme/design system. Nothing to do.

ISSUE 6 — PERSISTENT FOREGROUND NOTIFICATION TO KEEP THE APP ALIVE FOR REMINDERS: proper Android foreground service with low-priority, silent, minimal persistent notification (app name/icon, no sound/vibration, low importance channel) — Play-Store-compliant pattern; its job: keep process alive so the scheduled daily-reminder logic runs reliably; tapping notification opens app and/or one-time first-run explanation ("This keeps your daily reading reminder working reliably"); respect battery-optimization exemption flow — prompt once during onboarding with clear explanation if required.

ISSUE 7 — VERIFY WEST AFRICA TIME (WAT) USED CONSISTENTLY (SITE + APP): audit per-user reading-day calendar generation (Tue/Fri exclusion, day boundaries), daily quiz day boundary, read-ahead one-day limit boundary, admin Start Programme timestamp + 10-day join window, programme-end calculation, Android daily reminder scheduling — all must agree on WAT (UTC+1) as authoritative for day boundaries, not UTC and not device-local (a traveling user's reading day flips at WAT midnight). Fix any UTC/device-local/unspecified default; verify a specific known day-boundary case on both site and app.

ISSUE 8 — QUIZ UI POLISH (APP): redesign quiz screen consistent with existing unchanged design system: clear question hierarchy, well-spaced MC options with proper selected-state treatment, clear appropriately-weighted submit action, existing type scale/spacing/color tokens, not default unstyled system components.

ISSUE 9 — SCREENSHOT BLOCKING ON THE QUIZ SCREEN IS NOT ACTUALLY WORKING: original spec had FLAG_SECURE on quiz screen but operator successfully screenshotted it. Inspect exactly where FLAG_SECURE is/isn't applied (wrong Window/Activity/Fragment; set too late; false guard); fix so it's genuinely active the entire time the quiz screen is visible and genuinely removed on navigate-away; verify by actually attempting a screenshot on-device/emulator while the quiz is displayed.

ISSUE 10 — MISSING CONFETTI + NO WAY BACK HOME AFTER DAY COMPLETION (APP): confetti animation on completing both reading and the one quiz attempt — verify whether implemented; fix trigger condition/race, or build per original spec (brief, tasteful, celebratory, settles back to calm UI). Add clear prominent "Return Home" (or equivalent) action on the completion screen.

TASK LIST: append `combined_fixes_v1` section (web repo BUILD_STATE.json for site-side tasks, app repo build-state for app-side tasks) with at minimum one task per issue (split where independent commits). Per task: inspect → implement → verify live/on-device with real evidence → commit → push → update build-state → next task, without stopping. Mark complete only when every issue genuinely verified.

ADDENDUM: "Also make the leaderboards immediate not locked to day 3 and multiply the points by 7 so that it will be more encouraging meaning every point allocation is now 7 times more."
"""

## Session 5 (2026-09-15) — EXTEND/UPDATE: Leaderboard Points Fix, Broken Scripture Loading, New Icon

Operator issued the ForgeBuild App Build Contract EXTEND/UPDATE for forgehouse50 (current latest release forgehouse50-v2; next release forgehouse50-v3). Full instruction text below; credential values (GITHUB_PAT, CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID, TELEGRAM_BOT_TOKEN) were provided in the operator prompt but are REDACTED here per the contract's never-commit-credentials rule:

"""
# ForgeHouse 50 — Leaderboard Points Fix, Broken Scripture Loading, New Icon

## Scope

Three fixes: one leaderboard regression (site + app both consume the same backend, so this fixes both), one broken Bible-reading screen (diagnose whether it's site, app, or both — the operator's report doesn't specify), and an icon swap (app-side; also update the web PWA icon from the same new source if the web app's icon hasn't already been separately updated to match, since both should stay visually in sync).

## Attached file

The operator has attached a new ForgeHouse 50 app icon (`forgehouse50-icon-v3.png` — glossier/more dimensional version of the existing blue rounded-square flame/house/globe/"50" mark, corners already clean). Use this as the new master source image, replacing `forgehouse50-logo-source.png` wherever it's referenced (web PWA icon generation and Android adaptive icon generation both need to be regenerated from this new source). Commit it to the same source-asset location(s) already established in each repo for the icon source.

Use the same persistent-session discipline as every prior contract on these projects: relevant `BUILD_STATE.json`/build-state file as source of truth, never stop to ask for confirmation, commit and push after every step, verify every fix live/on-device with real evidence before marking it done.

CREDENTIALS: [REDACTED — provided in operator prompt; never committed]

ISSUE 1 — LEADERBOARD MUST SHOW RAW TOTAL POINTS, NOT A PER-DAY AVERAGE: regression against explicit, already-given instructions. The leaderboard's primary "Overall Score" must be **raw cumulative total points** — every point earned from reading, quiz score, streaks, observations, questions, everything, simply added up. Never show or rank by any per-day average, including the confidence-weighted `adjusted_score` damping formula. Clarify actual scope of the damping formula: `adjusted_score = (raw_average × elapsed_days) / (elapsed_days + K)` was introduced specifically to prevent a short hot streak appearing equal to a long sustained streak **when both are still mid-programme and compared on an averaged basis** — a secondary signal at most, never the primary Overall Score. Fix: identify exactly where `adjusted_score` (or any per-day-average calc) is displayed/used as the main "Overall Score" leaderboard ranking/value on both website and Android app (likely one backend fix; verify both surfaces reflect it — app caching may mask the backend fix). Change the primary "Overall Score" value and ranking back to raw cumulative total points (sum of every point source: reading completion, quiz score contribution, streaks, observations, questions, community sharing, weekly targets, full-programme completion) — no division by elapsed days, no damping, for the in-progress leaderboard. If a separate clearly-labeled "damped/fairness-adjusted" view has value as a secondary/optional view, it may be kept but must never be the default or primary Overall Score, and must be clearly labeled as something else entirely; if it adds more confusion than value, remove it. The final one-time end-of-programme ranking snapshot logic (from earlier launch/finale work) already correctly specified raw total points — confirm untouched and still correct. Given app caching, ensure the Android app's cached leaderboard data is invalidated/refetched after this backend fix; verify on a real device after the fix, not just a direct API call. Verify live: check a real user's leaderboard entry and confirm the displayed value matches a manual sum of their actual point-earning history, not a divided/averaged figure.

ISSUE 2 — BIBLE / SCRIPTURE SCREEN STUCK ON LOADING: reading screen stuck showing a loading state, never displays content. Diagnose which surface(s) are affected first — check both website and Android app directly (operator's report doesn't specify which). Reproduce: open Read/Bible screen for a real reading day, observe actual network request(s) and their actual responses (or absence of a response/hung request). May relate coincidentally to Issue 3's icon work, or be a separate regression from recent work — check whether it correlates with the same "Request failed (200)" misdiagnosis pattern fixed in the prior session (a loading screen that never resolves suggests a request that never completes, throws silently and is swallowed without updating any loading-state flag, or is stuck on a promise/callback that never resolves). Find the actual root cause with real evidence (network inspection, logs, or a reproduced hang) rather than guessing. Common causes: unhandled promise rejection that never flips the "loading" flag false in either success or error branch; race where loading flag is set after the fetch already resolved; genuinely failed/hanging request to VerseWell or the backend's scripture-proxying endpoint. Fix the actual cause; ensure the loading state has a proper timeout/error fallback going forward (a future genuine failure shows a clear error message instead of an infinite spinner) — a reasonable defensive addition regardless of root cause. Verify live: load scripture text for at least two different reading days on whichever surface(s) were affected, confirming content actually displays, not just that the spinner disappears.

ISSUE 3 — REPLACE APP ICON WITH NEW ATTACHED VERSION: regenerate the Android adaptive icon (foreground/background layers, safe zone padding, legacy fallback icon) from the newly attached source image, replacing every previously generated icon asset in place, using the same corner-artifact-free generation discipline already established (verify pixel-level, no white/opaque corner artifacts on any density). Also regenerate the website's PWA icon set (192/512 standard + maskable, favicon, apple-touch-icon) from this same new source image, so the web app and Android app use visually matching, currently-correct icons. Verify by actually installing the Android app on a real or emulated device and visually confirming the home-screen icon renders correctly, and by checking the live web app's manifest icons render correctly — real rendered verification, not just confirming the files exist.

TASK LIST: append a `leaderboard_scripture_icon_fix_v1` section (web repo's BUILD_STATE.json and, where app-specific work is involved, the Android app's own build-state file) with one task per issue at minimum, splitting further if an issue spans independent commits (e.g. Issue 1's backend fix vs. Android cache-invalidation vs. verification). For each task: inspect → implement → verify live/on-device with real evidence → commit → push → update the relevant build-state file → move to the next task without stopping to ask for confirmation. Mark the section complete only once all three issues are independently, genuinely verified as fixed — not source-inspected.
"""

---

## 2026-09-15 — Follow-up: scripture/audio STILL unavailable, leaderboard STILL /day (rewire request)

Operator report after forgehouse50-v5 (verbatim, credentials redacted):

> The fix has been applied but the scripture and audio is not still available,
> also the leaderboards is still showing /day.
>
> The fix has been applied but I still don't see scriptures or audio, please
> just rewire that entire function in the app so it will work properly.

Interpretation / scope:
- v5's gating fix stopped the infinite spinner but did NOT restore content:
  the passage/audio LaunchedEffect in ReadScreen.kt was keyed on
  (day, selected, translation) and early-returned while dayLoaded=false. For a
  returning user (translation id already stored, never changes) the effect
  fires once, returns, and never re-fires -> passage + audio never load.
- Operator explicitly asks to REWIRE the whole scripture/audio load function
  in the app so it works properly.
- Leaderboard: app must show RAW TOTAL points, never any /day average. Prefer
  the explicit total_points field and invalidate the stale local cache.

(Credential values from the operator message are intentionally NOT recorded here.)


---

## 2026-09-16 — Leaderboard categories, scripture loading, audio removal, admin points fix, offline Bible downloads (target: forgehouse50-v7)

Operator EXTEND/UPDATE contract (verbatim substance; credential values redacted — never committed):

# ForgeHouse 50 — Leaderboard Categories, Scripture Loading, Audio Removal, Admin Fix, Offline Bible Downloads

## Scope

Seven issues across the website and Android app. The operator has attached a screenshot (`registration-host-error.jpg`) showing the host-resolution error referenced in Issue 6 below — review it directly as part of that issue's investigation. Read both repositories' current state fully before starting — do not assume prior sessions' "verified live" claims in either `BUILD_STATE.json` or the Android app's build-state file are accurate, given this project's history of fixes being reported complete when they visibly weren't. Verify everything yourself, from scratch, with real evidence, before building anything new on top of an assumed-working foundation.

Same persistent-session discipline: build-state file as source of truth, never stop to ask for confirmation, commit and push after every step, verify every fix live/on-device before marking it done — not via source inspection alone.

CREDENTIALS: [REDACTED — provided in operator prompt; never committed]

ISSUE 1 — LEADERBOARD CATEGORIES ALL SHOW THE SAME POINTS VALUE: Only "Reading Time" shows its own correct value. Every other category (Overall Score, Consistency, Chapters Completed, Observations, Questions) incorrectly displays the same total-points figure. Inspect the leaderboard query/computation per category (likely one shared bug: a query or response-mapping function always returning/displaying the same `points` field regardless of category — check both backend query construction and frontend rendering). Fix each category to genuinely reflect its own metric: Overall Score = raw cumulative total points (per prior-session correction — confirm still correct); Consistency = current/longest streak, not points; Chapters Completed = count of chapters actually completed; Reading Time = already correct, leave as reference; Observations = count of Observation-type notes; Questions = count of Question-type notes. Verify live: for a real test user with known activity, manually calculate each category's expected value and confirm leaderboard UI AND direct API response match for every category.

ISSUE 2 — SCRIPTURE LOADING STILL BROKEN (PRIOR FIX DID NOT WORK): Prior session's fix for the stuck-loading Bible/scripture screen did not resolve it. Do not repeat the prior session's diagnostic conclusion — re-verify from first principles: reproduce the failure live, capture the actual real network request and response (or genuine absence of one) for the scripture-loading call, on both the website and the Android app independently. Given Issue 5 changes how the Android app sources scripture (bundled/downloaded local content), consider fixing this on the current live-fetch architecture for the website (which is not changing architecture) and treat the Android version as potentially moot only AFTER Issue 5's replacement path is confirmed working — not as an excuse to skip it. Fix the actual root cause with real evidence. Verify live: scripture text loads for at least three different reading days, actual content renders, on the website at minimum and on Android if Issue 5's rework isn't yet serving its content.

ISSUE 3 — REMOVE AUDIO BIBLE FEATURE ENTIRELY (SITE + APP): The operator removed audio narration from the upstream VerseWell API entirely (permanent). Website: remove the entire audio player component from the Reading page (seek bar, skip-5s buttons, play/pause, single-chapter download, download-all-for-today, chapter-audio-availability checks), any code calling VerseWell's removed audio endpoints, and audio-related UI copy. Android: remove the same functionality — the media playback/foreground service built for Bible-audio playback, audio-download UI, audio-only permissions (review the foreground-service situation: if the audio service and the daily-reminder-reliability foreground service are separate, keep the reminder one and remove only the audio one; if implemented as the same single service, refactor so reminder reliability survives independently). Remove any `audio_url`/audio-availability fields from API responses/request handling — clean up rather than leaving dead fields. Verify live: no audio player, controls, or audio errors/loading states anywhere on either surface.

ISSUE 4 — ADMIN POINT-EDITING BROKEN, NO SUCCESS FEEDBACK: Admin "award/remove points" no longer actually updates a user's points and provides no loading/success/failure indicator. Reproduce directly: attempt to edit a real test user's points via the admin interface, inspect the actual API call and response, check whether the underlying D1 update is genuinely attempted/succeeding/silently failing. This "used to work, then stopped" pattern suggests regression from a later session's schema/API change (surname field addition, per-user calendar migration, or quiz redesign could have altered a shared user-update code path or API contract) — check for exactly this kind of drift. Fix the actual broken update path. Add proper UI feedback: loading indicator in flight, clear success or error message on resolution. Verify live: award points to a real test user via admin UI, confirm loading-then-success feedback, and independently confirm via direct D1/API check (not just the UI's own message) that the points genuinely changed.

ISSUE 5 — OFFLINE BIBLE TRANSLATIONS: KJV BUNDLED, OTHERS DOWNLOADABLE (ANDROID APP ONLY): Genuine architecture change; does NOT apply to the website (website keeps live-fetch-from-VerseWell unchanged). Goal: Android app works fully offline for reading scripture, zero read-time API dependency for available translations. (a) KJV New Testament ships inside the app: at build time fetch the complete KJV NT text (all books/chapters matching the existing reading-assignment structure — 260 chapters across the 50-day assignment map) from the current API and bundle into app resources/assets in a well-structured local format (small local SQLite DB or structured JSON in assets — optimize for fast lookup by book/chapter); must render correctly with existing footnote/section-intro handling using bundled data; KJV is default/pre-selected on first use, always available, no download prompt. (b) Other translations downloadable per-translation on demand: on the Read screen or a dedicated "Manage Translations" area (easy to find from Read screen), list every API-available translation except KJV, each with visible file size and a Download action; tapping download fetches that translation's complete NT once, stores it in genuine on-device persistent storage (not evictable cache), marks it available for offline reading with no further network dependency; users can download many translations, see which are downloaded, and switch between downloaded ones freely from the existing translation selector entirely offline. (c) Storage management: show total storage used by downloaded translations and allow removing one (freeing storage) — extend the existing "Downloaded content" management area to cover translation downloads only (audio-download management is moot per Issue 3). NO audio download options anywhere in this flow. What NOT to change: website's scripture-fetching architecture (stays live-fetch); the backend/API itself is not removed or structurally changed — the Android app simply stops depending on it at read-time for downloaded translations; the API still must work for the KJV bundle-time fetch, per-translation downloads, and everything else (auth, progress, quiz, leaderboard, notes, admin).

ISSUE 6 — "UNABLE TO RESOLVE HOST" ERROR ON REGISTRATION + COMPRESS BUNDLED AVATARS: Screenshot shows `Unable to resolve host "forgehouse50.pages.dev": No address associated with hostname` at registration where the avatar picker displays (the avatar set in that screenshot is an earlier placeholder set, NOT the illustration-style set already bundled — treat the screenshot as evidence of the network error and the "avatars not displaying instantly" complaint, not as the target avatar set). Diagnose correctly: `Unable to resolve host` is DNS-level — could be no real device connectivity at that moment (environment issue, not app bug), a mistyped hostname in app config (search the Android codebase for every place `forgehouse50.pages.dev` / API base URL is defined and confirm spelling matches the live Cloudflare Pages domain exactly), or a transient Cloudflare availability issue. Determine which through real investigation rather than assuming a code fix is needed — if the hostname is correctly configured and the site is reachable, this may only need better error handling: on network/DNS failure during registration show a clear friendly actionable message (e.g. "Couldn't connect — check your internet connection and try again") instead of a raw low-level exception. ALSO: compress the bundled avatar illustration assets (the real currently-used illustration set) to reduce APK size — PNG recompression or WebP if the asset pipeline supports it cleanly — keeping quality crisp at profile-picture size in picker and on Profile/Leaderboard. Verify the app's total APK size measurably decreases and avatars still render correctly and quickly.

TASK LIST: append a `leaderboard_audio_removal_offline_bible_v1` section (web repo's BUILD_STATE.json and the Android app's build-state file, whichever relevant per task) with at least one task per issue (seven total), splitting further where an issue spans independent commits (Issue 5 should be several: KJV bundling, download-UI, per-translation download-and-store, storage management UI, removal of moot audio-download management). For each task: inspect → implement → verify live/on-device with real evidence → commit → push → update build-state → next task without stopping. Mark complete only once all seven issues are independently, genuinely verified as fixed — including a real offline test for Issue 5 (airplane mode on real/emulated device after downloading a translation; confirm it still reads with zero network).


---

## 2026-09-16 — Testing-phase control, progress/completion bug, scripture bundle format, reader UI restoration (target: forgehouse50-v8)

Operator EXTEND/UPDATE contract (verbatim substance; credential values redacted — never committed):

# ForgeHouse 50 — Testing-Phase Control, Progress/Completion Bug, Scripture Bundle Format, Reader UI Restoration

## Scope

Five issues in the Android app, at least two of which are very likely the same underlying bug wearing different symptoms (see Issue 2). Read the app's current build-state file and the web repo's BUILD_STATE.json fully before starting — do not assume prior sessions' "verified live" claims are accurate. Verify everything yourself, with real evidence, before treating anything as a stable foundation to build on.

Same persistent-session discipline as every prior contract: build-state file as source of truth, never stop to ask for confirmation, commit and push after every step, verify every fix live/on-device before marking it done.

CREDENTIALS: [REDACTED — provided in operator prompt; never committed]

ISSUE 1 — ADMIN "END TESTING PHASE" CONTROL WITH FULL PROGRESS RESET: App has no concept of a testing phase distinct from the real public programme run — anyone who registers now can read and progress immediately, but the operator has not officially started the programme (separate from and preceding the existing admin "Start Programme" control that governs the 10-day join window and programme-end calculation). App must currently behave as an open testing sandbox, with an explicit admin action to close testing and reset everything to a clean state before "Start Programme" is used. Add a new admin control, clearly distinct from and separate from the existing "Start Programme" button — label like "End Testing Phase & Reset". Until pressed, app behaves exactly as now: open registration, full functionality, for testing. When pressed: delete every non-admin user and all associated data across every relevant table (reading_progress, notes, bookmarks, highlights, user_badges, points, quiz_attempts, sessions, otp_codes, leaderboard_snapshots, downloaded-translation records if tracked server-side, and any other per-user table) — reuse the existing cascading-deletion routine already built for the earlier migration_brevo_v1 test-user cleanup and launch-wipe work rather than writing from scratch. If that exact functionality still exists as a reusable admin action, this issue may simply be "surface/rename that existing capability clearly as an explicit 'End Testing Phase' admin button" — investigate what already exists before writing new deletion code. Do NOT automatically trigger "Start Programme" — ending testing and starting the programme remain two separate deliberate admin actions. Verify live: as admin, press "End Testing Phase & Reset", confirm every non-admin test account and its data is gone, and confirm the admin account and all shared/global data (reading assignments, quiz question bank, programme configuration) remain untouched.

ISSUE 2 — MARK COMPLETE, QUIZ START, AND PROGRESS CHECKMARKS ALL BROKEN (LIKELY ONE ROOT CAUSE): Three symptoms: completed reading days show no checkmark on the Progress screen, user cannot mark a day complete, user cannot even start the quiz. Investigate as ONE connected failure starting from whichever action happens earliest in the user flow (almost certainly mark-complete/quiz-start failing at API or state-management level; missing Progress checkmarks are the downstream consequence of no completion ever being recorded — not two separate bugs). Reproduce the actual failure directly: attempt to complete a real reading day as a test user, inspect the actual API call and its actual response for mark-complete/start-quiz. Given this project's history of schema/API drift between sessions (surname field addition, per-user calendar migration, quiz redesign removing pass/fail gate, offline-Bible-download rework), check specifically whether the Android app's mark-complete/quiz-start request still matches the backend's actual current expected request shape — strong candidate root cause. Once the actual root cause is found (with real evidence, not guesses), fix it and confirm the fix restores all three symptoms simultaneously: quiz startable, day markable-complete, Progress checkmark appears once genuinely completed. Verify live: complete a real reading day end-to-end (read → start quiz → submit quiz → day marked complete) and confirm Progress shows a checkmark for that day.

ISSUE 3 — SCRIPTURE DOWNLOAD FAILING; REPLACE WITH A SINGLE BUNDLE FILE PER TRANSLATION: Downloading a translation (offline-Bible-download feature) is failing with the same kind of API/200-style issue seen before. Operator does NOT want many small JSON files fetched individually. Instead: pre-build a single complete bundle file per translation containing the entire NT (all books, chapters, verses, footnotes, section intros) in one file, generated once (server-side or as a build/prep step run against the current API) rather than assembled on-device. Reasonable format: single gzipped JSON file or single SQLite DB per translation — must be exactly ONE file per translation downloaded in one request and read locally. Host the pre-built bundles somewhere reliably fetchable (committed static assets served by existing Cloudflare Pages/Workers infrastructure, or R2 if already in stack — simple single-URL-per-translation download, NOT a live API endpoint assembling dynamically per request). Update Android download flow to fetch this one bundle per translation (showing real download progress/file size per earlier spec). Diagnose why the current mechanism fails with a 200-style error first — apply the same "a 200 status means success, so something is misinterpreting a successful response" diagnostic discipline from the earlier scripture-loading fix, in case a similar response-handling bug in the download path would otherwise survive the rework unnoticed. Verify live: download at least one non-KJV translation as a real bundle file on a real/emulated device, confirm it completes with no error, and confirm the downloaded translation reads correctly offline (airplane mode test).

ISSUE 4 — RESTORE PER-CHAPTER READING VIEW (NOT ALL CHAPTERS AT ONCE): App reading screen currently shows an entire reading day's assignment as one long block (operator sees multiple chapters at once), which is overwhelming. Must match the pagination behavior already correctly built and verified on the website: ONE chapter at a time, with "Next chapter"/"Previous chapter" controls to move through a multi-chapter reading day — not all of that day's chapters in one continuous view. Inspect the website's existing per-chapter reading-pane implementation (from the earlier reading-page-compaction/layout-fix work) as the reference behavior to replicate natively. Fix the app's Read screen: render only the current chapter, with Previous/Next chapter controls, using the chapter-count/chapter-range data the reading assignment already provides. Applies whether reading from bundled KJV data or a downloaded translation bundle (Issue 3) — pagination is independent of data source. Verify live: open a multi-chapter reading day, confirm only one chapter shows at a time, Next/Previous works and stays within the day's assigned range.

ISSUE 5 — RESTORE FONT-SIZE CONTROLS (MISSING FROM APP): Website Reading page has font-size increase/decrease controls (A-/A+); Android app is missing this entirely. Add equivalent font-size controls to the app Read screen matching website behavior, persisted per-user (on-device at minimum — must persist across app restarts on the same device). Apply to whatever text-rendering component displays chapter content after Issue 4's per-chapter rework. Verify live: increase/decrease font size, text visibly changes, setting persists after closing/reopening the app.

TASK LIST: append a testing_phase_and_reader_fixes_v1 section (Android build-state file, and web repo BUILD_STATE.json for backend-side work e.g. Issue 3 bundle generation) with at least one task per issue, splitting further where an issue spans independent commits (Issue 2 needs a distinct diagnostic task before its fix task). For each task: inspect → implement → verify live/on-device with real evidence → commit → push → update build-state → next task without stopping. Mark complete only once all five issues are independently, genuinely verified fixed — not source-inspected — including Issue 2 resolving all three symptoms together and Issue 3's bundle genuinely working offline.


---

# 2026-09-16 — Material 3 Expressive Overhaul + Targeted Fixes (operator instruction, verbatim)

(Full operator instruction received this session: Part A — full Material 3 Expressive overhaul app-wide: dynamic system-theme color (Material You dynamicColorScheme + static fallback), squiggly/wavy button interaction animation on every button on every screen, floating pill bottom navigation, M3 Expressive loading indicators everywhere a loading state exists, rounded Google font (Google Sans Flex — confirm exact name/availability) app-wide on a sensible M3 type scale, full commitment not partial; auth screens explicitly included (currently stuck on old dark theme). Part B targeted fixes: B1 leaderboard avatars from bundled illustration set per avatar_id on all categories + final results; B2 rebuild day-completion confetti as genuine multi-particle burst lasting a comfortable few seconds without obscuring completion message/Return Home; B3 regenerate adaptive icon (currently blank/invisible) from newest forgehouse50-logo-source.png in live website repo with proper foreground/background/safe-zone + legacy fallback, verified under multiple launcher mask shapes; B4 fix malformed-JSON scripture bundle bug (Unexpected JSON token at offset 154: expected string, got object at path Matthew/1/v[0]/f[0]) — fix bundle-generation footnote flattening, regenerate ALL bundles, add post-generation schema validation, verify live with Matthew 1. Scope: Android-app-only; do not port to web app. Persistent-session discipline: build-state as source of truth, commit+push per step, real rendered screenshot verification mandatory for every Part A task.)


---

## 2026-09-18 — Real Material 3 Expressive Implementation + Multiple Regressions (target: forgehouse50-v11)

Operator EXTEND/UPDATE contract (verbatim substance; credential values redacted — never committed):

# ForgeHouse 50 Android App — Real Material 3 Expressive Implementation + Multiple Regressions

## Scope

Hard correction session. The prior session claimed to implement Material 3 Expressive but did not — it produced manual visual approximations without actually using the real Material 3 Expressive library components and motion system. This session must undo that mistake and implement it correctly, plus fix five additional concrete regressions/bugs found through real device testing.

Before writing any UI code, verify use of actual androidx.compose.material3 Expressive components (real library APIs, at a library version that genuinely ships Expressive — check the actual current version/changelog, don't assume) — not custom Compose modifiers, custom animation code, or manual recreations. If a genuine Expressive component doesn't exist yet for something requested (verify by checking the library's real current API surface), say so honestly in BUILD_STATE.json rather than building a fake substitute — a documented gap is acceptable; a disguised fake is not.

Every task in the prior material3_expressive_overhaul_v1 section is treated as NOT actually done and re-verified/redone properly this session. Every task in this session requires an actual screenshot or real on-device recording as evidence, attached/described in the build-state file's notes for that task, before it can be marked done.

CREDENTIALS: [REDACTED — provided in operator prompt; never committed]

PART A — IMPLEMENT REAL MATERIAL 3 EXPRESSIVE (NOT AN APPROXIMATION): (1) Squiggly/wavy button press animation — real Material 3 Expressive button/interaction component that provides this, not custom-drawn; verify the actual library component exists and provides this specific effect before implementing; apply app-wide; prove with real screen recording/sequential screenshots of a button mid-press with squiggle visible. (2) Morphing button shapes — real library-provided shape-morph behavior on press/state-change, not scale/opacity tween. (3) Wavy/organic loading and progress indicators — replace every loading spinner/progress bar app-wide with the real M3 Expressive loading/progress components (shape-morphing/wavy-style), not standard CircularProgressIndicator/LinearProgressIndicator, not custom-drawn waves. (4) Re-verify floating bottom navigation, dynamic system-color theming, and Google Sans Flex (or correctly-identified equivalent) rounded font with real on-device screenshots. (5) Go through EVERY screen individually (auth/sign-in/registration, Home, Read, Notes, Progress, Leaderboard, Profile, admin, quiz, translation-download, completion/confetti), apply real Expressive components/motion consistently, capture a real screenshot of each screen as evidence in BUILD_STATE.json notes.

PART B — CONFETTI STILL BAD (SECOND ATTEMPT): Prior fix did not work — still flies across the screen too fast to read as celebration. Read the prior implementation first. Required: multiple individual confetti pieces, varied colors, varied simple shapes (small rectangles/circles sufficient), each with independent randomized fall trajectory, rotation, slight horizontal drift — genuine particle-style physics. Total duration ~2.5-4 seconds. Check specifically whether prior attempt's duration/speed parameter was set far too low as likely root cause before assuming a full rewrite. Verify with a real screen recording (not a screenshot alone) that it reads as confetti to a human.

PART C — READING TIME NOT TRACKED (SHOWS ZERO FOR EVERYONE): Leaderboard Reading Time category shows zero for all users — regression (was the one category confirmed working). Reproduce: read a chapter for a real measured duration, inspect the actual API call/payload and the actual stored value in D1. Determine client-side (app never measures/sends reading time — check whether tracking logic even exists in current Read-screen implementation; recent per-chapter pagination / offline bundle rework may have dropped it) vs backend bug. Fix the actual cause; if client tracking was lost, reimplement: track actual foreground time on Read screen per chapter/day, report via existing POST /api/read/time contract (web app implementation is the reference if it still works). Verify live: known read duration -> corresponding non-zero leaderboard value.

PART D — MARK-COMPLETE BUTTON HAS NO LOADING/FEEDBACK STATE: Tapping Mark Complete gives no visual feedback. Add proper loading state: disable button + show loading indicator (real M3 Expressive loading component from Part A) while request in flight; clear success feedback on resolve (transition to completion/confetti screen or distinct success indicator); clear actionable error feedback on failure. Verify live: loading state visible during request, clear feedback on completion.

PART E — FONT WEIGHT TOO LIGHT/THIN APP-WIDE: Verify which font is actually in use right now. Increase default body-text font weight app-wide to visibly less thin (e.g. Light/Regular -> Regular/Medium baseline) while keeping hierarchy. If the font is a variable font (Google Sans Flex is), adjusting the weight axis for body text may suffice. Verify live with before/after screenshot at real device default text size.

PART F — TRANSLATION DOWNLOAD: BROKEN PROGRESS BAR + "NOT GZIP FORMAT" ERROR: (1) Progress bar doesn't update during download — inspect whether download uses incremental byte-progress reporting vs one blocking await with no progress callback; wire genuine incremental progress from network layer to UI, not fake/simulated. (2) "Scripture is not in GZIP format" — fetch the currently-hosted bundle directly, inspect real content/headers, compare to app's decompression expectation; fix whichever side is actually wrong (regenerate/re-host bundle in genuine gzip, or update app expectation to match real format). Verify live: download a translation, progress animates proportionally throughout, no format error, downloaded translation reads correctly.

PART G — LOGIN/SIGN-IN SCREEN PERSISTENTLY BROKEN DARK MODE: Login screen "always in a very broken dark mode" every app open — flagged in prior overhaul session, evidently not fixed. Reproduce on real device in light AND dark system modes with screenshots. Identify actual cause (hardcoded dark color in login composable bypassing theme tokens; theme-provider scoping bug rendering login outside themed subtree; or genuine visual/layout bug) via the actual screenshots. Fix so login correctly follows the same dynamic system-theme logic as the rest of the app, no hardcoded colors, visually correct in both modes. Verify live with both-mode screenshots.

TASK LIST: append material3_expressive_correction_v1 section to Android build-state. One task per screen for Part A (auth, Home, Read, Notes, Progress, Leaderboard, Profile, admin, quiz, translation-download, completion/confetti) plus one per Part B-G. Every task's completion evidence must describe/reference a real screenshot/recording taken this session. Do not trust prior overhaul section's claims. Work through every task without stopping for confirmation. Mark section complete only once every screen genuinely re-verified with real Expressive components and all six Part B-G issues independently, concretely fixed and verified.

---

## 2026-09-18 — EXTEND / UPDATE: ForgeHouse 50 Android App — Post-Testing Polish Round

(Credential values redacted per contract; never committed.)

Six issues found through real device testing after the Material 3 Expressive
correction session. Refinement, not a full correction.

- ISSUE 1 — FONT STILL TOO THIN (SECOND REPORT): verify the real current body
  weight value; move at least one full step up (e.g. Medium -> SemiBold); err
  toward heavier rather than risk a third report.
- ISSUE 2 — DOWNLOAD SPINNER LOW-CONTRAST IN DARK MODE: spinner too dark vs its
  background in dark mode; fix it to use the same dynamic theme accent as every
  other loading indicator; verify light + dark.
- ISSUE 3 — NO "THIS IS YOU" ON THE LEADERBOARD: add a clear, tasteful visual
  distinction (accent tint + "You" chip) for the current user's own row across
  every category and the final-results screen, at any rank.
- ISSUE 4 — FOOTNOTES MISSING FROM READING VIEW: implement single tappable
  marker per verse-with-footnotes; tap expands ONE inline panel with all of that
  verse's footnotes; only one panel open at a time per chapter; no marker when a
  verse has none; Material 3 Expressive styling.
- ISSUE 5 — PREVIOUS/NEXT CHAPTER NEED ICONS, NO SQUIGGLE: chevron icons +
  button-like treatment; explicitly NO expressive squiggle/press-morph on these
  two controls; calmer press state.
- ISSUE 6 — APP ICON FULLY WHITE UNDER THEMED ICONS (MISSING MONOCHROME LAYER):
  get the CURRENT icon source from the live website repo (forgehouse50-icon-v3),
  extract the mark silhouette, add a real <monochrome> layer (not empty/opaque)
  to the adaptive icon XML, sized in the safe zone; leave working foreground/
  background layers untouched; verify with themed icons ON and OFF.
---

## 2026-09-27 — EXTEND / UPDATE: ForgeHouse 50 Android App — WhatsApp/Support Links, Catch-Up Reading, Home-Screen Widget, Gated Completion, Icon Reapplication

(Credential values redacted per contract; never committed.)

- ISSUE 1 — Add a distinct "Join ForgeHouse WhatsApp Group" action (opens
  https://chat.whatsapp.com/KNt6BxuzOWwFZ9p4LL2Dfq) AND a separate "Support
  ForgeHouse Global" action (opens a direct WhatsApp chat to the admin via
  https://wa.me/2349139095481). Never merge the two.
- ISSUE 2 — Catch-up reading: at most 2 days' reading per session, oldest
  outstanding days first, until caught up; no hard cap on missed days; the only
  limit is arithmetic (outstanding > remaining calendar reading days) shown as
  an honest Progress-screen status, never a lockout. Interacts with the existing
  1-day read-ahead: catch-up pacing takes precedence while a backlog exists.
  Each catch-up day keeps its own reading content and its own quiz.
- ISSUE 3 — Real Android home-screen App Widget (Jetpack Glance preferred):
  Day x/50, current assignment book+chapters, a key verse (first verse of the
  range as the default), X/260 progress, state-based primary action
  Start/Continue/Completed(+Reflect) deep-linking into the app, periodic
  refresh via WorkManager. Optional: participants-completed-today count only if
  a backend endpoint already exists (otherwise document as skipped).
- ISSUE 4 — Gate the Finish Reading / mark-complete action until every chapter
  of that day has actually been viewed (per-day inside catch-up sessions);
  visibly disabled with a "viewed k of N chapters" indicator, not hidden.
- ISSUE 5 — Reapply the app icon from the operator-supplied source image:
  full adaptive set (background, foreground, monochrome themed layer per the
  established pattern) plus legacy fallback; verify themed icons on and off.

---

## 2026-10-07 — EXTEND / UPDATE: ForgeHouse 50 Android App — Premium Visual Pass, Critical Cache-First Fix, Widget Redesign, Icon Silhouette Swap
(Credential values redacted per contract; never committed.)

# ForgeHouse 50 Android App — Premium Visual Pass, Critical Cache-First Fix, Widget Redesign, Icon Silhouette Swap

## Scope
Four issues, one of which (cache-first/stale-UI) is a correctness bug, not a visual preference, and should be treated with proportionate urgency.

## Attached files
1. `forgehouse50-icon-silhouette-v2.png` — clean source for monochrome/themed-icon silhouette layer.
2. Screenshot of AI-generated mockup of premium Home screen — mood/quality reference (soft shadows/elevation, generous corner radii, bold large numerals, full-width pill action rows with icon chip + label + chevron, comfortable spacing). Dynamic Material You theme preserved.

- ISSUE 1 — CRITICAL: APP (AND WIDGET) SHOW STALE/DEFAULT DATA BEFORE SNAPPING TO REAL DATA. Cache-first with background refresh violation: audit all screens (Home, Read, Notes, Progress, Leaderboard, Profile, admin, quiz) and widget. Persistent local caching rendered immediately on screen composition with zero default/placeholder flash, background refresh reconciles smoothly. Verify offline/airplane-mode reopen.
- ISSUE 2 — PREMIUM VISUAL PASS (HOME SCREEN AND APP-WIDE), REAL FONT FIX. Elevated visual confidence matching reference mockup: soft shadows/elevation, generous corner radii, bold large numerals for stats (points, days, streak), clean full-width pill action rows (icon chip + label + chevron) app-wide. Re-verify body font weight and ensure Regular/Medium (400-500)+ legibility.
- ISSUE 3 — WIDGET VISUAL REDESIGN (CLEAN, MATERIAL-STYLE). Material 3 widget styling, GlanceTheme/dynamic color, clear visual hierarchy, redesigned shape/layout (top day/progress, middle key verse, bottom action), cache-first data guarantee.
- ISSUE 4 — REPLACE THEMED-ICON SILHOUETTE WITH NEW ATTACHED VERSION. Replace monochrome layer with new clean v2 silhouette, safe-zone fitted, verify themed icons.

---

## 2026-10-08 — EXTEND / UPDATE: Plus Jakarta Sans font swap, widget button crop fix, full dynamic theming (dark mode), expressive wavy progress bar

Operator feedback:
1. Font replacement: Change font entirely rather than tweaking Nunito. Adopt Plus Jakarta Sans (premium geometric humanist font, clean weights, crisp legibility).
2. Widget button crop fix: Bottom of 'Start Reading' button is cut off on 2-cell launcher heights. Tighten vertical padding, collapse empty verse space, reduce button height to compact 38dp, and ensure comfortable bottom clearance.
3. Full dynamic theme: Widget was stuck in light mode. Provide complete Material You dynamic theming across values/, values-night/, values-v31/, and values-night-v31/ using system dynamic colors so dark mode renders with deep dark surfaces and system wallpaper accents.
4. Expressive wavy progress bar: Style widget progress bar with Material 3 Expressive wavy progress treatment.


---

## 2026-10-08 — EXTEND / UPDATE: Widget Layout/Theme Bugs, Fake Progress Bar, Icon Zoom/Shape Issues, Leaderboard Redesign

# ForgeHouse 50 Android App — Widget Layout/Theme Bugs, Fake Progress Bar, Icon Zoom/Shape Issues, Leaderboard Redesign

## Scope

Five issues, visible directly in the operator's attached screenshots.
Reference them by number in your own verification evidence.

The operator has attached five images:
1-2. Widget screenshots at two different sizes, showing the "Continue" button cropped at the bottom edge of the widget card, and a wavy progress indicator beneath the chapter title that is visibly static (not actually animating/moving) and doesn't match Material 3 Expressive's real wavy progress indicator's appearance.
3. The app's standard (non-themed) launcher icon as it currently renders — visibly over-zoomed/cropped compared to its intended design (flame/house/globe/"50" shape cut off at edges).
4. The app's themed-icon (Android 13+ monochrome) variant as it currently renders — using a harsh-edged square card shape with a visible hard background edge.
5. A third widget screenshot (same cropping/static-wave issues as 1-2, for confirmation across more than one instance).

═══════════════════════════════════════════════════════════
ISSUE 1 — WIDGET: "CONTINUE" BUTTON CROPPED AT CARD EDGE
═══════════════════════════════════════════════════════════
Fix padding/sizing so button renders fully with comfortable padding on all sides, never touching or clipped by card edge at every supported widget size.

═══════════════════════════════════════════════════════════
ISSUE 2 — WIDGET: FAKE/STATIC WAVY PROGRESS BAR
═══════════════════════════════════════════════════════════
Android App Widgets via RemoteViews do not support custom animated loops for determinate progress. Replace the fake static wave approximation with a clean, supported Material 3 progress bar styled cleanly, and document this platform constraint honestly in BUILD_STATE.json.

═══════════════════════════════════════════════════════════
ISSUE 3 — WIDGET: BROKEN LAYOUT ON RESIZE (ELEMENTS BUNCH TO ONE SIDE)
═══════════════════════════════════════════════════════════
Fix layout reflow/adaptation across Android's supported widget size range (prevent squeezed "ForgeH
ouse
50" header wrapping, distribute elements proportionally across widths and heights).

═══════════════════════════════════════════════════════════
ISSUE 4 — WIDGET: NO DARK THEME SUPPORT
═══════════════════════════════════════════════════════════
Wire widget theme/color scheme to follow system dark/light and dynamic Material You wallpaper colors cleanly.

═══════════════════════════════════════════════════════════
ISSUE 5 — STANDARD ICON OVER-ZOOMED/CROPPED
═══════════════════════════════════════════════════════════
Scale foreground layer properly within Android's adaptive icon safe zone (~66% diameter) centered on brand background, ensuring the complete mark is never clipped under any mask shape.

═══════════════════════════════════════════════════════════
ISSUE 6 — THEMED ICON: UGLY HARD-EDGED SQUARE CARD
═══════════════════════════════════════════════════════════
Rebuild monochrome silhouette layer so it contains ONLY the glyph shape with transparent surrounding area (zero card/background artifact), allowing launcher mask to define outer bounds.

═══════════════════════════════════════════════════════════
ISSUE 7 — LEADERBOARD UI NEEDS A REDESIGN
═══════════════════════════════════════════════════════════
Redesign Leaderboard UI with premium visual language: card-ized elevated rank rows, segmented pill category tabs, bold rank and score typography, elevated top-3 podium hero treatment, prominent "You" standing highlight.

---

## 2026-10-08 — EXTEND / UPDATE: Streak Calculation Bug, Reader UI Enhancement, Widget Cropping, Support Message, Progress-Screen Review Access

# ForgeHouse 50 Android App — Streak Calculation Bug, Reader UI Enhancement, Widget Cropping, Support Message, Progress-Screen Review Access

## Scope

Five issues. One (the streak bug) is a correctness/trust issue affecting
the leaderboard's Consistency category for every user and should be
treated as the highest priority in this session, similar to how the
cache-first bug was prioritized in an earlier session.

Read the Android app's current build-state file fully before starting.

# CREDENTIALS
[REDACTED PER FORGEBUILD CREDENTIAL DISCIPLINE]

═══════════════════════════════════════════════════════════
ISSUE 1 — CRITICAL: STREAK DOES NOT RESET AFTER INACTIVITY
═══════════════════════════════════════════════════════════

The operator has not opened/used the app in over a week, yet their streak
still shows 6 (presumably its value from their last active day), when it
should have reset to 0 once a day was missed without being completed (or,
depending on how catch-up interacts with streak — see below — at minimum
it should not simply remain frozen at its old value indefinitely while the
user is inactive).

**This directly affects the Leaderboard's Consistency category for every
user** — if streaks aren't being correctly recalculated/reset, that entire
leaderboard category is currently showing inaccurate data app-wide, not
just for this one operator account.

- Find the actual streak-calculation logic (backend, since this should be
  server-authoritative data consumed by both the leaderboard and the
  Android app, not something computed client-side-only) and determine
  why it isn't correctly decrementing/resetting when a user goes
  inactive. Likely causes: streak is only ever recalculated/updated at the
  moment a user actively completes a day (incrementing it), with no
  corresponding check that runs independently to detect and reset a
  broken streak when time passes without completion — i.e. the "reset"
  half of the logic may simply not exist at all, only the "increment"
  half.
- Define and implement the correct rule clearly: a user's current streak
  should reflect genuinely consecutive completed reading days. If a
  calendar day passes (per the project's established West Africa Time day
  boundary) where the user did not complete that day's reading
  (accounting correctly for the catch-up mechanic from an earlier
  session — a user actively working through a catch-up backlog should
  likely not have their streak zeroed out simply because they're
  catching up, since catch-up exists specifically to let someone recover
  without being doubly punished; your judgment on the precise
  interaction, but document it clearly, and at minimum: a user who is
  neither caught up nor actively working through catch-up — i.e.
  genuinely inactive, not opening the app at all, like the operator's own
  week-long absence — must have their streak correctly reflect that
  lapse, not remain frozen at an old value).
- Since this can't rely solely on "check streak when the user opens the
  app" (a user who never reopens the app would never trigger a
  recalculation, and their streak would remain stale in the database
  indefinitely, which matches exactly what's being observed here),
  implement this as a proper server-side check — either computed
  on-the-fly whenever streak is read/displayed (comparing last-completed-
  day against the current real date rather than trusting a stored streak
  number blindly), or via a scheduled job that periodically recalculates
  stale streaks, your judgment on which fits this stack better, but the
  critical requirement is that streak must be correct whenever it's
  displayed, regardless of whether the user themselves has opened the app
  recently.
- Verify live: using a real test account with a known streak and a known
  gap in activity (simulate/adjust test data as needed to test without
  waiting in real time), confirm the displayed streak correctly reflects
  the actual consecutive-completion history, confirm it correctly resets
  after a genuine gap, and confirm this is reflected both in the app and
  on the Leaderboard's Consistency category.

═══════════════════════════════════════════════════════════
ISSUE 2 — WIDGET BUTTON STILL INTERMITTENTLY CROPPED
═══════════════════════════════════════════════════════════

The prior session's fix for the widget's cropped "Continue" button
improved things but didn't fully resolve it — it's now cropped only
sometimes rather than every time, which suggests the fix addressed a
fixed-size case but not a dynamic one.

- Investigate what varies between the cropped and non-cropped occurrences
  — the most likely culprit given "sometimes" rather than "always": the
  widget's content height varies based on real data (e.g. longer book/
  chapter range text wrapping to an extra line, catch-up-mode text being
  present or absent, varying verse-snippet text length) and the layout's
  sizing doesn't correctly account for this variable content height when
  reserving space for the button — i.e. the previous fix likely added
  fixed padding/sizing that works for the shorter/common case but not
  when upstream content pushes the button lower than that fixed
  assumption accounts for.
- Fix this properly using genuinely flexible/responsive layout sizing
  (e.g. ensuring the container truly wraps its full content including the
  button with appropriate weight/sizing rather than any fixed height
  assumption) so the button is never cropped regardless of how much space
  the content above it actually takes up.
- Verify live across multiple real data scenarios specifically chosen to
  stress this (a long book/chapter range name, catch-up mode active vs.
  not, different verse-snippet lengths) rather than just one scenario,
  since the bug is data-dependent and a single successful screenshot
  doesn't prove it's fixed.

═══════════════════════════════════════════════════════════
ISSUE 3 — READER PAGE: FULL UI ENHANCEMENT + CLEARLY VISIBLE FOOTNOTE MARKER
═══════════════════════════════════════════════════════════

Two related requests: the footnote marker (implemented in an earlier
session) is currently too subtle to notice — it reads as part of the
regular verse text rather than as an interactive element — and more
broadly, the operator wants the entire Reading/reader screen brought up to
the same premium visual quality already applied to other screens in
recent sessions (Home, Leaderboard).

- **Footnote marker visibility**: redesign the marker itself to be
  unmistakably interactive — e.g. a small filled circular or pill-shaped
  chip (using the app's accent color) containing a numeral or a small
  footnote/asterisk-style icon, visually distinct from the surrounding
  verse text (different color, subtle background, slightly raised/
  superscript position) rather than rendering as plain inline text that
  blends in. It should be immediately obvious at a glance, even to a
  first-time user, that it's tappable.
- **Reader screen premium visual pass**: apply the same visual language
  established in recent sessions (soft elevation where appropriate,
  confident typography, generous spacing, clean use of the app's dynamic
  Material 3 Expressive theme) to the reading screen's chapter text
  container, the translation selector, the Previous/Next chapter controls
  (keep these calmer/non-squiggle per the earlier specific instruction
  that still applies), the font-size controls, and the footnote expanded-
  panel styling — this is the screen users spend the most time on and
  should feel at least as polished as the Home and Leaderboard screens
  now do, not left behind as the plainest-looking part of the app.
- Verify live with real screenshots showing: a verse with a visible,
  clearly-tappable footnote marker, the marker's expanded panel open, and
  the overall chapter-reading view reflecting the enhanced visual
  treatment.

═══════════════════════════════════════════════════════════
ISSUE 4 — SUPPORT LINK SHOULD OPEN WHATSAPP WITH A PRE-FILLED MESSAGE
═══════════════════════════════════════════════════════════

The "Support ForgeHouse Global" action correctly opens a direct WhatsApp
chat with the admin's number, but currently opens an empty chat requiring
the user to compose their own opening message.

- Update the `wa.me` deep link to include a pre-filled message using the
  `text` query parameter (e.g.
  `https://wa.me/2349139095481?text=<url-encoded message>`), so the
  chat opens with a ready-to-send (but still editable/sendable-as-is by
  the user) message already composed — something like "Hi, I'd like to
  support ForgeHouse 50" (your judgment on the exact friendly wording, but
  keep it short and clearly support/donation-intent-signaling), correctly
  URL-encoded so spaces and punctuation don't break the link.
- Verify live: tap the support action on a real device with WhatsApp
  installed and confirm it opens a chat with the admin's number with the
  intended message already populated in the text field, ready to send.

═══════════════════════════════════════════════════════════
ISSUE 5 — CRITICAL: ALL-CHAPTERS-VIEWED GATE IS NOT ACTUALLY ENFORCED — USERS CAN SKIP READING ENTIRELY
═══════════════════════════════════════════════════════════

**This is the opposite problem from how it may initially read — it is a
missing-enforcement bug, not an over-application of an existing gate.**
The "must view every chapter before the finish/mark-complete action is
enabled" requirement specified in an earlier session is not actually
being enforced for an active, not-yet-completed reading day. Concretely:
if a user opens a reading day — including, specifically, by navigating in
via the Progress screen — they are able to go straight to marking the day
complete without having genuinely viewed all (or, apparently, in the worst
case, any) of that day's assigned chapters. This is a real, exploitable
gap: a user who discovers this can skip reading entirely and still receive
full credit/points for a day they never actually read, which undermines
the entire purpose of the original gate and, by extension, the integrity
of the leaderboard.

- Re-inspect the actual current mark-complete/finish-reading button logic
  for an active reading day and determine why the all-chapters-viewed
  check isn't actually blocking anything right now. Likely causes: the
  "chapters viewed" tracking state isn't being correctly initialized/
  checked for every entry path into the reading screen (e.g. it may only
  be correctly wired when a user reaches the reading screen via the
  normal Today/Continue flow, but not when reached via the Progress
  screen's "open a specific day" navigation — if Progress-screen entry
  skips whatever view-tracking initialization the normal flow sets up,
  the gate would have nothing to check against and may be failing open
  — i.e. defaulting to "allowed" — rather than correctly defaulting to
  "blocked until proven viewed"); or the gate's condition check itself has
  a logic bug (e.g. checking against the wrong day's assignment, an
  off-by-one in chapter count, or a check that's present in the UI layer
  but not actually enforced server-side, meaning a user could bypass it
  entirely via a direct API call even if the button is visually disabled
  in the normal app flow).
- Fix this as a genuine, robust gate: for any active, not-yet-completed
  reading day, regardless of which screen/entry point the user used to
  reach that day's reading content, the mark-complete/finish action must
  remain disabled until every chapter in that day's assignment has
  actually been viewed (per the viewed-detection logic already specified
  in the original gate's build session). Critically, **enforce this
  server-side as well as in the UI** — the backend's day-completion
  endpoint should itself validate that all chapters were genuinely viewed
  (or at minimum, validate some genuine server-recorded evidence of
  reading activity for that day) before accepting a completion request,
  not rely solely on a client-side disabled button that a modified or
  directly-called API request could bypass.
- This gate applies specifically to completing an active, not-yet-done
  reading day — it should not block a user from freely browsing/
  re-reading chapters from an already-completed day via Progress for
  review purposes (that remains fine, since there's no completion action
  to gate on an already-completed day in the first place) — the fix here
  is strictly about closing the gap that currently lets an active,
  incomplete day be marked complete without genuine full reading.
- Verify live, concretely proving the exploit is closed: as a test user,
  open an active reading day via the Progress screen entry point
  specifically (since this is the path the operator identified as
  currently vulnerable), view only one chapter (not all of them), and
  confirm the mark-complete action is genuinely disabled/blocked — both
  visually in the UI and by attempting the underlying API call directly if
  feasible in your test environment, to confirm server-side enforcement
  too. Then view every remaining chapter and confirm the action correctly
  becomes available only once all have genuinely been viewed.

## 2026-10-08 — Operator: Unify Reading Screens, Footnote Polish, Streak/Catch-Up Fix

Full instruction text below; credential values (GITHUB_PAT, CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID, TELEGRAM_BOT_TOKEN) were provided in the operator prompt but are REDACTED here per the contract's never-commit-credentials rule:

"""
# ForgeHouse 50 Android App — Unify Reading Screens, Footnote Polish, Streak/Catch-Up Fix

## Scope

Four issues. The first is structurally significant (two divergent Reading-
screen implementations currently coexist) and should be understood and
fixed at that level, not patched per-entry-point.

Read the Android app's current build-state file fully before starting.

# CREDENTIALS

[REDACTED — provided in operator prompt; never committed]

═══════════════════════════════════════════════════════════
ISSUE 1 — TWO DIVERGENT READING-SCREEN IMPLEMENTATIONS EXIST; UNIFY INTO ONE
═══════════════════════════════════════════════════════════

The operator reports that the enhanced/premium reader UI (from the prior
session's reader visual-pass work) only appears when the Reading screen is
opened via the Progress screen or via the home-screen widget — opening it
normally from the Home screen still shows the old, pre-enhancement UI.
**This means two separate Reading-screen composables/implementations
currently exist in the codebase**, and only one of them received the
recent enhancements (footnote marker redesign, premium visual pass,
Issue 5's completion-gate fix from the prior session, etc.) — the other,
older implementation is still being used by at least the main Home-screen
entry point and was never updated, or was never actually replaced/removed
when the new one was built.

- Investigate directly: find every navigation entry point that leads to
  the Reading screen (Home/"Continue" action, Progress screen's per-day
  review access, the widget's tap action, and any catch-up-specific entry
  point) and trace which actual composable/screen implementation each one
  navigates to. Confirm the operator's report — that Home-screen entry
  uses a different (older) implementation than Progress/widget entry.
- **The correct fix is consolidation, not a third patch layered on top**:
  there should be exactly one Reading-screen implementation in the
  codebase, containing all of the enhancements and fixes already built
  across recent sessions (premium visual pass, redesigned footnote marker,
  the all-chapters-viewed completion gate with its server-side
  enforcement, per-chapter pagination, font-size controls, the
  Progress-review-vs-active-day mode distinction). Every entry point
  (Home, Progress, widget, catch-up) must navigate to this single
  implementation, passing in whatever context/parameters that entry point
  needs (which day, which chapter, review-mode vs. active-mode) rather
  than each entry point having its own separate screen.
- Delete the old, now-redundant Reading-screen implementation entirely
  once the single unified version is confirmed to correctly handle every
  entry point's needs — do not leave dead/unused duplicate code behind.
- Verify live: open the Reading screen via all four entry points (Home,
  Progress, widget, and an active catch-up session) on a real/emulated
  device, and confirm all four show the identical, fully-enhanced UI with
  no visual or functional divergence between them.

═══════════════════════════════════════════════════════════
ISSUE 2 — FOOTNOTE MARKER: REMOVE TEXT, ICON ONLY
═══════════════════════════════════════════════════════════

The operator likes the redesigned footnote marker overall but wants its
text label removed, leaving only the icon.

- Locate the current footnote marker component (the small chip/indicator
  built in the prior premium-visual-pass session) and remove any text
  label it currently includes, leaving only the icon element — sized and
  positioned the same way it currently is otherwise (small, immediately
  after the relevant verse text, clearly tappable), just without the
  accompanying text.
- Ensure the icon alone remains clearly legible/tappable at its current
  size without the text — if removing the text makes the marker feel too
  small or ambiguous on its own, a modest size increase for the icon
  itself is reasonable, your judgment, but don't reintroduce text to
  compensate.
- Verify live with a real screenshot showing the icon-only marker next to
  a footnoted verse.

═══════════════════════════════════════════════════════════
ISSUE 3 — REMOVE CROSS-CHAPTER ALPHABETIC FOOTNOTE LETTERING
═══════════════════════════════════════════════════════════

The current footnote implementation apparently labels footnotes with
sequential letters that continue incrementing across the whole chapter
(first footnote in the chapter is "A," the next one anywhere later in the
same chapter is "B," and so on). The operator doesn't want this running
cross-chapter/cross-verse lettering scheme at all.

- Remove the alphabetic (or any other sequential, chapter-wide) labeling
  scheme entirely — individual footnotes should not be identified by a
  letter/number that increments across the whole chapter.
- **For a single verse with more than one footnote specifically**, there
  should still be some simple way to visually separate the multiple
  footnotes from each other within that verse's expanded footnote panel
  (per the original footnote spec: multiple footnotes for one verse
  appear together under that verse's one marker) — but this separation
  should be local to that verse only (e.g. the footnotes simply listed as
  distinct items/paragraphs within the panel, perhaps with a small visual
  divider between them, or a minimal non-alphabetic marker like a bullet
  or a small numeral that only distinguishes footnotes *within that one
  verse's panel* — 1, 2, 3 starting fresh for each verse rather than
  continuing from the previous footnoted verse's count elsewhere in the
  chapter), not a running identifier tied to the whole chapter.
- Verify live: find a verse with a single footnote and confirm no
  letter/number labeling appears at all; find (or construct test data
  for) a verse with two or more footnotes and confirm they're clearly
  visually separated within that verse's panel without any chapter-wide
  running label scheme.

═══════════════════════════════════════════════════════════
ISSUE 4 — CRITICAL: CATCH-UP DAYS MUST NOT INCREASE STREAK
═══════════════════════════════════════════════════════════

**This resolves, definitively, the "your judgment" note left open in a
prior session's streak-fix prompt** — the operator has now explicitly
specified the correct rule: completing a catch-up day (a backlog day being
recovered, per the earlier catch-up mechanic) must **never** increase a
user's streak counter. Streak specifically measures consecutive genuine
activity — i.e. a user actually opening the app and reading on the actual
current calendar day, in real time, without gaps — not the act of clearing
a backlog of previously-missed days after the fact.

- Locate wherever streak is incremented upon day completion (likely the
  same points/completion logic touched by the earlier streak-reset fix)
  and add an explicit check: if the reading day being completed is a
  catch-up/backlog day (i.e. its assigned reading-day number is earlier
  than the user's actual current real-time position per their own
  per-user calendar — meaning it was missed and is now being recovered,
  not completed on its originally-scheduled real day) rather than the
  user's genuinely current day, completing it must award all of its normal
  points exactly as already specified (reading, quiz score, etc.) but must
  **not** increment the streak counter.
- Confirm the inverse remains true and unaffected: completing a day that
  genuinely is the user's current real-time day (whether that's normal
  single-day completion, or the final "caught up, now current" day at the
  end of a catch-up sequence) correctly increments streak exactly as
  before — this fix narrows what counts toward streak, it doesn't change
  how streak increments for genuinely current-day completion.
- This interacts with, but doesn't replace, the earlier streak-reset-on-
  inactivity fix — that fix handles resetting streak to 0 after a genuine
  gap; this fix handles not artificially inflating streak back up via
  catch-up completions once a user does return and starts recovering
  their backlog. Both rules apply together: going inactive resets streak
  toward 0, and working through catch-up afterward doesn't rebuild that
  streak number — only resuming genuine day-by-day current activity does,
  starting fresh from that point.
- Verify live: as a test user with a multi-day backlog, complete two
  catch-up days and confirm streak does not increase from either
  completion; then complete a day that is genuinely their current
  real-time day and confirm streak correctly increments at that point.

═══════════════════════════════════════════════════════════
TASK LIST
═══════════════════════════════════════════════════════════

Append a `reader_unify_footnote_streak_v1` section to the Android app's
build-state file with one task per issue above (four issues total). Issue
1 is structurally significant and should be treated as its own
investigation-then-consolidation effort (trace entry points → confirm
divergence → consolidate → delete dead code → verify all entry points)
rather than a quick patch. Issue 4 is a second critical correctness item
alongside Issue 1's structural fix — both affect data integrity/UI
consistency for every user, not just cosmetic preference, and should be
prioritized accordingly. For each task: inspect → implement → verify
live/on-device with real evidence → commit → push → update the build-state
file → move to the next task without stopping to ask for confirmation.
Mark the section complete only once all four issues are independently,
genuinely verified as fixed with real on-device evidence for each,
including Issue 1's explicit four-entry-point verification and Issue 4's
explicit catch-up-vs-current-day streak verification.

═══════════════════════════════════════════════════════════
END OF PROMPT
═══════════════════════════════════════════════════════════
"""



## 2026-10-11 — EXTEND / UPDATE: Translation Chips Not Scrollable (Surgical Fix)

# ForgeHouse 50 Android App — Translation Chips Not Scrollable (Surgical Fix)

## Repository and project background

The ForgeHouse 50 Android app is built with the operator's tool ForgeBuild. Its source lives in the repository `https://github.com/vjumbo264/forgebuild.git` under `apps/<slug>/` (find the folder for the ForgeHouse 50 app by inspecting `apps/`). Releases are tagged `<slug>-vN` (for example `forgehouse50-v12`); the next release must use the next number after the highest existing `<slug>-vN` tag. The web programme and backend live in a different repository, `vjumbo264/forgehouse50`; **do not touch it**.

ForgeHouse 50 is a church New Testament reading programme: 260 chapters across 50 reading days. The app has a reader screen with a translation selector: bundled KJV plus downloadable translation bundles (for example AMP, CEV, GW, TLB, NIV and more, plus VOICE). The reader supports normal days and catch-up sessions (a "Catch-up · Days 9 + 10" screen with a Day 9 / Day 10 switcher).

You are an independent AI session with no memory of earlier sessions. The repository, git history and `BUILD_STATE.json` are the project's memory.

## The bug (evidence from the operator's phone)

On the reader screen in **dark mode**, in the catch-up view (title "Catch-up · Days 9 + 10", chapter Luke 5, Day 9 selected):

1. The translation chips row shows KJV, AMP, CEV, GW, TLB (selected), NIV, and the row is cut off at the right edge of the screen. More downloaded translations exist, but **the row cannot be scrolled**, so the operator cannot reach the rest of the downloaded versions or select them.
2. A thin **vertical scrollbar indicator** is drawn at the right edge of the chips area, next to the chips. That means the chips container is a vertical scroller (or sits inside one), not a horizontal one.
3. Below the chips there is a large **empty gap** (roughly the height of two more chip rows) before the "Previous / Luke 5 / Next" row. It looks like the chips wrap into additional rows that are clipped or hidden inside a fixed-height container.

In short: the chip list is wrapped or clipped in a fixed-height, vertically scrolling or clipping container, so overflow chips are unreachable.

## Hard rules for this fix

- **Surgical change only.** Fix the translation chips container and nothing else. Do not restructure the reader layout, the top bar, the bottom bar, the footnote UI, the quiz entry, or the navigation. A previous large layout rewrite broke the reading page, so no layout refactors are allowed.
- Do not change behaviour: which translations are listed, their order, selection logic, downloading, caching, or the catch-up and quiz rules.
- Do not touch `vjumbo264/forgehouse50`, other apps under `apps/`, or the signing/keystore configuration.
- No emojis, no new dependencies, no new permissions.

## Required behaviour

1. The translation chips are a **single horizontally scrolling row** (one line, never wrapping). Swiping left/right scrolls through every available translation, with no vertical scrollbar and no clipped overflow.
2. The row's height wraps the chips exactly. Remove any fixed height or hard-coded min height that caused the empty gap, so the "Previous / chapter / Next" row sits right under the chips with the app's normal spacing.
3. When the selected translation changes (or the screen opens with a translation already selected), the row **scrolls the selected chip into view**, so a chip far to the right is visible when selected.
4. Horizontal swipes on the chips must not be swallowed by the reader's own vertical scroll, and the reader's vertical scroll must still work normally when swiping on the chapter text.
5. Show a soft fade or partial-chip cut at the right edge only if it already matches the app's existing style. It is optional. The key point is that the cut-off chip is draggable and reachable.
6. Apply the same fix **everywhere the same chips row is used**: the normal reading-day reader (for example Day 4 / Matthew 19), the catch-up reader, and any other screen that reuses the translation selector. Find all usages in the code (search for the translation chip composable/view and its callers) and make sure they share one fixed implementation instead of leaving one copy broken.
7. Works in light and dark themes, at font-size A- and A+, and with large numbers of translations (test with at least 10 downloaded).

## Likely cause (verify in the code, do not assume)

The chips are probably in a `FlowRow`, a `Column`, or a `Row` with a fixed height or `verticalScroll` modifier, or inside a container with `height(...)`/`heightIn(...)` plus `verticalScroll`. The expected fix, in Jetpack Compose terms, is to use `LazyRow` (or `Row` with `Modifier.horizontalScroll(rememberScrollState())`), with `horizontalArrangement = Arrangement.spacedBy(...)`, no fixed height, and a `LaunchedEffect(selected)` that calls `animateScrollToItem` (or `animateScrollTo`) for the selected chip. If the app is not written in Compose, apply the equivalent: a `HorizontalScrollView` (or `RecyclerView` with a horizontal `LinearLayoutManager`) with `wrap_content` height. Read the actual code first and follow its existing patterns.

## Evidence standard (mandatory)

Do not mark anything done from reading source code. You must prove it on a running build:

- Build a debug build and run it in an emulator (or a device if available) with at least 10 translations downloaded or injected (use whatever mechanism the app already has for downloaded bundles; for testing only, you may register extra fake entries in a debug-only path that you remove afterwards).
- Capture real screenshots: (a) catch-up reader in dark mode with chips scrolled to the far right and the last chip selected; (b) the same screen in light mode; (c) the normal reading-day reader in dark mode; (d) before/after of the empty gap (the Previous/Next row directly under the chips).
- Record a short screen recording or a sequence of screenshots proving a horizontal swipe on the chips reveals the last chip, and a vertical swipe on the chapter text still scrolls the chapter.
- Save evidence under `evidence/version_chips_scroll_fix/` in the repo (screenshots only, kept small).

If you cannot run an emulator in your environment, say so in `BUILD_STATE.json`, do the code fix and a Gradle build, and mark the task `"status": "blocked_on_visual_verification"` rather than claiming it is verified.

## Release

After the fix is verified, bump the app's version code and name, build a signed release APK using the signing setup already configured in the repo (never print or commit any keystore or password), publish it as a GitHub release tagged with the next `<slug>-vN`, and attach the APK. Release notes: "Fixed translation list not scrolling on the reader screen."

## Persistent-session rules

- `BUILD_STATE.json` in the repo is the source of truth. If this fix has no section yet, append `"reader_version_chips_scroll_fix"` with the tasks below, all `"pending"`, commit and push that seed before anything else.
- After **every** step: update `BUILD_STATE.json`, commit, push, and verify the push succeeded before the next step. Builders often die before committing, so commit very frequently.
- Never reset or delete existing sections of `BUILD_STATE.json`. Never redo tasks marked `"done"` with a commit sha.
- Never stop to ask the operator. If something is unspecified, make the most reasonable engineering decision, document the assumption in the task's `notes`, and continue.
- Never print, log or commit credentials. Placeholders:

```
GITHUB_PAT = <PLACEHOLDER>
CLOUDFLARE_API_TOKEN = <PLACEHOLDER>   (not needed for this task)
CLOUDFLARE_ACCOUNT_ID = <PLACEHOLDER>  (not needed for this task)
```

## Task list (under `reader_version_chips_scroll_fix`)

- c01: Clone `vjumbo264/forgebuild`, locate the ForgeHouse 50 app, seed the section in `BUILD_STATE.json`, commit and push.
- c02: Find every usage of the translation chips row. Record the file paths and the container structure responsible for the bug in `notes`.
- c03: Implement the single horizontal, non-wrapping, wrap-height chips row as one shared component, and replace every usage. Include scroll-selected-into-view.
- c04: Build debug, run on emulator with 10+ translations, capture evidence screenshots and the swipe proof.
- c05: Check that nothing else changed visually (top bar, bottom bar, Previous/Next row, chapter text, footnote and quiz buttons) by comparing screenshots against the originals.
- c06: Bump the version, build the release APK, publish the GitHub release with the next `<slug>-vN` tag.
- c07: Final commit and push. Set `reader_version_chips_scroll_fix.work_complete` to `true`, summarising what changed, the files touched, and the release tag.

END OF PROMPT


## 2026-10-11 — Admin: Delete Users + New Generated Admin Login (Build Contract)

# ForgeHouse 50 — Admin: Delete Users + New Generated Admin Login (Build Contract)

## Repositories

- Backend and website: `https://github.com/vjumbo264/forgehouse50.git` (Cloudflare Pages + Functions + D1, live at `forgehouse50.pages.dev`).
- Android app (built with the operator's tool ForgeBuild): `https://github.com/vjumbo264/forgebuild.git`, source under `apps/<slug>/` (find the ForgeHouse 50 app by inspecting `apps/`). Releases are tagged `<slug>-vN`; the next release uses the next number after the highest existing `<slug>-vN` tag.

Both repositories already exist and are live. This is a follow-on session. You are one of many independent AI sessions with no memory of earlier ones; the repositories, git history and each repo's `BUILD_STATE.json` are the project's memory.

## Project background

ForgeHouse 50 is a church New Testament reading programme: 260 chapters across 50 reading days. Users sign up with email plus a 6-digit OTP sent by Brevo from one verified Gmail sender. The website and the Android app are thin clients of the same Cloudflare backend, so backend rules are enforced server-side. Programme facts that matter here:

- Admin screens exist: Start Programme (10-day join window), End Testing Phase & Reset, award/remove points, badges, participant list. **Admins can never see users' private notes.**
- Tuesdays and Fridays are not reading days. Day boundaries use WAT (Africa/Lagos).
- Leaderboard Overall = raw total points; users appear from day 3.
- Existing real participants and testing leftovers (for example several "Test P." accounts) are in D1. The operator is a participant himself (Victor Fubara, on the leaderboard with real points).
- A website rebuild may be in progress on a branch called `site-v2` (a from-scratch frontend with the same backend). Check `BUILD_STATE.json` and the branches before editing the web frontend (see Part C).

## What the operator wants

1. **Admins can delete users** (from the admin area, in both the website and the Android app).
2. **The admin login must change to a new, generated login that is not the operator's email**, so he can hand it to his pastor. Right now admin access is tied to the operator's own email account (email + OTP).

# ABSOLUTE RULES

- **The repository is the source of truth.** Inspect → determine state → do the next unfinished task → commit → push → continue. Never stop merely because part of this is already done. Never ask the operator anything; decide, document the assumption in `BUILD_STATE.json` notes, and continue.
- **Never reset `BUILD_STATE.json`.** Append a new section `admin_delete_and_login_v1` (tasks below, all `"pending"`) in each repo you touch, commit and push the seed first. Never delete earlier sections or redo tasks marked `"done"` with a commit sha.
- **Per-step protocol:** perform step → validate → update `BUILD_STATE.json` → commit → push → verify the push → next step. Commit and push very frequently, since builders often die before committing.
- **Surgical changes only.** Do not restructure existing screens, navigation, the reader, quiz, leaderboard logic or any programme rule. Backend changes are additive (new endpoints, new tables or columns via a new migration). A previous large layout rewrite broke a screen, so no refactors.
- **No emojis** in UI or copy.
- **Credentials:** use placeholders and never print, log or commit them:

```
GITHUB_PAT = <PLACEHOLDER>
CLOUDFLARE_API_TOKEN = <PLACEHOLDER>
CLOUDFLARE_ACCOUNT_ID = <PLACEHOLDER>
TELEGRAM_BOT_TOKEN = <PLACEHOLDER>
BREVO = <PLACEHOLDER>
```

The one deliberate exception is the generated admin password in Part B, covered by its own rule.

- **Evidence standard:** nothing is "done" from reading source. Prove behaviour with real requests against the deployed preview/production (curl output saved, trimmed of secrets) and real screenshots of the website (390x844 and 1280x800) and the Android app on an emulator. If something cannot be visually verified in your environment, mark it `"blocked_on_visual_verification"`, never "done".
- Delete only users you created yourself for testing. Never delete a real participant while testing.

# PART A — Admin can delete users

## Backend (additive)

Add admin-only endpoints (match the existing admin auth and routing style; inspect the existing admin endpoints first):

- `POST /api/admin/users/:id/delete` (or `DELETE /api/admin/users/:id`, follow the existing convention).
- Optional bulk: `POST /api/admin/users/delete` with a list of ids (max 50 per request). Use one DB batch per user.

Rules the server must enforce (never trust the client):

1. Only a valid admin session may call it. Others get 403.
2. An admin **cannot delete themselves or any admin account**. Return a clear error.
3. Require a confirmation field in the request that matches the target user (for example the user's display name or id), so a stray tap cannot delete someone.
4. Deletion is **permanent and complete**. Discover the schema (read `migrations/` and run a table listing) and delete everything that references the user, in a safe order, including at least: reading progress, notes, bookmarks, highlights, user badges, points and point history, quiz attempts and answers, catch-up records, streak data, per-user calendars, audio progress (if the table still exists), sessions, OTP codes, device/notification tokens, leaderboard snapshots, and the user row itself. Do not leave orphan rows. Wrap per-user deletion in a single `db.batch([...])` so it is atomic.
5. The deleted user's sessions must stop working immediately (their next request returns the normal "signed out" response). If they sign up again later they get a fresh account.
6. Admins never see private notes. The delete flow must not read or display note contents.
7. Add an audit table in a new migration, `admin_audit` (`id`, `admin_id`, `action`, `target_user_id`, `target_display_name`, `created_at`), and write one row per deletion. Never store notes, emails of third parties beyond what the participant list already shows, or passwords.
8. After deletion, leaderboard and participant counts are recomputed on the next request (no stale cache). Verify this with a real before/after request.

## UI (website and Android app)

- In the admin participant list, each non-admin row gets a "Delete" action (menu or button, consistent with the existing admin styling; admin rows have no delete action).
- Tapping it opens a confirmation dialog that names the person, states that it permanently removes their progress, points, notes and badges and cannot be undone, and requires typing the person's name (or a short confirm word) before the red "Delete permanently" button enables.
- After success: remove the row, show a short confirmation, and refresh the list and counts. On failure show the server's real message (never a generic "Request failed").
- Optional but welcome: a "Select" mode for deleting several test users at once, using the bulk endpoint.

# PART B — New generated admin login (not the operator's email)

## Design

Admin access moves to a **dedicated admin credential: username + generated strong password**, independent of email OTP. The pastor logs in with that username and password; no email or OTP is needed for admin.

Requirements:

1. **Generate** a new admin account in a one-off script `tools/rotate-admin.mjs` (run with the Cloudflare API token against D1; works with `--remote`). The script creates (or replaces) the single admin credential:
   - Username: a generated, readable handle such as `fh50-admin-xxxxxx` (6 random characters, lowercase letters and digits).
   - Password: cryptographically random, at least 20 characters, mixed case, digits and a few safe symbols, with no ambiguous characters (no `0/O`, `1/l/I`). Use `crypto.randomBytes` (or WebCrypto), never `Math.random`.
2. **Store only a hash**: PBKDF2-SHA256 with a per-credential random salt, using WebCrypto in the Worker (use an iteration count that fits Cloudflare's free-tier CPU limit, around 100,000, and record the chosen number in `BUILD_STATE.json`). Store salt, iterations and hash. Never store or log the plaintext.
3. **Login endpoint** `POST /api/admin/login` with `{ username, password }`:
   - Constant-time comparison; the same generic error for unknown username and wrong password.
   - **Brute-force protection**: after 5 failed attempts for a username or from one IP, lock out for 15 minutes (track in D1; no KV). Return a clear "Too many attempts, try again later" message with the retry time.
   - On success issue an admin session in the same mechanism the app already uses (httpOnly secure cookie for the website, the existing token style for the app), with a **12-hour expiry**.
   - Admin sessions grant only admin endpoints; they are not participant accounts and have no points, streak or leaderboard presence.
4. **Retire the email-based admin path.** Remove the admin role from the operator's email-based participant account (he stays a normal participant with all his points, progress and leaderboard entry intact; verify his totals are unchanged before and after). Make sure **no email OTP login can ever grant admin rights** after this change. Delete any hard-coded admin-email checks and any `ADMIN_EMAIL`-style configuration that grants admin, and remove references to it from `README.md`/`ARCHITECTURE.md`.
5. **Change password**: in the admin area add a "Change password" screen (current password + new password with a minimum length of 12 and a basic strength check). On success, invalidate all other admin sessions. This lets the pastor replace the generated password with his own.
6. **Recoverability**: because the generated password is shown only once, `tools/rotate-admin.mjs` can be re-run at any time to generate a fresh credential and invalidate all old admin sessions. Document this in `README.md` (placeholders only, never real values).
7. **Admin login screen** (website and Android app): a small "Admin sign in" link at the bottom of the normal login screen, opening a plain username + password form with a show/hide toggle, matching the existing visual style. No emojis. Do not alter the normal participant login flow.
8. Keep every existing admin capability working with the new session (Start Programme, End Testing Phase & Reset, award/remove points, badges, participant list, and the new delete). Verify each with a real request.

## Delivering the generated credential (explicit exception to the credential rule)

The operator needs the generated username and password so he can give them to his pastor. Therefore:

- Generate it in your session, print the **plaintext username and password exactly once**, inside your **final response to the operator**, in a clearly labelled block, with a one-line reminder to hand it to the pastor and have him change the password after the first login.
- **Never** write the plaintext password to any file, commit, log, `BUILD_STATE.json`, README, issue, comment or screenshot. Do not echo it in the shell history of any step that is saved as evidence; redact it from any saved output. `BUILD_STATE.json` may record only the username, the hash algorithm, the iteration count, and the date it was rotated.
- If your session may end before the final response, rotate the credential as the **last** task, so the printed password is the live one. If it is somehow lost, the operator can re-run the rotate script (documented in README) and nothing else breaks.

# PART C — Clients

- **Android app** (`forgebuild`, `apps/<slug>/`): add the admin sign-in screen, the delete flow with confirmation, and change-password. Reuse the app's existing networking, theme (Material 3 Expressive, dynamic colour) and admin screens. Build, run on an emulator, capture screenshots of: admin sign-in, participant list with the delete action, the confirmation dialog, the post-delete list and the change-password screen. Then bump the version, build the signed release APK with the signing already configured (never print or commit keystores or passwords), and publish a GitHub release with the next `<slug>-vN` tag. Release notes: "Admin can delete users; new admin sign-in."
- **Website** (`forgehouse50`): check the state of the web frontend before editing.
  - If the `site-v2` rebuild has **already been merged to `main`**, implement the admin pieces in the new frontend, following its design tokens and components.
  - If `site-v2` exists but is **not yet merged**, implement them on `site-v2` (the new frontend). If the old frontend is still the live one and the rebuild has not started, implement them on the old frontend with the smallest possible change, and record in `BUILD_STATE.json` that the same admin UI must be carried into the rebuild. Never duplicate or break the backend contract.
  - The backend must be correct and tested regardless of the frontend state, because the Android app depends on it.
- Run the backend change against the **preview** deployment first (Cloudflare Pages preview on a feature branch) and apply the new D1 migration to production only after the preview tests pass. Record `git diff --stat` for `functions/` and `migrations/`.

# TASK LIST (under `admin_delete_and_login_v1`, in each repo you touch)

- a01: Clone both repos, inspect admin code, schema, existing admin auth, branches (`site-v2`), and the current frontend state. Seed the section, commit and push.
- a02: Write the migration (`admin_audit`, admin credential table, login attempt tracking) and the user-deletion routine. Include a cascade check script that lists every table with a user reference and reports orphans.
- a03: Implement `POST /api/admin/users/:id/delete` (and bulk) with all server rules. Test against preview with self-created test users: delete works, cascade leaves zero orphans, admins and self cannot be deleted, wrong confirmation is rejected, deleted user's session dies, leaderboard and participant counts update.
- a04: Implement `POST /api/admin/login`, hashing, lockout, 12-hour sessions, change-password, and the "no email path grants admin" guarantee. Test: correct login, wrong password, lockout after 5 failures, expired session, change-password invalidates other sessions, OTP login for the operator's email no longer yields admin.
- a05: Confirm every existing admin function works with the new admin session (real requests for each).
- a06: Android app: admin sign-in, delete flow with confirmation, change-password. Emulator screenshots.
- a07: Website: the same admin UI (per Part C rules). Screenshots at 390x844 and 1280x800, light and dark.
- a08: Apply the migration to production, confirm the operator's participant account lost admin rights but kept all points and progress (compare before and after totals), and that existing participants are untouched.
- a09: Update `README.md` and `ARCHITECTURE.md` (admin login model, rotate script usage with placeholders, deletion rules). Remove all references to the old email-based admin.
- a10: Publish the Android release (`<slug>-vN`) with the APK attached.
- a11: Delete every test user and test artifact you created (use the new delete endpoint to prove it works), confirm the participant list contains only real users.
- a12: Rotate the admin credential as the very last step (`tools/rotate-admin.mjs --remote`), record only non-secret metadata in `BUILD_STATE.json`, set `admin_delete_and_login_v1.work_complete` to `true`, push, and print the one-time credential block in the final response as described above.

END OF PROMPT

# 2026-10-12 — ForgeHouse 50 — Batch Fixes: Notes Auto-fill, Offline Translations List, Admin Dashboard Redesign + Batch Delete (Build Contract)

## Repositories

- Android app (built with the operator's tool ForgeBuild): `https://github.com/vjumbo264/forgebuild.git`, source under `apps/<slug>/` (find the ForgeHouse 50 app by inspecting `apps/`). Releases are tagged `<slug>-vN`; the next release uses the next number after the highest existing `<slug>-vN` tag.
- Backend and website: `https://github.com/vjumbo264/forgehouse50.git` (Cloudflare Pages + Functions + D1, live at `forgehouse50.pages.dev`).

Both repositories exist and are live. This is a follow-on session. You are one of many independent AI sessions with no memory of earlier ones. The repositories, git history and each repo's `BUILD_STATE.json` are the project's memory.

## Project background

ForgeHouse 50 is a church New Testament reading programme: 260 chapters across 50 reading days. The Android app and the website are thin clients of one Cloudflare backend. The app has bundled KJV plus downloadable translations, a reader, personal notes (private to the user), a Profile screen, and an Admin area reached through a dedicated admin sign-in (username + password, no email). Admins never see users' private notes. A previous session already added: admin delete-user endpoints, `admin_audit`, the dedicated admin credential and login, and change-password. The operator's pastor will use the admin area.

## Evidence from the operator's phone (dark theme, Android)

1. **New note screen** shows: Type chips (Observation, Question, Insight, Prayer, Application), a "Note (private to you)" text box, three empty fields "Book", "Chapter", "Verse", and a Save note button. Pastor feedback: the book (and chapter and verse) should appear automatically, and when a person highlights a verse they have a question about, it should also appear automatically.
2. **Profile → "Offline Bible translations"** lists every downloaded translation with a red trash icon beside each (Amplified, CEV, GOD'S WORD, Living Bible, NIV, NKJV, NLV, NLT, The Message, The Passion Translation, The Voice, ...). A "Manage versions" tab already exists where deleting belongs. Pastor feedback: the list should display the translations but not allow deleting here.
3. **Admin → Participants tab**: the tabs are Stats, Participants (the label wraps onto two lines, "Participant / s"), Programme, Password. Each participant row shows only a given name (for example "Victor", "David", "Julia Chinemerem" with some rows missing surnames, so duplicates like two "Victor"s and two "David"s cannot be told apart), then "pts · days · notes · member", then text buttons "Points" and "Delete". Deleting asks the admin to type the person's full name, which is annoying. There is no batch delete. The operator says the dashboard UI/UX is not nice.

# ABSOLUTE RULES

- **The repository is the source of truth.** Inspect → determine state → do the next unfinished task → commit → push → continue. Never stop merely because part of this is done. Never ask the operator anything; decide, document the assumption in `BUILD_STATE.json` notes, and continue.
- **Never reset `BUILD_STATE.json`.** Append a new section `batch_fixes_oct12_v1` (tasks below, all `"pending"`) in each repo you touch; commit and push the seed first. Never delete earlier sections or redo tasks marked `"done"` with a commit sha.
- **Per-step protocol:** perform step → validate → update `BUILD_STATE.json` → commit → push → verify the push → next step. Commit and push very frequently; builders often die before committing.
- **Scoped changes.** Touch only the screens named here: New note, the Profile offline-translations list, and the Admin area. Do not restructure the reader, quiz, leaderboard, navigation, or any programme rule. A previous large layout rewrite broke the reading page, so no refactors outside these screens. Backend changes are additive and backward-compatible only.
- **No emojis** in UI or copy.
- **Never print, store or commit the admin password.** Do not rotate the admin credential in this session; it is already set. Do not touch the admin credential table except through the existing change-password flow when testing (use a throwaway test admin you create and delete, never the real one).
- **Environment reality:** you (the builder) have **no Android emulator and no device**. Apps can only be built, and tests only run, on **GitHub Actions** in the `forgebuild` repo (use the existing workflow, or add one). Do not claim anything about how a screen looks from reading source.
- Delete only users you created for testing. Never delete a real participant.

# PART 1 — New note auto-fill
Goal: when a note is created from reading, Book, Chapter and Verse are filled in for the user.
1. Entry from the reader (the pencil icon in the reader's top bar, or the Notes action there): pre-fill **Book** and **Chapter** with the chapter currently open in the reader (the exact chapter on screen, including in catch-up sessions where two days are open: use the chapter being viewed). Pre-fill **Verse** with the verse or verse range the user has selected or highlighted if any; otherwise leave Verse empty but focusable.
2. Entry from a highlighted verse: when a user selects or highlights a verse (or several) in the reader, show the existing action for notes ("Add note") if present, or add one in the same style. Opening it pre-fills Book, Chapter and Verse (a range like `3-5` for several consecutive verses). If the highlight flow already has a "question" style or the user marks it as a question, preselect the **Question** type chip; otherwise leave the type unselected or on the existing default.
3. Show the pre-filled reference clearly. Make Book/Chapter/Verse remain **editable**, but replace free typing with **pickers limited to valid values** if that is easy in the existing UI (book list in canonical order, chapter list sized to the chosen book, verse list sized to the chosen chapter); if pickers would be a large change, keep text fields but validate against real book/chapter/verse counts and show a friendly error instead of saving an impossible reference.
4. Entry from "My notes" or anywhere with no reading context: fall back to the user's **current reading day first chapter** if there is one, otherwise leave empty; the user can pick.
5. Saved notes keep the same stored shape and stay private; do not change the notes API contract. Notes created this way must show their reference in "My notes" exactly like manual notes.
6. Test every entry point: from a normal day reader, from catch-up (check both Day tabs give the right chapter), from a verse highlight (single verse and a range), and from My notes.

# PART 2 — Profile: offline translations list is display-only
1. In **Profile → Offline Bible translations**, remove the trash/delete icon and any delete action or long-press delete from every row. Keep the name, "260 chapters · size", and the existing header text ("King James Version is bundled with the app — always available offline" and the total storage used).
2. Rows should not look tappable-to-delete. If useful, add one line under the header: "Manage downloads in Manage versions." (match the app's wording for that tab; find its real name in the code), with an optional tap-through to that tab if navigation to it already exists.
3. Deleting a downloaded translation must still be possible in the "Manage versions" tab. Verify it still works after your change, and that the storage total updates there and in Profile.

# PART 3 — Admin dashboard redesign, full names, easier delete, batch delete
## Backend (additive, backward-compatible)
- The admin participant list endpoint must return each participant's **full name** (given name + surname, as entered at registration), avatar id, total points, days completed, notes **count** only (never note contents), status, a short tag (the last 4 characters of the user id), created date, and whatever it already returns. Add fields; do not remove or rename existing ones. Do not return emails unless the existing list already did.
- Deletion confirmation contract: the server must stop requiring the user's typed name. For a single delete accept `{ confirm: true }`. For a batch delete accept `{ ids: [...], confirm_word: "DELETE" }` (case-insensitive compare, 1 to 50 ids). Keep the old name-matching confirmation accepted for backward compatibility so older app builds still work.
- Keep every existing server rule: admin session required, admins and the caller can never be deleted, atomic cascade delete per user with zero orphans (no leftover rows in any child table), immediate invalidation of the deleted user's sessions, one `admin_audit` row per deleted user, no note contents read or displayed. A batch must report per-id results (deleted, skipped with reason, failed) and never fail the whole batch because of one bad id.
- If a batch endpoint does not exist yet, add `POST /api/admin/users/delete` now.
- Run changes against a Cloudflare Pages preview first; apply any new migration to production only after the preview tests pass. Record `git diff --stat` for `functions/` and `migrations/`.

## Android app: Admin UI/UX
Keep the app's existing look and theme (Material 3 Expressive, dynamic colour, the current dark and light themes). Fix the following and polish the Admin area as a whole:
1. **Tabs:** Stats, Participants, Programme, Password must fit on one line each with no wrapping. Use a scrollable tab row, shorter labels where needed (keep "Participants"), and a clear selected indicator.
2. **Participants tab layout:**
   - Header: "Participants" with the count ("7 participants"), a search field (filters by full name), and a sort control (Points high to low, Newest, Name A to Z, Days completed).
   - Each person is a **card**: avatar, **full name** on one or two lines (wrap long names, never truncate to the given name), then a secondary line "886 pts · 8 days · 0 notes" and a small short tag (for example `#9ae4`), so same-named people can be told apart. Rows must be comfortably tappable (minimum 48dp targets).
   - Actions move off the row: one overflow menu (three dots) per card with "Award or remove points" (the existing Points action) and "Delete". Do not leave a bare red "Delete" text button on every row.
   - Empty state when the search returns nothing, and a loading state with a skeleton or progress indicator.
   - After any action show a snackbar with the result (not a permanent line of text at the top).
3. **Single delete:** choosing Delete opens a dialog that names the person ("Delete Victor Fubara?"), states in one or two lines that it permanently removes their progress, points, notes and badges and cannot be undone, with a Cancel button and a red "Delete permanently" button. No typing required. Show a progress state on the button while deleting and surface the server's real error message on failure.
4. **Batch delete:** a "Select" action in the header turns on selection mode: checkboxes on every card, a contextual top bar showing "N selected", a "Select all" toggle, and a **"Select inactive accounts"** shortcut that selects everyone with 0 points, 0 days and 0 notes (this is a selection helper only; it never deletes by itself, and it must show the resulting count). A bottom action bar shows "Delete (N)". Tapping it opens one dialog listing the count and the first few names ("and 4 more"), with a single text field where the admin types the word `DELETE` once to enable the red button. After completion show a result summary ("12 deleted, 1 skipped") and refresh the list and counts. Exit selection mode on completion or back.
5. **Stats, Programme, Password tabs:** keep their functions and server calls unchanged, but tidy spacing, typography and button hierarchy to match the new cards.
6. Make the screens correct in dark and light themes, at normal and large system font sizes, and with 0, 1 and 40+ participants.

## Website admin (only if it exists)
If the website's admin area exists in the current live frontend (or on the `site-v2` branch if the rebuild is in progress and not merged), apply the same changes: full names, search and sort, cards, no-typing single delete, select mode with batch delete, tab polish.

# PART 4 — Admin sign-in is easy to find
1. Make "Admin sign in" a clearly visible secondary button or link in the login screen, always reachable (scrollable content with proper insets so the keyboard never hides it), with an unambiguous label.
2. If a user enters something that does not look like an email (no `@`) in the participant Email field and submits, show a hint under the field: "Looks like an admin username. Use Admin sign in." with a tap target that opens the admin form with the value copied across.
3. Do the same on the website login if it has an admin sign-in.

# PART 5 — Release hardening
1. In the release build, ensure **signing is the very last step** (zipalign before signing; nothing modifies the APK after `apksigner sign`).
2. Run `apksigner verify --verbose` on the final APK in the build and fail the build if it does not pass.
3. After publishing the GitHub release, **download the asset back** from the release URL and confirm its SHA-256 and byte size match the local file. Add the SHA-256 and size to the release notes.
4. Never print or commit keystores or passwords.

# TASK LIST (under `batch_fixes_oct12_v1`)
- b01: Clone both repos, inspect the app (notes screen, reader selection/highlight code, Profile translations section, Admin screens), the admin endpoints, schema, branches (`site-v2`), and the web admin state. Seed the section in each repo, commit and push.
- b02: Part 1 — implement note auto-fill for all entry points; unit tests for each entry point; screenshots only if the Actions emulator works.
- b03: Part 2 — make the Profile translations list display-only; verify through code and tests that delete still works in Manage versions; screenshots only if the Actions emulator works.
- b04: Part 3 backend — full names and short tag in the participant list, `confirm: true` single delete, batch delete with `confirm_word`, per-id results. Test on preview with self-created test users: cascade leaves zero orphans, bad ids are skipped without failing the batch, admins and self are protected, the old name-confirm still works, audit rows written.
- b05: Part 3 Android — tabs, participant cards, search/sort, overflow menu, single-delete dialog, select mode, batch delete, snackbars, tidy of the other tabs. Unit/UI tests for sorting, search and selection logic; screenshots (dark and light, large font, 0/1/40+ participants) only if the Actions emulator works, otherwise list the screens in `needs_operator_visual_check`.
- b06: Part 3 website admin (if present, per rules above).
- b07: Part 4 — admin sign-in visibility and hint, app and website.
- b08: Apply any new migration to production after preview passes; confirm real participants are untouched (compare participant count, total points and leaderboard order before and after).
- b09: Update `README.md` and `ARCHITECTURE.md` for the changed delete confirmation contract, batch endpoint and participant-list fields.
- b10: Part 5 — signing and verification steps, bump the version, publish the next `<slug>-vN` release, attach the APK, verify the downloaded asset's SHA-256 and size, add them to the release notes. Release notes: "Notes auto-fill book/chapter/verse; translations list display-only; admin dashboard redesign with full names and batch delete; easier admin sign-in."
- b11: Delete every test user and artifact you created (use the new batch delete to prove it), confirm the participant list contains only real users.
- b12: Final commit and push; set `batch_fixes_oct12_v1.work_complete` to `true` with a summary of what changed, files touched and the release tag.
END OF PROMPT


## 2026-10-14 — Priority Batch: Programme Window Integrity, Site Repair, Forgot Password, Update Prompt + Changelog, Small-Screen Scaling, Floating Nav

# ForgeHouse 50 — Priority Batch: Programme Window Integrity, Site Repair, Forgot Password, Update Prompt + Changelog, Small-Screen Scaling, Floating Nav (Build Contract)

## Repositories

- Backend and website: `https://github.com/vjumbo264/forgehouse50.git` (Cloudflare Pages + Functions + D1, live at `forgehouse50.pages.dev`).
- Android app, built with the operator's tool ForgeBuild: `https://github.com/vjumbo264/forgebuild.git`, source under `apps/<slug>/` (find the ForgeHouse 50 app by inspecting `apps/`). Releases are tagged `<slug>-vN`; each new release uses the next number after the highest existing `<slug>-vN` tag.

Both repositories exist and are live with **real users who have already joined**. You are one of many independent AI sessions with no memory of earlier ones. The repositories, git history and each repo's `BUILD_STATE.json` are the project's memory.

## Project background

ForgeHouse 50 is a church New Testament reading programme: 260 chapters in 50 reading days. Users register with given name, surname, an illustration avatar, email and a password, verified by a 6-digit email OTP (Brevo, one verified sender); they sign in with email + password. The Android app and the website are thin clients of one Cloudflare backend (D1). Programme rules, all enforced server-side:

- Day boundaries use WAT (Africa/Lagos). Each user has a personal 50-day calendar starting on their own Day 1. Tuesdays and Fridays are not reading days (fasting and prayer).
- Finishing a day requires viewing every chapter of that day. Daily quiz: one attempt, no retry, no timer, proportional points, never blocks finishing.
- Catch-up: at most 2 days per session, oldest first; catch-up days never increase the streak. Read-ahead: at most 1 day. Streak resets on inactivity.
- Leaderboard: Overall is raw cumulative points; every category has its own metric; users appear from their personal day 3; the final top 3 by raw points are celebrated when the programme ends.
- Admin: a dedicated admin login (username + password, separate from participant email login), a Programme tab with **Start Programme** (opens a **10-day join window**) and **End Testing Phase & Reset** (wipes all non-admin users), participant list with Points, single delete and batch delete, and Change password. Admins never see private notes. Scripture comes from the church's VerseWell project (`versewell.pages.dev`) and downloadable translation bundles; KJV is bundled in the app; the audio feature was removed.

## Current situation (as reported by the operator on Wednesday 2026-10-14)

1. **The admin forgot to run End Testing Phase & Reset, and started the programme anyway, on Monday 2026-10-12.** New real users have joined since. **Nothing may be reset or deleted.** The join window must now close on schedule: 10 days counted from the Monday start, so **8 days remain as of Wednesday** (including today).
2. **The website is broken:** scriptures do not load, nothing works, it looks nothing like the app, and the planned landing page does not exist.
3. Users who forget their password have no way back in.
4. The Android app has no update prompt and no changelog.
5. On small or low-density phones the app UI looks far too large.
6. The Android bottom navigation (a floating pill bar with all labels always visible) should be replaced with the dynamic expanding style described in Item 6.

# ABSOLUTE RULES

- **The repository is the source of truth.** Inspect -> determine state -> do the next unfinished step -> commit -> push -> verify -> continue. Never stop merely because part of this is done. Never ask the operator anything; decide, document the assumption in `BUILD_STATE.json` notes, and continue.
- **ONE WORK ITEM AT A TIME.** The work items below are ordered by importance and criticality. Do them strictly in order. **Never work on two items at once**, except where an item explicitly marks a *paired* fix (only Item 4: the update prompt and the changelog are one paired item). An item may not start until the previous item has `"status": "done"`, its commit is pushed, and the push is verified. Record this in `BUILD_STATE.json` as a `gate` per item. Never start the next item "while waiting" for something.
- **COMMIT AND PUSH BEFORE EVERY NEXT STEP.** Per-step protocol: perform step -> validate -> update `BUILD_STATE.json` -> commit -> push -> verify the push succeeded -> only then the next step. Builders often die before committing, so commit and push very frequently, at least after every sub-step listed in an item.
- **Never reset `BUILD_STATE.json`.** Append a new section `ops_batch_oct14_v1` in each repo you touch (all items `"pending"`), commit and push the seed first. Never delete earlier sections or redo tasks marked `"done"` with a commit sha.
- **NEVER RESET, WIPE OR DELETE PRODUCTION DATA.** Do not call End Testing Phase & Reset, do not delete users, do not edit users' points or progress, do not restart or move the programme start. Production access is read-only except for explicitly named additive migrations. Test only on a Pages preview deployment or with test users you create yourself and delete yourself.
- **Frozen backend contract.** Backend changes are additive and backward-compatible (new endpoints, new columns/tables through a new migration). The Android app depends on the existing API.
- **No emojis** in UI or copy.

# TASK LIST (under `ops_batch_oct14_v1`; items strictly in order, each gated on the previous being done and pushed)

- w0: Clone both repos, inspect state, seed the section in each (items `w1` to `w6` all `"pending"`, with `gate`), commit and push.
- w1: Item 1 — programme window and end-date integrity, reset guard, read-only audit, tests, preview, production deploy.
- w2: Item 2 — website: 2a foundations, 2b data layer and scripture, 2c landing + `/api/app-release`, 2d app parity, 2e navigation to spec, 2f PWA + service-worker migration, 2g verify and merge. Each sub-step committed and pushed before the next.
- w3: Item 3 — forgot password: backend, then app, then website, each pushed before the next.
- w4: Item 4 — update prompt + changelog (the only paired item), release workflow changes, release.
- w5: Item 5 — minimum UI size on small and low-density screens.
- w6: Item 6 — dynamic floating navigation; final release; final verification of all items; set `ops_batch_oct14_v1.work_complete` to `true` in each repo and push.



---

## 2026-10-15 — Fixes Batch (Oct 15): Fake Data on the Site, Reading Completion + Offline Timer, Web Nav Contrast, Admin Reset/Oversight, Notes Visibility, Android Nav Animation + Swipe (Build Contract)

# ForgeHouse 50 — Fixes Batch (Oct 15): Fake Data on the Site, Reading Completion + Offline Timer, Web Nav Contrast, Admin Reset/Oversight, Notes Visibility, Android Nav Animation + Swipe (Build Contract)

## Repositories

- Backend and website: `https://github.com/vjumbo264/forgehouse50.git` (Cloudflare Pages + Functions + D1, live at `forgehouse50.pages.dev`).
- Android app, built with the operator's tool ForgeBuild: `https://github.com/vjumbo264/forgebuild.git`, source under `apps/<slug>/` (find the ForgeHouse 50 app by inspecting `apps/`). Releases are tagged `<slug>-vN`; each new release uses the next number after the highest existing `<slug>-vN` tag.

Both repositories exist and are live with **real users who are mid-programme**. You are one of many independent AI sessions with no memory of earlier ones. The repositories, git history and each repo's `BUILD_STATE.json` are the project's memory.

## Project background

ForgeHouse 50 is a church New Testament reading programme: 260 chapters in 50 reading days. Users register with given name, surname, avatar, email and password (email OTP verified through Brevo) and sign in with email + password. The Android app and the website are thin clients of one Cloudflare backend (D1). Rules enforced server-side:

- Day boundaries use WAT (Africa/Lagos). Each user has a personal 50-day calendar from their own Day 1. Tuesdays and Fridays are not reading days.
- Daily quiz: one attempt, no retry, no timer, proportional points, never blocks finishing the day.
- Catch-up: at most 2 days per session, oldest first; catch-up days never increase the streak. Read-ahead: at most 1 day. Streak resets on inactivity.
- Leaderboard: Overall is raw cumulative points; each category has its own metric (including a **Reading time** category); users appear from their personal day 3.
- Admin: a dedicated admin login (username + password, separate from participant login); admin area with Stats, Participants (search, sort, cards, single delete, batch delete), Programme (the programme has **already been started on 2026-10-12 and must never be reset**), Password.
- The reader screen shows a translation selector, A- / A+, chapter Previous/Next, a bottom bar with **Reading complete** and **Quiz**, and notes (Observation, Question, Insight, Prayer, Application, with Book/Chapter/Verse).

## Other contracts that may exist

A contract named `ops_batch_oct14_v1` (programme window, website rebuild, forgot password, update prompt, small-screen scaling, floating nav) may be in progress or finished in these repos. Read its state in each `BUILD_STATE.json` first. **Do not redo anything it marks done, and do not undo it.** This contract's items are narrow fixes; apply each to whatever is currently live. If a `site-v2` branch exists and has diverged from `main`, make each web fix on `main` and bring the same change into `site-v2` (cherry-pick or re-apply) so it is not lost, and record that in `BUILD_STATE.json`. Where this contract and the other one overlap (the nav), **this contract's specification wins**.

## What the operator reported (Thursday 2026-10-15)

1. **Website nav:** in light mode the selected floating-nav tab's label (and icon) is white on a near-white pill and cannot be read. The reverse may also be true in dark mode.
2. **Website shows the wrong users:** he can log in, but the users shown on the site are not the users in the real app. It looks like synthetic data.
3. **Reading completion:** remove the "chapter 1 of 2 / chapter 2 of 3 viewed" check, because it is unreliable. When a user reaches the **final chapter** of their reading, they must be able to tap **Mark complete**.
4. **Reading timer:** users complain the reading time does not match reality. It must count from the phone's own clock even when offline, and the real reading time must be sent when the user completes their reading. The admin must be able to see it. A **5-minute minimum** reading time before marking complete is approved.
5. **Admin oversight and fairness:** the admin must be able to see each user's reading time, see all **Observation** and **Question** notes (including existing ones), and **reset a user's latest reading day** so they have to read and take the quiz again, with that day's points reversed exactly. Lowering points by hand is unfair because it does not undo what the day originally gave, which can make the competition unwinnable for others.
6. **Android floating nav:** switching tabs has no slide animation (only the selected pill's width springs), and the requested horizontal swiping between tabs was not added.

# ABSOLUTE RULES

- **The repository is the source of truth.** Inspect → determine state → do the next unfinished step → commit → push → verify → continue. Never stop merely because part of this is done. Never ask the operator anything; decide, document the assumption in `BUILD_STATE.json` notes, and continue.
- **ONE ITEM AT A TIME, strictly in order.** The items below are ordered by importance and criticality. Never start an item until the previous one has `"status": "done"`, its commit is pushed and the push verified (`gate` in `BUILD_STATE.json`). Work inside one item only on the sub-steps listed for it; Item 2 is a *related group* (its sub-steps share the completion flow) and Item 4 pairs the reading-time view with the day reset (they share the same data). Nothing else may be combined.
- **COMMIT AND PUSH BEFORE EVERY NEXT STEP.** Per-step protocol: perform step → validate → update `BUILD_STATE.json` → commit → push → verify the push → only then the next step. Commit and push after every sub-step. Builders often die before committing.
- **Never reset `BUILD_STATE.json`.** Append `fixes_oct15_v1` (items `f1` to `f6`, all `"pending"`, with `gate`) in each repo you touch, commit and push the seed first. Never delete earlier sections or redo tasks marked `"done"` with a commit sha.
- **NEVER RESET, WIPE OR DELETE REAL PRODUCTION DATA.** No End Testing Phase & Reset (it is disabled for the live programme; keep it disabled), no deleting users, no editing real users' points, progress or notes. Production access is **read-only** except for the explicitly named additive migrations, applied only after preview tests pass. Test on a Pages preview with test users you create and delete yourself.
- **Frozen backend contract:** backend changes are additive and backward-compatible. Older Android builds are in users' hands and must keep working (see Item 2 for the legacy rule).
- **No emojis** in UI or copy. Never mention the admin area in user-facing text, release notes or the changelog.
- **Credentials** are placeholders; never print, log or commit them. Never touch, print or store the admin password or its hash.

# TASK LIST (under `fixes_oct15_v1`; strictly in order, each gated on the previous being done and pushed)

- f0: Clone both repos, read `BUILD_STATE.json` (including `ops_batch_oct14_v1`), seed `fixes_oct15_v1` in each repo with `gate`, commit and push.
- f1: Item 1 — website data audit, root cause, fix, equality proof against the app's data.
- f2: Item 2 — reading completion, offline timer, 5-minute minimum: 2a backend, 2b Android (then release), 2c website.
- f3: Item 3 — website nav contrast fix plus automated contrast checks in both themes.
- f4: Item 4 — admin reading-time view and reset latest day (paired), backend then Android then website.
- f5: Item 5 — admin Observation/Question notes view, honest wording and one-time notice, release.
- f6: Item 6 — Android nav slide animation and swipe between tabs, final release; verify every item once more against production; set `fixes_oct15_v1.work_complete` to `true` in each repo and push.

- f7: Item 7 — Profile pictures: 7a backend, 7b Android app (release), 7c website, 7d admin moderation.


## Session 7 (2026-10-15) — EXTEND/UPDATE: Fixes Batch 2 (Oct 15): Blank Website, Missing Chapter Bug, Continuous Reader Rebuild, Site Parity with VerseWell, Updater, Public Observations and Questions, Announcements, UI Refinement

Full instruction received from operator:
- Repositories: Backend and website (vjumbo264/forgehouse50), Android app (vjumbo264/forgebuild, apps/forgehouse50), Scripture source (VerseWell, vjumbo264/versewell).
- Rules: One item at a time strictly in order (g0 to g10). Commit and push before every next step. Never reset BUILD_STATE.json, never delete production data, frozen backend contract, no emojis in UI, never mention admin area in user-facing text.
- Items:
  * Item 1: Blank website fix (live diagnosis, root cause fix, self-healing service worker, boot guard, post-deploy smoke test).
  * Item 2: Reported blocker: chapter 6 missing after chapter 5 (diagnose, fix, regression/schedule tests, bundle audit).
  * Item 3: Reader rebuild: 3a timer correctness and persistence, 3b layout: full-height reading behind floating bars, continuous scroll, progress bar, chapter X of Y, 3c persistence across menus and app exits, 3d compact top panel, less prominent chapter intro, encouragement messages (5-30 min), release.
  * Item 4: Verse-level headings fixed at source (VerseWell or backend/client fallback) and in both clients.
  * Item 5: Website parity: VerseWell only (remove public-domain Bible API), downloaded-only switcher, reader parity, timer, profile pictures, nav contrast.
  * Item 6: Updater: keep downloaded APK, Profile update row status, hideable download, wavy progress.
  * Item 7: Support screen message (Help us get ForgeHouse 50 on Play Store).
  * Item 8: Observation and Question notes: 8a premium UI with pickers, 8b community sharing with anonymity, admin replies, WhatsApp formatting copy.
  * Item 9: Announcements: backend, admin UI, client cards and history.
  * Item 10: UI refinement pass across all touched screens; release.
