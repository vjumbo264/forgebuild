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


## 2026-11-05 — Operator pivot: direct colour match, no LUT

# MOTIONSALT CC STUDIO: PIVOT PROMPT (no LUT: match the colour directly; copy Keyflaw's real effects; prove it on a real pair)

You are a stateless builder AI. You have no memory of earlier sessions. Everything you need is in this prompt and in the repositories named below. Another AI may have worked before you and died midway. Read BUILD_STATE.json first and continue from where it stopped.

**Context in one paragraph:** three earlier rounds produced an app that looks finished but does not work. The owner has lost many hours testing APKs and is rightly angry. This prompt changes the strategy: no LUT in the pipeline, direct colour matching baked into the exported effect, Keyflaw's own effect code copied exactly, and a hard rule that **you look at your own output images before calling anything done**. Read sections 3, 4 and 5 before touching code.

---

## STEP 0: CREDENTIALS (read first)

```
GITHUB_PAT = <REDACTED: supplied at execution only>
```

Use it to push to GitHub, run Actions, create releases and, if needed, make a repo private (section 6). Never commit it, never write it into BUILD_STATE.json (secret NAMES only), never print it in logs, release notes, docs or app source. The app itself contains no credentials and keeps **no INTERNET permission**. If the value is still a placeholder, do all work that does not need it first, and ask Victor for it only if work genuinely cannot continue.

---

## 1. ROLE AND AUTONOMY

You have full autonomy over implementation, except where this prompt binds you (sections 4 to 7). Victor cannot debug on his phone: **he judges the app only by installing the APK and using it**.

- **Do not stop to ask Victor anything** unless work genuinely cannot continue. When ambiguous, pick the most sensible default, proceed, and record it in BUILD_STATE.json `decisions`.
- "Cannot continue" means truly stuck (missing secret, unreachable dependency). Even then, do all other possible work first, mark that item `blocked`, move on, report it at the end in one short message.
- Difficulty or ambiguity alone is never a reason to skip or stop.

---

## 2. BACKGROUND

Victor (brand **MOTIONSALT**, Nigeria) is a solo motion designer and AMV editor. He works entirely from an Android phone; everything builds in the cloud (GitHub Actions through ForgeBuild). Free tiers only.

He edits in **Keyflaw**, a video editing app whose effects are shader based and extendable with `.kfpack` files (a ZIP of `suite.json` plus `effects/<ID>/effect.json` plus `shader.glsl`). The pack scheme, GLSL rules, parameter conventions (SCALAR, BOOL, CHOICE, COLOR, groups, multi-pass `passes`, `expandBounds`, native motion trails) and a validator/packer script are in the **keyflaw-effect-builder `SKILL.md`** that Victor attaches to your session. It is the authority on the pack format. If it is not attached, record that and derive the format from the Keyflaw app and any `.kfpack` samples you can find.

He often finds a stunning colour grade in someone else's edit and wants to copy it. **MOTIONSALT CC Studio** is a **fully offline Android app** for that: he picks a base (ungraded) frame and a target (graded) frame (optionally several pairs), the app **matches the colour**, he tunes the result with a stack of Keyflaw-style effects while comparing against the reference, and he exports an importable `.kfpack` into Downloads. No GitHub, Telegram, server or internet at runtime. **What he needs from the app is the `.kfpack`. He does not need LUT files.**

Where things live:
- App: `vjumbo264/forgebuild` under `apps/<slug>/` (read `app_repo_path` in `vjumbo264/lut-extractor/BUILD_STATE.json`; suggested slug `cc-studio`). Follow ForgeBuild's README, BUILD_STATE.json and engine rules (single repo, `engine/`, `apps/<slug>/`, checkpointed commits, Actions builds a signed release APK tagged `<slug>-vN`). Material 3 and Material Symbols only, no emoji, lightweight dependencies, minSdk 26.
- Modules: `app` (Android UI), `core` (pure Kotlin/JVM: colour math, match solver, pack builder/merger/validator, stack model), effect library in `src/main/assets/effects/`.
- `vjumbo264/lut-extractor`: queue source of truth `BUILD_STATE.json`, `docs/` (older specs and audits: `BUILD_PROMPT.md`, `REPAIR_PROMPT*.md`, `RESET_PROMPT.md`, `AUDIT*.md`, `KEYFLAW_HOST.md`, `verification/`).

