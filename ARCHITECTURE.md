# ForgeBuild Engine — Architecture

The Engine is a **GitHub template repository**. Each new ForgeBuild app repo is
created from it via GitHub's "generate from template" API. The Engine is a
component/theme library — the generating AI writes real Kotlin/Compose code
against it. There is **no fixed layout schema**: a keyboard app
(`InputMethodService`), a chat app, a gallery, a browser, a foreground-service
app are all expressible.

## Structure
- `app/` — Android application module (Kotlin + Jetpack Compose).
- `app/src/main/java/com/forgebuild/engine/ui/theme/` — `ForgeBuildTheme`:
  Material 3, dynamic color (Android 12+), proper light/dark. All generated
  apps must use it.
- `app/src/main/java/com/forgebuild/engine/ui/icons/` — Material Symbols icons
  bundled as local Compose `ImageVector`s (offline, lean). Extend with
  `tools/gen_icons.py` — never emoji, never mixed icon sets.
- `app/src/main/java/com/forgebuild/engine/permissions/` — permission wiring:
  storage, camera, notifications, foreground service, background location,
  device administrator. **Nothing is declared or requested by default**; the
  generating AI enables only what the app legitimately needs (manifest entries
  + `PermissionWiring` calls).
- `tools/make_adaptive_icon.py` — foreground artwork in → safe-zoned adaptive
  icon layers out (72dp safe zone inside the 108dp canvas, background layer,
  monochrome layer for themed icons). Never a padded white square.
- `tools/make_keystore.sh` — one-time keystore generation; secrets go to the
  app repo's GitHub Actions secrets, never into git.
- `.github/workflows/release.yml` — builds the signed release APK on tag push
  (`v*`) or manual `workflow_dispatch`, writes `forgebuild-manifest.json`
  (name, package, permissions, notes) and attaches both to a GitHub Release.

## Extension points for the generating AI
1. Replace `namespace`/`applicationId` in `app/build.gradle.kts`.
2. Replace `app_name` in `res/values/strings.xml`.
3. Replace `StarterScreen()` in `MainActivity` with the real UI.
4. Add dependencies **only** when a feature requires them, with justification
   noted in the app repo. R8 minify + resource shrinking stay on.
5. Add icons via `tools/gen_icons.py`; permissions via the manifest +
   `PermissionWiring`; any service (foreground, IME, device admin) as ordinary
   Android components wired in the manifest.

## Signing & releases
Releases are GitHub Releases on the app's own repo (`v1`, `v2`, ...) — never
overwritten. Rollback/history come from the GitHub Releases API. Signing uses
the app repo's Actions secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`.
