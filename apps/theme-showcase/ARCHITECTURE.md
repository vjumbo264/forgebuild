# ForgeBuild Engine — Architecture

Reusable Android base (Kotlin + Jetpack Compose) that every generated app
copies into `apps/<slug>/`. Single source of truth for theme, icons,
permissions, data layer, file saves, and screen security.

## Theme systems (Engine-level selector)

Three selectable theme systems behind `ui/theme/EngineTheme.kt`
(`MATERIAL` / `MIUIX` / `LIQUID_GLASS`):

- **MATERIAL (DEFAULT) — Material 3 Expressive.** `ui/theme/Theme.kt` uses
  `MaterialExpressiveTheme` + `MotionScheme.expressive()` + expressive
  `Shapes()`. Dependency: `androidx.compose.material3:material3:1.5.0-alpha28`
  — the Expressive PUBLIC APIs (expressive theme/motion + wavy indicators) are
  NOT in stable 1.4.0 (verified against the artifacts); they exist only on the
  1.5.0 alpha train, so the Compose stack is pinned to `1.13.0-alpha01` (no BOM,
  since BOM 2026.09.00 still pins material3 to 1.4.0). Wavy/loading indicators:
  `ui/components/EngineProgress.kt`. Backward compatible: `ForgeBuildTheme`
  signature and dynamic-color/dark behavior unchanged.

- **MIUIX (opt-in).** `top.yukonga.miuix.kmp:miuix-ui:0.9.3` (compose-miuix-ui/miuix,
  Apache-2.0 — the most active/complete Miuix-for-Compose port; full component
  set, minSdk 23). Base theme `ui/miuix/MiuixEngineTheme.kt`; component wrappers
  `ui/miuix/MiuixEngineComponents.kt` (buttons, card, switch, slider, dialog,
  bottom nav). Shape = Miuix per-component squircle `cornerRadius` (no Material
  `Shapes`). Icons follow Miuix's own conventions.

- **LIQUID_GLASS (opt-in).** `io.github.kyant0:backdrop:2.0.1` +
  `io.github.kyant0:shapes:1.2.1` (Kyant0/AndroidLiquidGlass, Apache-2.0). The
  library is a backdrop/effect engine only (no components); the Engine's
  components live in `ui/glass/GlassComponents.kt`, adapted from the library's
  reference `catalog/components` (LiquidButton/LiquidToggle/LiquidSlider/
  LiquidBottomTabs). Pattern: `LiquidGlassTheme` → background via
  `Modifier.liquidBackdropSource(backdrop)` → `Glass*` components take the
  shared `Backdrop`.

## Liquid Glass capability / below-API-33 strategy (STANDING)

True refraction+blur uses AGSL `RuntimeShader` → **API 33+**. The library's own
`isRuntimeShaderSupported()` is the gate (`ui/glass/LiquidGlassSupport.kt`).
Below API 33 every `Glass*` component renders the **frosted-lite** fallback
(`ui/glass/LiquidGlassFallback.kt`): same layout/shape/typography on a
semi-translucent surface with a highlight + inner edge. Never a crash, never a
blank. App-level pickers may hide the option via `LiquidGlassSupport.isSupported`.
`compileSdk = 37` is REQUIRED by both new theme AARs (`minCompileSdk=37`) — do
not lower. `minSdk` stays 26; the fallback (not a minSdk bump) is how older
devices are handled.

## Toolchain

compileSdk/targetSdk 37, AGP 9.4.0, Kotlin 2.4.20, Gradle 9.6.0, minSdk 26,
Java 17 bytecode (CI uses JDK 17; AGP 9 runs on it). Compose stack pinned to
`1.13.0-alpha01` (see MATERIAL note). New engine-level deps are limited to the
three theme libraries + coroutines (cache-first data layer) — nothing else.

## Structure

```
engine/
  app/src/main/java/com/forgebuild/engine/
    ui/theme/        Theme.kt (M3 Expressive), EngineTheme.kt (selector)
    ui/miuix/        MiuixEngineTheme.kt, MiuixEngineComponents.kt
    ui/glass/        GlassTheme.kt, GlassComponents.kt, LiquidGlassSupport.kt,
                     LiquidGlassFallback.kt, util/GlassInteractiveHighlight.kt
    ui/components/   EngineProgress.kt (expressive wavy/loading)
    ui/icons/        EngineIcons.kt + EngineIconsGenerated.kt (Material Symbols)
    data/            CacheFirstStore.kt (cache-first, background-refresh)
    security/        ScreenSecurity.kt (per-screen FLAG_SECURE)
    files/           SafeSave.kt (explicit permission-scoped saves)
    permissions/     Permissions.kt (conditional permission suite)
  tools/             gen_icons.py, make_adaptive_icon.py, make_keystore.sh
```

## Standing requirements (unchanged by the theme work)

- Cache-first, background-refresh data pattern for any live-data app.
- Per-screen `FLAG_SECURE` via `ScreenSecurity`.
- Explicit, permission-scoped saves via `SafeSave` (never DownloadManager).
- Conditional permissions module (`PermissionWiring`), all opt-in.
- No emoji anywhere in generated UI. Material Symbols (Material) / Miuix icons
  (Miuix) / minimal vectors (Liquid Glass).
- Dynamic color, dark mode, adaptive icons must keep working under all themes.