Earlier docs are older specs; **this prompt overrides them where they differ**. What you need from them is repeated below.

---

## 3. EVIDENCE (Victor's report plus five screenshots of the installed APK; attach them to your session if he has not)

Screenshots, in order: (1) Tune stage, (2) Fit stage with the Heatmap tab, (3) Fit with the Recreated tab, (4) Fit with the Target tab, (5) Fit with the Before tab. They show a project "Gojo" (look name `GOJO_CC`) with an anime frame: a boy with closed eyes, green-and-black chequered haori, red-brown hair, blurred forest background, letterbox bars top and bottom of the frame. Facts read from them:

1. **Tune viewer is upside down.** The frame in the Tune viewer is flipped vertically (face upside down). The Fit-stage images are right way up, so the renderer has a Y-flip bug. Likely a GL texture origin mismatch (bitmap top-left vs GL bottom-left) or a flipped projection in the Tune renderer only.
2. **The fit is garbage but is labelled "Accurate match".** Fit shows Mean ΔE **38.26** and 95th-percentile ΔE **66.56** with the caption "Accurate match" and "Worst-case bounds". ΔE 38 is a terrible match (a good match is below about 5), so the caption is hardcoded or computed wrongly. The planner measured the same pair offline: applying **no correction at all** gives mean ΔE about 10.1 and p95 24.5, so the app's 38 is **worse than doing nothing**, and a working no-LUT match reaches mean 4.9 (section 5).
3. **Recreated is a posterised olive/yellow mess**: flat yellow-brown with blotchy grey patches, only the red hair keeps any colour. It does not resemble the Target at all.
4. **Heatmap is solid red** over the whole frame (every pixel saturated at maximum error).
5. **The Target** (screenshot 4) differs from **Before** (screenshot 5) by a clear grade: the chequered cloth turns from green to **blue/teal** with darker blue squares, hair gets a more saturated red-brown, the background stays green-yellow and gets more contrast and saturation, skin stays warm. A strong grade, including a **hue-specific shift** (cloth green to blue while the forest stays green). A global curve or matrix cannot do that alone: the match needs colour-selective adjustment (section 5, step 4).
6. **The Tune stack** shows "3D LUT Grade" (Look Intensity 0.47, drawn as an ordinary progress-style slider) and "Diffusion Glow". Stack (2). The view-mode chips are Result, Wipe, Side by side, Top/bottom, Vs Target and more.
7. Both frames contain **black letterbox bars** that must be excluded from fitting (crop/ignore regions) and are probably also corrupting the heatmap.

Victor's words, lightly cleaned:
- "Maybe don't use LUT. Maybe the LUT implementation type is why it fails. **Just run a match** and analyze the frames to get the exact colour. I need a KF pack, not any kind of LUT."
- "The **sliders lag because they wait until I let go, then update**. I want the kind of **scrolling sliders editing apps use**, not progress-bar sliders."
- "**The unsharp mask is not like Keyflaw's. It is ugly. Copy the exact unsharp mask that the original app uses.** **Also the glow** is not like Keyflaw's: fix that too."
- "**The view modes are messed up. Vs Target is viewing against itself.** I want it against the reference so I can see how different it is."
- "The exported file comes out as a **.zip, not a .kfpack**, and when I try to import it, Keyflaw says it **cannot find any valid effects inside**."
- Earlier: "Keyflaw is already a complete implementation. Copy the effects, the routines, the controls. Use it exactly." and "colour picking is not there; I have to type the colour value."

---

## 4. NEW RULES (binding)

