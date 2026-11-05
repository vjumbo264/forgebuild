# Keyflaw 1.4.9 — verified original host and effect evidence

2026-11-05 pivot. This supersedes the unsupported earlier '614 shaders / native-only standard effects' report.

## Provenance and scope
Original APK: private `vjumbo264/lut-extractor` release `keyflaw-ref-v1.4.9`, asset `Keyflaw-1.4.9-custom.apk`, asset id 713069787, 45,466,906 bytes, SHA256 `061898213797eadafd2c481db9dc7be8c5fc68c4009202fd23b7a5ac34dc95e2`. No binary is committed. Both repos verified private before extracting.

No shader.glsl/effect.json folders exist in APK assets; do not mistake that for native-only. DEX contains 145 complete literal fragment shader strings plus assembled shader builders. Built-in definitions are obfuscated but readable: `MHgl3w0w` (Sharpen and Unsharp), `I6qkZ` (basic colour, Vignette, sealed Glow), `JlDFs` (Tritone), `EieXW8j` (Diffusion), `KoukqoP` (Haze Flare), `GnDE8tS` (Film Grain), `HJJDEv` (colour corrections), `acA03gBw` (sealed Curves/Levels/HSL and readable Gamma & Exposure), `lcLLjVb0`/`DSPcKhXdY` (advanced looks).

JADX 1.5.3 decompilation reported 91 errors across the entire app; only inspected methods/fields are evidence. Original shader builder bytecode was converted with dex2jar 2.4, then executed to recover concatenated strings. R8 enum declarations and the empty-list/collection singleton were restored for JVM extraction, and effect constructor calls were projected into a metadata-only container to avoid Android rendering/UI initialization. No shader mathematical method was rewritten. Therefore shader strings are original builder outputs; JSON metadata wrappers are DERIVED, not falsely claimed as original APK JSON. An unrelated I6qkZ UI-initialization method failed AFTER its relevant definitions had been constructed; this and extraction scope are in manifest.json. Whole-catalog exhaustiveness is not claimed.

24 readable in-scope original effects were copied under `app/src/main/assets/effects/<original-id>/`. Each has raw untrimmed shader bytes, derived effect.json, and host_graph.json retaining scales, previous-pass selection, conditions and float-target flags. 65/65 original pass sources compiled with glslangValidator (GLSL ES 1.00); results and SHA256 hashes are in `docs/keyflaw-reference/`. This is COMPILE proof only, not GPU rendering or visual equivalence. The legacy ms_* approximations have not yet been removed; T7 must replace them and update picker counts only after T3 implements the graph. No APK is released from this intermediate asset checkpoint.

## Actual pack parser (definitive compatibility evidence)
Exact decompiled SuiteParser, SuiteModelFactory and SuiteRegistrar are retained privately in `docs/keyflaw-reference/`.

- suite.json at ZIP root; suite.effects = `["effect_id"]`, DIRECTORY NAMES relative to `effects/`, NOT `effects/id/effect.json`.
- Parser requires BOTH effect.json and shader.glsl even if passes is present. Pass filenames are relative to the effect folder.
- Pass names in JSON become a sequential shader list in SuiteRegistrar; built-in EffectPass scale/prevPass graph is richer than that public plugin parser.
- Original parser's createParam has SCALAR, BOOL and CHOICE branches. There is NO COLOR branch in this recovered version. Native colour pickers group R/G/B SCALAR parameters through separate colour metadata. A JSON COLOR hex default becomes optDouble(...,0), then a scalar; earlier COLOR vec3 contract was not supported by this factory. Export colour controls as three numeric RGB channels and group them, not a hex string.
- group and choice options are parsed. step affects display precision/scale through SuiteModelFactory.calculateDecimals/calculateScale, not a raw motion speed.
- expandBounds and usesNativeMotionBlur exist; no minAppVersion parser read was found in the inspected methods. Do not invent a required field or assume rejection on it.

