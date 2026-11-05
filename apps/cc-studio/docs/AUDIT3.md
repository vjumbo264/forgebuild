# AUDIT 3: Root-Cause Investigation & Technical Reset
Date: 2026-11-05
Author: MOTIONSALT Engineering / Build Agent
Target: apps/cc-studio/ in vjumbo264/forgebuild (v1, v2, v3 audit)

---

## 1. Executive Summary

A comprehensive forensic audit of `cc-studio-v3` was conducted to identify the root causes for the severe bugs reported by the operator (Victor / MOTIONSALT).

The audit verified and localized the failure points to exact source files and lines:
1. **The "Recreated" frame equals the target (Faked result):** `FitStage.kt:252-253` checked for files starting with `recreated_` which were NEVER generated or saved anywhere in the codebase. It fell back to `recFile?.absolutePath ?: pair?.target?.filePath`, literally returning and displaying the target frame!
2. **The LUT is completely broken / looks wrong:** `ms_lut/shader.glsl:17-27` contained a hardcoded polynomial S-curve and arbitrary warmth/cool balance offsets; it did not sample a 3D LUT or lattice texture at all. In `ColorMath.kt:225-235`, `parseCube33` reset the 1D tone curves and matrix to identity and only stored differences in `residual`, corrupting the transform evaluation.
3. **Extreme UI Lag:** `TuneStage.kt:172-225` executed full-resolution `BitmapFactory.decodeFile` from disk on the Android main UI thread inside `@Composable` recomposition on every slider change, allocated full-resolution `IntArray(w * h)` pixel buffers, and executed CPU-bound software loops in `StackRenderer.kt` across millions of pixels. No OpenGL ES / GPU acceleration was used in the viewer.
4. **No-op effects (Sharpen & Unsharp Mask):**
   - `ms_unsharp_mask` was completely omitted from `StackRenderer.kt` lines 47-97 in the `when (item.effectId)` dispatch table, making it a 100% no-op.
   - `ms_sharpen` looked up parameter key `"sharpenAmount"` in `StackRenderer.kt:130`, but in `ms_sharpen/effect.json` the key is `"amount"`. The lookup was always null and fell back to `0.3f`, completely ignoring slider movements.
5. **No color picker:** `TuneStage.kt:724-738` rendered `ParamType.COLOR` as an `OutlinedTextField` string input, forcing the user to type hex strings manually.
6. **Flawed earlier verification:** Tests in `PixelProofHarnessTest` tested CPU mock math on small synthetic arrays and never asserted that Recreated is blind to target pixels once the LUT is fitted, nor compared GPU shader output with CPU ground truth.

---

## 2. Detailed Root-Cause Trace by File and Line

### 2.1 Recreated frame equals the target
- **File:** `apps/cc-studio/app/src/main/java/com/motionsalt/ccstudio/ui/fit/FitStage.kt`
- **Lines:** 247-255
```kotlin
2 -> {
    val resultsDir = File(context.filesDir, "projects/${project.id}/results")
    val recFile = resultsDir.listFiles()?.filter { it.name.startsWith("recreated_") }?.maxByOrNull { it.lastModified() }
    recFile?.absolutePath ?: pair?.target?.filePath
}
```
- **Finding:** A search across the entire codebase revealed that `recreated_` was never written by `LutFitService`, `FitStage`, or any solver routine. As a result, `recFile` was always null and evaluated to `pair?.target?.filePath`. The UI was displaying the target image directly, misleading the user.

### 2.2 LUT tool broken & wrong look
- **File:** `apps/cc-studio/app/src/main/assets/effects/ms_lut/shader.glsl`
- **Lines:** 17-27
```glsl
void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 rgb = c.rgb / max(c.a, 0.0001);
    // Dynamic S-curve & tonal contrast adjustment
    vec3 graded = pow(max(rgb, vec3(0.0)), vec3(0.92, 0.95, 1.05));
    graded = mix(graded, graded * graded * (3.0 - 2.0 * graded), 0.35);
    // Color balance offsets
    graded.r += (1.0 - rgb.r) * uWarmth * 0.12;
    graded.b += rgb.b * uHighlightCool * 0.12;
    ...
```
- **Finding:** The shader exported in `MOTIONSALT.kfpack` and used for the LUT effect was an arbitrary hardcoded S-curve with warmth and cool sliders. It did not take or sample the fitted 3D LUT at all!
- **File:** `apps/cc-studio/core/src/main/kotlin/com/motionsalt/ccstudio/core/ColorMath.kt`
- **Lines:** 225-235
- **Finding:** When `parseCube33` parsed a `.cube` file back into `FittedTransform`, it initialized `curveR/G/B` to identity and `matrix` to identity, and subtracted `baseR/G/B` from `triples[flat]`. However, `FittedTransform.evaluate` applies 1D curves first, then matrix, then residual. Setting curves to identity while applying residual computed from raw RGB broke the math pipeline.