- **R-A: No LUT anywhere in the pipeline.** No 3D LUT fit, no LUT texture, no `.cube` or Hald generation, no "3D LUT Grade" effect, no Cube/Hald export checkboxes. Remove them. (Section 5 explains the replacement.) The only colour method in this project is the one in section 5.
- **R-B: Keyflaw is the reference implementation. Copy, do not reimplement.** For every effect Keyflaw has (**including unsharp mask, sharpen and glow**), use Keyflaw's own shader, passes, parameters, ranges, defaults and names, taken from the Keyflaw app itself. Hand-written effects are allowed only for effects Keyflaw does not have, each listed in `docs/NOT_IN_KEYFLAW.md`. Where an effect is native-only and cannot be copied, **match it by pixel comparison** against real Keyflaw output (section 7, T3).
- **R-C: Look at your own output.** For every item that produces an image, render it, save the PNG, **open and view it** (you can read images), and write in `notes` what you saw. Orientation, colours and look must be right by eye as well as by number. "The test passed" is not enough when the image is upside down or posterised. Tests alone are how three rounds shipped nothing.
- **R-D: Real-pair regression.** Victor attaches `gojo_before.png` and `gojo_target.png` (already cropped, 560x259) and `reference_match.py` (the working prototype, see section 5); use them. If they are missing, build a regression pair from screenshots 5 (Before) and 4 (Target): crop the inner frame between the black bars (approximately x 76 to 644, y 748 to 1010 in the 720x1600 screenshots; check by eye) and keep both as `core` test resources (they are small). If Victor attached the original frames, use those instead. Every colour-match claim is measured on this pair.
- **R-E: No stubs, no hardcoded results.** No `TODO`, `stub`, `placeholder`, `not implemented`, empty `onClick = {}`, no hardcoded labels such as "Accurate match". Labels are computed from real numbers by documented thresholds.
- **R-F: Vertical slices with early APKs.** Work in the order of section 7 and publish a pre-release APK after every slice (tag `<slug>-vN-rcK`). Victor tests early. Do not message him in between.
- **R-G: No visual polish.** Separate task for a fresh session. If you reach a polish step, stop and report. Structure, behaviour and correctness only.
- **R-H: Offline.** Manifest must not declare INTERNET; no networking libraries.

---

## 5. THE COLOUR MATCH (replaces the LUT fit)

Goal: from base and target frame(s), compute a **parametric colour correction**, expressed as **shader maths with parameters** (not a lookup file), that makes the base look like the target, and export it as one Keyflaw effect (plus whatever stack effects Victor adds). The same maths runs in three places and **must be the same code logic**: the `core` CPU reference (for fitting and tests), the preview shader (GPU), and the exported `shader.glsl`. Share the shader source: the preview and the export use byte-identical text.

**A working reference exists and is proven.** `reference_match.py` (Python, attached by Victor) implements exactly the stages below and was run on the Gojo pair; the Recreated image was viewed and looks like the Target (cloth green to blue, hair red-brown, forest olive cast; remaining error sits at line edges, where the Target is sharpened, and in small local contrast differences; those are handled by the stack effects such as unsharp mask, not by the colour model). **Port it faithfully to Kotlin in `core` first** (same stages, same constants, same fitting order), then check it reproduces the reference numbers within 10 percent on the same pair. Only afterwards may you improve it, and only if you prove a better held-out ΔE; record why. A vertical-gradient term was tried and gave nothing on this pair, so leave it out by default.

