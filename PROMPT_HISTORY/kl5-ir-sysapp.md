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
