package com.forgebuild.otgenabler

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class ConnectedUsbDevice(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturerName: String,
    val productName: String,
    val deviceClass: String,
    val isMassStorage: Boolean
) {
    val vendorHex: String get() = "0x%04X".format(vendorId)
    val productHex: String get() = "0x%04X".format(productId)
    val displayTitle: String
        get() = when {
            productName.isNotBlank() -> productName
            manufacturerName.isNotBlank() -> "$manufacturerName Device"
            else -> "USB Peripheral ($vendorHex:$productHex)"
        }
}

class UsbMonitor(private val context: Context) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val _connectedDevices = MutableStateFlow<List<ConnectedUsbDevice>>(emptyList())
    val connectedDevices: StateFlow<List<ConnectedUsbDevice>> = _connectedDevices.asStateFlow()

    suspend fun refresh(): List<ConnectedUsbDevice> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ConnectedUsbDevice>()

        // 1. Check via Android UsbManager API
        try {
            usbManager?.deviceList?.values?.forEach { device ->
                list.add(
                    ConnectedUsbDevice(
                        deviceName = device.deviceName ?: "USB Port",
                        vendorId = device.vendorId,
                        productId = device.productId,
                        manufacturerName = device.manufacturerName ?: "",
                        productName = device.productName ?: "",
                        deviceClass = classifyUsbDevice(device),
                        isMassStorage = device.deviceClass == UsbConstants.USB_CLASS_MASS_STORAGE ||
                                (0 until device.interfaceCount).any { i ->
                                    device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE
                                }
                    )
                )
            }
        } catch (e: Exception) {
            RootCommander.log(LogType.WARNING, "UsbManager inspection: ${e.message}")
        }

        // 2. Check via root lsusb if list is empty
        if (list.isEmpty()) {
            val lsusb = RootCommander.execSilent("lsusb")
            if (lsusb.isSuccess && lsusb.stdout.isNotBlank()) {
                val lines = lsusb.stdout.trim().lines()
                for (line in lines) {
                    // Typical lsusb output: "Bus 001 Device 002: ID 0951:1666 Kingston Technology DataTraveler"
                    val parts = line.split("ID ")
                    if (parts.size > 1) {
                        val idAndDesc = parts[1].trim()
                        val tokens = idAndDesc.split(" ", limit = 2)
                        val ids = tokens[0].split(":")
                        val vId = ids.getOrNull(0)?.toIntOrNull(16) ?: 0
                        val pId = ids.getOrNull(1)?.toIntOrNull(16) ?: 0
                        val desc = tokens.getOrNull(1) ?: "USB Device"
                        list.add(
                            ConnectedUsbDevice(
                                deviceName = line.substringBefore(":"),
                                vendorId = vId,
                                productId = pId,
                                manufacturerName = desc.substringBefore(" "),
                                productName = desc,
                                deviceClass = "Peripheral (lsusb)",
                                isMassStorage = desc.contains("Storage", ignoreCase = true) || desc.contains("Flash", ignoreCase = true)
                            )
                        )
                    }
                }
            }
        }

        _connectedDevices.value = list
        list
    }

    private fun classifyUsbDevice(device: UsbDevice): String {
        return when (device.deviceClass) {
            UsbConstants.USB_CLASS_MASS_STORAGE -> "Mass Storage (Flash Drive)"
            UsbConstants.USB_CLASS_HID -> "Human Interface Device (Keyboard/Mouse)"
            UsbConstants.USB_CLASS_AUDIO -> "Audio Device (DAC / Headset)"
            UsbConstants.USB_CLASS_HUB -> "USB Hub"
            UsbConstants.USB_CLASS_COMM -> "Communications Device"
            UsbConstants.USB_CLASS_VIDEO -> "Video Camera / UVC"
            UsbConstants.USB_CLASS_WIRELESS_CONTROLLER -> "Wireless Gamepad / Controller"
            else -> {
                // Check interface classes
                for (i in 0 until device.interfaceCount) {
                    when (device.getInterface(i).interfaceClass) {
                        UsbConstants.USB_CLASS_MASS_STORAGE -> return "Mass Storage (Flash Drive)"
                        UsbConstants.USB_CLASS_HID -> return "Human Interface Device (Keyboard/Mouse)"
                        UsbConstants.USB_CLASS_AUDIO -> return "Audio Device (DAC / Headset)"
                    }
                }
                "Generic USB Device"
            }
        }
    }
}