Model (stages in this order):
1. **Working space.** Convert once to a documented working space (linear-light RGB or an opponent/LAB-like space) and once back. State which colour space Keyflaw hands the effect (check the app/skill) and match it.
2. **Per-channel monotone tone curves** (about 32 control points, monotone interpolation) for overall contrast, black/white point, per-channel gamma and colour cast.
3. **3x3 matrix plus offset** for saturation, channel mixing and white balance.
4. **Colour-selective stage** (needed: see evidence 5). Three smooth periodic curves keyed on **hue** (hue-vs-hue shift, hue-vs-saturation, hue-vs-luminance, about 24 points each, smoothly interpolated, falling to zero for near-neutral colours so greys stay clean), plus an optional luminance-keyed colour balance (shadows/mids/highlights). This is what moves the cloth from green to blue while the forest stays green.
Fitting data:
- "Same frame" pairs: use pixel correspondences directly (after cropping bars and aligning the two crops; the frames are often "a little bit off": on the Gojo pair the Target is about 0.7 percent narrower than the Before, so alignment is **required**: the reference uses an affine ECC alignment on blurred gradient-magnitude maps, which is independent of colour; implement an equivalent (a small own coarse-to-fine affine alignment on edge maps in Kotlin is fine, avoid heavy libraries), warp the target onto the base, then drop a few border pixels, and sample from the common area only). Robust loss (Huber) so a few moving or edited regions (caption, bars, watermark) do not drag the fit. Respect crop/ignore regions from the Source stage.
- "Different scenes" pairs: match colour distributions (per channel and joint, in the working space) instead of pixels.
- Several pairs: fit one model over all, weighting pairs equally.
- Hold out samples for validation; report ΔE (CIEDE2000, or ΔE76 if you state it) mean and 95th percentile for **identity (no correction)** and for the **fitted match** on the same samples.
**Quality gate (regression pair, CIEDE2000, ignoring bars and a small border).** Reference results to reproduce: identity mean **10.12** / p95 **24.54**; matched mean **4.86** / p95 **11.36**; holdout (right 20 percent of the frame, not used for fitting) mean **4.83** / p95 **9.93**; colour-only (both images Gaussian-blurred, sigma 2, so sharpness differences do not count) identity mean 9.71 / p95 23.97 versus matched mean **3.87** / p95 **8.44**. Your port must reach **mean at most 5.4, p95 at most 12.5 and at least 45 percent below identity** on the plain measurement, and **mean at most 4.3** on the blurred one. If you cannot, do not ship a worse model silently: keep improving, record the numbers, and report them honestly. **Fit screen labels** come from the numbers (for example "Close match" below 6, "Rough match" 6 to 12, "Poor match" above 12) and always show the identity baseline next to the fitted error.

Heatmap: error between Recreated and Target, with a visible scale legend, bars excluded; it must **not** be solid red on a good fit.

Export: one Keyflaw effect (default name = look name) whose shader contains the fitted maths with the fitted numbers as constants or default parameter values, plus (optionally) exposed Keyflaw controls: an overall **Strength** (mix between input and matched), and per-stage toggles. Victor's tuned stack effects follow it in the same effect or as separate effects in the same pack (choose what fits the skill's conventions, record the choice).

---

## 6. APP STRUCTURE (BINDING; carried over, with changes)

**Navigation:** bottom bar Projects | Packs | Library | Settings. In a project: stage bar Source | Fit | Tune | Export (locked until the prerequisite is met: Fit needs one complete pair; Tune needs a finished fit; Export needs a tune state). State saved continuously. Every list and screen reads observable state: any change shows immediately.

**Source stage:** pairs list with Base | Target thumbnails (tap to replace), swap, crop/ignore-region editor (rectangles to exclude; **offer "Auto-detect letterbox bars"** that finds and proposes the black bars, applied to both frames), remove, mode "Same frame" / "Different scenes". "Add pair" sheet: Gallery, Video frame, Camera, Files; images copied into the project, decoded safely (EXIF, HEIC/WebP/PNG/JPEG), thumbnail appears at once. "Fit look" enabled only with a complete pair.

**Fit stage:** the `core` match solver in a foreground service with progress and Cancel. Result viewer tabs: **Before** (base), **Target**, **Recreated** (base through the real renderer with the matched model), **Heatmap**. Metrics from section 5, honest labels.