## Host contract / colour space
Readable shaders sample `uniform sampler2D uTexture`, with `varying vec2 vTex`. Inputs and outputs are PREMULTIPLIED RGBA (demonstrated by original Tritone: divide source.rgb by max(alpha,1e-4), process, multiply by alpha). Uniforms use `u` + capitalized key (`lS6Vcg` constructor). Per-instance numeric parameter controls must preserve id, min/max/default and display scale.

Built-in multipass needs MORE than two ping-pong textures: `uInput` = effect-entry original, `uTexture` = preceding pass, `uPrev` = retained explicitly indexed pass. `uTexelSize` is destination-pass texel size, `uSrcTexelSize` the source size, plus `uCompSize`, `uLayerRect`, `uLayerToLocal` and `uLocalToUv` where used. `wTD6U3C` stores scale, prevPass, requiredParam/Minimum, compOctaves, requiredAnyParams and floatTarget. Preserve earlier referenced pass outputs until last use. `host_graph.json` is private app metadata, not a claim that the public .kfpack loader supports it.

The renderer's bitmap origin must be canonicalized once; the original Haze shader uses `(centerX, 1-centerY)` conversion explicitly. The prior doc's 'bottom-left OR top-left' is not a usable contract.

No automatic linearization is performed in the readable Tritone/Brightness shaders: use encoded sRGB-like UI RGB and explicitly convert into a chosen colour-match space once if required. Android source-image ICC/profile behavior and sealed shader internals were NOT established by static analysis; GPU tests must verify. Do not label the host linear-light without that test.

## Exact required effects (not approximations)
- **Sharpen** (`MHgl3w0w`): Sharpen Amount 25, range 0..4000, no radius control. 9-tap detail `(20*c - 4*edges - corners)/6`; output `c + detail*(amount/100)`, alpha-safe clamp. Texel distance is at least one target texel, otherwise 1 pixel at 1080-height scaled by aspect. Saved byte-identical shader.
- **Unsharp Mask** (`MHgl3w0w`): Amount 50, 0..500; Radius 1.0, 0.1..500, display scale 10 and one decimal; Threshold 0, 0..255. Fourteen passes: six downsample rungs to 1/64, separable Gaussian/upsampling at 1/64, 1/16, 1/4, full; previous-pass references 3,7,1,9 and 11; final difference from uInput, max RGBA difference *255, soft threshold gate, amount/100. Gaussian radius is sigma in pixels at 1080-height. It IS readable, not native-only. Shader sources and full graph copied. Current CC Studio host cannot yet execute this graph correctly; T3 must implement retained side inputs and per-pass resolution, not substitute a 3x3 mask.
- **Glow** (`I6qkZ`): 16-pass graph with eight downsample rungs and indexed reconstruction. Its shader bodies are EMPTY sealed-vault entries, not readable shader code. Parameters preserved in manifest: threshold .4, smooth .4, radius .6, falloff .15, exposure 1.4, RGB tint 1, tintEnabled 0, tintAmount 1, chromatic 0, addMode 0, glowOnly 0. It requires native/reference calibration if not recoverable through supported app execution.
- **Diffusion**: 19 readable passes; size .03 (0..0.12), grade .25, glow .35 (0..2), highlightsOnly 1, highlightBias 0 (-1..1), exposureCompensation 0 (-4..4), RGB 1. This is NOT the old hand-written 'Diffusion Glow'.
- **Haze Flare**: seven readable passes. strength 1 (0..2), spillage .5, softness .65, reach .5 (0..2), exposure -1 (-8..5), reflectionExposure 0 (-8..5), reflection 1, axis choice 0, matteBoxSize 1.2 (0..2), matteBoxShade 0, RGB (1,.9,.75), preserveAlpha 1. Original names/ranges/types in effect.json.
- **Tritone**: grouped RGB shadows (0,0,0), mids (.5,.3,.15), highlights (1,1,1); Blend With Original 0. Smoothstep interpolation either side of luma .5, no hard colour threshold. RGB controls are SCALAR, not a COLOR vec3 uniform.
- **Vignette**: amount .45, size .6 (0..1.5), softness .5 (.01..1.5), single readable pass.
- **Film Grain**: intensity 1 (0..10), size 1 (.1..10), softness .3, aspect 1 (.1..5), per-RGB intensity 1 (0..5), saturation 1, shadows .7, midtones 1, highlights .3, animationSpeed 24 (0..120), seed 0 (0..9999). Single readable shader copied; needs time and composition uniforms.

