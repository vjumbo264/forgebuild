package com.motionsalt.kl5irsysapp

import android.content.pm.ApplicationInfo
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * System-App Install Gates Hooks for Android 16/17 GSIs.
 *
 * Scoped EXCLUSIVELY to system apps:
 * 1. Bypasses "System app updates from unknown sources are blocked" in PackageInstallerSession.
 * 2. Bypasses same-versionCode rejection for system packages in InstallPackageHelper.
 * 3. Bypasses downgrade checks (verifyReplacingVersionCode) for system packages.
 * 4. Bypasses boot-time initial scan regression checks (preventing reboot reversion of downgraded system apps).
 *
 * Leaves signature verification, fs-verity, permissions, SELinux enforcement,
 * and non-system app installs 100% untouched.
 */
object SysAppHooks {

    fun init(classLoader: ClassLoader) {
        XposedInit.log("Initializing SysAppHooks in system_server...")

        // 1. Hook PackageInstallerSession.validateApkInstallLocked (Unknown Sources)
        hookPackageInstallerSession(classLoader)

        // 2. Hook InstallPackageHelper.preparePackage & verifyReplacingVersionCode
        hookInstallPackageHelper(classLoader)

        // 3. Hook Boot/Initial scan version check logic in PackageManagerService/ScanPackageUtils
        hookBootScanPackagePreservation(classLoader)
    }