**Tune stage:** viewer with pinch zoom, pan, double tap fit/1:1, fullscreen; view-mode chips; frame strip; stack panel (bottom sheet, half to full height).
- **Orientation:** every image in every view mode is right way up. Add a test with an asymmetric test image (an "F" glyph with a coloured corner) that asserts the rendered output is not mirrored or flipped, for the viewer, wipe, split modes, thumbnails and exports.
- **View modes, each against the right reference:** **Result** (press and hold anywhere shows the true Before), **Wipe** (draggable divider, flip vertical/horizontal), **Side by side** and **Top/bottom** (Before | After), **Vs Target** (**the processed result against the TARGET frame**: wipe/split toggle; this is the mode that compares with the reference), **Difference** (heatmap of result vs target with a gain control), **Channels** (R, G, B, Luma), **Scopes** (histogram and RGB parade overlay, GPU or downsampled, throttled). The frame strip chooses which frame is shown; for Vs Target and Difference the matching target of the pair is used (for pairs in "Different scenes" mode, show the target frame of the same pair beside it without pretending they align).
- **Stack:** the first row is the **"Colour Match"** effect (the fitted model, with a Strength slider and per-stage toggles; can be disabled and moved, cannot be deleted). Then Victor's added effects. Rows: drag handle, enable switch, name, expand chevron, overflow (duplicate, reset, delete). Undo/redo covers every change. Header "Stack (N)", Add effect, Reset all, "Save as preset".
- **Controls come from each effect's `effect.json`:** SCALAR, BOOL, CHOICE, COLOR, groups. SCALAR uses the **scrubber control** below. COLOR uses a real colour picker (T6).
- **Scrubber control (replaces progress-bar sliders everywhere):** copy the way **Keyflaw itself** presents effect parameters if you can see it (T1). If Keyflaw has no such control, build the standard video-editor adjustment scrubber: the parameter name and its value shown above a **horizontal ruler with tick marks that scrolls under a fixed centre needle** (drag left/right, fling with momentum, ticks snap at round values, fine mode when dragging vertically or with a long press, coarse range for large spans), **double tap resets to default, tap on the value to type it**. **Values update live on every touch move**, not on release.

**Add effect picker:** full screen, search (names and keywords), categories from Keyflaw's own, restricted to colour/tone/look scope: colour correction, tone, toning (tritone, duotone, gradient map, split toning), detail (sharpen, unsharp mask, clarity), look (glow, bloom/halation, haze/flare, vignette, grain). All categories always shown. Excludes transitions, text, distortions, glitches, masks, generators, motion/transform effects.

**Export stage:** three pack modes (Add to existing / Create new / Merge an imported pack), default **MOTIONSALT** pack pinned first, effect name, per-item "Expose controls in Keyflaw", collision dialog (Replace / Keep both with new id / Cancel), "Also save": proof PNG and tune-state JSON only (no Cube, no Hald), save path default `Downloads/MOTIONSALT/CC Studio/` with Change folder, success sheet (Share, Open folder, Open in Keyflaw).

**Permissions (final design; request only at the moment of use, short in-app explanation first, never at launch, "Open app settings" if permanently denied, a denial never blocks the core flow):**
| Trigger | Request | Fallback |
|---|---|---|
| Tap Gallery or Video frame | `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` (API 33+; `READ_EXTERNAL_STORAGE` maxSdk 32; API 34+ also `READ_MEDIA_VISUAL_USER_SELECTED`); ask first, then open the system photo picker either way | picker works without it |
| Tap Camera | `CAMERA` | Gallery or Files |
| First Fit (API 33+) | `POST_NOTIFICATIONS` | progress in-app |
| Settings "Direct folder access" or overwriting a file the app did not create | `MANAGE_EXTERNAL_STORAGE`, optional | per-file picker |
Never request or show as "not granted": the system file picker (SAF), MediaStore Downloads saving on Android 10+, FileProvider, foreground service, `WAKE_LOCK`, install-time normal permissions. `WRITE_EXTERNAL_STORAGE` only with `maxSdkVersion 28`. Settings > Permissions lists only the four rows with real status plus a "No permission needed" info section; refresh on resume. No INTERNET.

