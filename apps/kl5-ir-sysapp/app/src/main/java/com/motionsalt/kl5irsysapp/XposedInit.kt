package com.motionsalt.kl5irsysapp

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

/**
 * LSPosed / Xposed entrypoint for KL5 IR & System-App Install Gate Hooks.
 *
 * Targets:
 * 1. android / system (system_server):
 *    - ConsumerIrService HIDL bridge (fixes IR emitter on Android 17 GSI)
 *    - PackageInstaller / InstallPackageHelper (unknown sources, same versionCode, downgrade)
 * 2. com.motionsalt.kl5irsysapp (Companion App):
 *    - Injects isModuleActive() -> true for live status detection.
 */
class XposedInit : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val TAG = "MOTIONSALT-Hook"
        const val TARGET_PACKAGE_SYSTEM_SERVER = "android"
        const val TARGET_PACKAGE_ALT_SYSTEM = "system"
        const val TARGET_PROCESS_SYSTEM_SERVER = "system_server"
        const val MODULE_PACKAGE = "com.motionsalt.kl5irsysapp"

        fun log(msg: String) {
            Log.i(TAG, msg)
            XposedBridge.log("[$TAG] $msg")
        }

        fun logWarn(msg: String, tr: Throwable? = null) {
            Log.w(TAG, msg, tr)
            XposedBridge.log("[$TAG] WARN: $msg" + (tr?.let { "\n" + Log.getStackTraceString(it) } ?: ""))
        }

        fun logError(msg: String, tr: Throwable? = null) {
            Log.e(TAG, msg, tr)
            XposedBridge.log("[$TAG] ERROR: $msg" + (tr?.let { "\n" + Log.getStackTraceString(it) } ?: ""))
        }
    }

    private var isSystemServerHooked = false

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        log("initZygote: module initialized for TECNO KL5 hooks")
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam?) {
        if (lpparam == null) return

        // Hook system_server: support both "android" and "system" package naming,
        // and processName "system_server", as different LSPosed/Xposed versions use different naming conventions.
        val isSystemServer = (lpparam.packageName == TARGET_PACKAGE_SYSTEM_SERVER ||
                              lpparam.packageName == TARGET_PACKAGE_ALT_SYSTEM ||
                              lpparam.processName == TARGET_PROCESS_SYSTEM_SERVER) &&
                             lpparam.packageName != MODULE_PACKAGE &&
                             lpparam.processName != MODULE_PACKAGE

        if (isSystemServer) {
            synchronized(this) {
                if (isSystemServerHooked) return
                isSystemServerHooked = true
            }
            log("Attaching to system_server (package: ${lpparam.packageName}, process: ${lpparam.processName})")

            // 1. Initialize System App Install Gate Hooks
            try {
                SysAppHooks.init(lpparam.classLoader)
            } catch (t: Throwable) {
                logError("Fatal error while initializing SysAppHooks in system_server", t)
            }

            // 2. Initialize ConsumerIrService HIDL Bridge Hooks
            try {
                IrBridgeHooks.init(lpparam.classLoader)
            } catch (t: Throwable) {
                logError("Fatal error while initializing IrBridgeHooks in system_server", t)
            }
            return
        }

        if (lpparam.packageName == MODULE_PACKAGE) {
            // Hook companion app to confirm module activation
            try {
                XposedHelpers.findAndHookMethod(
                    "$MODULE_PACKAGE.MainActivityKt",
                    lpparam.classLoader,
                    "isModuleActiveInXposed",
                    XC_MethodReplacement.returnConstant(true)
                )
                log("Companion app hooked: isModuleActiveInXposed() -> true")
            } catch (t: Throwable) {
                logWarn("Could not hook isModuleActiveInXposed in companion app: ${t.message}")
            }
        }
    }
}
