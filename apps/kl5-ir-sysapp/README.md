# KL5 IR & System-App Hook LSPosed Module

An LSPosed (Xposed API) module specifically designed for the **TECNO KL5** (MediaTek arm64) running rooted Android 16 and Android 17 GSIs (e.g. Restless OS).

It hooks `system_server` at runtime to resolve two issues without modifying `services.jar` bytecode:
1. **IR Blaster detection & transmission fix**: Bridges Android 17's AIDL-only `ConsumerIrService` to the device's real, working legacy HIDL HAL (`android.hardware.ir@1.0::IConsumerIr/default`).
2. **System-App Install Gates bypass**: Allows `pm install -r` of system packages regardless of unknown-source flags, identical versionCodes, or version downgrades, and preserves downgraded packages across device reboots.

---

## 🛠️ Architecture & Hook Map

### 1. IR Blaster HIDL Bridge (`IrBridgeHooks.kt` + `HidlIrBridge.kt` + `NativeIrBridge.kt`)
- **Root Cause**: On Android 17, `ConsumerIrService` only checks for AIDL `android.hardware.ir.IConsumerIr/default` via `ServiceManager.waitForDeclaredService()`. TECNO KL5 vendor partition only supplies legacy HIDL `android.hardware.ir@1.0::IConsumerIr/default` (`/dev/irtx` & `/dev/lirc0`).
- **Hook Targets**:
  - `ConsumerIrService.<init>`: Checks legacy HIDL availability if AIDL is missing; sets `mHasNativeHal = true`.
  - `ConsumerIrService.hasIrEmitter()`: Returns `true` when HIDL service is present.
  - `ConsumerIrService.getCarrierFrequencies()`: Calls `HidlIrBridge.getCarrierFrequencies()` (returns 30kHz - 60kHz frequency ranges).
  - `ConsumerIrService.transmit(int, int[])`: Forwards carrier frequency and pulse pattern directly into the HIDL HAL with `DeathRecipient` automatic reconnection.
- **Native JNI Source**: Included in `jni/motionsalt_ir_bridge.cpp`, `Android.bp`, and `Android.mk`. The module automatically uses native JNI if `libmotionsalt_ir_bridge.so` is bundled, with an active in-process `android.os.HwBinder` IPC fallback.

### 2. System-App Install Gates (`SysAppHooks.kt`)
- **PackageInstallerSession.validateApkInstallLocked()**: Suppresses `INSTALL_FAILED_SESSION_INVALID: System app updates from unknown sources are blocked` exclusively when the package is flagged as a system app.
- **InstallPackageHelper.preparePackage()**: Suppresses the same-versionCode rejection ("not allowed to update system package to the same versionCode") for system apps.
- **InstallPackageHelper.verifyReplacingVersionCode()**: Suppresses downgrade rejections (`INSTALL_FAILED_VERSION_DOWNGRADE`) for system apps.
- **Boot-Time Scan Preservation**: Hooks `ScanPackageUtils` / `PackageManagerService` initial boot scans to ensure downgraded system app updates are not reverted to the preloaded system image upon reboot.
- **Scope Safety**: Signature verification, fs-verity, permissions, and SELinux enforcement remain 100% untouched. Non-system apps are not affected.

---

## 🚀 Installation & Activation

1. Download and install the release APK (`app-kl5-ir-sysapp-v1.apk`).
2. Open **LSPosed Manager** (via Zygisk / ReZygisk / KernelSU).
3. Under the **Modules** tab, locate **KL5 IR & SysApp** and turn on the toggle switch.
4. Verify that the **System Framework** (`android`) scope is selected.
5. Reboot the device.

---

## 🧪 On-Device Test Commands

Run the following commands in Termux or an ADB shell as root:

```sh
su

# 1. Test ConsumerIrService detection (should return 1 / true)
service call consumer_ir 1

# 2. Test system-app downgrade / reinstall (e.g. DeskClock)
pm install -r /path/to/same-or-older-version-system-app.apk

# 3. Verify hook logs in logcat
logcat -d | grep -i MOTIONSALT-Hook | tail -80
```

---

## ⚙️ Build Instructions

To build the release APK locally:
```sh
cd apps/kl5-ir-sysapp
./gradlew :app:assembleRelease
```

To build the native JNI library in an AOSP / NDK tree:
```sh
cd apps/kl5-ir-sysapp/jni
mm -j$(nproc)
```
