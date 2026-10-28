package com.motionsalt.kl5irsysapp

/**
 * Native JNI bridge interface for android.hardware.ir@1.0::IConsumerIr.
 *
 * Automatically falls back to HidlIrBridge (Java HwBinder IPC) if the
 * native shared library libmotionsalt_ir_bridge.so is not present.
 */
object NativeIrBridge {

    private const val LIB_NAME = "motionsalt_ir_bridge"
    private var sNativeLoaded = false

    init {
        try {
            System.loadLibrary(LIB_NAME)
            sNativeLoaded = true
            XposedInit.log("Successfully loaded native library lib$LIB_NAME.so")
        } catch (t: Throwable) {
            XposedInit.log("Native library lib$LIB_NAME.so not loaded (${t.message}), using Java HwBinder IPC fallback")
            sNativeLoaded = false
        }
    }

    fun isNativeLoaded(): Boolean = sNativeLoaded

    fun isAvailable(): Boolean {
        return if (sNativeLoaded) {
            try {
                nativeIsAvailable()
            } catch (t: Throwable) {
                HidlIrBridge.isAvailable()
            }
        } else {
            HidlIrBridge.isAvailable()
        }
    }

    fun transmit(carrierFrequency: Int, pattern: IntArray): Boolean {
        return if (sNativeLoaded) {
            try {
                nativeTransmit(carrierFrequency, pattern)
            } catch (t: Throwable) {
                HidlIrBridge.transmit(carrierFrequency, pattern)
            }
        } else {
            HidlIrBridge.transmit(carrierFrequency, pattern)
        }
    }

    fun getCarrierFrequencies(): IntArray {
        return if (sNativeLoaded) {
            try {
                nativeGetCarrierFrequencies()
            } catch (t: Throwable) {
                HidlIrBridge.getCarrierFrequencies()
            }
        } else {
            HidlIrBridge.getCarrierFrequencies()
        }
    }

    // Native JNI signatures
    private external fun nativeIsAvailable(): Boolean
    private external fun nativeTransmit(carrierFrequency: Int, pattern: IntArray): Boolean
    private external fun nativeGetCarrierFrequencies(): IntArray
}