## Explicit sealed/native-only list in this scope
Glow, Curves, Levels, HSL Colors (`color_balance_hls`): metadata/readable graph exists but shader bodies are sealed; no fake replacement folder was created for them. Lens Blur and Lens Flare also sealed, but excluded from this colour/detail/look asset set. This is a PRECISE scoped list, not a claim that all Keyflaw effects are native-only. Calibration requirements are in CALIBRATION_NEEDED.md.

## Parameter controls: verified and unverified
`lS6Vcg` proves displayScale, decimals, BOOL/CHOICE, group, holdOnly, hiddenWhenOn, angleTurns and conditional visibility metadata. For example Unsharp radius displays one decimal, Sharpen is percent-like. Native grouped colours (`lZLNoOuC`) are RGB triples with optional enabled keys. Those must be retained instead of forcing hex input.

Static inspection located ruler code for an unrelated 3D editor (`moxm3yQ`: Edit3DViewRuler; SfQ3S: RulerScale). It does NOT prove the effect-parameter control is the same ruler. No live Keyflaw screen was run, so its effect-control visuals, fling, long-press and reset gestures remain unverified. T5 should implement the operator's explicit ruler fallback and record this decision; do not say the original effect UI was visually copied. T6 uses the requested HSV/eyedropper UI but exports numeric RGB channels compatible with the actual factory.

## Readable scope inventory
| Original id | Original name | Original category | Passes |
|---|---|---|---|
| sharpen | Sharpen | Blur | 1 |
| unsharp_mask | Unsharp Mask | Blur | 14 |
| brightness_contrast | Brightness/Contrast | Warna | 1 |
| hue_saturation | Hue/Saturation | Warna | 1 |
| vignette | Vignette | Warna | 1 |
| haze_flare | Haze Flare | Stilisasi | 7 |
| diffusion | Diffusion | Blur | 19 |
| neg_bleach_bypass | Neg Bleach Bypass | Warna | 1 |
| tritone | Tritone | Warna | 1 |
| gamma_exposure | Gamma & Exposure | Warna | 1 |
| invert | Invert | Warna | 1 |
| spot_exposure | Spot Exposure | Warna | 1 |
| gradient_exposure | Gradient Exposure | Warna | 1 |
| film_grain | Film Grain | Stilisasi | 1 |
| change_to_color | Change to Color | Warna | 1 |
| spectral_map | Spectral Map | Warna | 1 |
| cc_color_neutralizer | Color Neutralizer | Warna | 1 |
| cc_color_offset | Color Offset | Warna | 1 |
| cc_kernel | Kernel | Warna | 1 |
| inner_glow | Inner Glow | Stilisasi | 5 |
| chromatic_glow | Chromatic Glow | Stilisasi | 1 |
| glint_glare | Glint / Glare | Stilisasi | 1 |
| edge_rays | Edge Rays | Stilisasi | 1 |
| defocus_prism | Defocus Prism | Blur | 1 |

## Remaining acceptance boundaries
No screenshot, visual-effect equivalence, original-apk UI run, emulator, install/import test, no-LUT solver or new APK is claimed here. Shader compilation/hashes are real. The missing reference_match.py prevents a FAITHFUL PORT with the same constants/order required by T2; do not call a newly guessed optimizer the reference. The Gojo originals are available, not missing. SKILL.md was not attached; actual original parser is the pack authority for now.
