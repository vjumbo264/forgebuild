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



---
# 2026-11-05: RESET PROMPT (stop reimplementing Keyflaw; reuse it; prove every slice)

You are a stateless builder AI. You have no memory of earlier sessions. Everything you need is in this prompt and in the repositories named below. Another AI may have worked before you and died midway. Read BUILD_STATE.json first and continue from where it stopped.

**Context in one paragraph:** two earlier builders produced an app that looks complete but does not work, and the owner has now lost hours testing it. This prompt changes the strategy. Read sections 3 and 4 carefully before touching code: they say what went wrong and what is now forbidden.

---

## STEP 0: CREDENTIALS (read first)
[REDACTED PAT]

Use it to push to GitHub, run Actions, create releases and, if needed, make a repo private (section 6). Never commit it, never write it into BUILD_STATE.json (secret NAMES only), never print it in logs, release notes, docs or app source. The app itself contains no credentials and must keep **no INTERNET permission**. If the value is still a placeholder, do all the work that does not need it first, and ask Victor for it only if the work genuinely cannot continue.

---

## 1. ROLE AND AUTONOMY

You have full autonomy over implementation. Victor cannot debug on his phone and has run out of patience: **he judges the app only by installing the APK and using it**, so it must visibly work.

Rules for the whole session:
- **Do not stop to ask Victor anything** unless the work genuinely cannot continue. When something is ambiguous, pick the most sensible default, proceed, and record the assumption in BUILD_STATE.json `decisions`.
- "Cannot continue" means truly stuck. Even then, do all other possible work first, record the blocker, mark that item `blocked`, move on, and report it at the end in one short message.
- Difficulty or ambiguity alone is never a reason to skip or stop.

---

## 2. BACKGROUND

Victor (brand **MOTIONSALT**, Nigeria) is a solo motion designer and AMV editor. He works entirely from an Android phone; everything is built in the cloud (GitHub Actions through ForgeBuild). Free tiers only.

He edits in **Keyflaw**, a video editing app. Keyflaw's effects are shader based and can be extended with `.kfpack` files (a ZIP of `suite.json` plus `effects/<ID>/effect.json` plus `shader.glsl`). The pack scheme, GLSL rules, parameter conventions (SCALAR sliders, BOOL toggles, CHOICE dropdowns, COLOR params, groups, multi-pass `passes`, `expandBounds`, native motion trails) and a validator are in the **keyflaw-effect-builder `SKILL.md`** that Victor attaches to your session. It is the authority on the pack format.

**MOTIONSALT CC Studio** is a **fully offline Android app**: he picks a base (ungraded) frame and a target (graded) frame (optionally several pairs), the app fits a colour look as a 3D LUT on the phone, he tunes it with a stack of effects while comparing before/after, and exports an importable `.kfpack` into a folder in Downloads. No GitHub, Telegram, server or internet at runtime.

Where things live:
- App: `vjumbo264/forgebuild` under `apps/<slug>/` (read `app_repo_path` in `vjumbo264/lut-extractor/BUILD_STATE.json`; suggested slug was `cc-studio`). Follow ForgeBuild's README, BUILD_STATE.json and engine rules (single repo, `engine/`, `apps/<slug>/`, checkpointed commits, Actions builds a signed release APK tagged `<slug>-vN`). Material 3 and Material Symbols only, no emoji, lightweight dependencies, minSdk 26.
- Modules: `app` (Android UI), `core` (pure Kotlin/JVM: colour math, solver, LUT/Hald, pack builder/merger/validator, stack model), effect library in `src/main/assets/effects/`.
- `vjumbo264/lut-extractor`: queue source of truth `BUILD_STATE.json`, `docs/` (earlier specs and audits: `BUILD_PROMPT.md`, `REPAIR_PROMPT.md`, `REPAIR_PROMPT_2.md`, `AUDIT*.md`, `KEYFLAW_EFFECTS.md`, `verification/`), legacy baseline solver.

Earlier docs are older specs; **this prompt overrides them where they differ**. What you need from them is repeated below.

---

## 3. WHAT VICTOR REPORTS (evidence, after two repair rounds)

His words, lightly cleaned: after fitting, "I don't see any difference, and even the **result frame is just copying the target frame**. It's not really applying any tool." When he enables the LUT tool in the tuning area "it just looks completely different from what it's supposed to be doing." "Many of the effects are not working at all." "The app is **extremely laggy**." "**Color picking is not there**; I have to manually type in the colour value." "**Sharpen and unsharp mask: nothing is working.**" "The LUT is completely broken." And his instruction: "**Keyflaw is already a complete implementation. Just copy from it: the effects, the routines, the controls. Use it exactly.** Why can't the tool do what Keyflaw does, without being laggy on my device?"

