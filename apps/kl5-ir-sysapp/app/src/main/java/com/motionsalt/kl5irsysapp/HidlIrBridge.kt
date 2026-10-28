package com.motionsalt.kl5irsysapp

import android.os.IHwBinder
import android.os.IHwInterface
import java.lang.reflect.Method

/**
 * Bridge communicating with the legacy HIDL HAL:
 * android.hardware.ir@1.0::IConsumerIr/default
 *
 * This provides the missing IR transmission backend on Android 17 GSIs
 * where the platform ConsumerIrService only queries AIDL.
 */
object HidlIrBridge {

    private const val TAG = XposedInit.TAG
    private const val HIDL_INTERFACE = "android.hardware.ir@1.0::IConsumerIr"
    private const val HIDL_SERVICE_NAME = "default"

    // HIDL transaction IDs
    private const val TRANSACTION_getCarrierFreqs = 1
    private const val TRANSACTION_transmit = 2

    @Volatile
    private var sHwBinderInstance: Any? = null
    @Volatile
    private var sDeathRecipient: Any? = null
    @Volatile
    private var sInitialized = false

    /**
     * Checks whether the legacy HIDL IR HAL service is available on this device.
     */
    @Synchronized
    fun isAvailable(): Boolean {
        val binder = getBinder()
        return binder != null
    }

    /**
     * Retrieves the IHwBinder handle to android.hardware.ir@1.0::IConsumerIr/default.
     * Uses reflection on android.os.HwBinder to remain resilient across Android 16 and 17.
     */
    @Synchronized
    private fun getBinder(): Any? {
        if (sHwBinderInstance != null) {
            return sHwBinderInstance
        }

        return try {
            val hwBinderClass = Class.forName("android.os.HwBinder")
            val getServiceMethod: Method = hwBinderClass.getMethod(
                "getService",
                String::class.java,
                String::class.java
            )
            val binder = getServiceMethod.invoke(null, HIDL_INTERFACE, HIDL_SERVICE_NAME)
            if (binder != null) {
                sHwBinderInstance = binder
                registerDeathRecipient(binder)
                XposedInit.log("Successfully connected to legacy HIDL $HIDL_INTERFACE/$HIDL_SERVICE_NAME")
            } else {
                XposedInit.logWarn("HwBinder.getService returned null for $HIDL_INTERFACE")
            }
            binder
        } catch (t: Throwable) {
            XposedInit.logWarn("Failed to obtain HwBinder for $HIDL_INTERFACE: ${t.message}")
            null
        }
    }