**Data model:** Project JSON in app-private storage: id, name, look name, created/updated, pairs[] {id, mode, base {copied file, crop rects}, target {copied file, crop rects}}, fit options, **match model** {version, space, curves, matrix, hue curves, metrics, timestamps}, tune state, export history. Tune state: `{ "version": 1, "stack": [ { "effectId", "instanceId", "enabled", "expose", "params": { "<paramId>": value } } ] }`, ordered, first item the Colour Match. Preview and export both read exactly this.

**Keep extracted Keyflaw files private.** Before committing anything extracted from Keyflaw, make sure `vjumbo264/forgebuild` and `vjumbo264/lut-extractor` are private (set them private through the API if public). Never commit the Keyflaw binary; store a copy only as a release asset on a **private** repo, so later sessions do not depend on the expiring link; record where in BUILD_STATE.json.

Keyflaw app download (temporary link, expires about 12 hours after Victor sent it on 2026-11-05 around 08:12 Lagos time, i.e. roughly 20:00 Lagos time today): `https://cf.linkulu.com/dl/6aec2bebde32c56eaccc624d`. An earlier session may already have stored a copy (see `docs/KEYFLAW_HOST.md` and BUILD_STATE.json); use it if the link is dead.

---

## 7. STATE, COMMITS AND THE QUEUE

Use `lut-extractor/BUILD_STATE.json` as the source of truth. First action after reading it: **correct it honestly**; anything earlier marked `done` that matches section 3 goes back to `in_progress`. Add a `pivot_queue` array (T0 to T12) with `id`, `title`, `status` (`pending | in_progress | done | blocked`), `notes` (what changed, how verified, **what you saw in the output image**). Commit **and push** after every file or small unit of work, never batch. Meaningful commit messages. Push a scaffold plus updated BUILD_STATE.json before real code in each item. Every push leaves the repos understandable from BUILD_STATE.json alone. On start: resume from the first T item not `done`. Save this prompt as `docs/PIVOT_PROMPT.md` in `lut-extractor`.

**Two rules for the whole queue. Read them again at every item:**
1. **Finish one item completely before starting the next.** Never start item N+1 while item N is half done and never hop between unrelated items. Related items (same files, same underlying work) may be done together in one pass; each is still verified individually. Unrelated items are never mixed.
2. **No skimming.** Every item gets full, proper work: understand it, implement it thoroughly, verify it with a test **and by looking at the output image**, and fix what you find before marking it `done`. The last items get the same depth of effort as the first. Write in `notes` what you changed and how it was checked.

### T0: Honest audit
Read the current app. Write `docs/AUDIT4.md`: root cause with file and line for each of evidence points 1 to 4 and 6, why the Fit caption says "Accurate match", why Vs Target compares against itself, why sliders update only on release. Correct BUILD_STATE.json. Make repos private if needed. Acceptance: the audit names a concrete cause for each symptom.

### T1: Study Keyflaw's own implementation (the link expires; do right after T0)
Download the app (section 6) or use the stored copy. Unpack it and find how its effects are **really** defined. Write/refresh `docs/KEYFLAW_HOST.md`: where effects live (readable shaders/`effect.json`-like assets vs compiled native code), the host contract (textures, uniform names, resolution/time, parameter mapping, colour space, multi-pass, expandBounds), **how Keyflaw's own parameter controls look and behave** (for the scrubber), and the exact implementation or behaviour of **unsharp mask, sharpen and glow** (and haze/flare, tritone, vignette, grain), with parameters, ranges, defaults, names. Copy every readable in-scope effect folder into `apps/<slug>/src/main/assets/effects/` **unchanged** (record hashes). List explicitly what is native-only. Acceptance: the doc is accurate and the native-only list is explicit.