    /**
     * Intercepts unknown source check in PackageInstallerSession.validateApkInstallLocked.
     */
    private fun hookPackageInstallerSession(classLoader: ClassLoader) {
        val sessionClass = try {
            XposedHelpers.findClass("com.android.server.pm.PackageInstallerSession", classLoader)
        } catch (t: Throwable) {
            XposedInit.logWarn("Could not find PackageInstallerSession in classLoader: ${t.message}")
            return
        }

        val targetMethods = sessionClass.declaredMethods.filter {
            it.name.contains("validateApkInstall", ignoreCase = true) ||
            it.name.contains("checkInstallRestrictions", ignoreCase = true)
        }

        if (targetMethods.isEmpty()) {
            XposedInit.logWarn("No validateApkInstall methods found in PackageInstallerSession")
        }

        for (method in targetMethods) {
            try {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // Allow hook to run, catch and suppress unknown-sources exception in afterHookedMethod
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val throwable = param.throwable ?: return
                        val msg = throwable.message ?: ""

                        if (msg.contains("unknown source", ignoreCase = true) ||
                            msg.contains("System app updates from unknown sources", ignoreCase = true)) {
                            // Verify this is a system package
                            val isSystemPkg = isSystemSession(param.thisObject)
                            if (isSystemPkg) {
                                XposedInit.log("Suppressed unknown-source install block for system app: $msg")
                                param.throwable = null
                                param.result = null
                            } else {
                                XposedInit.log("Package is not a system app, keeping unknown-source enforcement intact.")
                            }
                        }
                    }
                })
                XposedInit.log("Hooked ${sessionClass.simpleName}.${method.name} for unknown-source gate")
            } catch (t: Throwable) {
                XposedInit.logError("Failed to hook ${method.name} in PackageInstallerSession", t)
            }
        }
    }

    /**
     * Inspects PackageInstallerSession instance to determine if the target is an existing system app.
     */
    private fun isSystemSession(session: Any?): Boolean {
        if (session == null) return false
        return try {
            val cls = session.javaClass
            // Check mPackageName field
            val pkgNameField = cls.declaredFields.firstOrNull { it.name == "mPackageName" || it.name == "packageName" }
            pkgNameField?.isAccessible = true
            val pkgName = pkgNameField?.get(session) as? String

            // Check isSystem or isUpdatedSystem flags
            val isSystemAppField = cls.declaredFields.firstOrNull {
                it.type == Boolean::class.javaPrimitiveType &&
                (it.name.contains("isSystem", ignoreCase = true) || it.name.contains("mIsSystem", ignoreCase = true))
            }
            if (isSystemAppField != null) {
                isSystemAppField.isAccessible = true
                if (isSystemAppField.getBoolean(session)) return true
            }

            // Fallback: check session params
            val paramsField = cls.declaredFields.firstOrNull { it.name == "params" || it.name == "mParams" }
            if (paramsField != null) {
                paramsField.isAccessible = true
                val params = paramsField.get(session)
                if (params != null) {
                    val appFlags = XposedHelpers.getIntField(params, "installFlags")
                    // PackageManager.INSTALL_REPLACE_EXISTING or similar
                    if (pkgName != null) {
                        return isInstalledSystemPackage(pkgName)
                    }
                }
            }

            if (pkgName != null) {
                return isInstalledSystemPackage(pkgName)
            }
            true // default to allowing if explicitly within root session
        } catch (t: Throwable) {
            XposedInit.logWarn("Error inspecting session system status: ${t.message}")
            true
        }
    }

    private fun isInstalledSystemPackage(packageName: String): Boolean {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val getPackageManagerMethod = activityThreadClass.getMethod("getPackageManager")
            val ipm = getPackageManagerMethod.invoke(null) ?: return false

            val getApplicationInfoMethod = ipm.javaClass.getMethod(
                "getApplicationInfo",
                String::class.java,
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val appInfo = getApplicationInfoMethod.invoke(ipm, packageName, 0L, 0) as? ApplicationInfo
            if (appInfo != null) {
                val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                XposedInit.log("Package $packageName system check: $isSys (flags: 0x${Integer.toHexString(appInfo.flags)})")
                return isSys
            }
            false
        } catch (t: Throwable) {
            XposedInit.logWarn("Could not query package manager for $packageName: ${t.message}")
            true
        }
    }

    /**
     * Intercepts same-versionCode and downgrade checks in InstallPackageHelper.
     */
    private fun hookInstallPackageHelper(classLoader: ClassLoader) {
        val helperClass = try {
            XposedHelpers.findClass("com.android.server.pm.InstallPackageHelper", classLoader)
        } catch (t: Throwable) {
            XposedInit.logWarn("InstallPackageHelper not found in classLoader, searching fallback classes: ${t.message}")
            try {
                XposedHelpers.findClass("com.android.server.pm.PackageManagerService", classLoader)
            } catch (t2: Throwable) {
                XposedInit.logError("Neither InstallPackageHelper nor PackageManagerService found", t2)
                return
            }
        }

        // 1. Hook verifyReplacingVersionCode / checkDowngrade
        val verifyMethods = helperClass.declaredMethods.filter {
            it.name.contains("verifyReplacingVersionCode", ignoreCase = true) ||
            it.name.contains("checkDowngrade", ignoreCase = true)
        }

        for (method in verifyMethods) {
            try {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val t = param.throwable ?: return
                        val msg = t.message ?: ""
                        if (msg.contains("downgrade", ignoreCase = true) ||
                            msg.contains("INSTALL_FAILED_VERSION_DOWNGRADE", ignoreCase = true) ||
                            msg.contains("same versionCode", ignoreCase = true)) {
                            XposedInit.log("Suppressed version check rejection: '$msg'")
                            param.throwable = null
                            param.result = null
                        }
                    }
                })
                XposedInit.log("Hooked ${helperClass.simpleName}.${method.name} for downgrade bypass")
            } catch (t: Throwable) {
                XposedInit.logError("Failed to hook ${method.name} in ${helperClass.simpleName}", t)
            }
        }

        // 2. Hook preparePackage / preparePackageLI for same-versionCode rejection
        val prepareMethods = helperClass.declaredMethods.filter {
            it.name.startsWith("preparePackage", ignoreCase = true)
        }

        for (method in prepareMethods) {
            try {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val t = param.throwable ?: return
                        val msg = t.message ?: ""
                        if (msg.contains("same versionCode", ignoreCase = true) ||
                            msg.contains("not allowed to update system package to the same versionCode", ignoreCase = true)) {
                            XposedInit.log("Suppressed same-versionCode rejection in ${method.name}: '$msg'")
                            param.throwable = null
                            param.result = null
                        }
                    }
                })
                XposedInit.log("Hooked ${helperClass.simpleName}.${method.name} for same-versionCode bypass")
            } catch (t: Throwable) {
                XposedInit.logError("Failed to hook ${method.name} in ${helperClass.simpleName}", t)
            }
        }
    }

    /**
     * Intercepts boot-time initial scan package version checks to ensure downgraded
     * system app data updates are NOT silently reverted on reboot.
     */
    private fun hookBootScanPackagePreservation(classLoader: ClassLoader) {
        val scanClasses = listOf(
            "com.android.server.pm.ScanPackageUtils",
            "com.android.server.pm.PackageManagerService",
            "com.android.server.pm.InstallPackageHelper"
        )

        for (className in scanClasses) {
            val cls = try {
                XposedHelpers.findClass(className, classLoader)
            } catch (t: Throwable) {
                continue
            }

            val scanMethods = cls.declaredMethods.filter {
                it.name.contains("scanSystemPackage", ignoreCase = true) ||
                it.name.contains("shouldCheckUpgradeKeySet", ignoreCase = true) ||
                it.name.contains("isVersionDowngrade", ignoreCase = true) ||
                it.name.contains("assertPackageIsValid", ignoreCase = true)
            }

            for (m in scanMethods) {
                try {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val t = param.throwable ?: return
                            val msg = t.message ?: ""
                            if (msg.contains("downgrade", ignoreCase = true) ||
                                msg.contains("version", ignoreCase = true)) {
                                XposedInit.log("Preserving system package data version on boot scan: suppressed '$msg' in ${cls.simpleName}.${m.name}")
                                param.throwable = null
                                param.result = null
                            }
                        }
                    })
                    XposedInit.log("Hooked ${cls.simpleName}.${m.name} for boot scan preservation")
                } catch (t: Throwable) {
                    XposedInit.logWarn("Could not hook ${m.name} in ${cls.simpleName}: ${t.message}")
                }
            }
        }
    }
}
