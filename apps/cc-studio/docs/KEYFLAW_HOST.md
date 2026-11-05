# Keyflaw Effect Host Contract & Architecture Specification
Date: 2026-11-05
Author: MOTIONSALT Engineering / Victor Jumbo (vjumbo264)
Reference App: Keyflaw 1.4.9 (Package: com.ryn.keyflaw / Engine: com.angkin.keyflow.engine)

---

## 1. Where Keyflaw Effects Live (Decompilation & Binary Audit)

Keyflaw 1.4.9 (`Keyflaw-1.4.9-custom.apk`, 45,466,906 bytes) was thoroughly inspected across all DEX files, assets, resources, and native libraries:
1. **Assets directory (`assets/`):**
   - Contains only TensorFlow Lite model (`assets/roto/magic_touch.tflite`) and sample thumbnails (`sample.jpg`, `sample_night_market.png`, etc.).
   - Contains NO raw `shader.glsl` or `effect.json` files for standard effects.
2. **Native Libraries (`lib/`):**
   - `libkeyflow_identity.so` (330,440 bytes) provides low-level rendering, video pipeline interfaces, and native shader assembly.
3. **Core Engine (`classes.dex`):**
   - Package `com.angkin.keyflow.engine` houses the internal rendering graph (`EffectStore`, `TransformValueStore`, `KeyframeStore`, `TileFold`, `SingleInputVideoGraph`).
   - Contains 614 compiled internal shader strings used for composition and motion tiles.
4. **Plugin Host (`classes2.dex`):**
   - Package `com.ryn.keyflaw.effects.loader` contains the official community `.kfpack` host:
     - `SuiteParser`: Parses `suite.json`, `effect.json`, `shader.glsl`, and multi-pass `passes`.
     - `SuiteModelFactory`: Instantiates internal effect models (`createEffect`, `createShader`, `createParam` for `SCALAR`, `BOOL`, `CHOICE`, `COLOR`).
     - `SuiteRegistrar`: Manages `sCatalogEffectsList`, `sCatalogEffectsMap`, `sEffectMetaMap`, `sRegisteredEffects`, `expandBounds`, and `usesNativeMotionBlur`.
     - `SuiteManager`: Manages import from `/storage/emulated/0/Download/Keyflaw` and `/Documents/Keyflaw/Effects`.

---

## 2. Keyflaw Host Contract (Binding Specification)

Every `.kfpack` effect executed by Keyflaw or hosted in MOTIONSALT CC Studio adheres to the following contract:

### 2.1 Texture & Coordinate Uniforms
- `uniform sampler2D uTexture;` — The active input frame (premultiplied ARGB_8888).
- `varying vec2 vTex;` — Normalized 0.0 to 1.0 UV coordinates (0,0 bottom-left or top-left depending on orientation, mapped to quad corners).
- `uniform vec2 uTexelSize;` — `(1.0 / width, 1.0 / height)` of the rendered buffer.
- `uniform float uTime;` — Continuous timeline clock in seconds.

### 2.2 Parameter-to-Uniform Mapping
Each parameter defined in `effect.json` is exposed to GLSL as:
- Parameter key: camelCase starting lowercase (`amount`, `shadowColor`, `waveType`).
- Uniform name: `"u" + key.capitalizeFirstLetter()`:
  - `amount` -> `uniform float uAmount;`
  - `radius` -> `uniform float uRadius;`
  - `shadowColor` -> `uniform vec3 uShadowColor;` (for `COLOR` type)
  - `waveType` -> `uniform float uWaveType;` (for `CHOICE` or `BOOL` type)

### 2.3 Parameter Types
| Type | UI Control | JSON Schema | GLSL Uniform |
|---|---|---|---|
| `SCALAR` | Slider + numeric field | `"type": "SCALAR", "default": 1.0, "min": 0.0, "max": 2.0, "step": 0.01` | `uniform float uKey;` |
| `BOOL` | Switch toggle | `"type": "BOOL", "default": 1, "min": 0, "max": 1, "step": 1` | `uniform float uKey;` (`> 0.5`) |
| `CHOICE` | Dropdown menu | `"type": "CHOICE", "options": ["A", "B"], "default": 0, "min": 0, "max": 1, "step": 1` | `uniform float uKey;` (`< 0.5`, `< 1.5`)|
| `COLOR` | Color picker | `"type": "COLOR", "default": "#FF8820"` | `uniform vec3 uKey;` (0..1 RGB) |

### 2.4 Multi-Pass Pipeline (`passes`)
- Declared in `effect.json` as `"passes": ["p1.glsl", "p2.glsl", ...]`.
- Rendered using framebuffer object (FBO) ping-pong.
- Pass 1 receives the clip frame as `uTexture`.
- Subsequent passes receive the rendered FBO texture of the preceding pass as `uTexture`.

### 2.5 Bounds Expansion (`expandBounds`)
- Declared in `effect.json` as `"expandBounds": 0.3` (30% padding outside layer quad).
- Prevents glows, blurs, and shadows from clipping at frame edges.

### 2.6 3D LUT Sampling Convention (GLES 2.0 / 3.0)
- In `.cube` format, Red varies fastest, then Green, then Blue:
  `flatIndex = b * N * N + g * N + r`.
- On GPU, sampling uses half-texel correction:
  `uv = coord * (N - 1.0) / N + 0.5 / N`
  with trilinear interpolation and edge clamping.
- In GLES 2.0 / GLSL ES 1.00: 2D strip texture with size `(N * N) x N` or `N x (N * N)`, where slice `floor(b)` and slice `ceil(b)` are bilinearly sampled and linearly mixed.
- In GLES 3.0: `sampler3D` texture with `texture(uLut3D, coord)`.

---

## 3. In-Scope Effect Library

The 22 in-scope effects span 5 categories:
1. **Colour correction:** `ms_lut`, `ms_brightness_contrast`, `ms_exposure_gamma`, `ms_white_balance`, `ms_hue_saturation`, `ms_color_balance`, `ms_selective_color`, `ms_channel_mixer`.
2. **Tone:** `ms_levels_curves`, `ms_highlights_shadows`.
3. **Toning:** `ms_tritone`, `ms_duotone`, `ms_gradient_map`, `ms_split_toning`.
4. **Detail:** `ms_sharpen`, `ms_unsharp_mask`, `ms_clarity`.
5. **Look:** `ms_glow`, `ms_bloom_halation`, `ms_haze_flare`, `ms_vignette`, `ms_grain`.
