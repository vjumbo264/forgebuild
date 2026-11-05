# AUDIT4 — direct-colour pivot, 2026-11-05

## Scope and honesty
Audited the actual main source f129491 (cc-studio-v4 lineage), not the stale v2 header. Both repositories are private, verified with authenticated GitHub API. No runtime fix, pixel parity, emulator run, or release is claimed by this audit. The five operator screenshots were visually inspected: upside-down Tune, upright Fit frames, green original vs blue/teal target cloth, flattened olive recreated frame, saturated red heatmap; metrics visibly 38.26 / 66.56. Those observations are operator evidence, NOT measurements rerun by this session.

Paths below are relative to apps/cc-studio. Line numbers refer to the audited baseline.

## 1. Upside-down Tune
`app/src/main/java/com/motionsalt/ccstudio/renderer/KeyflawHostRenderer.kt:104-115` maps bottom screen vertices to v=0; `:299-304` uploads Android's top-first bitmap rows unchanged; vertex shaders `:341-347` and `:471-477` pass UVs unchanged. `:587-596` reuses this coordinate buffer for bitmap, FBO and final-screen sampling. There is no bitmap-to-canonical origin conversion. Thus bitmap top appears at screen bottom, unlike Fit's Compose Image path. Correct normalization must occur ONCE at import-to-FBO (including Before and Target), not by blindly flipping each pass, which would alternate orientation with effect count. An asymmetric F/corner test through actual GL is required; existing JVM tests exercise a different CPU renderer and do not test this path.

## 2. Poor fit and dishonest caption
`ui/fit/FitStage.kt:69-86` directly pairs equal x,y at step 8 over minimum dimensions. No affine edge alignment, crop/ignore-region application, or bar rejection. `:93-95` silently substitutes synthetic data on decode failure. `core/.../LutSolver.kt:127-162` bins target channel averages then uses a cumulative maximum as 'monotonic smoothing'; an early high outlier permanently lifts later bins and flattens tonal ranges. `:165-189` never fits a 3x3 matrix: it leaves identity and estimates only offsets. `:192-229` nearest-bin residuals have no smoothing, robust loss or hue-selective model. `:88-102` measures training data only using CIE76, with no held-out or identity baseline. Thus the screenshot's 38.26 is NOT comparable to the prompt's CIEDE2000 reference without recomputation.

`ui/fit/FitStage.kt:240` explicitly returns 'Accurate match' for EVERY mean >=3, including 38.26. `:253` calls p95 'Worst-case bounds', which is not a maximum. These are concrete source defects, not a tuning issue.

## 3. Posterised olive Recreated
`ui/fit/FitStage.kt:129` really applies the fitted transform to the base; current code no longer substitutes Target here. The broken curves, offset-only matrix and sparse discontinuous residual estimates above feed `core/.../ColorMath.kt:21-124`, where channels are rounded to one of 256 curve bins, clamped after matrix mixing, and clamped again after residual addition. This loses detail before any final comparison. The supplied image confirms the symptom; exact 38.26 and its dominant component have NOT been independently reproduced, so no invented numerical attribution is made.

## 4. Solid red Heatmap
`core/.../ColorMath.kt:391-418` computes CIE76 and clips all errors >=10 to red (`:402`). With this fit most pixels exceed that tiny scale. `ui/fit/FitStage.kt:137-147` resizes rather than aligns target and includes bars. No scale legend or valid-area mask exists. Red saturation is a consequence of both the bad fit and the fixed scale, not proof of a valid diagnostic.

## 5. Why Vs Target can compare a frame against itself
`ui/tune/TuneStage.kt:94-100,480-491` puts BOTH base and target into one selectable strip and allows target to become `activeFramePath`; `:107-114` loads that selection into the BASE texture. Independently `:124-131` always uses the FIRST complete pair's target, not the selected frame's corresponding pair. Selecting that target therefore supplies the same image to source and reference (identical if match is disabled/unavailable). GPU shader `renderer/KeyflawHostRenderer.kt:434-440` does reference a separate target sampler, so it would be inaccurate to claim it unconditionally aliases the result. `:574` silently falls back to base if target is missing. `:443` Difference compares to BEFORE, not TARGET — definite wrong-reference bug. Fix must bind a pair identity, not simply change a chip label.

## 6. Sliders wait for release / lag
`ui/tune/TuneStage.kt:690-706` is an ordinary Material Slider, not a scrolling ruler. `:699-703` assigns localValue inside onValueChange then invokes onSliderDrag on a same-key LaunchedEffect. `:580-590` does send uniform overrides immediately, but every move cancels and re-launches the 300ms persistence coroutine. The nested deferred callback is unnecessary and can coalesce moves; no instrumented event-count test exists. It is NOT truthful to claim every GL update is itself delayed 300ms: source contradicts that. Further concrete live-preview defects: renderer overrides keyed by effectId at `:156-158,535`, so duplicate instances share overrides; stale overrides are never cleared on undo/reset. The screen-level pointerInput(Unit) captures initial values. Required repair is direct per-move instance-keyed uniforms, isolated control state, debounced DISK writes only, and measured 120-step pixel/event proof.

## 7. Export .zip and 'no valid effects'
`ui/export/ExportStage.kt:133-142` saves .kfpack with application/zip, never reads back DISPLAY_NAME, stores a display-path string instead of content URI. Share at `:338-340` sends EXTRA_TEXT, not the archive bytes. No SafeSave use.

The definitive 'no valid effects' cause is verified against the RECOVERED ORIGINAL APK's `com.ryn.keyflaw.effects.loader.SuiteParser.parseSuiteDir`: it resolves each suite.effects item as a DIRECTORY under `effects/`, then looks for effect.json and shader.glsl inside it. Our `core/.../PackBuilder.kt:63-66` writes `effects/<id>/effect.json` as each list item. Keyflaw searches `effects/effects/<id>/effect.json` as a directory and skips EVERY effect. The local validator accepts the same erroneous convention, so its success was circular. Exact original SuiteParser was decompiled with JADX 1.5.3 from the private reference asset (SHA256 061898213797eadafd2c481db9dc7be8c5fc68c4009202fd23b7a5ac34dc95e2). T9 must compare against this parser, not the existing self-validator.

`ui/export/ExportStage.kt:93-98` exports effect definitions rather than tuned instance parameters. `app/src/main/assets/effects/ms_lut/shader.glsl:14` is identity; the pack builder never receives the fitted transform, so the advertised fitted look is NOT actually embedded. Preview/export agreement claims are invalid.

## Additional concrete findings
- Fit's 'Survives backgrounding' text is false: FitStage uses its composition coroutine at :59, never FitService. Cancel :212 only flips a Boolean, not the computation.
- Hardcoded shader preload list :463-470 and one renderEffectPass :216-217 do not load JSON passes. Therefore 'running Keyflaw folders unchanged' is not implemented.
- Prior docs say 614 shaders and native-only effects without provenance. This session found 145 DEX strings with complete main/gl_FragColor. T1 must trace them to effect definitions before calling any effect native-only.
- reference_match.py and keyflaw-effect-builder SKILL.md are not in the user attachments or checked repository trees. Do not claim a faithful port or official-validator pass without them. The original frame attachments ARE available (560x259).

## Acceptance
Static checks confirmed each named source condition and original parser path mismatch. T0 complete as an audit only. No cosmetic changes, engine edits, generated artwork or new APK. Next: T1 inspect original effect classes, shader strings, parameters, and parameter controls; correct unsupported host assumptions.
