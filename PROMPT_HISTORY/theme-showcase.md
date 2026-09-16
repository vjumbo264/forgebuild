# PROMPT HISTORY — theme-showcase

Append-only log of operator instructions for this app, in order received.

## 2026-09-16 — Initial build contract (session 1)

> Simple test app to experience Google Expressive, MiuiX, and Liquid Glass's full new UI suite.

Interpretation: a theme-suite showcase app. One codebase, a top-level selector between
the Engine's three theme systems — Material 3 Expressive (EngineTheme.MATERIAL),
Miuix (EngineTheme.MIUIX), Liquid Glass (EngineTheme.LIQUID_GLASS) — each rendering a
full gallery of that system's components (buttons, cards, switch, slider, dialog,
bottom navigation, progress/loading indicators) so the operator can experience the
whole suite on device. Liquid Glass entry disabled/hidden below API 33 via
LiquidGlassSupport.isSupported. No network, no permissions, no sensitive screens,
no file saves — purely local demo app.
