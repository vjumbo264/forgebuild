# PROMPT HISTORY — cc-studio (append-only)

## 2026-11-05 — Operator instruction (EXTEND / UPDATE, after cc-studio-v1)
"""
# MOTIONSALT CC STUDIO: REPAIR PROMPT (the app is a non-working shell)

You are a stateless builder AI. You have no memory of earlier sessions. Everything you need is in this prompt and in the repositories named below. Another AI may have worked before you and died midway. Read BUILD_STATE.json first and continue from where it stopped.

---

## STEP 0: CREDENTIALS (read first)
GITHUB_PAT = [CONFIGURED_AT_RUNTIME]

## 1. ROLE AND AUTONOMY
Full autonomy over the implementation. Every control Victor can see must actually work.

## 2. BACKGROUND
Victor (brand MOTIONSALT, Nigeria) is a solo motion designer and AMV editor. He edits in Keyflaw (.kfpack). MOTIONSALT CC Studio is a fully offline Android app to fit a colour look as a 3D LUT, tune it with adjustments, and export an importable .kfpack straight to Downloads.

## 3. THE PROBLEM (Victor tested the installed APK)
1. Cannot add any frame to a pair.
2. Cannot delete projects.
3. View mode buttons don't work.
4. Cannot export.
5. App never asks for permissions.
6. Cannot add effects.

## 4. HARD RULES FOR THIS REPAIR
- No stubs, ever.
- 'Done' means exercised by automated tests.
- Binding UI structure (section 6) stays as specified.
- Keep it offline: zero network permissions.

## 7. REPAIR QUEUE
R1: Honest audit
R2: Verification harness
R3: Delete projects
R4: Add frames to pairs
R5: Permissions
R6: Add effects and the stack editor
R7: View modes and the viewer
R8: Fit stage and Export stage, end to end
R9: Release and proof
"""


---
## Date: 2026-11-05
### REPAIR PROMPT 2

# MOTIONSALT CC STUDIO: REPAIR PROMPT 2 (navigation works, but nothing actually does anything)

You are a stateless builder AI. You have no memory of earlier sessions. Everything you need is in this prompt and in the repositories named below. Another AI may have worked before you and died midway. Read BUILD_STATE.json first and continue from where it stopped.

---

## STEP 0: CREDENTIALS (read first)

```
GITHUB_PAT = [REDACTED_GITHUB_PAT]
```

Use it to push to GitHub, run Actions and create releases. Never commit it, never write it into BUILD_STATE.json (secret NAMES only), never print it in logs, release notes, docs or app source. The app itself contains no credentials and must keep **no INTERNET permission**. If the value is still a placeholder, do all the work that does not need it first, and ask Victor for it only if the work genuinely cannot continue.

---

## 1. ROLE AND AUTONOMY

You have full autonomy over the implementation. Victor cannot debug on his phone: **he can only judge the app by installing the APK and using it**. The only acceptable result is an app where every visible control changes what he sees.

Rules for the whole session:
- **Do not stop to ask Victor anything** unless the work genuinely cannot continue. When something is ambiguous, pick the most sensible default, proceed, and record the assumption in BUILD_STATE.json `decisions`.
- "Cannot continue" means truly stuck. Even then, do all other possible work first, record the blocker, mark that item `blocked`, move on, and report it at the end in one short message.
- Difficulty or ambiguity alone is never a reason to skip or stop.

---

## 2. BACKGROUND

Victor (brand **MOTIONSALT**, Nigeria) is a solo motion designer and AMV editor. He works entirely from an Android phone; everything is built in the cloud (GitHub Actions through ForgeBuild). Free tiers only.

He edits in **Keyflaw**, a video editing app whose effects are shader packs (`.kfpack`, a ZIP of `suite.json` plus `effects/<ID>/effect.json` plus `shader.glsl`). The pack scheme and validator are in the **keyflaw-effect-builder `SKILL.md`** that Victor attaches to your session; it is the authority on the pack format.

**MOTIONSALT CC Studio** is a **fully offline Android app**. He picks a base (ungraded) frame and a target (graded) frame, optionally several pairs, the app fits a colour look as a 3D LUT on the phone, he tunes it with a stack of adjustments while comparing before/after, and exports an importable `.kfpack` into a folder in Downloads. No GitHub, Telegram, server or internet at runtime.

Where things live:
- App: `vjumbo264/forgebuild` under `apps/<slug>/` (read `app_repo_path` in `vjumbo264/lut-extractor/BUILD_STATE.json`; the suggested slug was `cc-studio`). Follow ForgeBuild's README, BUILD_STATE.json and engine rules (single repo, `engine/`, `apps/<slug>/`, checkpointed commits, Actions builds a signed release APK tagged `<slug>-vN`). Material 3 and Material Symbols only, no emoji, lightweight dependencies, minSdk 26.
- Modules: `app` (Android UI), `core` (pure Kotlin/JVM: colour math, solver, LUT/Hald, pack builder/merger/validator, effect stack model), effect library in `src/main/assets/effects/`.
- `vjumbo264/lut-extractor`: queue source of truth `BUILD_STATE.json`, `docs/` (`BUILD_PROMPT.md` original spec, `REPAIR_PROMPT.md` first repair, `AUDIT.md`, `KEYFLAW_EFFECTS.md`, `verification/`), legacy baseline solver.

If those docs exist they are earlier specs; **this prompt overrides them where they differ**. Everything you need from them is repeated below.

History: the first build was a shell; a first repair round made navigation and the screens work. This is the second repair round.

---

## 3. THE PROBLEM (Victor tested the installed APK; evidence)

1. **Importing frames is broken.** The file picker does not work: he cannot import an image from it. Even when an image is imported (other routes), it **does not appear until he leaves the project and comes back**. That means the UI is not observing state: bad UX.
2. **Nothing he does to the look changes anything.** He says: "The LUT extraction doesn't work. None of the functions are working. I can navigate, I can add effects, but nothing is actually working. If I tune them and do anything, it doesn't change anything. Even the LUT, I don't see any difference. It doesn't do anything at all, just as if nothing happened." So: sliders, adding effects, enabling/disabling, LUT intensity, and the fit itself produce no visible change in the viewer.
3. **Effects are missing from the picker.** He cannot find the **tritone** effect or any of the **tone** effects anywhere in the Add effect picker.
4. **Permissions are wrong.** The app only really needs: media (photos/videos), the file picker, and camera. It **does not ask for these when he uses them**; he has to go to the app's system settings and grant them by hand. Also, the app's permissions screen shows other entries as "not granted" that are **not user-grantable permissions at all** (they are normal scopes apps simply have, like using the system file picker, saving through MediaStore, normal install-time permissions). Those entries are false alarms and confusing.

---

## 4. HARD RULES

- **No stubs, ever.** A visible control either works or is not shown. No `TODO`, `stub`, `placeholder`, `not implemented`, empty `onClick = {}`.
- **"Done" means a test proved the visible outcome.** For anything that should change what the user sees, the test must compare **actual pixels** (see S1). A state change, a log line or a passing compile is not proof.
- The binding UI structure (section 6) stays. Do **not** do visual polish; that is a separate task in a fresh session. If you reach a polish step, stop and report.
- Offline: the manifest must not declare INTERNET; no networking libraries.

---

## 5. STATE, COMMITS AND RESUME

Use `lut-extractor/BUILD_STATE.json` as the source of truth. First action after reading it: **correct it honestly.** Anything previously `done` that matches the symptoms above goes back to `in_progress` with a note. Add a `repair2_queue` array (items S1 to S8 below) with the same fields as `queue`.