---

## 4. WHAT WENT WRONG AND THE NEW RULES (binding)

Diagnosis (verify each in T0, but plan on it):
1. **The "Recreated" frame equals the target.** A result that is identical to the target means the app is showing or copying the target image instead of applying a fitted LUT to the base. That is faking, whether by accident or not.
2. **The builders reimplemented every effect by hand** from guesswork instead of using Keyflaw's own implementation. Hand-written sharpen, unsharp mask, glow and the rest were never compared with Keyflaw's real behaviour, and many do nothing.
3. **The LUT path is wrong** (colour order, texel-centre mapping, colour space, or never bound), so enabling it gives a different, wrong look.
4. **Rendering is slow**, probably from work done per frame on the CPU or main thread, full-resolution textures, repeated uploads or readbacks, and UI recomposition on every slider move.
5. **Earlier verification did not catch any of this** because tests checked state and clicks, not pixels, and nothing checked that the result does not depend on the target image.

New rules:
- **R-A: Keyflaw is the reference implementation. Copy, do not reimplement.** For every effect that exists in Keyflaw, use Keyflaw's own shader, passes, parameters, ranges, defaults and names, taken from the Keyflaw app itself (section 5, T1). Hand-written effects are allowed **only** for effects Keyflaw does not have, and each must be listed in `docs/NOT_IN_KEYFLAW.md` with the reason.
- **R-B: The app is a host for Keyflaw effects.** Build one GLES renderer that implements Keyflaw's own effect-host contract (inputs, uniforms, resolution/time values, multi-pass, expandBounds) so effect folders run **unchanged**. Adding any effect, including a user `.kfpack`, is dropping in its folder; the renderer and the UI are driven by each effect's `effect.json`.
- **R-C: Verification is pixel-based and target-blind.** See T3 and T4: a test must prove the recreated output does not depend on the target pixels once the LUT has been fitted, and must compare the GPU output with a CPU reference.
- **R-D: No stubs, ever.** No `TODO`, `stub`, `placeholder`, `not implemented`, empty `onClick = {}`, fake data. A control either works or is not shown.
- **R-E: Vertical slices with early APKs.** Work in the order of section 7 and **publish a pre-release APK after every slice** (tag `<slug>-vN-rcK`) so Victor can test early. Do not wait for the end. Do not message him in between; the releases are the signal.
- **R-F: Do not do visual polish.** That is a separate task in a fresh session. If you reach a polish step, stop and report.
- **R-G: Offline.** The manifest must not declare INTERNET; no networking libraries.

---

## 5. KEYFLAW HOST SPEC AND PERFORMANCE (binding)

Keyflaw app download reference stored as private release asset on vjumbo264/lut-extractor: tag keyflaw-ref-v1.4.9.

