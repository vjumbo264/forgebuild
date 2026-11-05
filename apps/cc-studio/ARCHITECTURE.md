# MOTIONSALT CC Studio Architecture

## Module Structure
- `:core`: Pure Kotlin/JVM color math engine. Zero Android framework dependencies so it can be verified with instant unit tests. Contains CIE Lab conversions, Delta E 76/2000 math, 1D Tone spline matching, 3x3 chromatic matrix regression, 33x33x33 residual lattice fitting with 3-pass Laplacian regularization, `.cube` text generation, and true identity Hald Level 8 generator.
- `:app`: Android application module built with Jetpack Compose and Material 3 Expressive. Contains AndroidManifest.xml (strictly NO internet permission), UI screens (`ProjectsScreen`, `ProjectFlowScreen` with 4 stages: `SourceStage`, `FitStage`, `TuneStage`, `ExportStage`), OpenGL ES renderer, and SAF storage integration for `Downloads/MOTIONSALT/CC Studio/`.
- `assets/effects/`: 17 bundled native Keyflaw effect definitions and GLSL ES 1.00 shaders.

## Keyflaw 1.4.9 Compliance
- Reverse domain suite ID: `com.motionsalt.effects`
- Matching categories: `MOTIONSALT`
- Uniform conventions: `uTexture`, `vTex`, `uTime`, `uTexelSize`, `u<Param>`
- Safety constraints: Protected divisions and square roots (`max(x, 0.0001)`), literal loop bounds for Mali/Adreno GPUs.
