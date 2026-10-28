package com.motionsalt.kl5irsysapp

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

/**
 * Hooks for com.android.server.ConsumerIrService inside system_server.
 *
 * Bridges AIDL-only framework calls in Android 17 GSI to the real
 * working legacy HIDL HAL (android.hardware.ir@1.0::IConsumerIr/default).
 */
object IrBridgeHooks {

    private const val TARGET_CLASS = "com.android.server.ConsumerIrService"

    fun init(classLoader: ClassLoader) {
        val targetClass = try {
            XposedHelpers.findClass(TARGET_CLASS, classLoader)
        } catch (t: Throwable) {
            XposedInit.logWarn("Could not find $TARGET_CLASS in system_server: ${t.message}")
            return
        }

        XposedInit.log("Found $TARGET_CLASS in system_server. Attaching IR bridge hooks...")

        // 1. Hook Constructors to override mHasNativeHal when legacy HIDL is present
        hookConstructors(targetClass)

        // 2. Hook hasIrEmitter()
        hookHasIrEmitter(targetClass)

        // 3. Hook getCarrierFrequencies()
        hookGetCarrierFrequencies(targetClass)

        // 4. Hook transmit(...)
        hookTransmit(targetClass)
    }

    private fun hookConstructors(targetClass: Class<*>) {
        try {
            XposedBridge.hookAllConstructors(targetClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val thisObj = param.thisObject
                        // Check mHasNativeHal field
                        val fields = targetClass.declaredFields
                        for (f in fields) {
                            if (f.name == "mHasNativeHal" || (f.type == Boolean::class.javaPrimitiveType && f.name.contains("Hal", ignoreCase = true))) {
                                f.isAccessible = true
                                val currentVal = f.getBoolean(thisObj)
                                if (!currentVal && HidlIrBridge.isAvailable()) {
                                    f.setBoolean(thisObj, true)
                                    XposedInit.log("Overrode ConsumerIrService.${f.name} from false -> true (HIDL HAL connected)")
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        XposedInit.logWarn("Error in ConsumerIrService constructor hook: ${t.message}")
                    }
                }
            })
            XposedInit.log("Hooked ConsumerIrService constructors successfully")
        } catch (t: Throwable) {
            XposedInit.logError("Failed to hook ConsumerIrService constructors", t)
        }
    }

    private fun hookHasIrEmitter(targetClass: Class<*>) {
        try {
            val methods = targetClass.declaredMethods.filter { it.name == "hasIrEmitter" }
            if (methods.isEmpty()) {
                XposedInit.logWarn("No hasIrEmitter method found on $TARGET_CLASS")
                return
            }

            for (m in methods) {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (HidlIrBridge.isAvailable()) {
                            param.result = true
                        }
                    }
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val res = param.result as? Boolean ?: false
                        if (!res && HidlIrBridge.isAvailable()) {
                            param.result = true
                            XposedInit.log("ConsumerIrService.hasIrEmitter() overridden -> true")
                        }
                    }
                })
            }
            XposedInit.log("Hooked ConsumerIrService.hasIrEmitter() successfully")
        } catch (t: Throwable) {
            XposedInit.logError("Failed to hook ConsumerIrService.hasIrEmitter", t)
        }
    }

    private fun hookGetCarrierFrequencies(targetClass: Class<*>) {
        try {
            val methods = targetClass.declaredMethods.filter { it.name == "getCarrierFrequencies" }
            for (m in methods) {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val currentRes = param.result
                        val isEmpty = when (currentRes) {
                            null -> true
                            is IntArray -> currentRes.isEmpty()
                            is Array<*> -> currentRes.isEmpty()
                            else -> false
                        }

                        if (isEmpty && HidlIrBridge.isAvailable()) {
                            val freqs = HidlIrBridge.getCarrierFrequencies()
                            param.result = freqs
                            XposedInit.log("ConsumerIrService.getCarrierFrequencies() provided from HIDL HAL: [${freqs.joinToString()}]")
                        }
                    }
                })
            }
            XposedInit.log("Hooked ConsumerIrService.getCarrierFrequencies() successfully")
        } catch (t: Throwable) {
            XposedInit.logError("Failed to hook ConsumerIrService.getCarrierFrequencies", t)
        }
    }

    private fun hookTransmit(targetClass: Class<*>) {
        try {
            val methods = targetClass.declaredMethods.filter { it.name == "transmit" }
            if (methods.isEmpty()) {
                XposedInit.logWarn("No transmit method found on $TARGET_CLASS")
                return
            }

            for (m in methods) {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // Extract carrierFrequency and pattern args
                        var carrierFreq: Int? = null
                        var pattern: IntArray? = null

                        for (arg in param.args) {
                            when (arg) {
                                is Int -> if (carrierFreq == null && arg > 1000) carrierFreq = arg
                                is IntArray -> pattern = arg
                            }
                        }

                        if (carrierFreq != null && pattern != null) {
                            XposedInit.log("Intercepted transmit(carrierFreq=$carrierFreq, patternLen=${pattern.size}) -> delegating to HIDL HAL")
                            val sent = HidlIrBridge.transmit(carrierFreq, pattern)
                            if (sent) {
                                // Short-circuit platform AIDL call since it doesn't exist on this device
                                param.result = null
                            }
                        }
                    }
                })
            }
            XposedInit.log("Hooked ConsumerIrService.transmit() successfully")
        } catch (t: Throwable) {
            XposedInit.logError("Failed to hook ConsumerIrService.transmit", t)
        }
    }
}
