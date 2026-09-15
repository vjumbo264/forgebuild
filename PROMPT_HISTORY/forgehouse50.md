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
