package com.forgebuild.engine.ui.theme

/**
 * Engine-level theme selector.
 *
 * Any generated app reads/writes this to choose which visual system the whole
 * UI runs under. The three options:
 *
 *  - [MATERIAL]      -> **Material 3 Expressive** (the DEFAULT). See
 *                       [ForgeBuildTheme] / com.forgebuild.engine.ui.theme.Theme.
 *  - [MIUIX]         -> Miuix (opt-in). See com.forgebuild.engine.ui.miuix.
 *  - [LIQUID_GLASS]  -> Liquid Glass (opt-in). See com.forgebuild.engine.ui.glass.
 *
 * This is intentionally a plain enum (not tied to any persistence layer) so a
 * generated app can store the selection however it already stores settings
 * (DataStore, SharedPreferences, an in-memory StateFlow, ...). A Material /
 * Miuix / Liquid Glass *picker UI* is deliberately NOT built into the Engine —
 * a generated app can build that trivially on top of this enum plus
 * [com.forgebuild.engine.ui.glass.LiquidGlassSupport.isSupported] (to hide /
 * disable the Liquid Glass entry on devices below API 33). Recorded as a
 * follow-up in BUILD_STATE.json.
 *
 * Icon conventions per theme (no emoji anywhere in generated UI):
 *  - MATERIAL     -> Material Symbols (com.forgebuild.engine.ui.icons.EngineIcons)
 *  - MIUIX        -> Miuix's own icon conventions (top.yukonga.miuix.kmp.icon)
 *  - LIQUID_GLASS -> minimal single-weight icons (reuse EngineIcons vectors)
 */
enum class EngineTheme {
    MATERIAL,
    MIUIX,
    LIQUID_GLASS
}
