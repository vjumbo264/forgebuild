# OTG Enabler (Enhanced Universal Root Edition)

A modernized, universal, root-powered USB OTG (On-The-Go) host enabler for Android devices.

Forked and evolved from the original [nikita-yfh/otg-enabler](https://github.com/nikita-yfh/otg-enabler) project, this edition solves the `"SDK version too low"` installation block on modern Android (Android 14/15/16, API 34-37) and vastly extends hardware compatibility from single Transsion/MTK nodes to a universal multi-strategy driver engine covering all major SoCs and Linux kernel subsystems.

---

## Why this fork?

1. **Modern Android SDK Target (compileSdk 37 / targetSdk 37 / minSdk 26)**:
   - Modern Android OS builds (Android 14+) actively refuse installation of legacy APKs (`INSTALL_FAILED_DEPRECATED_SDK_VERSION`). This build uses modern Android toolchains, Kotlin, and Jetpack Compose.
2. **Universal Multi-Method Kernel Driver Engine**:
   - The original app only targeted `/sys/devices/platform/tran_battery/OTG_CTL`.
   - This enhanced fork adds 10 specialized strategies plus an intelligent **Universal Auto-Detect** mode:
     - **Transsion / Tecno / Infinix / itel**: `/sys/devices/platform/tran_battery/OTG_CTL` & `otg_switch`
     - **MediaTek MTK**: `/sys/class/power_supply/usb/otg_switch` & `/sys/devices/platform/mt_usb/otg_mode`
     - **Linux Dual-Role USB Subsystem**: `/sys/class/dual_role_usb/*/mode` (forces DFP host data & source power roles)
     - **Modern Linux USB Role Switch**: `/sys/class/usb_role/*/role` (mainline kernel 5.x/6.x)
     - **Type-C TCPM / USB-PD**: `/sys/class/typec/port0/` (data_role host, power_role source)
     - **Qualcomm Snapdragon / DWC3**: `/sys/devices/platform/soc/*.dwc3/otg_mode` & MSM OTG debugfs
     - **Extcon Hardware Pin Simulation**: `/sys/class/extcon/*/state` (simulates grounded ID pin)
     - **OEM System Properties**: `persist.sys.oem.otg_support`, `persist.sys.oppo.otg_support`, `persist.vendor.otg.support` (OnePlus, Oppo, Realme, Xiaomi)
     - **Aggressive All-In-One Force**: Executes all triggers sequentially for difficult kernels.
3. **Deep Sysfs Node Scanner & Tester**:
   - Discovers any vendor-specific OTG, dual-role, or power supply nodes directly on the device's kernel with real-time permissions, read values, and 1-click test writes (`echo 1`, `echo host`, `echo 0`).
4. **Live USB Peripheral Detection**:
   - Real-time monitoring of connected USB flash drives, keyboards, mice, DACs, and gamepads via Android's `UsbManager` and `lsusb` fallback.
5. **Quick Settings Tile & Boot Automation**:
   - Includes a Quick Settings tile for 1-tap toggling without opening the app.
   - Optional "Reapply on Boot" service ensuring OTG stays active across device restarts.
6. **SELinux Permissive Helper**:
   - Detects if SELinux is enforcing and provides a 1-tap `setenforce 0` fix if kernel policies block sysfs writes.
7. **Premium Material 3 Expressive UI**:
   - Fully built in Jetpack Compose adhering to Google's Material 3 Expressive design system with dynamic Material You coloring and expressive motion.

---

## Requirements

- **Root Access**: Magisk, KernelSU, APatch, or standard `su`.
- **Android**: Android 8.0 (API 26) through Android 15/16 (API 37).
- **USB OTG Adapter or Type-C Hub**: Connected to the device's charging port.