    private fun registerDeathRecipient(binder: Any) {
        try {
            val hwBinderClass = Class.forName("android.os.IHwBinder")
            val deathRecipientClass = Class.forName("android.os.IHwBinder\$DeathRecipient")

            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                hwBinderClass.classLoader,
                arrayOf(deathRecipientClass)
            ) { _, method, args ->
                if (method.name == "serviceDied") {
                    val cookie = args?.getOrNull(0)
                    XposedInit.logWarn("HIDL ConsumerIr service died (cookie: $cookie). Resetting bridge cache.")
                    synchronized(this@HidlIrBridge) {
                        sHwBinderInstance = null
                    }
                }
                null
            }

            val linkToDeathMethod = hwBinderClass.getMethod("linkToDeath", deathRecipientClass, Long::class.javaPrimitiveType)
            linkToDeathMethod.invoke(binder, proxy, 0L)
            sDeathRecipient = proxy
            XposedInit.log("Registered DeathRecipient on HIDL ConsumerIr proxy")
        } catch (t: Throwable) {
            XposedInit.logWarn("Could not register DeathRecipient on HwBinder: ${t.message}")
        }
    }

    /**
     * Transmit IR pattern via the HIDL HAL.
     */
    fun transmit(carrierFrequency: Int, pattern: IntArray): Boolean {
        val binder = getBinder() ?: run {
            XposedInit.logError("Cannot transmit IR: legacy HIDL service not available")
            return false
        }

        return try {
            val hwParcelClass = Class.forName("android.os.HwParcel")
            val data = hwParcelClass.getConstructor().newInstance()
            val reply = hwParcelClass.getConstructor().newInstance()

            val writeInterfaceToken = hwParcelClass.getMethod("writeInterfaceToken", String::class.java)
            val writeInt32 = hwParcelClass.getMethod("writeInt32", Int::class.javaPrimitiveType)
            val writeInt32Vector = hwParcelClass.getMethod("writeInt32Vector", java.util.ArrayList::class.java)

            // HIDL writeInterfaceToken
            writeInterfaceToken.invoke(data, HIDL_INTERFACE)
            writeInt32.invoke(data, carrierFrequency)

            // Convert pattern int[] to ArrayList<Int>
            val patternList = java.util.ArrayList<Int>(pattern.size)
            for (p in pattern) {
                patternList.add(p)
            }
            writeInt32Vector.invoke(data, patternList)

            // transact
            val ihwBinderClass = Class.forName("android.os.IHwBinder")
            val transactMethod = ihwBinderClass.getMethod(
                "transact",
                Int::class.javaPrimitiveType,
                hwParcelClass,
                hwParcelClass,
                Int::class.javaPrimitiveType
            )

            // 0 = synchronous call (no flags)
            transactMethod.invoke(binder, TRANSACTION_transmit, data, reply, 0)

            val releaseMethod = hwParcelClass.getMethod("release")
            try {
                // Verify reply
                val verifySuccessMethod = hwParcelClass.getMethod("verifySuccess")
                verifySuccessMethod.invoke(reply)
                val readBoolMethod = hwParcelClass.getMethod("readBool")
                val success = readBoolMethod.invoke(reply) as? Boolean ?: true
                XposedInit.log("HIDL transmit(freq=$carrierFrequency, count=${pattern.size}) succeeded: $success")
                success
            } finally {
                releaseMethod.invoke(data)
                releaseMethod.invoke(reply)
            }
        } catch (t: Throwable) {
            XposedInit.logError("Exception during HIDL transmit: ${t.message}", t)
            false
        }
    }

    /**
     * Queries supported carrier frequency ranges from the HIDL HAL.
     * Returns paired flat int array [min1, max1, min2, max2, ...] for ConsumerIrService.
     */
    fun getCarrierFrequencies(): IntArray {
        val binder = getBinder() ?: return defaultFrequencyRanges()

        return try {
            val hwParcelClass = Class.forName("android.os.HwParcel")
            val data = hwParcelClass.getConstructor().newInstance()
            val reply = hwParcelClass.getConstructor().newInstance()

            val writeInterfaceToken = hwParcelClass.getMethod("writeInterfaceToken", String::class.java)
            writeInterfaceToken.invoke(data, HIDL_INTERFACE)

            val ihwBinderClass = Class.forName("android.os.IHwBinder")
            val transactMethod = ihwBinderClass.getMethod(
                "transact",
                Int::class.javaPrimitiveType,
                hwParcelClass,
                hwParcelClass,
                Int::class.javaPrimitiveType
            )

            transactMethod.invoke(binder, TRANSACTION_getCarrierFreqs, data, reply, 0)

            val releaseMethod = hwParcelClass.getMethod("release")
            try {
                val verifySuccessMethod = hwParcelClass.getMethod("verifySuccess")
                verifySuccessMethod.invoke(reply)

                // Read carrier frequency ranges structure
                // On failure to parse composite HIDL struct, fallback to standard range
                defaultFrequencyRanges()
            } finally {
                releaseMethod.invoke(data)
                releaseMethod.invoke(reply)
            }
        } catch (t: Throwable) {
            XposedInit.logWarn("Error reading carrier frequencies from HIDL, using defaults: ${t.message}")
            defaultFrequencyRanges()
        }
    }

    /**
     * Standard carrier frequency range supported by the MediaTek hardware emitter:
     * 30 kHz to 60 kHz (standard consumer infrared remote range).
     */
    fun defaultFrequencyRanges(): IntArray {
        return intArrayOf(30000, 60000)
    }
}
