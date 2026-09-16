# forgebuild-engine

ForgeBuild Engine — reusable Android base: Kotlin + Jetpack Compose, Material Symbols icons, conditional permissions module, adaptive-icon generator, lean Gradle, release-APK GitHub Actions.

## Theme systems (Engine-level, selectable)

The Engine ships **three selectable theme systems**, read/written through the
Engine-level selector `com.forgebuild.engine.ui.theme.EngineTheme`:

| `EngineTheme` | System | Status | Where |
|---|---|---|---|
| `MATERIAL` | **Material 3 Expressive** (dynamic color, dark mode, spring motion, expressive shapes, wavy/loading indicators) | **DEFAULT** — backward compatible | `ui/theme/Theme.kt`, `ui/components/EngineProgress.kt` |
| `MIUIX` | Miuix (`top.yukonga.miuix.kmp:miuix-ui`, Apache-2.0) | opt-in | `ui/miuix/` |
| `LIQUID_GLASS` | Liquid Glass (`io.github.kyant0:backdrop`, Apache-2.0) | opt-in | `ui/glass/` |

### Material 3 Expressive (default)
`ForgeBuildTheme` wraps `MaterialExpressiveTheme` with `MotionScheme.expressive()`
and the expressive `Shapes()` defaults; dynamic color (Android 12+) and dark
mode are preserved exactly as before. Expressive progress/loading lives in
`com.forgebuild.engine.ui.components.EngineProgress`
(`LinearWavyProgressIndicator`, `CircularWavyProgressIndicator`,
`LoadingIndicator` — official components, no hand-rolled approximations).
Existing generated apps keep calling `ForgeBuildTheme` unchanged.

### Miuix (opt-in)
`MiuixEngineTheme` + the `MiuixEngine*` component wrappers
(buttons, card, switch, slider, dialog, bottom nav) under
`com.forgebuild.engine.ui.miuix`. Corners are Miuix's per-component squircle
(`cornerRadius`), not a Material `Shapes` object. Uses Miuix's own icon
conventions.

### Liquid Glass (opt-in)
`LiquidGlassTheme` + the `Glass*` components (button, card, switch, slider,
dialog, bottom nav) under `com.forgebuild.engine.ui.glass`, built on the
`backdrop` effect engine and adapted from its reference examples. Pattern:
wrap content in `LiquidGlassTheme`, mark the background with
`Modifier.liquidBackdropSource(backdrop)`, and pass that `Backdrop` to each
`Glass*` component. **Capability:** the real refraction/blur needs an AGSL
RuntimeShader, i.e. **API 33+**; below that, every component automatically
renders a frosted-lite fallback (same layout/shape/typography, translucent
surface + highlight) — see `LiquidGlassSupport.isSupported` /
`LiquidGlassFallback`. Never a crash, never a blank. Minimal single-weight
icons (reuse `EngineIcons`); no emoji.

> App-level theme *picker UI* is intentionally not built in; a generated app
> can build it trivially on `EngineTheme` + `LiquidGlassSupport.isSupported`.
> (Recorded as a follow-up in `BUILD_STATE.json`.)

All three themes preserve dynamic color, dark mode, adaptive icons, and the
existing `FLAG_SECURE` / permissions modules. No emoji anywhere in generated UI.
