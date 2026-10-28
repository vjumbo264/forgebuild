# KL5 IR & System-App Hook LSPosed Module

An LSPosed (Xposed API) module specifically designed for the **TECNO KL5** (MediaTek arm64) running rooted Android 16 and Android 17 GSIs (e.g. Restless OS).

It hooks `system_server` at runtime to resolve two issues without modifying `services.jar` bytecode:
1. **IR Blaster detection & transmission fix**: Bridges Android 17's AIDL-only `ConsumerIrService` to the device's real, working legacy HIDL HAL (`android.hardware.ir@1.0::IConsumerIr/default`).
2. **System-App Install Gates bypass**: Allows `pm install -r` of system packages regardless of unknown-source flags, identical versionCodes, or version downgrades, and preserves downgraded packages across device reboots.

---

## 🔍 Update v2: LSPosed Scope Fix ("Nothing to hook" resolution)

### What was wrong in v1
In v1, the module declared its scope using `<meta-data android:name="xposedscope" android:resource="@array/xposed_scope" />` pointing to an array with `<item>android</item>`. 
On modern LSPosed / LSPosed Manager on Android 14+ / Android 17 GSIs:
1. Cross-process resource extraction via `pm.getResourcesForApplication()` failed due to package visibility restrictions and resource table partitioning, returning `null` when parsing `xposedscope`.
2. LSPosed's legacy scope parser contains a bidirectional name-swapping rule (`android` <-> `system`) that caused single-item scope declarations to miss the framework depending on which identifier the manager checked.
3. Without a resolved scope, LSPosed treated the module as an empty per-app hook module, displaying the warning: *"Nothing to hook — this module is enabled but has no apps selected, so it will not do anything."*

### What changed in v2
1. **Direct string manifest value**: Changed `xposedscope` in `AndroidManifest.xml` to `android:value="android;system"`. This reads directly from `ApplicationInfo.metaData` without cross-process resource resolution.
2. **Dual-identifier string array & R8 keep rule**: Defined both `<item>android</item>` and `<item>system</item>` in `res/values/arrays.xml`, protected with `res/raw/keep.xml` against R8 resource shrinking.
3. **Modern LSPosed / Vector scope declarations**: Bundled `META-INF/xposed/scope.list` (listing `system` and `android`) and `META-INF/xposed/module.prop` (`staticScope=true`) so modern LSPosed and Vector managers recognize the module as a system-scoped module with a fixed, automatic scope.
4. **Widened `XposedInit` target matching**: Updated `handleLoadPackage` to check for `android`, `system`, and `processName == "system_server"` with a thread-safe idempotency guard to ensure hooks attach reliably.

---

## ✅ Minimal Verification Step (Scope Fix)

Before testing any IR or install-gate hooks, verify that the module is properly scoped:

1. Install `app-kl5-ir-sysapp-v2.apk`.
2. Open **LSPosed Manager** (or Vector Manager).
3. Navigate to the **Modules** screen and toggle **KL5 IR & SysApp** ON.
4. **Expected verification result**:
   - The warning *"Nothing to hook — this module is enabled but has no apps selected"* is **GONE**.
   - Under the module's scope list, **System Framework** (`android` / `system_server`) is automatically selected and scoped.
   - The user does **not** need to manually search for or select any application.
5. Reboot the device.

Once the above step passes, proceed to testing the install-gate and IR hooks below.

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
