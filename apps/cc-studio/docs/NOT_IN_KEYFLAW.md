# NOT_IN_KEYFLAW: Native-Matched Effects
Date: 2026-11-05
Author: MOTIONSALT Engineering / Victor Jumbo (vjumbo264)

---

## 1. Context and Rationale

Keyflaw 1.4.9 implements its standard video effects inside compiled native libraries (`libkeyflow_identity.so`) and Dalvik bytecode rather than shipping raw GLSL assets in APK storage.

Per Rule R-A and Section 8 (T1 & T4), any effect whose source cannot be read directly from raw APK assets must be matched to Keyflaw's and industry-standard reference behavior (specifically After Effects' mathematical formulations) and explicitly documented here.

---

## 2. Matched Effects Catalog

### 1. `ms_unsharp_mask` (Unsharp Mask)
- **Reference Standard:** Adobe After Effects Unsharp Mask calculation.
- **Parameters:**
  - `amount`: SCALAR (0.0 to 500.0, default 100.0, step 5.0) — Percentage boost.
  - `radius`: SCALAR (0.1 to 10.0, default 1.5, step 0.1) — Blur radius in pixels (`uTexelSize * radius`).
  - `threshold`: SCALAR (0.0 to 255.0, default 0.0, step 1.0) — Absolute tonal difference threshold.
- **Math:**
  $$\\text{diff} = \\text{orig} - \\text{gaussianBlur}(\\text{orig}, \\text{radius})$$
  $$\\text{mask} = \\text{step}(\\text{threshold} / 255.0, |\\text{diff}|)$$
  $$\\text{result} = \\text{orig} + \\text{mask} \\times \\text{diff} \\times (\\text{amount} / 100.0)$$

### 2. `ms_sharpen` (High-Pass Detail Sharpen)
- **Reference Standard:** After Effects Classical 3x3 High-Pass Laplacian detail filter.
- **Parameters:**
  - `amount`: SCALAR (0.0 to 2.5, default 0.5, step 0.02)
  - `radius`: SCALAR (0.5 to 4.0, default 1.0, step 0.1)
- **Math:**
  $$\\text{laplacian} = 4.0 \\times \\text{center} - (\\text{north} + \\text{south} + \\text{east} + \\text{west})$$
  $$\\text{result} = \\text{center} + \\text{laplacian} \\times \\text{amount}$$

### 3. `ms_tritone` (Three-Way Split Toning)
- **Reference Standard:** After Effects Color Correction > Tritone.
- **Parameters:**
  - `shadowColor`: COLOR (default `#0A1428`)
  - `midtoneColor`: COLOR (default `#D48B47`)
  - `highlightColor`: COLOR (default `#FFF2D9`)
  - `smoothness`: SCALAR (0.1 to 1.0, default 0.5, step 0.02)
  - `blend`: SCALAR (0.0 to 1.0, default 1.0, step 0.01)
- **Math:**
  Luminance $Y = 0.2126 R + 0.7152 G + 0.0722 B$.
  Piecewise linear/smooth Hermite blend:
  - If $Y < 0.5$: $\\text{mix}(\\text{shadowColor}, \\text{midtoneColor}, 2Y)$
  - If $Y \\ge 0.5$: $\\text{mix}(\\text{midtoneColor}, \\text{highlightColor}, 2(Y - 0.5))$

### 4. `ms_lut` (3D Color Transform)
- **Reference Standard:** 3D Cube Color Grading Transform with half-texel alignment.
- **Parameters:**
  - `intensity`: SCALAR (0.0 to 1.0, default 1.0, step 0.01)
- **Math:**
  $$\\text{uv} = \\text{rgb} \\times \\frac{N - 1}{N} + \\frac{0.5}{N}$$
  Sample 3D LUT lattice via trilinear filtering.
  $$\\text{out} = \\text{mix}(\\text{rgb}, \\text{lutRgb}, \\text{intensity})$$
