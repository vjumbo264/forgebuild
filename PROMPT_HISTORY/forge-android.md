# Prompt history — forge-android

## 2026-09-19 — session-1 (new app build)
Operator instruction (ForgeBuild App Build Contract — NEW APP, slug not predetermined):
APP_DESCRIPTION: "An android version of the forge build site"

Full contract terms as issued: choose slug (permanent), copy engine/ into apps/<slug>/,
set identity, adaptive icon via tools/make_adaptive_icon.py, reuse repo-level signing
secrets, implement one feature per commit/push, Material 3 Expressive discipline via the
Engine (ForgeBuildTheme, EngineIcons, CacheFirstStore for live data, ScreenSecurity,
SafeSave), no library substitution, dispatch release.yml (app_path=apps/<slug>) and
verify <slug>-v1 live via the Releases API before setting build_complete: true.
