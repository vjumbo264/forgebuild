# PROMPT HISTORY — kl5-ir-sysapp

## 2026-10-28 — Operator instruction #1 (initial build contract)
ForgeBuild App Build Contract — NEW APP
LSPosed module hooking ConsumerIrService (IR fix) and the system-app install gates — Android 17 GSI, TECNO KL5

Target issues:
1. System-app install gates:
   - Hook PackageInstallerSession.validateApkInstallLocked() (unknown-source check for system apps)
   - Hook InstallPackageHelper.preparePackage() (same-versionCode check for system apps)
   - Hook InstallPackageHelper.verifyReplacingVersionCode() (downgrade check for system apps)
   - Hook PackageManagerService initial-scan / reboot scan path to prevent reboot revert of downgraded system apps
   - Narrowly scoped to packages already flagged as system apps, leaving signature, fs-verity, permissions, and SELinux intact.
2. IR blaster detection & bridge:
   - ConsumerIrService AIDL fallback to legacy HIDL interface android.hardware.ir@1.0::IConsumerIr/default
   - Native JNI library linking against HIDL client libraries (libhidlbase, libutils, libcutils)
   - Hook ConsumerIrService hasIrEmitter(), getCarrierFrequencies(), transmit(int, int[]) to bridge to HIDL
   - Safe fail open/loud logging (tag: MOTIONSALT-Hook) without crashing system_server.

Scope:
- Target: system_server (package "android")
- Architecture: arm64-v8a (TECNO KL5, MediaTek)
- Format: LSPosed Xposed API module with companion UI in Jetpack Compose (Material 3 Expressive)


## 2026-10-28 — Operator instruction #2 (fix LSPosed scope declaration)

# Update prompt: fix LSPosed scope declaration — module shows "Nothing to hook" with no apps selected

## Who you're talking to and how to work

You are a stateless AI coding assistant with no memory of any earlier session. This continues the repo `kl5-lsposed-ir-sysapp` (or whatever you actually named it last time — check `BUILD_STATE.json` in the repo for the real name/state before doing anything else). Full autonomy; same hard constraints as before.

**Hard constraint:** Do not ask the user any clarifying question, for any reason, and do not end on a question. Make the reasonable call yourself and keep going. Commit and push to GitHub frequently, keep `BUILD_STATE.json` current.

## The bug report

The user installed the module (package `com.motionsalt.kl5irsysapp`, labeled "KL5 IR & SysApp") and enabled it in LSPosed Manager. LSPosed shows it as an ordinary scoped module — i.e., a module that hooks into *other selected apps*, with an empty app-selection list and the warning "Nothing to hook — this module is enabled but has no apps selected, so it will not do anything." The user should never need to manually pick apps for this module: it needs to hook `system_server`/the Android system process itself, and LSPosed should treat it as always-active against that scope, the same way a system-level LSPosed module normally declares itself.

This means the module's **Xposed scope declaration in its manifest/metadata was built incorrectly or omitted** — it's being treated as a general per-app hook module instead of a system-scoped one. This is purely a packaging/manifest issue, not a problem with the hook logic itself (which may be entirely correct and just never getting the chance to load, since it's never being loaded into `system_server` with no scope set).

## What to fix

1. **Find and correct the Xposed module scope declaration** so LSPosed loads this module into `android` (the system process / `system_server`) automatically, without requiring the user to tick any app in the LSPosed Manager app list. Depending on the current LSPosed module API/convention (check what's actually current — don't assume an old Xposed-API convention that LSPosed has since changed):
   - This is typically done via a `<meta-data>` entry in `AndroidManifest.xml` (historically `xposedminversion`, `xposeddescription`, and a scope list — but LSPosed's modern convention may differ; verify against current LSPosed documentation/sample modules rather than assuming the old Xposed Framework convention still applies verbatim).
   - Some LSPosed module setups require listing `android` explicitly as a scope in a resource/XML scope list bundled in the APK, not just relying on "always active" being implicit.
   - Make sure whatever mechanism is used actually results in `system_server`/`android` appearing as a fixed, non-removable scope entry in LSPosed Manager (ideally shown as "Always in scope" against the system process specifically, the way the user's screenshot shows "Always in scope" under the module's own entry but with system_server missing from what's hooked) — cross-check against a known-working LSPosed module's manifest if you have reference material available, rather than guessing blind.

2. **Verify the hook registration entry point is correct** for current LSPosed (`xposed_init` file listing the hook class under `assets/`, or the modern equivalent, plus the class implementing the current `IXposedHookLoadPackage`/`IXposedHookZygoteInit` interfaces or LSPosed's own equivalents) — confirm this matches what you actually shipped last time, since a scope-declaration bug and an entry-point bug can look identical from the user's side (module "does nothing"), so check both rather than assuming only the scope was wrong.

3. **Do not change the hook logic itself** (the `PackageInstallerSession`/`InstallPackageHelper`/`ConsumerIrService` hooks and the native JNI IR bridge) unless you find it's also broken — this update is scoped to fixing why the module never gets loaded into the right process at all. If you do find and need to fix an unrelated bug in the hook logic while you're in there, fix it and note it clearly in `BUILD_STATE.json`/README, but the primary job is the scope/loading bug.

4. **Rebuild, and give the user an exact, minimal verification step** for this specific fix before they re-test the actual hooks: after reinstalling the updated APK and rebooting, LSPosed Manager should show the module with `android`/system framework automatically scoped — no manual app selection needed, no "Nothing to hook" warning. Only after confirming that should the user move on to testing whether the actual install-gate and IR hooks work (that's covered by the original prompt's test steps — don't duplicate them here, just get the module loadable).

## Deliverable

Updated module (source + APK if your toolchain allows building it) in the same repo, committed and pushed, with `BUILD_STATE.json` and the README updated to describe what was wrong and what changed. Keep this change tightly scoped to the loading/scope bug.