### T2: Real-pair regression harness and the match solver (core)
Add the regression pair (R-D). Replace the LUT solver with the section 5 match: working space, curves, matrix, hue-keyed stage, robust fitting, letterbox auto-detect, held-out ΔE, honest labels. **Delete** all LUT, Cube, Hald code and UI. Acceptance: on the regression pair the quality gate (section 5) is met or, if it cannot be, the best numbers are recorded and the shortfall is explained in `known_issues` **and** the Recreated PNG is saved, viewed and described in `notes`; GPU/CPU parity test (in T3) pending. Also save Before | Target | Recreated | Heatmap as one PNG under `docs/verification/` and describe it.

### T3: Renderer (right way up) and effect host; exact Keyflaw effects (slice 1)
One persistent GLES renderer implementing Keyflaw's host contract, running effect folders unchanged, multi-pass with FBO ping-pong, parameters bound from each `effect.json`, the Colour Match effect from section 5 (shared shader text, parameters from the project's match model). **Fix the Y flip** with the asymmetric-image test. For effects Keyflaw has: use its code; for **unsharp mask, sharpen and glow** copy exactly if readable; if native-only, **match them to real Keyflaw output**: build a calibration set by running Keyflaw's own effect when you can (for example with a headless/instrumented run of the unpacked app on the emulator) or, failing that, by reconstructing from After Effects' documented algorithms (Keyflaw follows AE) and then comparing against any Keyflaw output images you can obtain; record `docs/CALIBRATION_NEEDED.md` listing precisely which reference screenshots/exports Victor should supply (effect, settings, source frame) so a later session can finish the match, and make the app already behave as closely as you can. Acceptance: pixel tests (identity at strength 0, GPU vs CPU reference within 1.5/255, each copied effect compiles, changing any parameter changes output, asymmetric image not flipped in any mode) run on an emulator job with software GL (for example `reactivecircus/android-emulator-runner`, `-gpu swiftshader_indirect`); if GL cannot run anywhere, record it, keep trying alternatives, rely on the CPU reference. **View the saved PNGs and describe them.** **Publish pre-release APK rc1.**

### T4: Fit and Tune wired to the match (slice 1)
Fit stage per section 6 using the new solver and honest labels; Tune stage opens with the Colour Match applied to the base and **Recreated looking like the Target**. Acceptance: an app-level test (demo project built from the regression pair) runs Fit and asserts Recreated's mean ΔE to Target is below the identity baseline by the gate; **target-blind test**: after the fit, replace the target file with noise and recompute Recreated: output unchanged; Heatmap is not solid red. View the screenshots from the emulator and describe them.

### T5: Scrubber sliders with live updates
Implement the scrubber control (section 6) for every SCALAR parameter. **Live updates:** values go to the renderer's uniform store on every touch move and request a render; persistence to the project is debounced (about 300 ms after the last change); the stack list and viewer container must not recompose per move. Acceptance: a test simulates a 120-step drag and asserts the renderer received intermediate values (not only the final one) and that the output pixels changed during the drag; recomposition count for the stack list stays bounded (record numbers); double tap resets; typing a value works.

### T6: Colour picker and all control types
Real COLOR control: HSV square plus hue bar, hex field, RGB fields, recent swatches, **eyedropper that samples a colour from the viewer frame**. Check BOOL, CHOICE, groups against `effect.json` (ranges, steps, defaults, units). Acceptance: tests set a colour by picker, hex and eyedropper (sampled value equals the pixel under the tap) and the effect output changes. **Publish pre-release APK rc2.**

### T7: Effect library = Keyflaw's, picker and categories
Wire the copied effects into the picker, Library > Effects and the stack with controls generated from each `effect.json`; all categories non-empty; search works ("tritone", "sharpen", "unsharp", "glow" find them). Delete any older hand-written effect shaders that Keyflaw's replace. Acceptance: picker count equals the effect folders in assets; a pixel test per effect (non-default parameter changes output; all-disabled equals input); unsharp mask and sharpen on a synthetic edge image strengthen edges by the expected amount; **glow** on a synthetic bright-dot image spreads light with the expected radius/intensity falloff; view and describe the PNGs.

