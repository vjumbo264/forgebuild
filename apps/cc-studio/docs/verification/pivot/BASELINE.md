# Gojo pivot regression checkpoint — 2026-11-05

## What is actually measured
Original user-supplied frames are preserved losslessly under core/src/test/resources/gojo (560x259). SHA256 hashes and the affine matrix are in baseline_metrics.json. These originals have no letterbox bars, so this fixture does NOT validate bar detection. A four-pixel border and out-of-bounds affine intersection are excluded. Identity baseline and legacy reproduction use the SAME valid pixels. Errors are CIEDE2000, D65/2-degree, encoded sRGB converted to Lab. Colour-only comparison uses Gaussian sigma=2. Edge-ECC is on blurred gradient magnitude maps, independently implemented in the test-only Python harness; it is not yet ported into Android core.

| Measurement | Mean | p95 |
|---|---:|---:|
| Identity, aligned | 10.143125 | 24.598464 |
| LEGACY solver, aligned | 5.827156 | 12.681231 |
| Identity, blurred sigma=2 | 9.729748 | 24.088624 |
| LEGACY solver, blurred sigma=2 | 4.853753 | 10.066395 |

This is the EXISTING solver, NOT the required new no-LUT model. It still fails all four gate conditions: mean >5.4; p95 >12.5; reduction only 42.55% (<45%); blurred mean >4.3. It trained across the entire frame. Right-20% metrics in JSON are DIAGNOSTIC ONLY, NOT held-out validation. The new model is explicitly null and new_model_gate_passed=false.

Unaligned Kotlin CIEDE2000 mean identity=10.590102, legacy=6.338914. Independent scikit-image values=10.589870 and 6.338810; discrepancies <0.0003. This cross-check and 20 published Sharma pairs establish that the new metric utility is not just the old CIE76 function relabelled.

## What was viewed
Opened gojo_legacy_audit.png and target_aligned.png with image viewing, not just file existence checks. All panels upright. Before cloth is green; target cloth is pale blue/cyan with warm skin and deep green forest. Legacy output shifts cloth toward blue but is too dark/muddy and visibly clips/flat-tens some hair/background details; it does NOT reproduce the extreme olive-yellow failure in the operator's installed-app screenshot. Heatmap varies across cyan/green/yellow/red with pronounced edge errors, not uniform red; excluded border is grey. Aligned target remains upright with tiny edge warp; it is reference evidence, never substituted as the recreated output.

## Important limit on earlier audit attribution
Running current legacy Kotlin directly on these originals gives training CIE76 mean=8.852894 / p95=17.542171, NOT the installed screenshot's 38.26 / 66.56. Source-verified defects in AUDIT4 stand, but an exact reproduction/attribution of THAT stored project failure is not established. Do not claim it was reproduced or fixed. Different imported image/crop state or installed-source provenance remains unverified.

## Verification actually run
- All main and test Kotlin core sources compile with Kotlin 2.1.20, JVM toolchain.
- New ColourMetricsTest: 3/3 pass (20 Sharma reference cases, symmetry, sRGB neutrals/identity, percentile, quality labels and gate boundaries).
- Full JVM suite from correct core working directory: 29 tests, 28 pass, 1 FAIL. Pre-existing PixelProofHarnessTest.testSharpenLaplacianResponse asserts expected boost but gets 0.006597229. Not suppressed or relabelled green. It exercises legacy CPU approximation, not newly recovered original shader assets.
- Original-shader static compile: 65/65 pass; not a GPU or render-equivalence test.
- Actual APK, emulator, install/update, preview orientation, live-scrubber pixel events, Keyflaw import, no-LUT preview/export parity: NOT RUN / NOT CLAIMED.
- Python packages used only by verification tooling; no runtime app dependencies or manifest networking permissions were added.

## Genuine execution boundary / next operation
The pasted contract requires a FAITHFUL port of reference_match.py, with the SAME stages, constants and fitting order, BEFORE improvements. The file was not among supplied attachments (only the contract and images), not in either checked main repository tree, and not among stored release assets. The old legacy/baseline_solver.py is a DIFFERENT LUT model, not that reference. SKILL.md is also absent, but the actual original parser already provides independent pack-format authority.

T2 is BLOCKED on the missing reference_match.py; its fixture and metric prerequisites are implemented and tested. Do not invent a replacement optimizer then call it a faithful port. Next operation after the source is supplied: append it as operator input without credentials, preserve its hash under regression tooling, port to pure Kotlin, measure against this pair with rightmost20% excluded from ALL model selection/fitting, and inspect the real output. Only then replace all runtime LUT paths, finish T2, and proceed T3..T12 in order. An explicit operator authorization to abandon the faithful-port requirement would also resolve this constraint.

No rc1, rc2, final APK or new sample .kfpack was published. Last release remains cc-studio-v4. build_complete=false; T3..T12 remain pending. Engine unchanged; signing secrets untouched; no repository visibility changed because both were already private.