**LUT convention (get this exactly right once, document it, test it).** The fitted transform lives in `core` in a documented colour space; the same space is used when sampling it. The `.cube` file has red varying fastest. On the GPU use a 3D texture (GLES 3.0) or the 2D layout Keyflaw itself uses for its own LUT effect (prefer Keyflaw's way if it has a LUT effect: copy it); sample with half-texel correction `uv = c * (N-1)/N + 0.5/N`, trilinear filtering, clamp to edge; intensity is a mix between input and LUT output. Tests: identity LUT reproduces the input within 1/255; a channel-swap LUT and an invert LUT produce exactly the expected pixels; a fitted LUT applied on the GPU matches the CPU application in `core` within 1.5/255 on the same image.

**Performance budget (the app is currently extremely laggy):**
- One persistent GL context and renderer; effects compiled once and cached; never recompile on a slider move.
- **Preview resolution cap:** render the preview at the viewer's pixel size (cap the long edge at about 1280 by default, configurable in Settings); keep full-resolution copies only for the fit and for 1:1 zoom tiles.
- Framebuffer-object ping-pong between passes; **no CPU readbacks and no bitmap uploads per frame**; textures uploaded once per frame image.
- Slider drags update **uniform values only**, in the renderer, at display rate; persisting the tune state is **debounced** (for example 300 ms after the last change); the UI state for a dragging slider must not trigger recomposition of the whole stack list or the viewer container.
- Scopes (histogram, RGB parade) are computed on the GPU or from a small downsampled image and throttled (for example a few times per second); never from the full-resolution frame on the CPU.
- The fit, thumbnails, image decoding and file work are off the main thread; no per-frame allocations in hot paths; no logging in hot paths.
- Target: dragging any slider keeps the viewer at 30 fps or better on a mid-range phone with a 1080p frame and a stack of five effects. You cannot measure on a phone: add a frame-time readout (average ms and fps) to Diagnostics (T8) so Victor can report real numbers, and keep the design rules above.

---

## 6. APP STRUCTURE (BINDING; carried over)

**Navigation:** bottom bar Projects | Packs | Library | Settings. In a project, a stage bar Source | Fit | Tune | Export (locked until the prerequisite is met: Fit needs one complete pair; Tune needs a finished fit; Export needs a tune state). State is saved continuously. Every list and screen reads from observable state: any change (image added, effect added, parameter edited, project deleted) shows immediately.

**Source stage:** pairs list, Base | Target thumbnails (tap to replace), swap, crop/ignore-region editor, remove, mode switch "Same frame" / "Different scenes". "Add pair" sheet: Gallery, Video frame, Camera, Files; images copied into the project on selection, decoded safely (EXIF rotation, HEIC/WebP/PNG/JPEG), thumbnail appears at once. "Fit look" enabled only with a complete pair.

**Fit stage:** the `core` solver in a foreground service with progress and Cancel. The result viewer tabs: Before (base), Target, **Recreated (the base with the fitted LUT applied by the real renderer)**, Heatmap (Recreated vs Target). Metrics (mean and 95th-percentile delta E on held-out data) must beat the identity baseline.

**Tune stage:** viewer (pinch zoom, pan, double tap fit/1:1, fullscreen), view-mode chips (Result with press-and-hold Before, Wipe with orientation flip, Side by side, Top/bottom, Vs Target, Difference with gain, Channels R/G/B/Luma, Scopes), frame strip, stack panel (bottom sheet half to full height). Stack rows: drag handle, enable switch, name, expand chevron, overflow (duplicate, reset, delete); the first row is always the fitted LUT with an Intensity slider (can be disabled and moved, not deleted); undo/redo covers every change; header "Stack (N)", Add effect, Reset all, "Save as preset". **Controls come from each effect's `effect.json`:** SCALAR = slider plus numeric field (double tap resets), BOOL = switch, CHOICE = dropdown, **COLOR = a real colour picker** (see T6), groups = collapsible sections.

**Add effect picker:** full screen, search (names and keywords), categories taken from Keyflaw's own categories, restricted to the colour/tone/toning/detail/look scope: colour correction, tone, toning (tritone, duotone, gradient map, split toning), detail (sharpen, unsharp mask, clarity), look (glow, bloom/halation, haze/flare, vignette, grain). Show every category always. Scope excludes transitions, text, distortions, glitches, masks, generators, motion/transform effects.

**Export stage:** three pack modes (Add to existing / Create new / Merge an imported pack), default MOTIONSALT pack pinned first, effect name, per-item "Expose controls in Keyflaw", collision dialog (Replace / Keep both with a new id / Cancel), "Also save" (Cube, Hald on by default; proof PNG, tune-state JSON off), save path default `Downloads/MOTIONSALT/CC Studio/` with Change folder, success sheet (Share, Open folder, Open in Keyflaw). The exported effect is built from **the same effect folders the preview ran** (byte-identical shaders) plus the fitted LUT.

**Permissions (final design):** requested only at the moment of use, with a one-line in-app explanation first, then the system prompt; never at launch; if permanently denied an "Open app settings" action; a denial never blocks the core flow.

**Data model:** Project JSON in app-private storage. Tune state JSON: `{ "version": 1, "stack": [ { "effectId", "instanceId", "enabled", "expose", "params": { "<paramId>": value } } ] }`, ordered, first item the LUT. Preview and export both read exactly this.

**Extracted Keyflaw files stay private.** Checked and verified private repos.

---

## 7. STATE, COMMITS AND RESUME

Use `lut-extractor/BUILD_STATE.json` as the source of truth. Add a `reset_queue` array (T0 to T10). Commit and push after every unit of work.

---

## 8. THE QUEUE
- T0: Honest reset and audit (first)
- T1: Study Keyflaw's own implementation
- T2: Keyflaw effect host renderer (slice 1, part 1)
- T3: Fit produces a real LUT and Recreated is honest (slice 1, part 2)
- T4: The effect library is Keyflaw's own (slice 2, part 1)
- T5: Stack editor and view modes on the new host (slice 2, part 2)
- T6: Colour picker and controls
- T7: Performance pass
- T8: Diagnostics screen
- T9: Export from the same effect folders
- T10: Release and proof (last)