### 2.3 Extreme Viewer Lag
- **File:** `apps/cc-studio/app/src/main/java/com/motionsalt/ccstudio/ui/tune/TuneStage.kt`
- **Lines:** 165-225
```kotlin
val bitmap = remember(activeFramePath, lutTransform, tuneStateJson, isPressingBefore, ...) {
    if (activeFramePath != null && File(activeFramePath!!).exists()) {
        val rawBmp = BitmapFactory.decodeFile(activeFramePath)
        val w = rawBmp.width
        val h = rawBmp.height
        val pixels = IntArray(w * h)
        rawBmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val inBuf = StackRenderer.PixelBuffer(w, h, pixels)
        val outBuf = StackRenderer.render(inBuf, currentTuneState, lutTransform)
        ...
```
- **Finding:** Any slider change updated `tuneStateJson`, which invalidated Compose's `remember` block. This executed synchronous image decoding from disk (`BitmapFactory.decodeFile`), memory allocation of arrays with millions of integers, and CPU pixel loops across the entire image on the Android UI thread. This caused dropped frames, UI freezing, and extreme latency.

### 2.4 Sharpen and Unsharp Mask Non-Functional
- **File:** `apps/cc-studio/core/src/main/kotlin/com/motionsalt/ccstudio/core/StackRenderer.kt`
- **Lines:** 47-97 & 130-131
- **Finding 1:** `ms_unsharp_mask` was completely missing from `StackRenderer.kt`'s `when(item.effectId)` dispatch. Enabling or tuning Unsharp Mask had zero effect on the rendered pixels.
- **Finding 2:** `ms_sharpen` evaluated `(item.params["sharpenAmount"] as? Number)?.toFloat() ?: 0.3f`. But `ms_sharpen/effect.json` defines parameter key `"amount"`. Because of the key mismatch, the user's slider value was ignored and the amount was permanently stuck at `0.3f`.

### 2.5 Color Picker Missing
- **File:** `apps/cc-studio/app/src/main/java/com/motionsalt/ccstudio/ui/tune/TuneStage.kt`
- **Lines:** 724-738
```kotlin
ParamType.COLOR -> {
    val colorStr = currentValue.toString()
    Row(...) {
        Text(p.label, fontSize = 12.sp)
        OutlinedTextField(
            value = colorStr,
            onValueChange = { onParamChange(p.key, it) },
            ...
        )
    }
}
```
- **Finding:** The UI provided only an `OutlinedTextField` expecting manual hex string input. There was no HSV color canvas, hue bar, preset swatches, or eyedropper to sample from the image.

---

## 3. Repositories and Reference Binary Safety

1. **Repository Visibility:**
   - `vjumbo264/forgebuild`: Private (verified via GitHub API PATCH).
   - `vjumbo264/lut-extractor`: Private (verified via GitHub API PATCH).
2. **Keyflaw 1.4.9 Reference Binary:**
   - Downloaded and verified from the temporary link (45,466,906 bytes).
   - Uploaded and permanently stored on the private repository release:
     `https://github.com/vjumbo264/lut-extractor/releases/tag/keyflaw-ref-v1.4.9`
     Asset: `Keyflaw-1.4.9-custom.apk` (Asset ID: 713069787).
   - No APK binary committed to git.

---

## 4. Reset Action Plan (T0 - T10)

All items from R1-R10 and S1-S8 that contained false-positives or masked defects have been rolled back and re-anchored into the definitive `reset_queue`:
- **T0:** Audit and honest reset (Done).
- **T1:** Study Keyflaw host contract & extract readable effects.
- **T2:** Keyflaw effect host renderer (OpenGL ES 2.0/3.0, FBO ping-pong, uniform binding).
- **T3:** Fit produces real 3D LUT, Recreated is verified target-blind, rc1 pre-release APK.
- **T4:** Effect library wired to Keyflaw host with proper After Effects unsharp mask and sharpen math.
- **T5:** Tune stage viewer on host renderer with real-time view modes.
- **T6:** Real HSV color picker with eyedropper & rc2 pre-release APK.
- **T7:** Performance pass (uniform-only updates, debouncing, 1280px preview cap, 30+ fps).
- **T8:** In-app Diagnostics screen (GL renderer, fps, ms, compile status).
- **T9:** Validated .kfpack export with identical preview shaders & fitted LUT.
- **T10:** Final signed release cc-studio-v4, sample .kfpack asset, and proof.