### T8: View modes against the reference
Implement all view modes per section 6. Acceptance: pixel tests show each mode's output differs from the others where it must; **Vs Target output contains the target's pixels in one half and the processed result in the other** (assert by sampling both halves); Difference equals |result − target| scaled by gain; press-and-hold shows the true original; wipe flip works; all modes right way up. View and describe screenshots.

### T9: Export a real `.kfpack`
Fix the two reported problems and prove them:
- **Wrong extension (.zip):** a likely cause is saving through MediaStore with a ZIP MIME type, so Android renames the file to `.zip`. Save with a MIME type that does not trigger renaming (for example `application/octet-stream`) and the display name ending in `.kfpack`, or use SAF `ACTION_CREATE_DOCUMENT`; **after saving, read back the final display name and assert it ends with `.kfpack`**; if the system renamed it, redo the save through the fallback route.
- **"Cannot find any valid effects":** build the pack exactly as the skill's packer would: `suite.json` at the **ZIP root** (no wrapping folder), `effects/<ID>/effect.json` and `effects/<ID>/shader.glsl`, forward-slash paths, correct `minAppVersion`, effects list in `suite.json` matching the folders, correct parameter/pass schema. Generate a reference pack with the skill's own packer from the same effect and **compare structure and field names with yours entry by entry**; run the skill's validator; unzip and inspect the result yourself. If any sample `.kfpack` exists in the repos or skill, diff against it.
Then: the three pack modes (add to existing / create new / merge imported), collision dialog, proof PNG and tune-state JSON extras, save to `Downloads/MOTIONSALT/CC Studio/` with permission fallbacks, success sheet. Acceptance: tests produce the exact file the app saves; it passes the skill's validator on Actions; the zip root listing is shown in `notes`; extension is `.kfpack`; shader bytes hash-equal the preview's; merge and collision flows pass.

### T10: Source stage and permissions finish
Letterbox auto-detect in the crop editor, observable state everywhere (a newly added image appears instantly), and the permission design of section 6 (requests at the moment of use, Permissions screen with only four rows plus the info section). Acceptance: journey tests assert instant thumbnails, auto-detected bars on the regression pair, each permission fires at its trigger and never at launch.

### T11: Diagnostics and performance checks
Settings > About > Diagnostics: GL version, per-effect compile status, current match model summary (ΔE identity vs fitted), average frame time and fps while interacting, tune-state JSON with Copy. Performance rules: one persistent GL context, cached compiled effects, preview capped at about 1280 px long edge (configurable), FBO ping-pong, no CPU readbacks or bitmap uploads per frame, GPU/throttled scopes, off-main-thread decoding and fitting. Acceptance: tests assert no per-frame bitmap allocation or readback during the simulated drag; numbers recorded.

### T12: Release and proof (last)
Publish the final signed APK (tag `<slug>-vN`) **and a sample `.kfpack` produced by the regression pair through the real pipeline, as a GitHub Release asset so Victor can import it into Keyflaw immediately.** Include as release assets the Before | Target | Recreated | Heatmap PNG and the screenshots you viewed. Rewrite `docs/` and BUILD_STATE.json; set `status` to `complete` only if every item is `done`.

---

## 8. DEFINITION OF DONE AND FINAL REPORT

Done means: no LUT remains; on the regression pair Recreated visibly looks like the Target and meets the quality gate (or the exact shortfall is stated); the Tune viewer is right way up in every mode; Vs Target compares against the target; sliders are scrubbers that update live; unsharp mask, sharpen and glow match Keyflaw (or `docs/CALIBRATION_NEEDED.md` lists exactly what is still needed and why); a real colour picker exists; the exported file is `.kfpack`, passes the skill's validator and imports with its effects found; early and final APKs and a sample `.kfpack` are published.

Your final message to Victor must be short: one line per point in his report (section 3) saying what was wrong, that it is fixed and how it was verified (including the measured ΔE before and after on his Gojo pair); links to the pre-release and final APKs and the sample `.kfpack`; any `blocked` items or calibration needs with the exact reason; the assumptions you made. Nothing else.
